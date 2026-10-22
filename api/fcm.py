"""FCM HTTP v1 발송기 — 계정별 알림(운영자 개별 발송·주간 리포트·2년 확인)용.

공지·광고는 Firebase 콘솔에서 토픽으로 보낸다. 이 모듈은 서버가 토큰을 알고 있는
계정별 알림만 보낸다.

- 서비스 계정 자격은 모듈 수명으로 캐시하고, 만료 시 토큰 갱신(블로킹 HTTP)은
  asyncio.to_thread 로 이벤트 루프 밖에서 한다.
- httpx.AsyncClient 는 발송기 수명으로 재사용한다.
- 응답 분류: 무효 토큰(404 UNREGISTERED · 400 토큰 필드 오류 · 403 SENDER_ID_MISMATCH)은 정리 대상,
  429/5xx·네트워크 오류는 Retry-After 또는 지수 백오프로 3회 재시도,
  401 THIRD_PARTY_AUTH_ERROR(APNs 키 문제 — iOS 토큰에만 발생)는 그 토큰만 실패로 기록하고 계속,
  그 밖의 401/403(자격·권한 문제)은 FcmAuthError 로 일괄 발송을 즉시 중단한다.
- 설정(FIREBASE_*)이 없으면 PushUnavailable — 앱 기동은 정상이고 발송 시점에만 실패한다.
"""

import asyncio
import logging
import time
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any, Literal, Optional, Protocol

import httpx

from observability import mask_identifier


log = logging.getLogger("gugu-api")

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
FCM_ENDPOINT = "https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
MAX_CONCURRENCY = 10
MAX_RETRIES = 3
BASE_BACKOFF_SECONDS = 1.0
MAX_BACKOFF_SECONDS = 60.0
REQUEST_TIMEOUT_SECONDS = 15.0

AndroidChannel = Literal["service_notice", "guardian_report", "promotion"]
SendOutcome = Literal["sent", "invalid_token", "failed"]
ResponseClass = Literal["sent", "prune", "retry", "auth", "fail"]


class PushUnavailable(RuntimeError):
    """푸시 발송 설정(FIREBASE_*)이 없거나 서비스 계정 파일을 읽을 수 없음."""


class FcmAuthError(RuntimeError):
    """FCM 이 자격·권한 문제(401/403)로 거부함 — 이후 발송도 모두 실패하므로 중단한다."""

    def __init__(self, status_code: int, error_code: Optional[str]):
        super().__init__(f"FCM 인증/권한 오류 ({status_code} {error_code or '-'})")
        self.status_code = status_code
        self.error_code = error_code


@dataclass(frozen=True)
class PushMessage:
    title: str
    body: str
    kind: str
    link: str
    android_channel: AndroidChannel
    ttl_seconds: int = 86_400


@dataclass(frozen=True)
class SendResult:
    token: str
    outcome: SendOutcome
    status_code: Optional[int]
    error_code: Optional[str]
    message_id: Optional[str] = None


class AccessTokenProvider(Protocol):
    async def get_token(self) -> str: ...


def build_message(token: str, message: PushMessage, now_epoch: Optional[float] = None) -> dict[str, Any]:
    """FCM v1 요청 본문. data 는 앱 딥링크 처리용(kind·link), 값은 모두 문자열이어야 한다."""
    expires_at = int((now_epoch if now_epoch is not None else time.time()) + message.ttl_seconds)
    return {
        "message": {
            "token": token,
            "notification": {"title": message.title, "body": message.body},
            "data": {"kind": message.kind, "link": message.link},
            "android": {
                "ttl": f"{message.ttl_seconds}s",
                "notification": {"channel_id": message.android_channel},
            },
            "apns": {
                "headers": {"apns-expiration": str(expires_at)},
                "payload": {"aps": {"sound": "default"}},
            },
        }
    }


def _error_info(response: httpx.Response) -> tuple[Optional[str], bool]:
    """(FCM errorCode 또는 google.rpc status, 토큰 필드 위반 여부)."""
    try:
        payload = response.json()
    except ValueError:
        return None, False
    error = payload.get("error") if isinstance(payload, dict) else None
    if not isinstance(error, dict):
        return None, False
    error_code: Optional[str] = error.get("status") if isinstance(error.get("status"), str) else None
    token_violation = False
    details = error.get("details")
    if isinstance(details, list):
        for detail in details:
            if not isinstance(detail, dict):
                continue
            detail_type = detail.get("@type", "")
            if detail_type.endswith("google.firebase.fcm.v1.FcmError") and isinstance(detail.get("errorCode"), str):
                error_code = detail["errorCode"]
            if detail_type.endswith("google.rpc.BadRequest"):
                for violation in detail.get("fieldViolations") or []:
                    if isinstance(violation, dict) and violation.get("field") == "message.token":
                        token_violation = True
    message = error.get("message")
    if isinstance(message, str) and "registration token" in message.lower():
        token_violation = True
    return error_code, token_violation


def classify_response(response: httpx.Response) -> tuple[ResponseClass, Optional[str]]:
    """FCM 응답을 처리 방식으로 분류한다."""
    status = response.status_code
    if status == 200:
        return "sent", None
    error_code, token_violation = _error_info(response)
    if status == 404:
        # FcmError 세부 코드 UNREGISTERED 일 때만 토큰 정리. 세부 코드 없는 일반 NOT_FOUND 는
        # 프로젝트 ID·설정 오류일 수 있어 정리하면 전체 토큰이 한꺼번에 지워진다 — 실패로만 남긴다.
        return ("prune" if error_code == "UNREGISTERED" else "fail"), error_code
    if status == 400 and error_code == "INVALID_ARGUMENT":
        # INVALID_ARGUMENT 는 메시지 본문 오류에도 온다. 토큰 문제임이 명시될 때만 정리한다 —
        # 우리 쪽 페이로드 버그로 전체 토큰을 지우는 사고를 막는다.
        return ("prune" if token_violation else "fail"), error_code
    if status == 403 and error_code == "SENDER_ID_MISMATCH":
        # 다른 Firebase 프로젝트의 토큰 — 토큰 단위 문제라 중단하지 않고 정리한다.
        # 중단하면 조작된 토큰 하나로 배치 전체를 매시 멈출 수 있다.
        return "prune", error_code
    if status == 401 and error_code == "THIRD_PARTY_AUTH_ERROR":
        # APNs 인증 키·인증서 문제 — iOS 토큰에만 나고 Android 발송은 정상이다.
        # 배치 전체를 멈추지 않고 해당 토큰만 실패로 기록한다(error_code 로 원인 추적).
        return "fail", error_code
    if status in (401, 403):
        return "auth", error_code
    if status == 429 or status >= 500:
        return "retry", error_code
    return "fail", error_code


def retry_delay(response: Optional[httpx.Response], attempt: int, base: float = BASE_BACKOFF_SECONDS) -> float:
    """Retry-After(초 또는 HTTP 날짜)가 있으면 따르고, 없으면 base·2^attempt."""
    if response is not None:
        header = response.headers.get("retry-after")
        if header:
            header = header.strip()
            if header.isdigit():
                return min(float(header), MAX_BACKOFF_SECONDS)
            try:
                retry_at = parsedate_to_datetime(header)
            except (TypeError, ValueError):
                retry_at = None
            if retry_at is not None:
                return min(max(retry_at.timestamp() - time.time(), 0.0), MAX_BACKOFF_SECONDS)
    return min(base * (2**attempt), MAX_BACKOFF_SECONDS)


class ServiceAccountTokenProvider:
    """서비스 계정 JSON 으로 OAuth 액세스 토큰을 발급·캐시한다."""

    def __init__(self, service_account_path: Path):
        self._path = service_account_path
        self._credentials: Any = None
        self._lock = asyncio.Lock()

    def _load(self) -> Any:
        from google.oauth2 import service_account

        if not self._path.is_file():
            raise PushUnavailable("FIREBASE_SERVICE_ACCOUNT_JSON 파일을 찾을 수 없습니다")
        try:
            return service_account.Credentials.from_service_account_file(str(self._path), scopes=[FCM_SCOPE])
        except (OSError, ValueError) as exc:
            raise PushUnavailable("FIREBASE_SERVICE_ACCOUNT_JSON 을 서비스 계정 자격으로 읽을 수 없습니다") from exc

    @staticmethod
    def _refresh(credentials: Any) -> None:
        from google.auth.transport.requests import Request as GoogleAuthRequest

        credentials.refresh(GoogleAuthRequest())

    async def get_token(self) -> str:
        async with self._lock:
            if self._credentials is None:
                self._credentials = await asyncio.to_thread(self._load)
            if not self._credentials.valid:
                await asyncio.to_thread(self._refresh, self._credentials)
            token = self._credentials.token
        if not isinstance(token, str) or not token:
            raise PushUnavailable("FCM 액세스 토큰을 발급받지 못했습니다")
        return token


class FcmSender:
    def __init__(
        self,
        project_id: str,
        token_provider: AccessTokenProvider,
        client: httpx.AsyncClient,
        *,
        max_concurrency: int = MAX_CONCURRENCY,
        max_retries: int = MAX_RETRIES,
        backoff_base: float = BASE_BACKOFF_SECONDS,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ):
        self._url = FCM_ENDPOINT.format(project_id=project_id)
        self._tokens = token_provider
        self._client = client
        self._semaphore = asyncio.Semaphore(max_concurrency)
        self._max_retries = max_retries
        self._backoff_base = backoff_base
        self._sleep = sleep

    async def send(self, token: str, message: PushMessage) -> SendResult:
        async with self._semaphore:
            return await self._send_with_retry(token, message)

    async def _send_with_retry(self, token: str, message: PushMessage) -> SendResult:
        body = build_message(token, message)
        last_status: Optional[int] = None
        last_error: Optional[str] = None
        for attempt in range(self._max_retries + 1):
            access_token = await self._tokens.get_token()
            response: Optional[httpx.Response] = None
            try:
                response = await self._client.post(
                    self._url,
                    headers={"Authorization": f"Bearer {access_token}"},
                    json=body,
                )
            except httpx.TransportError as exc:
                last_status, last_error = None, type(exc).__name__
                verdict: ResponseClass = "retry"
            else:
                verdict, last_error = classify_response(response)
                last_status = response.status_code

            if verdict == "sent":
                message_id = None
                if response is not None:
                    try:
                        message_id = response.json().get("name")
                    except ValueError:
                        message_id = None
                return SendResult(token, "sent", last_status, None, message_id)
            if verdict == "prune":
                log.info(
                    "FCM 무효 토큰 token=%s status=%s code=%s",
                    mask_identifier(token), last_status, last_error,
                )
                return SendResult(token, "invalid_token", last_status, last_error)
            if verdict == "auth":
                log.error("FCM 인증/권한 오류 status=%s code=%s — 발송 중단", last_status, last_error)
                raise FcmAuthError(last_status or 0, last_error)
            if verdict == "fail":
                log.warning(
                    "FCM 발송 실패 token=%s status=%s code=%s",
                    mask_identifier(token), last_status, last_error,
                )
                return SendResult(token, "failed", last_status, last_error)
            if attempt < self._max_retries:
                delay = retry_delay(response, attempt, self._backoff_base)
                log.warning(
                    "FCM 일시 오류 재시도 token=%s status=%s code=%s attempt=%d delay=%.1fs",
                    mask_identifier(token), last_status, last_error, attempt + 1, delay,
                )
                await self._sleep(delay)

        log.warning(
            "FCM 재시도 소진 token=%s status=%s code=%s",
            mask_identifier(token), last_status, last_error,
        )
        return SendResult(token, "failed", last_status, last_error)

    async def send_many(self, tokens: Sequence[str], message: PushMessage) -> list[SendResult]:
        """여러 기기에 보낸다. FcmAuthError 가 나면 남은 발송을 취소하고 그대로 올린다."""
        if not tokens:
            return []
        try:
            async with asyncio.TaskGroup() as group:
                tasks = [group.create_task(self.send(token, message)) for token in tokens]
        except* FcmAuthError as eg:
            raise eg.exceptions[0] from None
        return [task.result() for task in tasks]

    async def aclose(self) -> None:
        await self._client.aclose()


_sender: Optional[FcmSender] = None


def get_sender() -> FcmSender:
    """설정으로 만든 모듈 수명 발송기. 설정이 없으면 PushUnavailable."""
    global _sender
    if _sender is not None:
        return _sender
    from settings import settings

    if settings.firebase_project_id is None or settings.firebase_service_account_json is None:
        raise PushUnavailable("FIREBASE_PROJECT_ID / FIREBASE_SERVICE_ACCOUNT_JSON 이 설정되지 않았습니다")
    _sender = FcmSender(
        settings.firebase_project_id,
        ServiceAccountTokenProvider(settings.firebase_service_account_json),
        httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS),
    )
    return _sender


def is_configured() -> bool:
    from settings import settings

    return settings.firebase_project_id is not None and settings.firebase_service_account_json is not None


async def close_sender() -> None:
    """CLI 종료 시 연결을 정리한다."""
    global _sender
    if _sender is not None:
        await _sender.aclose()
        _sender = None
