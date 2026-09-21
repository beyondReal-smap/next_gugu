"""Supabase JWT 검증 — FastAPI 의존성.

- 기본: Supabase JWKS(ES256/RS256) 원격 키로 로컬 검증 (PyJWKClient가 키 캐시)
- 레거시: SUPABASE_JWT_SECRET이 설정되면 HS256 대칭키 검증도 허용
- SUPABASE_URL은 settings.py가 앱 시작 시 필수 검증
"""
import logging
from dataclasses import dataclass
from typing import Optional

import jwt
from fastapi import Header, HTTPException
from jwt import PyJWKClient

from settings import settings

log = logging.getLogger("gugu-api")

SUPABASE_URL = str(settings.supabase_url).rstrip("/")
SUPABASE_JWT_SECRET = (
    settings.supabase_jwt_secret.get_secret_value() if settings.supabase_jwt_secret else ""
)
SUPABASE_JWT_AUD = settings.supabase_jwt_aud

_jwks_client: Optional[PyJWKClient] = None


@dataclass
class AuthUser:
    id: str  # Supabase auth.users.id (UUID)
    email: Optional[str]
    app_metadata: dict[str, object]


def _get_jwks_client() -> PyJWKClient:
    global _jwks_client
    if _jwks_client is None:
        _jwks_client = PyJWKClient(
            f"{SUPABASE_URL}/auth/v1/.well-known/jwks.json",
            cache_keys=True, lifespan=3600,
        )
    return _jwks_client


def _decode(token: str) -> dict:
    header = jwt.get_unverified_header(token)
    if header.get("alg") == "HS256":
        if not SUPABASE_JWT_SECRET:
            raise HTTPException(401, "HS256 토큰이지만 SUPABASE_JWT_SECRET이 설정되지 않았습니다")
        return jwt.decode(
            token, SUPABASE_JWT_SECRET, algorithms=["HS256"], audience=SUPABASE_JWT_AUD,
        )
    signing_key = _get_jwks_client().get_signing_key_from_jwt(token)
    return jwt.decode(
        token, signing_key.key, algorithms=["ES256", "RS256"], audience=SUPABASE_JWT_AUD,
    )


def _verify_bearer(authorization: str) -> AuthUser:
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(401, "Authorization 헤더 형식이 올바르지 않습니다 (Bearer <token>)")
    if not SUPABASE_URL:
        raise HTTPException(503, "인증이 서버에 구성되지 않았습니다 (SUPABASE_URL 미설정)")
    try:
        claims = _decode(token)
    except HTTPException:
        raise
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "토큰이 만료되었습니다")
    except jwt.PyJWTError as e:
        log.warning("JWT 검증 실패: %s", e)
        raise HTTPException(401, "유효하지 않은 토큰입니다")

    sub = claims.get("sub")
    if not sub:
        raise HTTPException(401, "토큰에 sub 클레임이 없습니다")
    raw_app_metadata = claims.get("app_metadata")
    if raw_app_metadata is None:
        app_metadata: dict[str, object] = {}
    elif isinstance(raw_app_metadata, dict):
        app_metadata = raw_app_metadata
    else:
        raise HTTPException(401, "토큰의 app_metadata 형식이 올바르지 않습니다")
    return AuthUser(id=sub, email=claims.get("email"), app_metadata=app_metadata)


# 주의: 블로킹 I/O(JWKS fetch)가 있어 sync def — FastAPI가 스레드풀에서 실행
def require_user(authorization: Optional[str] = Header(None)) -> AuthUser:
    """로그인 필수 엔드포인트용. 헤더 없음/무효 → 401."""
    if not authorization:
        raise HTTPException(401, "로그인이 필요합니다")
    return _verify_bearer(authorization)


def optional_user(authorization: Optional[str] = Header(None)) -> Optional[AuthUser]:
    """게스트 허용 엔드포인트용. 헤더 없으면 None, 있는데 무효면 401(조용히 무시하지 않음)."""
    if not authorization:
        return None
    return _verify_bearer(authorization)


def lenient_user(authorization: Optional[str] = Header(None)) -> Optional[AuthUser]:
    """영수증 검증 경로 전용 — 토큰이 만료/무효여도 구매 검증을 막지 않고 게스트로 강등.

    결제 검증은 인증 성공에 의존해서는 안 된다(만료 토큰 때문에 정상 구매가 실패하는 퇴행 방지).
    계정 연결은 이후 '구매 복원'으로 재시도 가능. 강등 시 경고 로그로 추적성 유지.
    """
    if not authorization:
        return None
    try:
        return _verify_bearer(authorization)
    except HTTPException as e:
        log.warning("IAP 검증에 첨부된 토큰 무효(%s) — 게스트로 진행: %s", e.status_code, e.detail)
        return None
