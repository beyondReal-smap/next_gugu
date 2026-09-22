"""인증된 AI 오답 코치 API."""

import logging
from typing import Any, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

import llm_client
import mcp_server
import supabase_admin
from auth import AuthUser, require_user
from entitlements import require_feature
from learner_access import require_guardian_learner_access
from learning import error_detail, learning_store_unavailable, parse_limited_json, require_learning_upload_permission
from observability import mask_identifier
from settings import settings


log = logging.getLogger("gugu-api")
router = APIRouter(prefix="/api/v1/coach", tags=["coach"])
CONTENT_VERSION = str(mcp_server.FACTS_DOC["version"])


class CoachExplainRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    learnerId: UUID
    factId: str = Field(pattern=r"^[2-9]x[1-9]$")
    submittedAnswer: int | bool | str
    attemptNo: int = Field(ge=1, le=100)
    locale: str = Field(default="ko-KR", min_length=2, max_length=16, pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")

    @field_validator("submittedAnswer")
    @classmethod
    def validate_submitted_answer(cls, value: int | bool | str) -> int | bool | str:
        if isinstance(value, str) and (not value.strip() or len(value) > 32):
            raise ValueError("문자열 답안은 1~32자여야 합니다")
        return value


class CoachLLMOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    hint: str = Field(min_length=1, max_length=400)
    checkQuestion: str = Field(min_length=1, max_length=200)


class CoachExplainResponse(BaseModel):
    hint: str
    strategyRef: str
    misconceptionCode: str
    checkQuestion: str
    contentVersion: str
    cached: bool
    fallback: bool
    providerStatus: Literal["generated", "fallback", "disabled"]
    quotaRemaining: int = Field(ge=0)


def _grounding(request: CoachExplainRequest) -> tuple[dict[str, Any], str, str, CoachLLMOutput]:
    fact = mcp_server.FACTS.get(request.factId)
    if fact is None:
        raise HTTPException(400, detail=error_detail("UNKNOWN_FACT", "지원하지 않는 곱셈식입니다"))
    table = mcp_server.TABLES[fact["a"]]
    mistake = next((item for item in table["commonMistakes"] if item["fact"] == request.factId), None)
    confused = bool(mistake and request.submittedAnswer in mistake["confusedWith"])
    misconception = "COMMON_CONFUSION" if confused else "FACT_RECALL_ERROR"
    strategy = table["strategies"][0]
    if mistake is not None:
        strategy_ref = f"commonMistake:{request.factId}"
        fallback_hint = mistake["fix"]
    else:
        strategy_ref = f"table:{fact['a']}:strategy:0"
        fallback_hint = strategy["detail"]
    fallback = CoachLLMOutput(
        hint=fallback_hint,
        checkQuestion=f"'{fact['reading']}'을 천천히 읽고 {request.factId.replace('x', ' × ')}의 답을 다시 말해 보세요.",
    )
    grounding = {
        "factId": request.factId,
        "reading": fact["reading"],
        "commonMistake": mistake,
        "strategy": {"title": strategy["title"], "detail": strategy["detail"]},
    }
    return grounding, misconception, strategy_ref, fallback


async def _quota(user_id: str, consume: bool) -> tuple[bool, int]:
    try:
        raw = await supabase_admin.call_rpc(
            "gugu_coach_quota",
            {
                "p_owner_user_id": user_id,
                "p_daily_limit": settings.coach_daily_quota,
                "p_consume": consume,
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    allowed = raw.get("allowed")
    remaining = raw.get("quotaRemaining")
    if not isinstance(allowed, bool) or not isinstance(remaining, int) or remaining < 0:
        raise HTTPException(502, detail=error_detail("COACH_QUOTA_INVALID_RESPONSE", "코치 할당량 응답이 올바르지 않습니다"))
    return allowed, remaining


def _response(
    output: CoachLLMOutput,
    strategy_ref: str,
    misconception: str,
    *,
    cached: bool,
    fallback: bool,
    provider_status: Literal["generated", "fallback", "disabled"],
    quota_remaining: int,
) -> CoachExplainResponse:
    return CoachExplainResponse(
        hint=output.hint,
        strategyRef=strategy_ref,
        misconceptionCode=misconception,
        checkQuestion=output.checkQuestion,
        contentVersion=CONTENT_VERSION,
        cached=cached,
        fallback=fallback,
        providerStatus=provider_status,
        quotaRemaining=quota_remaining,
    )


@router.post("/explain", response_model=CoachExplainResponse)
async def explain_wrong_answer(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> CoachExplainResponse:
    require_learning_upload_permission(user)
    require_feature(user.id, "ai_coach")
    explain_request = await parse_limited_json(request, CoachExplainRequest)
    await require_guardian_learner_access(user.id, explain_request.learnerId)
    grounding, misconception, strategy_ref, fallback_output = _grounding(explain_request)

    cache_params = {
        "fact_id": f"eq.{explain_request.factId}",
        "misconception_code": f"eq.{misconception}",
        "content_version": f"eq.{CONTENT_VERSION}",
        "locale": f"eq.{explain_request.locale}",
        "select": "response",
        "limit": "1",
    }
    try:
        cached_rows = await supabase_admin.select_rows("coach_explanation_cache", cache_params)
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    if cached_rows:
        try:
            cached_response = CoachExplainResponse.model_validate(cached_rows[0]["response"])
        except (KeyError, ValidationError) as exc:
            raise HTTPException(502, detail=error_detail("COACH_CACHE_INVALID_RESPONSE", "코치 캐시 응답이 올바르지 않습니다")) from exc
        _, remaining = await _quota(user.id, consume=False)
        return cached_response.model_copy(update={"cached": True, "quotaRemaining": remaining})

    if not llm_client.is_enabled():
        _, remaining = await _quota(user.id, consume=False)
        return _response(
            fallback_output,
            strategy_ref,
            misconception,
            cached=False,
            fallback=True,
            provider_status="disabled",
            quota_remaining=remaining,
        )

    allowed, remaining = await _quota(user.id, consume=True)
    if not allowed:
        return _response(
            fallback_output,
            strategy_ref,
            misconception,
            cached=False,
            fallback=True,
            provider_status="fallback",
            quota_remaining=0,
        )

    try:
        generated = await llm_client.generate_structured_coach(grounding, explain_request.locale, CoachLLMOutput)
        output = CoachLLMOutput.model_validate(generated)
        provider_status: Literal["generated", "fallback", "disabled"] = "generated"
        used_fallback = False
    except (llm_client.CoachProviderError, ValidationError) as exc:
        log.warning(
            "AI 코치 폴백 user=%s fact=%s reason=%s",
            mask_identifier(user.id),
            explain_request.factId,
            type(exc).__name__,
        )
        output = fallback_output
        provider_status = "fallback"
        used_fallback = True

    response = _response(
        output,
        strategy_ref,
        misconception,
        cached=False,
        fallback=used_fallback,
        provider_status=provider_status,
        quota_remaining=remaining,
    )
    try:
        await supabase_admin.upsert_rows(
            "coach_explanation_cache",
            [{
                "fact_id": explain_request.factId,
                "misconception_code": misconception,
                "content_version": CONTENT_VERSION,
                "locale": explain_request.locale,
                "response": response.model_dump(mode="json"),
            }],
            "fact_id,misconception_code,content_version,locale",
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    return response
