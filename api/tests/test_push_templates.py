"""알림 템플릿 — 광고 문구 검사, 학습 수치 차단, 딥링크 허용 목록."""

from datetime import datetime, timezone

import pytest

from push_templates import (
    ALLOWED_LINKS,
    OPERATOR_TEMPLATES,
    TEMPLATES,
    TemplateError,
    ensure_informational,
    find_ad_terms,
    format_consent_date,
    language_for_locale,
    render,
    validate_link,
)


def test_allowed_links_match_app_contract():
    assert ALLOWED_LINKS == {"home", "learn", "profile", "profile/report", "paywall", "settings/notifications"}


@pytest.mark.parametrize("link", sorted(ALLOWED_LINKS))
def test_validate_link_accepts_allowlist(link):
    assert validate_link(link) == link


@pytest.mark.parametrize("link", ["", "settings", "https://evil.example", "profile/../paywall", "Home", "learn?x=1"])
def test_validate_link_rejects_others(link):
    with pytest.raises(TemplateError):
        validate_link(link)


def test_every_template_uses_allowed_link_and_valid_channel():
    for template in TEMPLATES.values():
        assert template.link in ALLOWED_LINKS
        assert template.android_channel in ("service_notice", "guardian_report", "promotion")
        # 서버 발송은 정보성만 — 광고 채널을 쓰지 않는다
        assert template.android_channel != "promotion"


@pytest.mark.parametrize("key", [k for k in TEMPLATES if k != "marketing_reconfirm"])
def test_informational_templates_pass_ad_check(key):
    message = render(key)
    assert find_ad_terms(f"{message.title} {message.body}") == []
    assert message.kind == key


def test_operator_templates_exclude_batch_only():
    assert "weekly_report" not in OPERATOR_TEMPLATES
    assert "marketing_reconfirm" not in OPERATOR_TEMPLATES


@pytest.mark.parametrize(
    "text,term",
    [
        ("(광고) 가을 맞이", "(광고)"),
        ("지금 50% 할인!", "할인"),
        ("이 벤 트 참여하세요", "이벤트"),  # 공백으로 피해 가지 못한다
        ("프리미엄 구매하고 받기", "구매"),
        ("오늘의 특가", "특가"),
        ("쿠폰이 도착했어요", "쿠폰"),
        ("BIG SALE", "sale"),
        ("Promo code inside", "promo"),
    ],
)
def test_find_ad_terms_detects_keywords(text, term):
    assert term in find_ad_terms(text)


def test_ensure_informational_rejects_ads_and_learning_numbers():
    with pytest.raises(TemplateError, match="광고성"):
        ensure_informational("계정 안내", "지금 구매하면 할인돼요")
    with pytest.raises(TemplateError, match="학습 수치"):
        ensure_informational("이번 주 리포트", "정답률 85%를 달성했어요")
    with pytest.raises(TemplateError, match="학습 수치"):
        ensure_informational("이번 주 리포트", "7단에서 12문제를 틀렸어요")
    with pytest.raises(TemplateError):
        ensure_informational("", "본문")
    with pytest.raises(TemplateError):
        ensure_informational("가" * 51, "본문")
    ensure_informational("계정 안내", "10월 20일 점검 예정이에요. 앱에서 확인해 주세요.")


def test_weekly_report_has_no_learning_numbers():
    message = render("weekly_report")
    assert not any(char.isdigit() for char in message.title + message.body)
    assert message.link == "profile/report" and message.android_channel == "guardian_report"


def test_operator_override_is_checked():
    message = render("account_notice", title="계정 안내", body="문의하신 내용을 처리했어요.")
    assert message.body == "문의하신 내용을 처리했어요."
    with pytest.raises(TemplateError):
        render("account_notice", body="지금만 쿠폰 증정")


def test_marketing_reconfirm_requires_date_and_is_fixed():
    consent = datetime(2024, 10, 19, 16, 0, tzinfo=timezone.utc)  # KST 2024-10-20 01:00
    message = render("marketing_reconfirm", consent_date=consent)
    assert "2024년 10월 20일" in message.body
    assert "설정 > 알림" in message.body and "구구 어드벤처" in message.body
    assert not message.title.startswith("(광고)")
    assert message.link == "settings/notifications"
    with pytest.raises(TemplateError):
        render("marketing_reconfirm")
    with pytest.raises(TemplateError):
        render("marketing_reconfirm", consent_date=consent, body="바꾼 문구")


def test_format_consent_date_uses_kst():
    assert format_consent_date(datetime(2026, 1, 1, 15, 30, tzinfo=timezone.utc)) == "2026년 1월 2일"


def test_unknown_template_rejected():
    with pytest.raises(TemplateError):
        render("promo_blast")  # type: ignore[arg-type]


@pytest.mark.parametrize("locale,lang", [(None, "ko"), ("ko-KR", "ko"), ("ja-JP", "ja"), ("ja", "ja"),
                                         ("en-US", "en"), ("en-FR", "en"), ("fr_FR", "en")])
def test_language_for_locale(locale, lang):
    assert language_for_locale(locale) == lang


@pytest.mark.parametrize("key", ["premium_granted", "premium_revoked", "account_notice", "weekly_report", "test"])
@pytest.mark.parametrize("lang", ["en", "ja"])
def test_informational_templates_have_clean_translations(key, lang):
    message = render(key, lang=lang)
    assert find_ad_terms(f"{message.title} {message.body}") == []
    assert message.title != TEMPLATES[key].title["ko"]


def test_marketing_reconfirm_is_korean_only():
    with pytest.raises(TemplateError):
        render("marketing_reconfirm", lang="en", consent_date=datetime(2024, 10, 20, tzinfo=timezone.utc))


@pytest.mark.parametrize("text", ["7問 正解しました", "せいかい 90点", "5日連続", "You got 9 answers right",
                                  "3 days in a row!", "Score: 12 points"])
def test_learning_numbers_rejected_in_every_language(text):
    with pytest.raises(TemplateError):
        ensure_informational("お知らせ", text)


@pytest.mark.parametrize("text", ["期間限定セール", "クーポン配布中", "Special offer today"])
def test_ad_terms_detected_in_every_language(text):
    assert find_ad_terms(text)
