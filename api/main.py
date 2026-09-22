"""구구 어드벤처 인앱결제 영수증 검증 API + 공개 콘텐츠 MCP 서버.

- iOS: Apple verifyReceipt (프로덕션 → 21007 시 샌드박스 폴백)
- Android: Google Play Developer API (서비스 계정 필요)
- 검증 성공 시 MariaDB(gugu.purchases)에 기록
- Supabase JWT(Bearer) 동반 시 구매를 계정에 연결 → 웹·타 기기에서 프리미엄 인식
- /api/mcp: 구구단 학습 콘텐츠를 AI에 제공하는 읽기 전용 MCP 서버 (mcp_server.py)
  cloudflared 라우팅이 gugu.smap.site/api/* → 이 프로세스라, 별도 인프라 없이 공개된다.
"""
import json
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Literal, Optional

import httpx
from fastapi import Depends, FastAPI, HTTPException
from mcp.server.transport_security import TransportSecuritySettings
from pydantic import BaseModel

from settings import settings  # noqa: E402

import mcp_server  # noqa: E402
import supabase_admin  # noqa: E402
import apple_jws  # noqa: E402
from auth import AuthUser, lenient_user, require_user  # noqa: E402
from cors import ScopedCORSMiddleware  # noqa: E402
from db import (  # noqa: E402
    DatabaseHealthError,
    PurchasePersistenceError,
    check_database,
    ensure_schema,
    record_purchase,
    unlink_user_purchases,
)
from entitlements import FEATURE_KEYS, FeatureKey, premium_products  # noqa: E402
from observability import RequestIdMiddleware, mask_identifier  # noqa: E402
from learning import error_detail, router as learning_router  # noqa: E402
from adaptive import router as adaptive_router  # noqa: E402
from coach import router as coach_router  # noqa: E402
from reports import router as reports_router  # noqa: E402
import llm_client  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("gugu-api")

PRODUCT_ID = settings.product_id
ANDROID_PACKAGE = settings.android_package
GOOGLE_SA_PATH = str(settings.google_service_account_json or "")
# Capacitor 웹뷰 오리진 (웹 배포는 같은 오리진이라 CORS 불필요)
CORS_ORIGINS = settings.cors_origins

APPLE_PROD_URL = "https://buy.itunes.apple.com/verifyReceipt"
APPLE_SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt"


MCP_MOUNT_PATH = "/api/mcp"

# MCP는 세션 없이(stateless) 동작시킨다 — pm2 단일 프로세스에 상태를 남기지 않아 재시작에 안전하다.
# streamable_http_path="/"라 마운트 경로가 그대로 최종 URL이 된다 → /api/mcp/
mcp_http_app = mcp_server.mcp.streamable_http_app(
    streamable_http_path="/",
    stateless_http=True,
    # DNS rebinding 보호는 "로컬호스트에서 도는 MCP 서버를 악성 웹페이지가 피해자 브라우저를 통해
    # 호출하는 것"을 막는 장치다. 이 엔드포인트는 공개 인터넷에 있고 인증 없이 누구나 읽을 수 있는
    # 콘텐츠만 내보내므로 rebinding으로 얻을 것이 없다. 반대로 켜 두면 cloudflared를 거쳐 들어온
    # Host(gugu.smap.site)가 기본 허용값(127.0.0.1)과 달라 모든 요청이 421로 막힌다.
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    ensure_schema()
    # 마운트된 sub-app의 lifespan은 자동으로 돌지 않는다 → 세션 매니저를 여기서 직접 연다
    async with mcp_http_app.router.lifespan_context(mcp_http_app):
        yield


app = FastAPI(title="gugu-api", docs_url=None, redoc_url=None, lifespan=lifespan)
app.add_middleware(
    ScopedCORSMiddleware,
    public_prefix=MCP_MOUNT_PATH,
    # 공개 읽기 전용 데이터 — 브라우저 기반 MCP 클라이언트를 위해 모든 오리진을 허용한다.
    # 쿠키를 쓰지 않으므로(allow_credentials 기본 False) 와일드카드가 안전하다.
    public_options={
        "allow_origins": ["*"],
        "allow_methods": ["GET", "POST", "DELETE", "OPTIONS"],
        "allow_headers": ["*"],
        "expose_headers": ["Mcp-Session-Id", "Mcp-Protocol-Version", "X-Request-ID"],
    },
    # 결제·계정 API는 Authorization을 받으므로 기존대로 오리진을 제한한다.
    default_options={
        "allow_origins": list(CORS_ORIGINS),
        "allow_methods": ["GET", "POST", "DELETE"],
        "allow_headers": ["Authorization", "Content-Type", "X-Request-ID"],
        "expose_headers": ["X-Request-ID"],
    },
)
app.add_middleware(RequestIdMiddleware)
app.include_router(learning_router)
app.include_router(adaptive_router)
app.include_router(coach_router)
app.include_router(reports_router)
app.mount(MCP_MOUNT_PATH, mcp_http_app)


class VerifyRequest(BaseModel):
    platform: Literal["ios", "android"]
    productId: str
    # iOS: StoreKit 2 Transaction.jwsRepresentation (권장 — 설치 경로와 무관하게 존재)
    jws: Optional[str] = None
    # iOS: base64 appStoreReceipt (레거시 — App Store/TestFlight 설치본만) / Android: purchaseToken
    receipt: Optional[str] = None
    purchaseToken: Optional[str] = None


class VerifyResponse(BaseModel):
    ok: bool
    premium: bool
    transactionId: Optional[str] = None


@app.get("/api/health")
def health():
    return {"status": "ok", "service": "gugu-api"}


@app.get("/api/health/live")
def health_live():
    return {"status": "ok", "service": "gugu-api"}


@app.get("/api/health/ready")
def health_ready():
    try:
        check_database()
    except DatabaseHealthError as exc:
        log.warning("준비 상태 확인 실패: database")
        raise HTTPException(
            503,
            detail={"code": "DATABASE_UNAVAILABLE", "message": "데이터베이스가 준비되지 않았습니다"},
        ) from exc
    return {
        "status": "ok",
        "service": "gugu-api",
        "checks": {
            "settings": "ok",
            "database": "ok",
            "coach": "enabled" if llm_client.is_enabled() else "disabled_no_api_key",
        },
    }


async def verify_apple(receipt_b64: str) -> tuple[bool, str]:
    """Apple 영수증 검증. (성공 여부, 트랜잭션ID) 반환"""
    payload = {"receipt-data": receipt_b64}
    async with httpx.AsyncClient(timeout=15) as client:
        res = await client.post(APPLE_PROD_URL, json=payload)
        data = res.json()
        # 21007 = 샌드박스 영수증을 프로덕션에 보냄 → 샌드박스로 재시도 (심사 시 필수)
        if data.get("status") == 21007:
            res = await client.post(APPLE_SANDBOX_URL, json=payload)
            data = res.json()

    status = data.get("status")
    if status != 0:
        log.warning("Apple 검증 실패 status=%s", status)
        return False, ""

    in_app = (data.get("receipt") or {}).get("in_app", [])
    for tx in in_app:
        if tx.get("product_id") == PRODUCT_ID:
            return True, tx.get("original_transaction_id") or tx.get("transaction_id", "")
    log.warning("Apple 영수증에 %s 상품 없음", PRODUCT_ID)
    return False, ""


async def verify_google(purchase_token: str, product_id: str) -> tuple[bool, str]:
    """Google Play 상품 구매 검증. (성공 여부, 주문ID) 반환"""
    if not GOOGLE_SA_PATH or not Path(GOOGLE_SA_PATH).is_file():
        # 서비스 계정 미설정 — 명확히 실패시켜 원인 표면화
        raise HTTPException(503, "Google 서비스 계정이 서버에 설정되지 않았습니다")

    from google.auth.transport.requests import Request as GARequest
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_file(
        GOOGLE_SA_PATH, scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    creds.refresh(GARequest())

    url = (
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
        f"{ANDROID_PACKAGE}/purchases/products/{product_id}/tokens/{purchase_token}"
    )
    async with httpx.AsyncClient(timeout=15) as client:
        res = await client.get(url, headers={"Authorization": f"Bearer {creds.token}"})

    if res.status_code != 200:
        log.warning("Google 검증 실패 %s: %s", res.status_code, res.text[:200])
        return False, ""

    data = res.json()
    # purchaseState 0 = 구매 완료 (1=취소, 2=보류)
    if data.get("purchaseState") == 0:
        return True, data.get("orderId", purchase_token[:32])
    return False, ""


async def verify_ios(req: VerifyRequest) -> tuple[bool, str, Optional[str]]:
    """iOS 검증 — StoreKit 2 JWS 우선, 없으면 레거시 영수증. (성공, 거래ID, 환경) 반환.

    JWS 는 Apple 이 직접 서명한 토큰으로 설치 경로와 무관하게 기기에 존재하고,
    사용 중단이 예고된 verifyReceipt 에 의존하지 않는다. 다만 심사 중인 2.1.0
    Capacitor 빌드는 receipt 만 보내므로 레거시 경로를 함께 유지한다.
    """
    if req.jws:
        try:
            verified = apple_jws.verify_transaction_jws(req.jws, PRODUCT_ID)
        except apple_jws.AppleJwsUnavailable as exc:
            # 설정 미비를 "구매 없음"으로 흘리면 원인이 묻힌다 → 503 으로 표면화
            log.error("JWS 검증 설정 오류: %s", exc)
            raise HTTPException(
                503,
                detail={
                    "code": "APPLE_JWS_UNAVAILABLE",
                    "message": "구매 검증 설정이 완료되지 않았습니다. 잠시 후 다시 시도해 주세요.",
                },
            ) from exc
        if verified is None:
            return False, "", None
        return True, verified.transaction_id, verified.environment

    if req.receipt:
        ok, tx_id = await verify_apple(req.receipt)
        return ok, tx_id, None

    raise HTTPException(400, "iOS 검증에는 jws 또는 receipt가 필요합니다")


class CdvValidateRequest(BaseModel):
    """cordova-plugin-purchase v13 validator 프로토콜 요청"""
    id: str
    type: str
    transaction: dict
    device: Optional[dict] = None


def persist_verified_purchase(
    platform: str,
    transaction_id: str,
    product_id: str,
    user_id: Optional[str],
    environment: Optional[str] = None,
) -> None:
    try:
        record_purchase(
            platform,
            transaction_id,
            product_id,
            "verified",
            user_id=user_id,
            environment=environment,
        )
    except PurchasePersistenceError as exc:
        raise HTTPException(
            503,
            detail={
                "code": "PURCHASE_PERSISTENCE_FAILED",
                "message": "구매 검증 결과를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            },
        ) from exc


@app.post("/api/iap/cdv-validate")
async def cdv_validate(req: CdvValidateRequest, user: Optional[AuthUser] = Depends(lenient_user)):
    """cordova-plugin-purchase의 store.validator 엔드포인트.

    성공: {ok: true, data: {id, latest_receipt, transaction}}
    실패: {ok: false, code, message}
    Bearer JWT 동반 시 검증 성공 구매를 해당 계정에 연결.
    """
    tx = req.transaction
    tx_type = tx.get("type", "")

    try:
        if tx_type == "ios-appstore":
            receipt = tx.get("appStoreReceipt") or ""
            if not receipt:
                return {"ok": False, "code": 6778001, "message": "appStoreReceipt 없음"}
            ok, tx_id = await verify_apple(receipt)
        elif tx_type == "android-playstore":
            token = tx.get("purchaseToken")
            if not token:
                # receipt JSON 문자열 안에 purchaseToken이 들어오는 경우 폴백
                try:
                    token = json.loads(tx.get("receipt", "{}")).get("purchaseToken")
                except (ValueError, TypeError):
                    token = None
            if not token:
                return {"ok": False, "code": 6778001, "message": "purchaseToken 없음"}
            ok, tx_id = await verify_google(token, PRODUCT_ID)
        else:
            return {"ok": False, "code": 6778001, "message": f"알 수 없는 플랫폼: {tx_type}"}
    except HTTPException as e:
        return {"ok": False, "code": 6778001, "message": e.detail}

    if not ok:
        return {"ok": False, "code": 6778003, "message": "영수증 검증 실패"}

    persist_verified_purchase(
        "ios" if tx_type == "ios-appstore" else "android",
        tx_id,
        PRODUCT_ID,
        user.id if user else None,
    )
    log.info(
        "cdv 검증 성공 type=%s tx=%s user=%s",
        tx_type,
        mask_identifier(tx_id),
        mask_identifier(user.id if user else None),
    )
    return {"ok": True, "data": {"id": req.id, "latest_receipt": True, "transaction": tx}}


@app.post("/api/iap/verify", response_model=VerifyResponse)
async def verify(req: VerifyRequest, user: Optional[AuthUser] = Depends(lenient_user)):
    if req.productId != PRODUCT_ID:
        raise HTTPException(400, f"알 수 없는 상품: {req.productId}")

    environment: Optional[str] = None
    if req.platform == "ios":
        ok, tx_id, environment = await verify_ios(req)
    else:
        if not req.purchaseToken:
            raise HTTPException(400, "Android 검증에는 purchaseToken이 필요합니다")
        ok, tx_id = await verify_google(req.purchaseToken, req.productId)

    if ok:
        persist_verified_purchase(
            req.platform,
            tx_id,
            req.productId,
            user.id if user else None,
            environment,
        )
        log.info(
            "검증 성공 platform=%s tx=%s user=%s env=%s source=%s",
            req.platform,
            mask_identifier(tx_id),
            mask_identifier(user.id if user else None),
            environment or "-",
            "jws" if req.jws else "receipt",
        )

    return VerifyResponse(ok=True, premium=ok, transactionId=tx_id or None)


class PremiumEntitlement(BaseModel):
    featureKey: FeatureKey
    status: Literal["active"]
    source: Literal["lifetime_purchase"]


class PremiumStatusResponse(BaseModel):
    premium: bool
    products: list[str]
    entitlements: list[PremiumEntitlement]


@app.get("/api/premium/status", response_model=PremiumStatusResponse)
def premium_status(user: AuthUser = Depends(require_user)):
    """로그인 계정의 프리미엄 상태 — 웹·타 기기에서 구매 인식용."""
    products = premium_products(user.id)
    premium = len(products) > 0
    entitlements = []
    if premium:
        entitlements = [
            PremiumEntitlement(featureKey=feature_key, status="active", source="lifetime_purchase")
            for feature_key in FEATURE_KEYS
        ]
    return PremiumStatusResponse(
        premium=premium,
        products=products,
        entitlements=entitlements,
    )


class GuardianVerifyResponse(BaseModel):
    guardian: bool
    products: list[str]
    verifiedBy: str
    verifiedAt: str


@app.post("/api/account/guardian", response_model=GuardianVerifyResponse)
async def verify_guardian(user: AuthUser = Depends(require_user)):
    """앱 내 구매 이력을 근거로 보호자 권한을 설정한다.

    학습 업로드 게이트(require_learning_upload_permission)가 요구하는
    app_metadata.account_role=guardian / age_verified=true 를 여기서만 쓴다.

    한계: IAP 구매는 성인 확인이 아니다. 가족 공유·기프트카드·아이가 부모 기기로
    결제한 경우까지 통과한다. "성인이 결제한 것으로 추정"하는 수준이며, 근거를
    age_verified_by 로 남겨 나중에 더 강한 검증으로 격상할 수 있게 한다.
    """
    # 익명 계정은 제외 — 기기를 잃으면 되찾을 수 없어 보호자 계정으로 볼 수 없다
    if not user.email:
        raise HTTPException(
            403,
            detail=error_detail(
                "ANONYMOUS_NOT_ELIGIBLE",
                "이메일을 연결한 계정에서만 기록 보관을 켤 수 있습니다",
            ),
        )

    products = premium_products(user.id)
    if not products:
        raise HTTPException(
            403,
            detail=error_detail(
                "NO_PURCHASE_FOUND",
                "이 계정에 연결된 구매 내역이 없습니다",
            ),
        )

    verified_at = datetime.now(timezone.utc).isoformat()
    await supabase_admin.update_user_app_metadata(
        user.id,
        {
            "account_role": "guardian",
            "age_verified": True,
            "age_verified_by": "iap_purchase",
            "age_verified_at": verified_at,
        },
    )
    log.info(
        "보호자 권한 설정 user=%s products=%d 근거=iap_purchase",
        mask_identifier(user.id), len(products),
    )
    # app_metadata 는 JWT 에 박혀 나가므로 클라이언트가 토큰을 새로 받아야 반영된다
    return GuardianVerifyResponse(
        guardian=True, products=products,
        verifiedBy="iap_purchase", verifiedAt=verified_at,
    )


@app.delete("/api/account")
async def delete_account(user: AuthUser = Depends(require_user)):
    """계정 삭제 (App Store 5.1.1(v)).

    순서가 중요하다 — 학습 데이터를 먼저 지우고 인증 사용자를 지운다.
    auth.users 를 참조하는 FK 가 없어서, 사용자를 먼저 지우면 학습 데이터가 고아로
    남아 어떤 계정의 것인지도 알 수 없게 된다(2026-09-22 발견). 개인정보처리방침은
    계정과 학습 기록을 함께 지운다고 고지하므로 반드시 둘 다 지워야 한다.

    구매 영수증은 환불·감사 대응을 위해 보존하고 계정 연결만 해제한다.
    """
    try:
        purged = await supabase_admin.call_rpc(
            "gugu_purge_owner_data", {"p_owner_user_id": user.id}
        )
    except supabase_admin.SupabaseDataError as exc:
        # 학습 데이터를 못 지웠는데 계정만 지우면 고아 데이터가 남는다 → 중단한다
        log.exception("계정 삭제 중단: 학습 데이터 정리 실패 user=%s", mask_identifier(user.id))
        raise HTTPException(
            503,
            detail=error_detail(
                "ACCOUNT_DELETE_FAILED",
                "계정을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            ),
        ) from exc

    unlinked = unlink_user_purchases(user.id)
    await supabase_admin.delete_user(user.id)
    log.info(
        "계정 삭제 user=%s 학습데이터=%s 구매연결해제=%d건",
        mask_identifier(user.id),
        purged,
        unlinked,
    )
    return {"ok": True}
