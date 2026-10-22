"""KST 발송 창·주간 리포트 대상 주·2년 재확인 마감 계산."""

from datetime import datetime, timedelta, timezone

import pytest

from push_schedule import (
    KST,
    add_years,
    is_daytime,
    is_reconfirm_due,
    is_reconfirm_window,
    reconfirm_cutoff,
    reconfirm_due_at,
    report_week_for,
    stale_device_cutoff,
    weekly_report_week,
)


def kst(*args) -> datetime:
    return datetime(*args, tzinfo=KST)


@pytest.mark.parametrize(
    "moment,expected",
    [
        (kst(2026, 10, 19, 7, 59), False),
        (kst(2026, 10, 19, 8, 0), True),
        (kst(2026, 10, 19, 20, 59), True),
        (kst(2026, 10, 19, 21, 0), False),
        (kst(2026, 10, 19, 2, 0), False),
        # UTC 입력도 KST 로 판정한다: 2026-10-19 12:00Z = 21:00 KST
        (datetime(2026, 10, 19, 12, 0, tzinfo=timezone.utc), False),
        (datetime(2026, 10, 18, 23, 0, tzinfo=timezone.utc), True),  # 08:00 KST
    ],
)
def test_is_daytime(moment, expected):
    assert is_daytime(moment) is expected


def test_naive_datetime_rejected():
    with pytest.raises(ValueError):
        is_daytime(datetime(2026, 10, 19, 12, 0))


# 2026-10-18 은 일요일, 2026-10-19 는 월요일
@pytest.mark.parametrize(
    "moment,expected_key",
    [
        (kst(2026, 10, 18, 18, 59), None),
        (kst(2026, 10, 18, 19, 0), "2026-W42"),
        (kst(2026, 10, 18, 20, 5), "2026-W42"),
        (kst(2026, 10, 18, 21, 5), None),  # 야간
        (kst(2026, 10, 19, 7, 5), None),  # 야간(월요일 아침)
        (kst(2026, 10, 19, 8, 5), "2026-W42"),  # 월요일 보충 = 지난주
        (kst(2026, 10, 19, 19, 59), "2026-W42"),
        (kst(2026, 10, 19, 20, 0), None),
        (kst(2026, 10, 20, 12, 0), None),  # 화요일
        (kst(2026, 10, 17, 19, 30), None),  # 토요일
    ],
)
def test_weekly_report_window(moment, expected_key):
    week = weekly_report_week(moment)
    assert (week.iso_key if week else None) == expected_key


def test_report_week_bounds_are_kst_monday_to_monday():
    week = report_week_for(kst(2026, 10, 18, 19, 5))
    assert week.start == kst(2026, 10, 12, 0, 0)
    assert week.end == kst(2026, 10, 19, 0, 0)
    assert report_week_for(kst(2026, 10, 19, 9, 0)) == week


def test_report_week_across_year_boundary():
    # 2027-01-03(일) — 그 주 월요일 2026-12-28 은 ISO 2026-W53
    week = report_week_for(kst(2027, 1, 3, 19, 5))
    assert week.start == kst(2026, 12, 28, 0, 0)
    assert week.iso_key == "2026-W53"


def test_add_years_handles_leap_day():
    assert add_years(kst(2024, 2, 29, 10, 0), 2) == kst(2026, 2, 28, 10, 0)
    assert add_years(kst(2026, 10, 19, 10, 0), -2) == kst(2024, 10, 19, 10, 0)


def test_reconfirm_due_is_two_years_minus_fourteen_days():
    basis = datetime(2024, 10, 19, 1, 0, tzinfo=timezone.utc)
    assert reconfirm_due_at(basis) == datetime(2026, 10, 5, 1, 0, tzinfo=timezone.utc)
    assert not is_reconfirm_due(basis, datetime(2026, 10, 5, 0, 59, tzinfo=timezone.utc))
    assert is_reconfirm_due(basis, datetime(2026, 10, 5, 1, 0, tzinfo=timezone.utc))


@pytest.mark.parametrize(
    "now",
    [
        datetime(2026, 10, 19, 3, 0, tzinfo=timezone.utc),
        datetime(2026, 3, 1, 3, 0, tzinfo=timezone.utc),
        datetime(2028, 3, 14, 3, 0, tzinfo=timezone.utc),
    ],
)
def test_reconfirm_cutoff_matches_due_rule(now):
    cutoff = reconfirm_cutoff(now)
    # 마감 시각을 기준일로 가진 기기는 지금 대상이고, 하루 뒤 기준일은 아직 아니다
    assert is_reconfirm_due(cutoff, now)
    assert not is_reconfirm_due(cutoff + timedelta(days=1), now)
    # SQL 가드(700일 이전만 허용)를 만족한다
    assert cutoff < now - timedelta(days=700)


def test_reconfirm_window_is_10_to_20_kst():
    assert not is_reconfirm_window(kst(2026, 10, 19, 9, 59))
    assert is_reconfirm_window(kst(2026, 10, 19, 10, 0))
    assert is_reconfirm_window(kst(2026, 10, 19, 20, 59))
    assert not is_reconfirm_window(kst(2026, 10, 19, 21, 0))


def test_stale_device_cutoff_is_270_days():
    now = datetime(2026, 10, 19, tzinfo=timezone.utc)
    assert now - stale_device_cutoff(now) == timedelta(days=270)
