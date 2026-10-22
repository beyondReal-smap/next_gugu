"""자동 알림 배치 — 발송 창, 이용권 필터, dedupe 키, dry-run, 실행 기록."""

import asyncio
from datetime import datetime

import pytest

import fcm
import push_jobs
import supabase_admin
from fcm import SendResult
from push_schedule import KST


GUARDIAN = "aaaaaaaa-0000-0000-0000-000000000001"
NO_PLAN = "aaaaaaaa-0000-0000-0000-000000000002"
INSTALL = "bbbbbbbb-0000-0000-0000-000000000001"
SUNDAY_EVENING = datetime(2026, 10, 18, 19, 5, tzinfo=KST)
MONDAY_NOON = datetime(2026, 10, 19, 12, 5, tzinfo=KST)


class FakeSender:
    def __init__(self, outcome="sent"):
        self.sent: list[tuple[list[str], str]] = []
        self.titles: list[str] = []
        self.outcome = outcome

    async def send_many(self, tokens, message):
        self.sent.append((list(tokens), message.kind))
        self.titles.append(message.title)
        return [SendResult(token, self.outcome, 200, None) for token in tokens]


@pytest.fixture
def rpc(monkeypatch):
    state = {"calls": [], "claimed": set(), "responses": {}}

    async def fake_call_rpc(name, payload):
        state["calls"].append((name, payload))
        if name == "gugu_push_claim_send":
            key = payload["p_dedupe_key"]
            if key in state["claimed"]:
                return {"claimed": False, "status": "sent"}
            state["claimed"].add(key)
            return {"claimed": True, "status": "claimed"}
        if name in ("gugu_push_finish_send", "gugu_push_record_job_run"):
            return {"ok": True}
        if name == "gugu_push_prune_tokens":
            return {"prunedByToken": len(payload.get("p_tokens") or []), "prunedStale": 3, "dryRun": bool(payload.get("p_dry_run"))}
        if name == "gugu_push_mark_reconfirm_notified":
            return {"updated": True, "recordedAt": "2026-10-19T03:05:00+00:00"}
        return state["responses"][name]

    monkeypatch.setattr(supabase_admin, "call_rpc", fake_call_rpc)
    monkeypatch.setattr(push_jobs, "active_features", lambda user_id: {"longitudinal_report"} if user_id == GUARDIAN else set())
    return state


def weekly_candidates():
    return {"candidates": [
        {"userId": GUARDIAN, "devices": [{"pushInstallId": INSTALL, "fcmToken": "tok-g", "platform": "ios"}]},
        {"userId": NO_PLAN, "devices": [{"pushInstallId": INSTALL, "fcmToken": "tok-n", "platform": "android"}]},
    ]}


def test_weekly_report_skips_outside_window(rpc):
    # 수요일 낮 — 지구상 어느 시간대도 일 19시~월 20시가 아니다 (화요일 낮 KST 는 UTC-12 에서 아직 월요일)
    outcome = asyncio.run(push_jobs.run_weekly_report(datetime(2026, 10, 21, 12, 0, tzinfo=KST), dry_run=False))
    assert outcome.status == "skipped"
    assert rpc["calls"] == []


def test_weekly_report_sends_once_per_entitled_guardian(rpc, monkeypatch):
    rpc["responses"]["gugu_push_weekly_candidates"] = weekly_candidates()
    sender = FakeSender()
    monkeypatch.setattr(fcm, "get_sender", lambda: sender)

    first = asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=False))
    assert first.detail["sent"] == 1 and first.detail["noEntitlement"] == 1
    assert sender.sent == [(["tok-g"], "weekly_report")]
    claim = next(payload for name, payload in rpc["calls"] if name == "gugu_push_claim_send")
    assert claim["p_dedupe_key"] == f"weekly_report:{GUARDIAN}:2026-W42"
    # 활동 조회는 최근 8일 — 기기마다 시간대가 달라 한 번 조회로 "현지 월~일"을 맞출 수 없다
    window = next(payload for name, payload in rpc["calls"] if name == "gugu_push_weekly_candidates")
    assert window == {"p_from": "2026-10-10T19:05:00+09:00", "p_to": "2026-10-18T19:05:00+09:00"}

    # 월요일 보충 실행은 같은 주라 다시 보내지 않는다
    second = asyncio.run(push_jobs.run_weekly_report(MONDAY_NOON, dry_run=False))
    assert second.detail["alreadySent"] == 1 and second.detail["sent"] == 0
    assert len(sender.sent) == 1


def overseas_candidates():
    return {"candidates": [{"userId": GUARDIAN, "devices": [
        {"pushInstallId": INSTALL, "fcmToken": "tok-ko", "platform": "ios", "locale": "ko-KR", "timeZone": "Asia/Seoul"},
        {"pushInstallId": INSTALL, "fcmToken": "tok-la", "platform": "ios", "locale": "en-US",
         "timeZone": "America/Los_Angeles"},
        {"pushInstallId": INSTALL, "fcmToken": "tok-jp", "platform": "android", "locale": "ja-JP", "timeZone": "Asia/Tokyo"},
    ]}]}


def test_weekly_report_uses_device_local_time_and_language(rpc, monkeypatch):
    rpc["responses"]["gugu_push_weekly_candidates"] = overseas_candidates()
    sender = FakeSender()
    monkeypatch.setattr(fcm, "get_sender", lambda: sender)

    # 일 19:05 KST — 서울·도쿄는 창 안, LA 는 일요일 새벽 3시라 보내지 않는다
    first = asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=False))
    assert first.detail["sent"] == 2 and first.detail["notInLocalWindow"] == 1
    assert sorted(sender.sent) == [(["tok-jp"], "weekly_report"), (["tok-ko"], "weekly_report")]
    assert sorted(sender.titles) == sorted(["이번 주 학습 리포트가 도착했어요", "今週の学習レポートが届きました"])

    # 월 11:30 KST = LA 일요일 19:30 — LA 기기에 영어로, 같은 현지 주(W42)
    asyncio.run(push_jobs.run_weekly_report(datetime(2026, 10, 19, 11, 30, tzinfo=KST), dry_run=False))
    assert sender.sent[-1] == (["tok-la"], "weekly_report")
    assert sender.titles[-1] == "This week's learning report is ready"
    keys = [payload["p_dedupe_key"] for name, payload in rpc["calls"] if name == "gugu_push_claim_send"]
    assert f"weekly_report:{GUARDIAN}:2026-W42:en" in keys and f"weekly_report:{GUARDIAN}:2026-W42" in keys


def test_weekly_report_sends_korean_to_pre_multilingual_apps(rpc, monkeypatch):
    # 2.6 이전 앱은 화면이 한국어뿐인데 locale 에 기기 언어를 보냈다 — 기기 언어가 영어여도 한국어로
    rpc["responses"]["gugu_push_weekly_candidates"] = {"candidates": [{"userId": GUARDIAN, "devices": [
        {"pushInstallId": INSTALL, "fcmToken": "tok-old", "platform": "android", "locale": "en-US",
         "timeZone": "Asia/Seoul", "appVersion": "2.5.0"},
        {"pushInstallId": INSTALL, "fcmToken": "tok-new", "platform": "ios", "locale": "ja-JP",
         "timeZone": "Asia/Seoul", "appVersion": "2.6.0 (34)"},
    ]}]}
    sender = FakeSender()
    monkeypatch.setattr(fcm, "get_sender", lambda: sender)
    asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=False))
    assert sorted(sender.sent) == [(["tok-new"], "weekly_report"), (["tok-old"], "weekly_report")]
    assert sorted(sender.titles) == sorted(["이번 주 학습 리포트가 도착했어요", "今週の学習レポートが届きました"])


def test_weekly_report_skips_unknown_time_zone(rpc, monkeypatch):
    rpc["responses"]["gugu_push_weekly_candidates"] = {"candidates": [{"userId": GUARDIAN, "devices": [
        {"pushInstallId": INSTALL, "fcmToken": "tok-x", "platform": "ios", "locale": "en-US", "timeZone": "Mars/Base"},
    ]}]}
    monkeypatch.setattr(fcm, "get_sender", lambda: FakeSender())
    outcome = asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=False))
    assert outcome.detail["invalidTimeZone"] == 1 and outcome.detail["sent"] == 0


def test_weekly_report_dry_run_does_not_claim_or_send(rpc, monkeypatch):
    rpc["responses"]["gugu_push_weekly_candidates"] = weekly_candidates()

    def no_sender():
        raise AssertionError("dry-run 은 발송기를 만들지 않는다")

    monkeypatch.setattr(fcm, "get_sender", no_sender)
    outcome = asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=True))
    assert outcome.status == "dry_run" and outcome.detail["wouldSend"] == 1
    assert not any(name == "gugu_push_claim_send" for name, _ in rpc["calls"])


def test_reconfirm_sends_and_marks_notified(rpc, monkeypatch):
    rpc["responses"]["gugu_push_reconfirm_candidates"] = {"candidates": [{
        "pushInstallId": INSTALL,
        "userId": GUARDIAN,
        "fcmToken": "tok-m",
        "platform": "ios",
        "marketingOptInAt": "2024-10-01T01:00:00+00:00",
        "basisAt": "2024-10-01T01:00:00+00:00",
    }]}
    sender = FakeSender()
    monkeypatch.setattr(fcm, "get_sender", lambda: sender)
    outcome = asyncio.run(push_jobs.run_marketing_reconfirm(MONDAY_NOON, dry_run=False))
    assert outcome.detail["sent"] == 1 and outcome.detail["marked"] == 1
    assert sender.sent == [(["tok-m"], "marketing_reconfirm")]
    names = [name for name, _ in rpc["calls"]]
    assert names.index("gugu_push_finish_send") < names.index("gugu_push_mark_reconfirm_notified")


def test_reconfirm_skips_before_ten(rpc):
    outcome = asyncio.run(push_jobs.run_marketing_reconfirm(datetime(2026, 10, 19, 9, 5, tzinfo=KST), dry_run=False))
    assert outcome.status == "skipped"


def test_invalid_tokens_are_pruned(rpc, monkeypatch):
    rpc["responses"]["gugu_push_weekly_candidates"] = weekly_candidates()
    monkeypatch.setattr(fcm, "get_sender", lambda: FakeSender(outcome="invalid_token"))
    outcome = asyncio.run(push_jobs.run_weekly_report(SUNDAY_EVENING, dry_run=False))
    assert outcome.detail["failed"] == 1 and outcome.detail["pruned"] == 1
    prune = next(payload for name, payload in rpc["calls"] if name == "gugu_push_prune_tokens")
    assert prune == {"p_tokens": ["tok-g"]}


def test_run_due_records_every_job_and_reports_failure(rpc, monkeypatch):
    def unavailable():
        raise fcm.PushUnavailable("설정 없음")

    rpc["responses"]["gugu_push_weekly_candidates"] = weekly_candidates()
    rpc["responses"]["gugu_push_reconfirm_candidates"] = {"candidates": []}
    monkeypatch.setattr(fcm, "get_sender", unavailable)
    code = asyncio.run(push_jobs.run_due(dry_run=False, now=SUNDAY_EVENING))
    assert code == 1
    records = [payload for name, payload in rpc["calls"] if name == "gugu_push_record_job_run"]
    # 한 작업이 실패해도 나머지는 돌고, 모두 기록된다(일요일 19:05 는 재확인 창 10~20시 안)
    assert [(r["p_job"], r["p_status"]) for r in records] == [
        ("weekly_report", "failed"),
        ("marketing_reconfirm", "ok"),
        ("prune", "ok"),
    ]
    assert records[0]["p_detail"] == {"error": "PushUnavailable"}
