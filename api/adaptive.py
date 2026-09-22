"""서버 권위 적응형 복습 계획 API."""

import logging
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, ValidationError

import supabase_admin
from auth import AuthUser, require_user
from learning import error_detail, learning_store_unavailable, parse_limited_json, require_learning_upload_permission
from observability import mask_identifier


log = logging.getLogger("gugu-api")
router = APIRouter(prefix="/api/v1/practice", tags=["practice"])


class PracticePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    learnerId: UUID
    count: int = Field(default=20, ge=5, le=30)
    seed: int | None = Field(default=None, ge=0, le=2_147_483_647)


class PracticePlanItem(BaseModel):
    itemId: UUID
    factId: str = Field(pattern=r"^[2-9]x[1-9]$")
    promptVariant: Literal["standard", "missing", "reverse"]
    seed: int = Field(ge=0, le=2_147_483_647)
    selectionReason: Literal["overdue", "unseen", "reinforcement"]
    dueAt: AwareDatetime | None


class PracticePlanResponse(BaseModel):
    planId: UUID
    algorithmVersion: str
    expiresAt: AwareDatetime
    items: list[PracticePlanItem] = Field(min_length=5, max_length=30)


@router.post("/plan", response_model=PracticePlanResponse)
async def create_practice_plan(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> PracticePlanResponse:
    require_learning_upload_permission(user)
    plan_request = await parse_limited_json(request, PracticePlanRequest)
    try:
        raw_response = await supabase_admin.call_rpc(
            "gugu_create_practice_plan",
            {
                "p_owner_user_id": user.id,
                "p_learner_id": str(plan_request.learnerId),
                "p_count": plan_request.count,
                "p_seed": plan_request.seed,
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc

    try:
        response = PracticePlanResponse.model_validate(raw_response)
    except ValidationError as exc:
        log.error("적응형 계획 응답 계약 오류 user=%s", mask_identifier(user.id))
        raise HTTPException(
            502,
            detail=error_detail("PRACTICE_PLAN_INVALID_RESPONSE", "적응형 계획 저장소 응답이 올바르지 않습니다"),
        ) from exc
    return response
