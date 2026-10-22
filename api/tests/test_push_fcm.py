"""FCM 발송기 — 응답 분류, 재시도·백오프, 인증 오류 중단, 동시성 상한 (httpx MockTransport)."""

import asyncio

import httpx
import pytest

import fcm
from fcm import FcmAuthError, FcmSender, PushMessage, classify_response, retry_delay


MESSAGE = PushMessage(
    title="테스트 알림",
    body="구구 어드벤처 알림이 잘 도착했어요.",
    kind="test",
    link="home",
    android_channel="service_notice",
    ttl_seconds=3600,
)


def fcm_error(status: int, rpc_status: str, error_code: str | None = None, token_field: bool = False, message: str = "x"):
    details = []
    if error_code:
        details.append({"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": error_code})
    if token_field:
        details.append({
            "@type": "type.googleapis.com/google.rpc.BadRequest",
            "fieldViolations": [{"field": "message.token", "description": "Invalid registration token"}],
        })
    return httpx.Response(status, json={"error": {"code": status, "message": message, "status": rpc_status, "details": details}})


class StaticToken:
    async def get_token(self) -> str:
        return "access-token"


def make_sender(handler, sleeps=None, **kwargs) -> FcmSender:
    async def fake_sleep(seconds: float) -> None:
        if sleeps is not None:
            sleeps.append(seconds)

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return FcmSender("gugu-adventure", StaticToken(), client, sleep=fake_sleep, **kwargs)


# --- 분류 --------------------------------------------------------------------


@pytest.mark.parametrize(
    "response,expected",
    [
        (httpx.Response(200, json={"name": "projects/p/messages/1"}), "sent"),
        (fcm_error(404, "NOT_FOUND", "UNREGISTERED"), "prune"),
        # 세부 코드 없는 404 는 프로젝트·설정 오류일 수 있다 — 토큰을 지우지 않는다
        (fcm_error(404, "NOT_FOUND"), "fail"),
        (fcm_error(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", token_field=True), "prune"),
        (fcm_error(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", message="The registration token is not a valid FCM registration token"), "prune"),
        # 페이로드 오류일 수 있는 INVALID_ARGUMENT 는 토큰을 지우지 않는다
        (fcm_error(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", message="Invalid JSON payload"), "fail"),
        (fcm_error(403, "PERMISSION_DENIED", "SENDER_ID_MISMATCH"), "prune"),
        # APNs 키 문제는 iOS 토큰 단위 실패 — 배치를 멈추지 않는다
        (fcm_error(401, "UNAUTHENTICATED", "THIRD_PARTY_AUTH_ERROR"), "fail"),
        (fcm_error(401, "UNAUTHENTICATED"), "auth"),
        (fcm_error(403, "PERMISSION_DENIED"), "auth"),
        (fcm_error(429, "RESOURCE_EXHAUSTED", "QUOTA_EXCEEDED"), "retry"),
        (fcm_error(503, "UNAVAILABLE", "UNAVAILABLE"), "retry"),
        (httpx.Response(500, text="oops"), "retry"),
        (httpx.Response(418, text="teapot"), "fail"),
    ],
)
def test_classify_response(response, expected):
    assert classify_response(response)[0] == expected


def test_retry_delay_uses_retry_after_then_exponential():
    assert retry_delay(httpx.Response(429, headers={"Retry-After": "7"}), attempt=0) == 7
    assert retry_delay(httpx.Response(429, headers={"Retry-After": "9999"}), attempt=0) == fcm.MAX_BACKOFF_SECONDS
    assert retry_delay(httpx.Response(503), attempt=0, base=1.0) == 1.0
    assert retry_delay(httpx.Response(503), attempt=2, base=1.0) == 4.0
    assert retry_delay(None, attempt=1, base=0.5) == 1.0


def test_build_message_shape():
    body = fcm.build_message("tok", MESSAGE, now_epoch=1_000)
    message = body["message"]
    assert message["token"] == "tok"
    assert message["notification"] == {"title": MESSAGE.title, "body": MESSAGE.body}
    assert message["data"] == {"kind": "test", "link": "home"}
    assert message["android"] == {"ttl": "3600s", "notification": {"channel_id": "service_notice"}}
    assert message["apns"]["headers"]["apns-expiration"] == "4600"
    assert message["apns"]["payload"]["aps"]["sound"] == "default"


# --- 발송기 ------------------------------------------------------------------


def test_send_success_sets_bearer_and_body():
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(200, json={"name": "projects/gugu-adventure/messages/42"})

    result = asyncio.run(make_sender(handler).send("tok-1", MESSAGE))
    assert result.outcome == "sent" and result.message_id.endswith("/42")
    assert seen[0].url.path == "/v1/projects/gugu-adventure/messages:send"
    assert seen[0].headers["authorization"] == "Bearer access-token"


def test_unregistered_token_is_marked_invalid_without_retry():
    calls = []

    def handler(request):
        calls.append(1)
        return fcm_error(404, "NOT_FOUND", "UNREGISTERED")

    result = asyncio.run(make_sender(handler).send("tok", MESSAGE))
    assert result.outcome == "invalid_token" and result.error_code == "UNREGISTERED"
    assert len(calls) == 1


def test_transient_errors_retry_with_backoff_then_succeed():
    responses = iter([
        fcm_error(503, "UNAVAILABLE"),
        httpx.Response(429, headers={"Retry-After": "5"}, json={"error": {"status": "RESOURCE_EXHAUSTED"}}),
        httpx.Response(200, json={"name": "m"}),
    ])
    sleeps: list[float] = []
    result = asyncio.run(make_sender(lambda request: next(responses), sleeps, backoff_base=1.0).send("tok", MESSAGE))
    assert result.outcome == "sent"
    assert sleeps == [1.0, 5.0]


def test_retries_are_capped_at_three():
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(500)

    sleeps: list[float] = []
    result = asyncio.run(make_sender(handler, sleeps, backoff_base=1.0).send("tok", MESSAGE))
    assert result.outcome == "failed" and result.status_code == 500
    assert len(calls) == 4  # 최초 1회 + 재시도 3회
    assert sleeps == [1.0, 2.0, 4.0]


def test_network_error_is_retried():
    attempts = []

    def handler(request):
        attempts.append(1)
        if len(attempts) == 1:
            raise httpx.ConnectError("boom", request=request)
        return httpx.Response(200, json={"name": "m"})

    assert asyncio.run(make_sender(handler, []).send("tok", MESSAGE)).outcome == "sent"


def test_auth_error_aborts_batch():
    def handler(request):
        return fcm_error(401, "UNAUTHENTICATED")

    with pytest.raises(FcmAuthError) as info:
        asyncio.run(make_sender(handler).send_many([f"tok-{i}" for i in range(5)], MESSAGE))
    assert info.value.status_code == 401


def test_permission_denied_aborts_batch():
    def handler(request):
        return fcm_error(403, "PERMISSION_DENIED")

    with pytest.raises(FcmAuthError) as info:
        asyncio.run(make_sender(handler).send_many([f"tok-{i}" for i in range(5)], MESSAGE))
    assert info.value.status_code == 403


def test_third_party_auth_error_fails_only_that_token():
    calls = []

    def handler(request):
        calls.append(1)
        if '"ios-tok"' in request.read().decode():
            return fcm_error(401, "UNAUTHENTICATED", "THIRD_PARTY_AUTH_ERROR")
        return httpx.Response(200, json={"name": "m"})

    results = asyncio.run(make_sender(handler).send_many(["android-tok", "ios-tok", "android-tok2"], MESSAGE))
    assert [r.outcome for r in results] == ["sent", "failed", "sent"]
    assert results[1].status_code == 401 and results[1].error_code == "THIRD_PARTY_AUTH_ERROR"
    assert len(calls) == 3  # 재시도 없이 토큰당 1회


def test_plain_not_found_is_failed_not_pruned():
    calls = []

    def handler(request):
        calls.append(1)
        return fcm_error(404, "NOT_FOUND", message="Requested entity was not found.")

    results = asyncio.run(make_sender(handler).send_many(["tok-a", "tok-b"], MESSAGE))
    assert [r.outcome for r in results] == ["failed", "failed"]
    assert all(r.status_code == 404 and r.error_code == "NOT_FOUND" for r in results)
    assert len(calls) == 2


def test_send_many_mixed_results_keep_order():
    def handler(request):
        token = request.read().decode()
        if '"bad"' in token:
            return fcm_error(404, "NOT_FOUND", "UNREGISTERED")
        return httpx.Response(200, json={"name": "m"})

    results = asyncio.run(make_sender(handler).send_many(["good", "bad", "good2"], MESSAGE))
    assert [r.outcome for r in results] == ["sent", "invalid_token", "sent"]
    assert [r.token for r in results] == ["good", "bad", "good2"]


def test_concurrency_is_limited_to_ten():
    state = {"active": 0, "peak": 0}

    async def run():
        async def handler(request):
            state["active"] += 1
            state["peak"] = max(state["peak"], state["active"])
            await asyncio.sleep(0.01)
            state["active"] -= 1
            return httpx.Response(200, json={"name": "m"})

        sender = make_sender(handler)
        return await sender.send_many([f"t{i}" for i in range(40)], MESSAGE)

    results = asyncio.run(run())
    assert len(results) == 40
    assert state["peak"] == fcm.MAX_CONCURRENCY


def test_get_sender_without_config_raises_push_unavailable(monkeypatch):
    monkeypatch.setattr(fcm, "_sender", None)
    with pytest.raises(fcm.PushUnavailable):
        fcm.get_sender()
    assert fcm.is_configured() is False
