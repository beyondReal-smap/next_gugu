"""서버 발송 알림 문구 — 정보성 템플릿, 광고 문구 검사, 딥링크 허용 목록.

서버(push_admin.py · push_jobs.py)는 정보성 알림만 보낸다. 광고는 Firebase 콘솔에서
promo 토픽으로만 보낸다(README "Firebase 콘솔 운영 규칙"). 정보성 알림에 구매 권유
문구가 섞이면 광고성 정보가 되므로(정보통신망법 제50조) 운영자가 입력한 문구는 광고 키워드를
검사해 거부한다.

잠금화면에 보이는 문구에는 아동의 학습 수치(정답 수·정답률·약점 단 등)를 넣지 않는다
(Apple 4.5.4, 아동 개인정보 최소 노출). 자세한 내용은 앱 안에서만 보여준다.

문구는 기기 앱 언어(push_devices.locale 의 언어 — 앱이 AppLanguage 로 보낸다)별로 고른다.
ko·ja 가 아니면 en. locale 이 없는 기기(앱 2.6 이전)는 한국어 기기라 ko.
광고 수신동의 2년 확인은 한국 법정 통지이고 광고 동의는 한국 스토어에서만 받으므로 ko 만 있다.
"""

import re
from dataclasses import dataclass
from datetime import datetime
from typing import Literal, Optional

from fcm import AndroidChannel, PushMessage


Lang = Literal["ko", "en", "ja"]

SENDER_NAME = "구구 어드벤처"
MAX_TITLE_CHARS = 50
MAX_BODY_CHARS = 200

DeepLink = Literal["home", "learn", "profile", "profile/report", "paywall", "settings/notifications"]
ALLOWED_LINKS: frozenset[str] = frozenset(
    ("home", "learn", "profile", "profile/report", "paywall", "settings/notifications")
)

TemplateKey = Literal[
    "premium_granted",
    "premium_revoked",
    "account_notice",
    "weekly_report",
    "marketing_reconfirm",
    "test",
]
# 운영자 CLI 가 고를 수 있는 템플릿 — 주간 리포트·2년 확인은 배치 전용이다
OPERATOR_TEMPLATES: tuple[TemplateKey, ...] = ("premium_granted", "premium_revoked", "account_notice", "test")

# 구매 권유·판촉으로 읽히는 표현. 공백을 지우고 소문자로 비교한다.
AD_KEYWORDS: tuple[str, ...] = (
    "(광고)",
    "광고",
    "할인",
    "이벤트",
    "구매",
    "결제",
    "특가",
    "쿠폰",
    "세일",
    "프로모션",
    "혜택",
    "증정",
    "선착순",
    "오늘만",
    "한정",
    "최저가",
    "sale",
    "discount",
    "coupon",
    "promo",
    "event",
    "%off",
    "offer",
    "freetrial",
    "deal",
    "広告",
    "セール",
    "割引",
    "クーポン",
    "キャンペーン",
    "限定",
    "特典",
    "購入",
)

# 잠금화면에 아동 학습 수치를 넣지 않는다 — 숫자+단위 패턴을 막는다 (ko·en·ja)
_LEARNING_NUMBER_RE = re.compile(
    r"\d+\s*(?:%|퍼센트|점|문제|개|단|일\s*연속|問|点|日連続|のだん|の段"
    r"|points?\b|questions?\b|problems?\b|answers?\b|days?\s+in\s+a\s+row)",
    re.IGNORECASE,
)


def language_for_locale(locale: Optional[str]) -> Lang:
    """기기 locale("ja-JP", "en-FR") → 알림 언어. 없으면 ko(2.6 이전 한국 기기), ko·ja 가 아니면 en."""
    if locale is None:
        return "ko"
    language = locale.replace("_", "-").split("-", 1)[0].lower()
    if language == "ko":
        return "ko"
    if language == "ja":
        return "ja"
    return "en"


_APP_VERSION_RE = re.compile(r"^(\d+)\.(\d+)")
# 이 버전부터 앱 화면이 다국어 — 이전 앱은 화면이 한국어뿐인데 locale 에 기기 언어("en-US")를 보냈다
FIRST_MULTILINGUAL_APP = (2, 6)


def language_for_device(locale: Optional[str], app_version: Optional[str]) -> Lang:
    """알림 언어 = 앱 화면 언어. 2.6 이전 앱(iOS·Android)은 locale 과 무관하게 ko.
    app_version 이 없으면(2026-10-23 migration 이전 RPC) locale 만 본다."""
    if app_version is not None:
        match = _APP_VERSION_RE.match(app_version)
        if match is None or (int(match[1]), int(match[2])) < FIRST_MULTILINGUAL_APP:
            return "ko"
    return language_for_locale(locale)


class TemplateError(ValueError):
    """템플릿·문구가 발송 정책을 어긴다."""


@dataclass(frozen=True)
class Template:
    key: TemplateKey
    title: dict[Lang, str]
    body: dict[Lang, str]
    link: DeepLink
    android_channel: AndroidChannel
    ttl_seconds: int = 86_400
    # 2년 확인 알림은 법정 정보성 통지라 "이벤트·혜택 알림"이라는 동의 항목 이름을 써야 한다
    allows_consent_terms: bool = False


TEMPLATES: dict[TemplateKey, Template] = {
    "premium_granted": Template(
        key="premium_granted",
        title={"ko": "프리미엄이 적용되었어요", "en": "Premium is now active",
               "ja": "プレミアムが有効になりました"},
        body={"ko": "지금 바로 구구 어드벤처 프리미엄을 이용할 수 있어요.",
              "en": "You can start using Gugu Adventure Premium right away.",
              "ja": "九九アドベンチャーのプレミアムをすぐにご利用いただけます。"},
        link="profile",
        android_channel="service_notice",
    ),
    "premium_revoked": Template(
        key="premium_revoked",
        title={"ko": "프리미엄 이용 상태가 바뀌었어요", "en": "Your Premium status has changed",
               "ja": "プレミアムのご利用状況が変わりました"},
        body={"ko": "자세한 내용은 앱의 내 정보에서 확인해 주세요.",
              "en": "Please check the Profile screen in the app for details.",
              "ja": "くわしくはアプリのプロフィールでご確認ください。"},
        link="profile",
        android_channel="service_notice",
    ),
    "account_notice": Template(
        key="account_notice",
        title={"ko": "계정 안내", "en": "Account notice", "ja": "アカウントのお知らせ"},
        body={"ko": "구구 어드벤처 계정에 관한 안내가 있어요. 앱에서 확인해 주세요.",
              "en": "There's a notice about your Gugu Adventure account. Please check the app.",
              "ja": "九九アドベンチャーのアカウントについてお知らせがあります。アプリでご確認ください。"},
        link="home",
        android_channel="service_notice",
    ),
    "weekly_report": Template(
        key="weekly_report",
        title={"ko": "이번 주 학습 리포트가 도착했어요", "en": "This week's learning report is ready",
               "ja": "今週の学習レポートが届きました"},
        body={"ko": "아이의 한 주 학습 기록을 앱에서 확인해 보세요.",
              "en": "Check your child's week of practice in the app.",
              "ja": "お子さまの今週の学習記録をアプリでご確認ください。"},
        link="profile/report",
        android_channel="guardian_report",
        # 다음 주 리포트가 나오기 전까지만 의미가 있다
        ttl_seconds=2 * 86_400,
    ),
    "marketing_reconfirm": Template(
        key="marketing_reconfirm",
        # 한국 법정 통지(정보통신망법 시행령 제62조의3) — 광고 동의는 한국 스토어에서만 받으므로 ko 만
        title={"ko": "알림 수신 동의 확인 안내"},
        body={"ko": (
            "{consent_date}에 동의하신 이벤트·혜택 알림을 계속 받고 계세요. "
            "그만 받으려면 설정 > 알림에서 끌 수 있어요. " + SENDER_NAME
        )},
        link="settings/notifications",
        android_channel="service_notice",
        ttl_seconds=7 * 86_400,
        allows_consent_terms=True,
    ),
    "test": Template(
        key="test",
        title={"ko": "테스트 알림", "en": "Test notification", "ja": "テスト通知"},
        body={"ko": "구구 어드벤처 알림이 잘 도착했어요.",
              "en": "Gugu Adventure notifications are arriving correctly.",
              "ja": "九九アドベンチャーの通知が正しく届きました。"},
        link="home",
        android_channel="service_notice",
        ttl_seconds=3_600,
    ),
}


def _normalize(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def find_ad_terms(text: str) -> list[str]:
    """문구에 들어 있는 광고성 표현(중복 없이, 목록 순서대로)."""
    normalized = _normalize(text)
    return [keyword for keyword in AD_KEYWORDS if keyword in normalized]


def ensure_informational(title: str, body: str) -> None:
    """정보성 알림으로 보낼 수 있는 문구인지 검사한다. 위반이면 TemplateError."""
    if not title.strip() or not body.strip():
        raise TemplateError("제목과 본문은 비어 있을 수 없습니다")
    if len(title) > MAX_TITLE_CHARS:
        raise TemplateError(f"제목은 {MAX_TITLE_CHARS}자를 넘을 수 없습니다")
    if len(body) > MAX_BODY_CHARS:
        raise TemplateError(f"본문은 {MAX_BODY_CHARS}자를 넘을 수 없습니다")
    terms = find_ad_terms(f"{title} {body}")
    if terms:
        raise TemplateError(
            "광고성 표현이 있어 서버 발송을 거부합니다: "
            + ", ".join(terms)
            + " — 광고는 Firebase 콘솔 promo 토픽으로만 보냅니다"
        )
    if _LEARNING_NUMBER_RE.search(f"{title} {body}"):
        raise TemplateError("잠금화면 문구에 학습 수치를 넣을 수 없습니다 — 앱 안에서만 보여 주세요")


def validate_link(link: str) -> DeepLink:
    if link not in ALLOWED_LINKS:
        raise TemplateError(f"허용되지 않은 딥링크입니다: {link}")
    return link  # type: ignore[return-value]


def format_consent_date(moment: datetime) -> str:
    """KST 기준 'YYYY년 M월 D일'."""
    from push_schedule import to_kst

    local = to_kst(moment)
    return f"{local.year}년 {local.month}월 {local.day}일"


def render(
    key: TemplateKey,
    *,
    lang: Lang = "ko",
    title: Optional[str] = None,
    body: Optional[str] = None,
    consent_date: Optional[datetime] = None,
) -> PushMessage:
    """템플릿을 발송 메시지로 만든다. 운영자 입력(title/body)은 광고 검사를 거친다."""
    template = TEMPLATES.get(key)
    if template is None:
        raise TemplateError(f"알 수 없는 템플릿입니다: {key}")
    if lang not in template.title:
        raise TemplateError(f"{key} 템플릿에는 {lang} 문구가 없습니다")
    validate_link(template.link)

    if template.allows_consent_terms:
        if title is not None or body is not None:
            raise TemplateError("2년 확인 알림 문구는 바꿀 수 없습니다")
        if consent_date is None:
            raise TemplateError("2년 확인 알림에는 동의 날짜가 필요합니다")
        final_title = template.title[lang]
        final_body = template.body[lang].format(consent_date=format_consent_date(consent_date))
        if final_title.startswith("(광고)"):
            raise TemplateError("2년 확인 알림은 광고가 아닙니다")
    else:
        final_title = title if title is not None else template.title[lang]
        final_body = body if body is not None else template.body[lang]
        ensure_informational(final_title, final_body)

    return PushMessage(
        title=final_title,
        body=final_body,
        kind=template.key,
        link=template.link,
        android_channel=template.android_channel,
        ttl_seconds=template.ttl_seconds,
    )
