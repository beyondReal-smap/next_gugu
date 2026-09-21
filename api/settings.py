"""API 환경 설정 로드와 시작 시 검증."""

import os
import re
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from pydantic import AnyHttpUrl, BaseModel, ConfigDict, Field, SecretStr, ValidationError, field_validator, model_validator


load_dotenv(Path(__file__).with_name(".env"))


class Settings(BaseModel):
    """필수 환경변수를 타입과 형식까지 검증한 불변 설정."""

    model_config = ConfigDict(frozen=True)

    product_id: str = Field(min_length=1)
    android_package: str = Field(min_length=3)
    # StoreKit 2 JWS 검증 — 번들 ID/앱 Apple ID 는 서명 페이로드와 대조한다
    ios_bundle_id: str = "site.smap.gugudan"
    ios_app_apple_id: Optional[int] = None
    # 인증서 온라인 폐기 확인(OCSP). 켜면 매 검증이 Apple OCSP 응답에 의존한다
    apple_jws_online_checks: bool = False
    db_socket: Path
    db_user: str = Field(min_length=1)
    db_password: SecretStr
    db_name: str = Field(min_length=1)
    supabase_url: AnyHttpUrl
    supabase_service_role_key: SecretStr
    supabase_jwt_secret: Optional[SecretStr] = None
    supabase_jwt_aud: str = "authenticated"
    google_service_account_json: Optional[Path] = None
    coach_llm_api_key: Optional[SecretStr] = None
    coach_llm_model: Optional[str] = None
    coach_llm_base_url: AnyHttpUrl = "https://api.openai.com/v1"
    coach_llm_timeout_seconds: float = Field(default=4.0, ge=1.0, le=15.0)
    coach_daily_quota: int = Field(default=30, ge=1, le=1000)
    cors_origins: tuple[str, ...] = (
        "https://localhost",
        "capacitor://localhost",
        "http://localhost:3000",
    )

    @field_validator("product_id", "db_user", "db_name", "supabase_jwt_aud", mode="before")
    @classmethod
    def validate_non_blank(cls, value: object) -> object:
        if not isinstance(value, str) or not value.strip():
            raise ValueError("빈 문자열일 수 없습니다")
        return value.strip()

    @field_validator("db_password", "supabase_service_role_key", mode="before")
    @classmethod
    def validate_required_secret(cls, value: object) -> object:
        if not isinstance(value, str) or not value.strip():
            raise ValueError("빈 문자열일 수 없습니다")
        return value

    @field_validator("product_id")
    @classmethod
    def validate_product_id(cls, value: str) -> str:
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", value):
            raise ValueError("영문자·숫자·점·밑줄·하이픈만 사용할 수 있습니다")
        return value

    @field_validator("ios_app_apple_id", mode="before")
    @classmethod
    def normalize_optional_int(cls, value: object) -> object:
        return None if value in (None, "") else value

    @field_validator("android_package", "ios_bundle_id")
    @classmethod
    def validate_android_package(cls, value: str) -> str:
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", value):
            raise ValueError("역도메인 형식이어야 합니다")
        return value

    @field_validator("db_socket")
    @classmethod
    def validate_db_socket(cls, value: Path) -> Path:
        if not value.is_absolute():
            raise ValueError("절대 경로여야 합니다")
        return value

    @field_validator("supabase_jwt_secret", "coach_llm_api_key", mode="before")
    @classmethod
    def normalize_optional_secret(cls, value: object) -> object:
        if value in (None, ""):
            return None
        if not isinstance(value, str) or not value.strip():
            raise ValueError("빈 문자열일 수 없습니다")
        return value.strip()

    @field_validator("google_service_account_json", mode="before")
    @classmethod
    def normalize_optional_path(cls, value: object) -> object:
        return None if value in (None, "") else value

    @field_validator("cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: object) -> object:
        if isinstance(value, str):
            origins = tuple(origin.strip() for origin in value.split(",") if origin.strip())
            if not origins:
                raise ValueError("하나 이상의 오리진이 필요합니다")
            return origins
        return value

    @field_validator("coach_llm_model", mode="before")
    @classmethod
    def normalize_optional_model(cls, value: object) -> object:
        if value in (None, ""):
            return None
        if not isinstance(value, str) or not value.strip():
            raise ValueError("빈 문자열일 수 없습니다")
        return value.strip()

    @model_validator(mode="after")
    def validate_coach_llm(self) -> "Settings":
        if self.coach_llm_api_key is not None and self.coach_llm_model is None:
            raise ValueError("COACH_LLM_API_KEY가 있으면 COACH_LLM_MODEL도 필요합니다")
        return self


_ENV_BY_FIELD = {
    "coach_llm_config": "COACH_LLM_CONFIG",
    "product_id": "PRODUCT_ID",
    "android_package": "ANDROID_PACKAGE",
    "ios_bundle_id": "IOS_BUNDLE_ID",
    "ios_app_apple_id": "IOS_APP_APPLE_ID",
    "apple_jws_online_checks": "APPLE_JWS_ONLINE_CHECKS",
    "db_socket": "DB_SOCKET",
    "db_user": "DB_USER",
    "db_password": "DB_PASSWORD",
    "db_name": "DB_NAME",
    "supabase_url": "SUPABASE_URL",
    "supabase_service_role_key": "SUPABASE_SERVICE_ROLE_KEY",
    "supabase_jwt_secret": "SUPABASE_JWT_SECRET",
    "supabase_jwt_aud": "SUPABASE_JWT_AUD",
    "google_service_account_json": "GOOGLE_SERVICE_ACCOUNT_JSON",
    "coach_llm_api_key": "COACH_LLM_API_KEY",
    "coach_llm_model": "COACH_LLM_MODEL",
    "coach_llm_base_url": "COACH_LLM_BASE_URL",
    "coach_llm_timeout_seconds": "COACH_LLM_TIMEOUT_SECONDS",
    "coach_daily_quota": "COACH_DAILY_QUOTA",
    "cors_origins": "CORS_ORIGINS",
}


def load_settings() -> Settings:
    values = {
        field: os.environ[env]
        for field, env in _ENV_BY_FIELD.items()
        if env in os.environ
    }
    try:
        return Settings.model_validate(values)
    except ValidationError as exc:
        details = []
        for error in exc.errors(include_input=False):
            field = str(error["loc"][0]) if error["loc"] else "coach_llm_config"
            env_name = _ENV_BY_FIELD.get(field, field)
            details.append(f"{env_name}: {error['msg']}")
        raise RuntimeError(f"API 필수 환경 설정 오류: {'; '.join(details)}") from None


settings = load_settings()
