"""학습 이벤트 원장 업로드·상태 조회 API.

학습 데이터 쓰기는 Supabase service_role 전용 RPC로 제한하고,
인증 계정의 아동 안전 속성은 서버가 기본 거부 정책으로 강제한다.
"""

import logging
from datetime import datetime
from typing import Literal, TypeVar
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

import supabase_admin
from auth import AuthUser, require_user
from observability import mask_identifier


log = logging.getLogger("gugu-api")
router = APIRouter(prefix="/api/v1/learning", tags=["learning"])

MAX_BATCH_EVENTS = 100
MAX_BODY_BYTES = 256 * 1024
ModelT = TypeVar("ModelT", bound=BaseModel)


def error_detail(code: str, message: str, **extra: object) -> dict[str, object]:
    return {"code": code, "message": message, **extra}


async def parse_limited_json(request: Request, model: type[ModelT]) -> ModelT:
    """본문을 상한 내에서 읽고 Pydantic 경계 검증을 수행한다."""
    content_length = request.headers.get("content-length")
    if content_length is not None:
        try:
            declared_size = int(content_length)
        except ValueError as exc:
            raise HTTPException(
                400,
                detail=error_detail("INVALID_CONTENT_LENGTH", "Content-Length가 정수가 아닙니다"),
            ) from exc
        if declared_size < 0:
            raise HTTPException(
                400,
                detail=error_detail("INVALID_CONTENT_LENGTH", "Content-Length가 음수입니다"),
            )
        if declared_size > MAX_BODY_BYTES:
            raise HTTPException(
                413,
                detail=error_detail(
                    "REQUEST_BODY_TOO_LARGE",
                    f"요청 본문은 {MAX_BODY_BYTES}바이트를 넘을 수 없습니다",
                ),
            )

    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > MAX_BODY_BYTES:
            raise HTTPException(
                413,
                detail=error_detail(
                    "REQUEST_BODY_TOO_LARGE",
                    f"요청 본문은 {MAX_BODY_BYTES}바이트를 넘을 수 없습니다",
                ),
            )

    try:
        return model.model_validate_json(bytes(body), strict=True)
    except ValidationError as exc:
        raise HTTPException(
            400,
            detail=error_detail(
                "INVALID_REQUEST",
                "요청 형식이 올바르지 않습니다",
                errors=exc.errors(include_input=False, include_context=False),
            ),
        ) from exc


def require_learning_upload_permission(user: AuthUser) -> None:
    """Supabase app_metadata의 관리자 검증 속성으로 아동 데이터 업로드를 차단한다."""
    account_role = user.app_metadata.get("account_role")
    if account_role == "child":
        raise HTTPException(
            403,
            detail=error_detail(
                "CHILD_ACCOUNT_UPLOAD_FORBIDDEN",
                "아이 계정의 학습 데이터는 서버에 업로드하지 않습니다",
            ),
        )
    if account_role != "guardian":
        raise HTTPException(
            403,
            detail=error_detail(
                "ACCOUNT_ROLE_UNVERIFIED",
                "계정 역할이 보호자로 확인되지 않았습니다",
            ),
        )
    if user.app_metadata.get("age_verified") is not True:
        raise HTTPException(
            403,
            detail=error_detail(
                "ACCOUNT_AGE_UNVERIFIED",
                "보호자의 성인 여부가 확인되지 않았습니다",
            ),
        )


class LearningEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    eventId: UUID
    learnerId: UUID
    deviceId: UUID
    sessionId: UUID
    sequenceNo: int = Field(ge=0, le=10_000)
    factId: str = Field(pattern=r"^[2-9]x[1-9]$")
    mode: Literal["practice", "timeAttack", "challenge", "survival", "missing", "truefalse"]
    tableNo: int | None = Field(default=None, ge=2, le=9)
    submittedAnswer: int | bool | str
    correct: bool
    responseMs: int = Field(ge=0, le=600_000)
    attemptNo: int = Field(ge=1, le=100)
    occurredAt: AwareDatetime
    contentVersion: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9._-]+$")

    @field_validator("submittedAnswer")
    @classmethod
    def validate_submitted_answer(cls, value: int | bool | str) -> int | bool | str:
        if isinstance(value, str) and (not value.strip() or len(value) > 32):
            raise ValueError("문자열 답안은 1~32자여야 합니다")
        return value

    @model_validator(mode="after")
    def validate_fact_table(self) -> "LearningEvent":
        fact_table = int(self.factId.split("x", 1)[0])
        if self.tableNo is not None and self.tableNo != fact_table:
            raise ValueError("tableNo와 factId의 단 번호가 일치해야 합니다")
        return self


class LearningEventBatchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    events: list[LearningEvent] = Field(min_length=1, max_length=MAX_BATCH_EVENTS)


class RejectedEvent(BaseModel):
    eventId: UUID
    code: str = Field(min_length=1, max_length=64)


class LearningEventBatchResponse(BaseModel):
    acceptedEventIds: list[UUID]
    duplicateEventIds: list[UUID]
    rejected: list[RejectedEvent]
    serverCursor: int = Field(ge=0)


class LearningSnapshot(BaseModel):
    learnerId: UUID
    revision: int = Field(ge=1)
    serverCursor: int = Field(ge=0)
    state: dict[str, object]
    updatedAt: datetime


class LearningStateResponse(BaseModel):
    snapshots: list[LearningSnapshot]
    serverCursor: int = Field(ge=0)


class ClaimRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    installId: UUID
    localEventCount: int = Field(ge=0, le=1_000_000)


class ClaimCandidate(BaseModel):
    learnerId: UUID
    displayName: str
    updatedAt: datetime


class ClaimResponse(BaseModel):
    action: Literal["create_learner", "attach_to_existing", "conflict"]
    candidates: list[ClaimCandidate]
    learnerId: UUID | None = None


class ClaimConfirmRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    installId: UUID
    learnerId: UUID


class ClaimConfirmResponse(BaseModel):
    learnerId: UUID


def learning_store_unavailable(exc: supabase_admin.SupabaseDataError) -> HTTPException:
    log.error(
        "학습 원장 호출 실패 operation=%s status=%s",
        exc.operation,
        exc.status_code,
    )
    return HTTPException(
        503,
        detail=error_detail(
            "LEARNING_STORE_UNAVAILABLE",
            "학습 기록 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        ),
    )


@router.post("/events:batch", response_model=LearningEventBatchResponse)
async def upload_learning_events(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> LearningEventBatchResponse:
    require_learning_upload_permission(user)
    batch = await parse_limited_json(request, LearningEventBatchRequest)
    try:
        raw_response = await supabase_admin.call_rpc(
            "gugu_ingest_learning_events",
            {
                "p_owner_user_id": user.id,
                "p_events": [event.model_dump(mode="json") for event in batch.events],
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc

    try:
        response = LearningEventBatchResponse.model_validate(raw_response)
    except ValidationError as exc:
        log.error("학습 원장 응답 계약 오류 user=%s", mask_identifier(user.id))
        raise HTTPException(
            502,
            detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "학습 기록 저장소 응답이 올바르지 않습니다"),
        ) from exc
    log.info(
        "학습 이벤트 배치 처리 user=%s accepted=%d duplicate=%d rejected=%d",
        mask_identifier(user.id),
        len(response.acceptedEventIds),
        len(response.duplicateEventIds),
        len(response.rejected),
    )
    return response


@router.get("/state", response_model=LearningStateResponse)
async def get_learning_state(
    learnerId: UUID | None = Query(default=None),
    user: AuthUser = Depends(require_user),
) -> LearningStateResponse:
    params = {
        "owner_user_id": f"eq.{user.id}",
        "select": "learner_id,revision,server_cursor,state,updated_at",
        "order": "updated_at.desc",
    }
    if learnerId is not None:
        params["learner_id"] = f"eq.{learnerId}"
    try:
        rows = await supabase_admin.select_rows("progress_snapshots", params)
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc

    snapshots: list[LearningSnapshot] = []
    try:
        for row in rows:
            snapshots.append(
                LearningSnapshot.model_validate(
                    {
                        "learnerId": row.get("learner_id"),
                        "revision": row.get("revision"),
                        "serverCursor": row.get("server_cursor"),
                        "state": row.get("state"),
                        "updatedAt": row.get("updated_at"),
                    }
                )
            )
    except ValidationError as exc:
        log.error("학습 스냅샷 응답 계약 오류 user=%s", mask_identifier(user.id))
        raise HTTPException(
            502,
            detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "학습 상태 응답이 올바르지 않습니다"),
        ) from exc
    cursor = max((snapshot.serverCursor for snapshot in snapshots), default=0)
    return LearningStateResponse(snapshots=snapshots, serverCursor=cursor)


@router.post("/claim", response_model=ClaimResponse)
async def inspect_guest_claim(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> ClaimResponse:
    require_learning_upload_permission(user)
    claim = await parse_limited_json(request, ClaimRequest)
    try:
        device_rows = await supabase_admin.select_rows(
            "devices",
            {
                "id": f"eq.{claim.installId}",
                "select": "owner_user_id",
                "limit": "1",
            },
        )
        claim_rows = await supabase_admin.select_rows(
            "device_learner_claims",
            {
                "device_id": f"eq.{claim.installId}",
                "select": "owner_user_id,learner_id",
                "limit": "1",
            },
        )
        learner_rows = await supabase_admin.select_rows(
            "learners",
            {
                "owner_user_id": f"eq.{user.id}",
                "status": "eq.active",
                "select": "id,display_name,updated_at",
                "order": "updated_at.desc",
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc

    candidates: list[ClaimCandidate] = []
    try:
        for row in learner_rows:
            candidates.append(
                ClaimCandidate.model_validate(
                    {
                        "learnerId": row.get("id"),
                        "displayName": row.get("display_name"),
                        "updatedAt": row.get("updated_at"),
                    }
                )
            )
    except ValidationError as exc:
        log.error("학습자 후보 응답 계약 오류 user=%s", mask_identifier(user.id))
        raise HTTPException(
            502,
            detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "학습자 후보 응답이 올바르지 않습니다"),
        ) from exc

    device_conflict = bool(device_rows) and device_rows[0].get("owner_user_id") != user.id
    claimed_learner_id: UUID | None = None
    if claim_rows:
        if claim_rows[0].get("owner_user_id") != user.id:
            device_conflict = True
        else:
            try:
                claimed_learner_id = UUID(str(claim_rows[0].get("learner_id")))
            except ValueError as exc:
                raise HTTPException(
                    502,
                    detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "기기 귀속 응답이 올바르지 않습니다"),
                ) from exc
            if not any(candidate.learnerId == claimed_learner_id for candidate in candidates):
                device_conflict = True

    if device_conflict or len(candidates) > 1:
        action: Literal["create_learner", "attach_to_existing", "conflict"] = "conflict"
        claimed_learner_id = None
    elif claimed_learner_id is not None:
        action = "attach_to_existing"
    elif len(candidates) == 1:
        action = "attach_to_existing"
    else:
        try:
            raw_created = await supabase_admin.call_rpc(
                "gugu_create_learner_claim",
                {
                    "p_owner_user_id": user.id,
                    "p_install_id": str(claim.installId),
                    "p_local_event_count": claim.localEventCount,
                },
            )
        except supabase_admin.SupabaseDataError as exc:
            raise learning_store_unavailable(exc) from exc
        try:
            claimed_learner_id = UUID(str(raw_created["learnerId"]))
        except (KeyError, ValueError) as exc:
            raise HTTPException(
                502,
                detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "학습자 생성 응답이 올바르지 않습니다"),
            ) from exc
        action = "create_learner"
    return ClaimResponse(action=action, candidates=candidates, learnerId=claimed_learner_id)


@router.post("/claim/confirm", response_model=ClaimConfirmResponse)
async def confirm_guest_claim(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> ClaimConfirmResponse:
    require_learning_upload_permission(user)
    confirm = await parse_limited_json(request, ClaimConfirmRequest)
    try:
        learner_rows = await supabase_admin.select_rows(
            "learners",
            {
                "id": f"eq.{confirm.learnerId}",
                "owner_user_id": f"eq.{user.id}",
                "status": "eq.active",
                "select": "id",
                "limit": "1",
            },
        )
        device_rows = await supabase_admin.select_rows(
            "devices",
            {
                "id": f"eq.{confirm.installId}",
                "select": "owner_user_id",
                "limit": "1",
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    if not learner_rows:
        raise HTTPException(404, detail=error_detail("LEARNER_NOT_FOUND", "연결할 학습자를 찾을 수 없습니다"))
    if device_rows and device_rows[0].get("owner_user_id") != user.id:
        raise HTTPException(409, detail=error_detail("DEVICE_CLAIM_CONFLICT", "다른 계정에 연결된 기기입니다"))

    try:
        raw_confirmed = await supabase_admin.call_rpc(
            "gugu_confirm_learner_claim",
            {
                "p_owner_user_id": user.id,
                "p_install_id": str(confirm.installId),
                "p_learner_id": str(confirm.learnerId),
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    try:
        return ClaimConfirmResponse.model_validate(raw_confirmed)
    except ValidationError as exc:
        raise HTTPException(
            502,
            detail=error_detail("LEARNING_STORE_INVALID_RESPONSE", "학습자 연결 응답이 올바르지 않습니다"),
        ) from exc
