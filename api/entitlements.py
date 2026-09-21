"""구매 원장에서 파생한 서버 권위 기능별 권한."""

from typing import Literal

from fastapi import HTTPException

from db import PremiumLookupError, user_premium_products


FeatureKey = Literal["ai_coach", "longitudinal_report", "multi_learner", "adventure_pack"]
FEATURE_KEYS: tuple[FeatureKey, ...] = (
    "ai_coach",
    "longitudinal_report",
    "multi_learner",
    "adventure_pack",
)


def premium_products(user_id: str) -> list[str]:
    try:
        return user_premium_products(user_id)
    except PremiumLookupError as exc:
        raise HTTPException(
            503,
            detail={
                "code": "ENTITLEMENT_STORE_UNAVAILABLE",
                "message": "프리미엄 권한 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
            },
        ) from exc


def active_features(user_id: str) -> set[FeatureKey]:
    return set(FEATURE_KEYS) if premium_products(user_id) else set()


def require_feature(user_id: str, feature_key: FeatureKey) -> None:
    if feature_key not in active_features(user_id):
        raise HTTPException(
            403,
            detail={
                "code": "FEATURE_NOT_ENTITLED",
                "message": "이 기능을 사용할 권한이 없습니다",
                "featureKey": feature_key,
            },
        )
