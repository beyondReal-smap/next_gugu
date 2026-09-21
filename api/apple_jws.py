"""StoreKit 2 서명 트랜잭션(JWS) 검증.

레거시 경로(appStoreReceipt + Apple verifyReceipt)는 App Store·TestFlight 설치본에만
존재하는 번들 영수증 파일을 요구한다. 그래서 개발 서명으로 직접 설치한 빌드에서는
구매를 계정에 연결할 수 없었다. StoreKit 2 의 `Transaction.jwsRepresentation` 은
Apple 이 직접 서명한 토큰이라 설치 경로와 무관하게 기기에 존재하므로 이 경로를
정식 검증으로 쓴다. Apple 이 verifyReceipt 사용 중단을 예고했으므로 장기적으로도 맞다.

검증 자체는 Apple 공식 `app-store-server-library` 의 SignedDataVerifier 가 수행한다
(x5c 인증서 체인 → Apple 루트 CA, ES256 서명, bundleId, environment 일치까지).
서버는 그 위에 상품 일치·환불 취소 여부만 추가로 판정한다.
"""
import base64
import binascii
import json
import logging
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Optional

from appstoreserverlibrary.models.Environment import Environment
from appstoreserverlibrary.signed_data_verifier import SignedDataVerifier, VerificationException

from settings import settings

log = logging.getLogger("gugu-api")

CERT_DIR = Path(__file__).with_name("apple_certs")

# 기기에서 올라온 JWS 는 이 두 환경 중 하나다. XCODE/LOCAL_TESTING 은 서명이 없어 받지 않는다.
_ACCEPTED_ENVIRONMENTS = (Environment.PRODUCTION, Environment.SANDBOX)
_ENVIRONMENT_BY_CLAIM = {env.value: env for env in _ACCEPTED_ENVIRONMENTS}


class AppleJwsUnavailable(RuntimeError):
    """JWS 검증에 필요한 서버 설정(루트 인증서·앱 식별자)이 갖춰지지 않음."""


@dataclass(frozen=True)
class VerifiedTransaction:
    """서명 검증을 통과한 거래."""

    transaction_id: str   # originalTransactionId — 구매 원장의 키
    product_id: str
    environment: str      # "Production" | "Sandbox"


@lru_cache(maxsize=1)
def _root_certificates() -> tuple[bytes, ...]:
    """Apple 루트 CA(DER) — 프로세스 수명 동안 한 번만 읽는다."""
    if not CERT_DIR.is_dir():
        raise AppleJwsUnavailable(f"Apple 루트 인증서 디렉토리가 없습니다: {CERT_DIR}")
    certificates = tuple(path.read_bytes() for path in sorted(CERT_DIR.glob("*.cer")))
    if not certificates:
        raise AppleJwsUnavailable(f"Apple 루트 인증서를 찾지 못했습니다: {CERT_DIR}")
    return certificates


@lru_cache(maxsize=len(_ACCEPTED_ENVIRONMENTS))
def _verifier(environment: Environment) -> SignedDataVerifier:
    """환경별 검증기 — 인증서 파싱 비용을 피해 프로세스 수명으로 재사용한다."""
    if environment is Environment.PRODUCTION and settings.ios_app_apple_id is None:
        raise AppleJwsUnavailable("프로덕션 거래 검증에는 IOS_APP_APPLE_ID 설정이 필요합니다")
    return SignedDataVerifier(
        list(_root_certificates()),
        settings.apple_jws_online_checks,
        environment,
        settings.ios_bundle_id,
        settings.ios_app_apple_id,
    )


def _claimed_environment(jws: str) -> Optional[Environment]:
    """서명 검증 전 페이로드의 environment.

    어느 검증기를 먼저 쓸지 고르는 힌트일 뿐이다. 값이 위조돼도 검증기가 서명과
    environment 일치를 다시 확인하므로 판정에는 영향이 없다.
    """
    try:
        segment = jws.split(".")[1]
        payload = json.loads(base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4)))
    except (IndexError, ValueError, TypeError, binascii.Error):
        return None
    return _ENVIRONMENT_BY_CLAIM.get(payload.get("environment"))


def _verification_order(jws: str) -> tuple[Environment, ...]:
    hint = _claimed_environment(jws)
    if hint is None:
        return _ACCEPTED_ENVIRONMENTS
    return (hint, *(env for env in _ACCEPTED_ENVIRONMENTS if env is not hint))


def verify_transaction_jws(jws: str, expected_product_id: str) -> Optional[VerifiedTransaction]:
    """StoreKit 2 서명 트랜잭션을 검증한다. 실패하면 None.

    환경(프로덕션/샌드박스)은 요청이 알려주는 값을 믿지 않고 양쪽 검증기로 확인한다.
    설정 미비(AppleJwsUnavailable)는 검증 실패와 구분해 그대로 올린다 — 조용히
    폴백하면 서버 설정 오류가 "구매 없음"으로 묻힌다.
    """
    unavailable: Optional[AppleJwsUnavailable] = None
    failures: list[str] = []

    for environment in _verification_order(jws):
        try:
            verifier = _verifier(environment)
        except AppleJwsUnavailable as exc:
            unavailable = exc
            continue

        try:
            payload = verifier.verify_and_decode_signed_transaction(jws)
        except VerificationException as exc:
            failures.append(f"{environment.value}={exc.status.name}")
            continue

        return _judge(payload, environment, expected_product_id)

    if unavailable is not None and not failures:
        raise unavailable
    log.warning("JWS 서명 검증 실패 %s", ", ".join(failures) or "해석 불가")
    return None


def _judge(payload, environment: Environment, expected_product_id: str) -> Optional[VerifiedTransaction]:
    """서명은 통과한 거래에 대한 비즈니스 판정 — 상품 일치와 환불 취소 여부."""
    if payload.productId != expected_product_id:
        log.warning("JWS 상품 불일치 %s (기대 %s)", payload.productId, expected_product_id)
        return None
    if payload.revocationDate is not None:
        log.warning("JWS 취소된 거래 — 환불/취소일=%s", payload.revocationDate)
        return None

    transaction_id = payload.originalTransactionId or payload.transactionId
    if not transaction_id:
        log.warning("JWS 에 거래 식별자가 없습니다")
        return None

    return VerifiedTransaction(
        transaction_id=transaction_id,
        product_id=payload.productId,
        environment=payload.rawEnvironment or environment.value,
    )
