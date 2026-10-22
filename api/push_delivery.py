"""계정별 알림 발송 절차 — 중복 선점 → FCM 발송 → 무효 토큰 정리 → 결과 기록.

push_admin.py(운영자 개별 발송)와 push_jobs.py(자동 발송)가 같은 절차를 쓴다.
"""

import logging
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field, ValidationError

import supabase_admin
from fcm import FcmAuthError, FcmSender, PushMessage
from observability import mask_identifier


log = logging.getLogger("gugu-api")

SendStatus = Literal["sent", "partial", "failed"]


class PushStoreContractError(RuntimeError):
    """푸시 RPC 응답이 계약과 다르다."""


class _ClaimResult(BaseModel):
    claimed: bool
    status: Literal["claimed", "sent", "partial", "failed"]


class _PruneResult(BaseModel):
    prunedByToken: int = Field(ge=0)
    prunedStale: int = Field(ge=0)
    dryRun: bool


@dataclass(frozen=True)
class DeliveryReport:
    dedupe_key: str
    claimed: bool
    status: Optional[str]
    target: int = 0
    success: int = 0
    failure: int = 0
    pruned: int = 0


def _validate(model: type[BaseModel], raw: dict[str, Any], operation: str) -> Any:
    try:
        return model.model_validate(raw)
    except ValidationError as exc:
        raise PushStoreContractError(f"{operation} 응답 계약 오류") from exc


async def prune_tokens(tokens: Sequence[str]) -> int:
    if not tokens:
        return 0
    raw = await supabase_admin.call_rpc("gugu_push_prune_tokens", {"p_tokens": list(tokens)})
    return _validate(_PruneResult, raw, "gugu_push_prune_tokens").prunedByToken


async def prune_stale(last_seen_before_iso: str, dry_run: bool) -> int:
    raw = await supabase_admin.call_rpc(
        "gugu_push_prune_tokens",
        {"p_last_seen_before": last_seen_before_iso, "p_dry_run": dry_run},
    )
    return _validate(_PruneResult, raw, "gugu_push_prune_tokens").prunedStale


async def _finish(
    dedupe_key: str,
    status: SendStatus,
    target: int,
    success: int,
    failure: int,
    pruned: int,
    error_code: Optional[str],
) -> None:
    await supabase_admin.call_rpc(
        "gugu_push_finish_send",
        {
            "p_dedupe_key": dedupe_key,
            "p_status": status,
            "p_target_count": target,
            "p_success_count": success,
            "p_failure_count": failure,
            "p_pruned_count": pruned,
            "p_error_code": error_code,
        },
    )


async def deliver(
    sender: FcmSender,
    *,
    dedupe_key: str,
    kind: str,
    user_id: Optional[str],
    tokens: Sequence[str],
    message: PushMessage,
) -> DeliveryReport:
    """같은 dedupe_key 로는 한 번만 보낸다. 선점하지 못하면 claimed=False 로 돌려준다."""
    if not tokens:
        return DeliveryReport(dedupe_key=dedupe_key, claimed=False, status="no_devices")

    claim = _validate(
        _ClaimResult,
        await supabase_admin.call_rpc(
            "gugu_push_claim_send",
            {"p_dedupe_key": dedupe_key, "p_kind": kind, "p_user_id": user_id},
        ),
        "gugu_push_claim_send",
    )
    if not claim.claimed:
        return DeliveryReport(dedupe_key=dedupe_key, claimed=False, status=claim.status)

    try:
        results = await sender.send_many(tokens, message)
    except FcmAuthError as exc:
        # 자격 문제는 보통 첫 요청에서 난다 — 재시도할 수 있게 failed 로 남기고 그대로 올린다
        await _finish(dedupe_key, "failed", len(tokens), 0, len(tokens), 0, f"FCM_AUTH_{exc.status_code}")
        raise

    invalid = [result.token for result in results if result.outcome == "invalid_token"]
    pruned = await prune_tokens(invalid)
    success = sum(1 for result in results if result.outcome == "sent")
    failure = len(results) - success
    status: SendStatus = "sent" if failure == 0 else ("partial" if success > 0 else "failed")
    error_code = next((result.error_code for result in results if result.outcome != "sent"), None)
    await _finish(dedupe_key, status, len(results), success, failure, pruned, error_code)

    log.info(
        "푸시 발송 kind=%s user=%s status=%s target=%d success=%d failure=%d pruned=%d",
        kind,
        mask_identifier(user_id),
        status,
        len(results),
        success,
        failure,
        pruned,
    )
    return DeliveryReport(
        dedupe_key=dedupe_key,
        claimed=True,
        status=status,
        target=len(results),
        success=success,
        failure=failure,
        pruned=pruned,
    )
