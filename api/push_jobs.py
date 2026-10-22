"""자동 알림 배치 — pm2 cron(gugu-push-cron)으로 매시 5분에 실행한다.

    uv run python push_jobs.py run-due --dry-run   # 대상·건수만 계산(발송·정리·선점 없음)
    uv run python push_jobs.py run-due

작업(모두 멱등 — 같은 시간에 두 번 돌아도 중복 발송이 없다)
- weekly_report: 기기 현지 일 19:00 ~ 월 20:00 (시간대 없는 2.6 이전 기기는 KST). 보호자 + 최근 8일
  학습 활동 + 학습 소식 켠 기기(RPC) 중 이용권(entitlements longitudinal_report)이 있는 계정.
  문구는 기기 앱 언어별. dedupe weekly_report:<user>:<현지 ISO주>(ko) / …:<ISO주>:<언어>(en·ja).
- marketing_reconfirm: 10:00~20:59 KST. 광고 수신동의(또는 마지막 확인) 후 2년 - 14일이 지난 기기에
  동의 사실·유지/철회 방법 통지(시행령 제62조의3). 보낸 뒤 확인 시각을 기록해 다음 주기는 2년 뒤.
- prune: 270일 동안 앱이 등록을 갱신하지 않은 기기 삭제(시간 제한 없음).

모든 발송은 08:00~20:59 에만 한다(정보성 포함, 주간 리포트는 기기 현지 시각·그 밖은 KST). 서버 학습 공백 알림은 만들지 않는다
(미결정 2 — 공백은 앱 로컬 알림이 처리한다). 실행 결과는 push_job_runs 에 남고
/api/health/ready 의 checks.push_cron 이 이를 본다.
"""

import argparse
import asyncio
import logging
import sys
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Literal, Optional

from fastapi import HTTPException
from pydantic import AwareDatetime, BaseModel, ValidationError

import fcm
import supabase_admin
from entitlements import active_features
from observability import mask_identifier
from push_delivery import PushStoreContractError, deliver, prune_stale
from push_schedule import (
    WEEKLY_ACTIVITY_LOOKBACK,
    device_zone,
    is_reconfirm_due,
    is_reconfirm_window,
    reconfirm_cutoff,
    stale_device_cutoff,
    weekly_report_week,
    weekly_window_open_anywhere,
)
from push_templates import Lang, language_for_device, render


log = logging.getLogger("gugu-api")

JobName = Literal["weekly_report", "marketing_reconfirm", "prune"]
JobStatus = Literal["ok", "skipped", "dry_run", "failed"]

# 한 작업의 실패를 기록하고 다음 작업으로 넘어가는 운영 오류. 그 밖의 예외(버그)는 기록 후 즉시 중단한다.
OPERATIONAL_ERRORS = (
    fcm.PushUnavailable,
    fcm.FcmAuthError,
    supabase_admin.SupabaseDataError,
    PushStoreContractError,
    HTTPException,  # entitlements 의 ENTITLEMENT_STORE_UNAVAILABLE(503)
)


@dataclass
class JobOutcome:
    status: JobStatus
    detail: dict[str, Any] = field(default_factory=dict)


class _Device(BaseModel):
    pushInstallId: str
    fcmToken: str
    platform: str
    # 2026-10-22 migration 이전 RPC 는 보내지 않는다 — 없으면 한국 기기(ko·KST)
    locale: Optional[str] = None
    timeZone: Optional[str] = None
    # 2026-10-23 migration 이전 RPC 는 보내지 않는다 — 없으면 locale 만으로 언어를 고른다
    appVersion: Optional[str] = None


class _WeeklyCandidate(BaseModel):
    userId: str
    devices: list[_Device]


class _WeeklyCandidates(BaseModel):
    candidates: list[_WeeklyCandidate]


class _ReconfirmCandidate(BaseModel):
    pushInstallId: str
    userId: str
    fcmToken: str
    platform: str
    marketingOptInAt: AwareDatetime
    basisAt: AwareDatetime


class _ReconfirmCandidates(BaseModel):
    candidates: list[_ReconfirmCandidate]


def _validate(model: type[BaseModel], raw: dict[str, Any], operation: str) -> Any:
    try:
        return model.model_validate(raw)
    except ValidationError as exc:
        raise PushStoreContractError(f"{operation} 응답 계약 오류") from exc


def _sender(dry_run: bool) -> Optional[fcm.FcmSender]:
    return None if dry_run else fcm.get_sender()


def _weekly_groups(devices: list[_Device], now: datetime) -> tuple[dict[tuple[str, Lang], list[str]], int, int]:
    """지금 현지 발송 창이 열린 기기를 (현지 ISO 주, 언어)별 토큰으로 묶는다.
    반환: (묶음, 창 밖 기기 수, 시간대를 알 수 없는 기기 수)"""
    groups: dict[tuple[str, Lang], list[str]] = {}
    outside = invalid = 0
    for device in devices:
        try:
            zone = device_zone(device.timeZone)
        except ValueError:
            invalid += 1
            log.warning("주간 리포트: 알 수 없는 시간대 install=%s", mask_identifier(device.pushInstallId))
            continue
        week = weekly_report_week(now, zone)
        if week is None:
            outside += 1
            continue
        groups.setdefault((week.iso_key, language_for_device(device.locale, device.appVersion)), []).append(device.fcmToken)
    return groups, outside, invalid


async def run_weekly_report(now: datetime, dry_run: bool) -> JobOutcome:
    if not weekly_window_open_anywhere(now):
        return JobOutcome("skipped", {"reason": "outside_window"})

    raw = await supabase_admin.call_rpc(
        "gugu_push_weekly_candidates",
        {"p_from": (now - WEEKLY_ACTIVITY_LOOKBACK).isoformat(), "p_to": now.isoformat()},
    )
    candidates = _validate(_WeeklyCandidates, raw, "gugu_push_weekly_candidates").candidates
    counts = {"candidates": len(candidates), "notInLocalWindow": 0, "invalidTimeZone": 0, "noEntitlement": 0,
              "alreadySent": 0, "wouldSend": 0, "sent": 0, "partial": 0, "failed": 0, "pruned": 0}
    sender = _sender(dry_run) if candidates else None

    for candidate in candidates:
        groups, outside, invalid = _weekly_groups(candidate.devices, now)
        counts["notInLocalWindow"] += outside
        counts["invalidTimeZone"] += invalid
        if not groups:
            continue
        # 구매 원장(MariaDB)은 블로킹 조회라 스레드에서 한다
        features = await asyncio.to_thread(active_features, candidate.userId)
        if "longitudinal_report" not in features:
            counts["noEntitlement"] += 1
            continue
        for (week_key, lang), tokens in groups.items():
            if dry_run or sender is None:
                counts["wouldSend"] += 1
                continue
            # 한국어는 2.6 이전과 같은 키 — 같은 주에 두 번 보내지 않는다
            suffix = "" if lang == "ko" else f":{lang}"
            report = await deliver(
                sender,
                dedupe_key=f"weekly_report:{candidate.userId}:{week_key}{suffix}",
                kind="weekly_report",
                user_id=candidate.userId,
                tokens=tokens,
                message=render("weekly_report", lang=lang),
            )
            if not report.claimed:
                counts["alreadySent"] += 1
                continue
            counts[report.status or "failed"] += 1
            counts["pruned"] += report.pruned

    return JobOutcome("dry_run" if dry_run else "ok", counts)


async def _mark_reconfirm_notified(candidate: _ReconfirmCandidate) -> bool:
    raw = await supabase_admin.call_rpc(
        "gugu_push_mark_reconfirm_notified",
        {"p_push_install_id": candidate.pushInstallId, "p_expected_basis_at": candidate.basisAt.isoformat()},
    )
    updated = raw.get("updated")
    if not isinstance(updated, bool):
        raise PushStoreContractError("gugu_push_mark_reconfirm_notified 응답 계약 오류")
    return updated


async def run_marketing_reconfirm(now: datetime, dry_run: bool) -> JobOutcome:
    if not is_reconfirm_window(now):
        return JobOutcome("skipped", {"reason": "outside_window"})

    raw = await supabase_admin.call_rpc(
        "gugu_push_reconfirm_candidates",
        {"p_basis_before": reconfirm_cutoff(now).isoformat()},
    )
    candidates = _validate(_ReconfirmCandidates, raw, "gugu_push_reconfirm_candidates").candidates
    counts = {"candidates": len(candidates), "notDue": 0, "wouldSend": 0, "sent": 0, "partial": 0,
              "failed": 0, "pruned": 0, "marked": 0, "repaired": 0}
    sender = _sender(dry_run) if candidates else None

    for candidate in candidates:
        # 달력 계산(2월 29일)으로 SQL 마감과 하루 어긋날 수 있다 — 기한 전이면 다음 실행에 맡긴다
        if not is_reconfirm_due(candidate.basisAt, now):
            counts["notDue"] += 1
            continue
        if dry_run or sender is None:
            counts["wouldSend"] += 1
            continue
        report = await deliver(
            sender,
            dedupe_key=(
                f"marketing_reconfirm:{candidate.pushInstallId}:{candidate.basisAt.date().isoformat()}"
            ),
            kind="marketing_reconfirm",
            user_id=candidate.userId,
            tokens=[candidate.fcmToken],
            message=render("marketing_reconfirm", consent_date=candidate.marketingOptInAt),
        )
        if not report.claimed:
            # 보냈지만 확인 시각 기록 전에 중단된 경우 — 다시 보내지 않고 기록만 맞춘다
            if report.status == "sent" and await _mark_reconfirm_notified(candidate):
                counts["repaired"] += 1
            continue
        counts[report.status or "failed"] += 1
        counts["pruned"] += report.pruned
        if report.status == "sent" and await _mark_reconfirm_notified(candidate):
            counts["marked"] += 1
        log.info(
            "광고 수신동의 확인 알림 install=%s status=%s",
            mask_identifier(candidate.pushInstallId),
            report.status,
        )

    return JobOutcome("dry_run" if dry_run else "ok", counts)


async def run_prune(now: datetime, dry_run: bool) -> JobOutcome:
    pruned = await prune_stale(stale_device_cutoff(now).isoformat(), dry_run)
    return JobOutcome("dry_run" if dry_run else "ok", {"staleDevices": pruned})


JOBS: tuple[tuple[JobName, Callable[[datetime, bool], Awaitable[JobOutcome]]], ...] = (
    ("weekly_report", run_weekly_report),
    ("marketing_reconfirm", run_marketing_reconfirm),
    ("prune", run_prune),
)


async def _record(job: JobName, outcome: JobOutcome, started_at: datetime) -> None:
    await supabase_admin.call_rpc(
        "gugu_push_record_job_run",
        {
            "p_job": job,
            "p_status": outcome.status,
            "p_started_at": started_at.isoformat(),
            "p_detail": outcome.detail,
        },
    )


async def run_due(dry_run: bool, now: Optional[datetime] = None) -> int:
    """기한이 된 작업을 차례로 실행한다. 하나라도 실패하면 1을 돌려준다."""
    now = now or datetime.now(timezone.utc)
    exit_code = 0
    for job, runner in JOBS:
        started_at = datetime.now(timezone.utc)
        try:
            outcome = await runner(now, dry_run)
        except OPERATIONAL_ERRORS as exc:
            log.exception("푸시 배치 작업 실패 job=%s", job)
            outcome = JobOutcome("failed", {"error": type(exc).__name__})
            exit_code = 1
        except Exception as exc:
            # 버그 — 실패를 기록하고 즉시 중단해 원인을 드러낸다
            await _record(job, JobOutcome("failed", {"error": type(exc).__name__}), started_at)
            raise
        await _record(job, outcome, started_at)
        log.info("푸시 배치 job=%s status=%s detail=%s", job, outcome.status, outcome.detail)
        print(f"{job}: {outcome.status} {outcome.detail}")
    return exit_code


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="구구 어드벤처 자동 알림 배치")
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run-due", help="기한이 된 작업을 실행한다(매시 실행해도 멱등)")
    run.add_argument("--dry-run", action="store_true", help="발송·정리·선점 없이 대상 건수만 계산한다")
    return parser


async def _main(argv: list[str]) -> int:
    args = build_parser().parse_args(argv)
    try:
        return await run_due(args.dry_run)
    finally:
        await fcm.close_sender()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    sys.exit(asyncio.run(_main(sys.argv[1:])))
