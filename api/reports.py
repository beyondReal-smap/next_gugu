"""보호자용 서버 학습 리포트 API."""

import logging
from datetime import date, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import AwareDatetime, BaseModel, Field, ValidationError

import supabase_admin
from auth import AuthUser, require_user
from entitlements import require_feature
from learner_access import require_guardian_learner_access
from learning import error_detail, learning_store_unavailable, require_learning_upload_permission
from observability import mask_identifier


log = logging.getLogger("gugu-api")
router = APIRouter(prefix="/api/v1/reports", tags=["reports"])


class WeakKey(BaseModel):
    key: str
    misses: int = Field(ge=0)


class AdventureSummary(BaseModel):
    defeated: int = Field(ge=0)
    total: int = Field(ge=0)
    bosses: int = Field(ge=0)


class ReportSummary(BaseModel):
    from_: date = Field(alias="from")
    to: date
    daysActive: int = Field(ge=0)
    correct: int = Field(ge=0)
    wrong: int = Field(ge=0)
    avgMs: int = Field(ge=0)
    weakKeys: list[WeakKey]
    stars: dict[int, int]
    adventure: AdventureSummary
    roadmapStep: int = Field(ge=1, le=8)


class FactMasteryReport(BaseModel):
    factId: str
    stability: float = Field(ge=0)
    difficulty: float = Field(ge=1, le=10)
    correctStreak: int = Field(ge=0)
    lapses: int = Field(ge=0)
    meanResponseMs: int = Field(ge=0)
    lastSeenAt: AwareDatetime | None
    nextDueAt: AwareDatetime | None


class WeakFactReport(BaseModel):
    factId: str
    misses: int = Field(ge=0)
    accuracy: float = Field(ge=0, le=1)


class TrendPoint(BaseModel):
    date: date
    value: float = Field(ge=0)


class DueReview(BaseModel):
    factId: str
    dueAt: AwareDatetime


class LearnerReportResponse(BaseModel):
    summary: ReportSummary
    masteryByFact: list[FactMasteryReport]
    weakFacts: list[WeakFactReport]
    accuracyTrend: list[TrendPoint]
    speedTrend: list[TrendPoint]
    dueReviews: list[DueReview]
    recommendations: list[str]


@router.get("/learners/{learner_id}", response_model=LearnerReportResponse)
async def get_learner_report(
    learner_id: UUID,
    from_: date = Query(alias="from"),
    to: date = Query(),
    user: AuthUser = Depends(require_user),
) -> LearnerReportResponse:
    require_learning_upload_permission(user)
    require_feature(user.id, "longitudinal_report")
    if from_ > to:
        raise HTTPException(400, detail=error_detail("INVALID_DATE_RANGE", "from은 to보다 늦을 수 없습니다"))
    if to - from_ > timedelta(days=365):
        raise HTTPException(400, detail=error_detail("DATE_RANGE_TOO_LARGE", "조회 기간은 최대 366일입니다"))
    await require_guardian_learner_access(user.id, learner_id)
    try:
        raw = await supabase_admin.call_rpc(
            "gugu_build_learner_report",
            {
                "p_guardian_user_id": user.id,
                "p_learner_id": str(learner_id),
                "p_from": from_.isoformat(),
                "p_to": to.isoformat(),
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    try:
        return LearnerReportResponse.model_validate(raw)
    except ValidationError as exc:
        log.error("학습 리포트 응답 계약 오류 user=%s", mask_identifier(user.id))
        raise HTTPException(
            502,
            detail=error_detail("REPORT_INVALID_RESPONSE", "학습 리포트 저장소 응답이 올바르지 않습니다"),
        ) from exc
