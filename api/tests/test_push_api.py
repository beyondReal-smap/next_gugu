"""푸시 API — 요청 모델 경계 검증과 상태 코드 구분(400/401/404/503)."""

import json
import uuid

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import ValidationError

import push
import supabase_admin
from auth import AuthUser, require_user


INSTALL_ID = "3f0c8f9e-8a49-4a5e-9a4e-0d1f0b6b8c11"
TOKEN = "dXk3:APA91bH" + "a" * 140


def register_body(**overrides):
    body = {
        "pushInstallId": INSTALL_ID,
        "fcmToken": TOKEN,
        "platform": "ios",
        "locale": "ko-KR",
        "timeZone": "Asia/Seoul",
        "appVersion": "2.5.0 (27)",
        "serviceOptIn": True,
        "learningOptIn": False,
        "localReminderEnabled": True,
    }
    body.update(overrides)
    return body


def parse(model, body):
    return model.model_validate_json(json.dumps(body), strict=True)


# --- 요청 모델 ---------------------------------------------------------------


def test_register_request_accepts_valid_body():
    request = parse(push.RegisterDeviceRequest, register_body())
    assert request.pushInstallId == uuid.UUID(INSTALL_ID)
    assert request.platform == "ios"


def test_register_request_allows_missing_optional_locale_fields():
    body = register_body()
    del body["locale"], body["timeZone"]
    assert parse(push.RegisterDeviceRequest, body).locale is None


@pytest.mark.parametrize(
    "overrides",
    [
        {"fcmToken": "a" * 4097},
        {"fcmToken": "short"},
        {"fcmToken": "abc def" + "x" * 20},
        {"fcmToken": "<script>" + "x" * 20},
        {"platform": "web"},
        {"pushInstallId": "not-a-uuid"},
        {"serviceOptIn": "true"},  # strict — 문자열 불리언 거부
        {"locale": "ko KR"},
        {"timeZone": "../../etc"},
        {"appVersion": ""},
        {"appVersion": "x" * 33},
        {"marketingOptIn": True},  # 광고 동의는 /consents 로만 — extra=forbid
    ],
)
def test_register_request_rejects_invalid_fields(overrides):
    with pytest.raises(ValidationError):
        parse(push.RegisterDeviceRequest, register_body(**overrides))


def test_register_request_requires_flags():
    body = register_body()
    del body["learningOptIn"]
    with pytest.raises(ValidationError):
        parse(push.RegisterDeviceRequest, body)


def test_consent_request_only_marketing_kind():
    ok = parse(push.ConsentRequest, {"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": True})
    assert ok.source == "settings"
    for bad in (
        {"pushInstallId": INSTALL_ID, "kind": "marketing_night", "granted": True},
        {"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": 1},
        {"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": True, "source": "admin"},
        {"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": True, "birthYear": 1990},
    ):
        with pytest.raises(ValidationError):
            parse(push.ConsentRequest, bad)


# --- 라우터 ------------------------------------------------------------------


USER = AuthUser(id="11111111-2222-3333-4444-555555555555", email=None, app_metadata={})


@pytest.fixture
def client(monkeypatch):
    calls: list[tuple[str, dict]] = []
    responses: dict[str, object] = {}

    async def fake_call_rpc(name, payload):
        calls.append((name, payload))
        result = responses[name]
        if isinstance(result, Exception):
            raise result
        return result

    monkeypatch.setattr(supabase_admin, "call_rpc", fake_call_rpc)
    app = FastAPI()
    app.include_router(push.router)
    app.dependency_overrides[require_user] = lambda: USER
    test_client = TestClient(app)
    test_client.calls = calls  # type: ignore[attr-defined]
    test_client.responses = responses  # type: ignore[attr-defined]
    return test_client


def register_rpc_result(**overrides):
    result = {
        "pushInstallId": INSTALL_ID,
        "serviceOptIn": True,
        "learningOptIn": False,
        "localReminderEnabled": True,
        "marketingOptIn": False,
        "marketingOptInAt": None,
        "marketingReconfirmedAt": None,
        "updatedAt": "2026-10-19T10:00:00.123456+00:00",
        "replacedTokenRows": 0,
        "reassignedRows": 0,
        "evictedDevices": 0,
    }
    result.update(overrides)
    return result


def test_register_device_returns_server_consent_state(client):
    client.responses["gugu_push_register_device"] = register_rpc_result()
    res = client.post("/api/v1/push/devices", json=register_body())
    assert res.status_code == 200
    body = res.json()
    assert body["marketingOptIn"] is False
    assert "replacedTokenRows" not in body  # 내부 건수는 응답에 노출하지 않는다
    name, payload = client.calls[0]
    assert name == "gugu_push_register_device"
    assert payload["p_user_id"] == USER.id and payload["p_fcm_token"] == TOKEN


def test_register_device_invalid_body_is_400(client):
    res = client.post("/api/v1/push/devices", json=register_body(platform="web"))
    assert res.status_code == 400
    assert res.json()["detail"]["code"] == "INVALID_REQUEST"
    assert client.calls == []


def test_register_device_store_down_is_503(client):
    client.responses["gugu_push_register_device"] = supabase_admin.SupabaseDataError("gugu_push_register_device", 500)
    res = client.post("/api/v1/push/devices", json=register_body())
    assert res.status_code == 503
    assert res.json()["detail"]["code"] == "PUSH_STORE_UNAVAILABLE"


def test_register_device_bad_store_contract_is_502(client):
    client.responses["gugu_push_register_device"] = {"pushInstallId": INSTALL_ID}
    res = client.post("/api/v1/push/devices", json=register_body())
    assert res.status_code == 502


def test_consent_returns_recorded_at_and_sender(client):
    client.responses["gugu_push_set_consent"] = {
        "found": True, "kind": "marketing", "granted": True, "recordedAt": "2026-10-19T01:02:03+00:00",
    }
    res = client.post(
        "/api/v1/push/devices/consents",
        json={"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": True},
    )
    assert res.status_code == 200
    assert res.json() == {
        "kind": "marketing",
        "granted": True,
        "recordedAt": "2026-10-19T01:02:03Z",
        "sender": "구구 어드벤처",
    }


def test_consent_for_unknown_or_foreign_device_is_404(client):
    client.responses["gugu_push_set_consent"] = {"found": False}
    res = client.post(
        "/api/v1/push/devices/consents",
        json={"pushInstallId": INSTALL_ID, "kind": "marketing", "granted": False},
    )
    assert res.status_code == 404
    assert res.json()["detail"]["code"] == "PUSH_DEVICE_NOT_FOUND"


@pytest.mark.parametrize("status,http,deleted", [("deleted", 200, True), ("absent", 200, False), ("forbidden", 404, None)])
def test_unregister_is_idempotent_and_hides_foreign_devices(client, status, http, deleted):
    client.responses["gugu_push_unregister_device"] = {"status": status}
    res = client.delete(f"/api/v1/push/devices/{INSTALL_ID}")
    assert res.status_code == http
    if deleted is not None:
        assert res.json() == {"deleted": deleted}


def test_unregister_invalid_id_is_400(client):
    res = client.delete("/api/v1/push/devices/not-a-uuid")
    assert res.status_code == 400
    assert res.json()["detail"]["code"] == "INVALID_PUSH_INSTALL_ID"


def test_missing_authorization_is_401(monkeypatch):
    app = FastAPI()
    app.include_router(push.router)
    res = TestClient(app).post("/api/v1/push/devices", json=register_body())
    assert res.status_code == 401
