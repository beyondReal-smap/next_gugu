"""원격 푸시 기기 등록·광고 동의 기록 API.

- 알림을 켠 기기만 등록한다(앱은 알림을 켜기 전에는 FCM 토큰을 발급받지 않는다).
- 익명 계정도 허용한다 — 공지 알림은 로그인과 무관한 기능이다.
- 광고 동의는 POST /devices 로 바꿀 수 없고 POST /devices/consents 로만 바뀐다.
  앱은 이 응답(recordedAt)을 받은 뒤에만 promo 토픽을 구독하고, 처리 결과를 사용자에게
  알린다(정보통신망법 제50조 ⑦, 시행령 제62조).
- 쓰기는 service_role 전용 RPC(api/migrations/20261019_push.sql)로만 한다.
"""

import logging
from datetime import datetime
from typing import Literal, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError

import supabase_admin
from auth import AuthUser, require_user
from learning import error_detail, parse_limited_json
from observability import mask_identifier
from push_templates import SENDER_NAME


log = logging.getLogger("gugu-api")
router = APIRouter(prefix="/api/v1/push", tags=["push"])

FCM_TOKEN_PATTERN = r"^[A-Za-z0-9_:.\-]+$"
LOCALE_PATTERN = r"^[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8}){0,3}$"
TIME_ZONE_PATTERN = r"^[A-Za-z][A-Za-z0-9_+\-]*(?:/[A-Za-z0-9_+\-]+){0,2}$"
APP_VERSION_PATTERN = r"^[0-9A-Za-z][0-9A-Za-z._+() -]{0,31}$"


class RegisterDeviceRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    pushInstallId: UUID
    fcmToken: str = Field(min_length=16, max_length=4096, pattern=FCM_TOKEN_PATTERN)
    platform: Literal["ios", "android"]
    locale: Optional[str] = Field(default=None, max_length=35, pattern=LOCALE_PATTERN)
    timeZone: Optional[str] = Field(default=None, max_length=64, pattern=TIME_ZONE_PATTERN)
    appVersion: str = Field(min_length=1, max_length=32, pattern=APP_VERSION_PATTERN)
    # 공지(notice 토픽은 앱이 구독) · 보호자 학습 소식(서버 주간 리포트) · 로컬 학습 알림(참고용)
    serviceOptIn: bool
    learningOptIn: bool
    localReminderEnabled: bool


class RegisterDeviceResponse(BaseModel):
    pushInstallId: UUID
    serviceOptIn: bool
    learningOptIn: bool
    localReminderEnabled: bool
    # 서버 기준 광고 동의 상태 — 앱은 이 값으로 promo 토픽 구독을 맞춘다
    marketingOptIn: bool
    marketingOptInAt: Optional[datetime]
    marketingReconfirmedAt: Optional[datetime]
    updatedAt: datetime


class ConsentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    pushInstallId: UUID
    kind: Literal["marketing"]
    granted: bool
    # settings: 설정 화면 토글 / reconfirm: 2년 확인 알림을 보고 다시 동의
    source: Literal["settings", "reconfirm"] = "settings"
    appVersion: Optional[str] = Field(default=None, max_length=32, pattern=APP_VERSION_PATTERN)


class ConsentResponse(BaseModel):
    kind: Literal["marketing"]
    granted: bool
    recordedAt: datetime
    sender: str


class UnregisterResponse(BaseModel):
    deleted: bool


class _RegisterRpcResult(RegisterDeviceResponse):
    replacedTokenRows: int = Field(ge=0)
    reassignedRows: int = Field(ge=0)
    evictedDevices: int = Field(ge=0)


class _ConsentRpcResult(BaseModel):
    found: bool
    kind: Optional[Literal["marketing"]] = None
    granted: Optional[bool] = None
    recordedAt: Optional[datetime] = None


class _UnregisterRpcResult(BaseModel):
    status: Literal["deleted", "absent", "forbidden"]


def push_store_unavailable(exc: supabase_admin.SupabaseDataError) -> HTTPException:
    log.error("푸시 저장소 호출 실패 operation=%s status=%s", exc.operation, exc.status_code)
    return HTTPException(
        503,
        detail=error_detail(
            "PUSH_STORE_UNAVAILABLE",
            "알림 설정 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        ),
    )


def push_store_invalid_response(operation: str, user_id: str) -> HTTPException:
    log.error("푸시 저장소 응답 계약 오류 operation=%s user=%s", operation, mask_identifier(user_id))
    return HTTPException(
        502,
        detail=error_detail("PUSH_STORE_INVALID_RESPONSE", "알림 설정 저장소 응답이 올바르지 않습니다"),
    )


def device_not_found() -> HTTPException:
    return HTTPException(
        404,
        detail=error_detail("PUSH_DEVICE_NOT_FOUND", "알림 기기를 찾을 수 없습니다"),
    )


def parse_push_install_id(raw: str) -> UUID:
    try:
        return UUID(raw)
    except ValueError as exc:
        raise HTTPException(
            400,
            detail=error_detail("INVALID_PUSH_INSTALL_ID", "pushInstallId 형식이 올바르지 않습니다"),
        ) from exc


@router.post("/devices", response_model=RegisterDeviceResponse)
async def register_device(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> RegisterDeviceResponse:
    device = await parse_limited_json(request, RegisterDeviceRequest)
    try:
        raw = await supabase_admin.call_rpc(
            "gugu_push_register_device",
            {
                "p_user_id": user.id,
                "p_push_install_id": str(device.pushInstallId),
                "p_fcm_token": device.fcmToken,
                "p_platform": device.platform,
                "p_locale": device.locale,
                "p_time_zone": device.timeZone,
                "p_app_version": device.appVersion,
                "p_service_opt_in": device.serviceOptIn,
                "p_learning_opt_in": device.learningOptIn,
                "p_local_reminder_enabled": device.localReminderEnabled,
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise push_store_unavailable(exc) from exc
    try:
        result = _RegisterRpcResult.model_validate(raw)
    except ValidationError as exc:
        raise push_store_invalid_response("register_device", user.id) from exc

    log.info(
        "푸시 기기 등록 user=%s install=%s token=%s platform=%s service=%s learning=%s "
        "marketing=%s replaced=%d reassigned=%d evicted=%d",
        mask_identifier(user.id),
        mask_identifier(str(device.pushInstallId)),
        mask_identifier(device.fcmToken),
        device.platform,
        result.serviceOptIn,
        result.learningOptIn,
        result.marketingOptIn,
        result.replacedTokenRows,
        result.reassignedRows,
        result.evictedDevices,
    )
    return RegisterDeviceResponse.model_validate(result.model_dump(include=set(RegisterDeviceResponse.model_fields)))


@router.post("/devices/consents", response_model=ConsentResponse)
async def record_consent(
    request: Request,
    user: AuthUser = Depends(require_user),
) -> ConsentResponse:
    consent = await parse_limited_json(request, ConsentRequest)
    try:
        raw = await supabase_admin.call_rpc(
            "gugu_push_set_consent",
            {
                "p_user_id": user.id,
                "p_push_install_id": str(consent.pushInstallId),
                "p_kind": consent.kind,
                "p_granted": consent.granted,
                "p_source": consent.source,
                "p_app_version": consent.appVersion,
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise push_store_unavailable(exc) from exc
    try:
        result = _ConsentRpcResult.model_validate(raw)
    except ValidationError as exc:
        raise push_store_invalid_response("set_consent", user.id) from exc
    if not result.found:
        # 등록 전 동의 요청이거나 남의 기기 — 앱은 POST /devices 후 다시 시도한다
        raise device_not_found()
    if result.kind is None or result.granted is None or result.recordedAt is None:
        raise push_store_invalid_response("set_consent", user.id)

    log.info(
        "광고 수신 동의 기록 user=%s install=%s granted=%s source=%s",
        mask_identifier(user.id),
        mask_identifier(str(consent.pushInstallId)),
        result.granted,
        consent.source,
    )
    return ConsentResponse(
        kind=result.kind,
        granted=result.granted,
        recordedAt=result.recordedAt,
        sender=SENDER_NAME,
    )


@router.delete("/devices/{push_install_id}", response_model=UnregisterResponse)
async def unregister_device(
    push_install_id: str,
    user: AuthUser = Depends(require_user),
) -> UnregisterResponse:
    install_id = parse_push_install_id(push_install_id)
    try:
        raw = await supabase_admin.call_rpc(
            "gugu_push_unregister_device",
            {"p_user_id": user.id, "p_push_install_id": str(install_id)},
        )
    except supabase_admin.SupabaseDataError as exc:
        raise push_store_unavailable(exc) from exc
    try:
        result = _UnregisterRpcResult.model_validate(raw)
    except ValidationError as exc:
        raise push_store_invalid_response("unregister_device", user.id) from exc
    if result.status == "forbidden":
        raise device_not_found()

    log.info(
        "푸시 기기 해제 user=%s install=%s result=%s",
        mask_identifier(user.id),
        mask_identifier(str(install_id)),
        result.status,
    )
    # 이미 없는 기기도 성공 — 로그아웃 직전 재시도·중복 요청에 안전하다
    return UnregisterResponse(deleted=result.status == "deleted")
