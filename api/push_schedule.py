"""서버 발송 시각 정책 — 발송 창, 주간 리포트 대상 주, 광고 수신동의 2년 확인 마감.

- 모든 서버 발송(정보성 포함)은 받는 기기의 현지 08:00~20:59 에만 한다. 21~08시는 야간이다
  (정보통신망법 제50조 ③ 야간 광고는 별도 동의 — 이 앱은 야간 광고를 보내지 않는다는 결정,
  정보성도 아동 앱 특성상 야간에 깨우지 않는다). 시간대를 모르는 기기(앱 2.6 이전 = 한국 기기)는 KST.
- 한국은 일광절약시간이 없어 고정 +09:00 으로 계산한다. 해외 기기는 IANA 시간대(zoneinfo)로 계산한다.
"""

from dataclasses import dataclass
from datetime import datetime, time, timedelta, timezone, tzinfo
from typing import Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


KST = timezone(timedelta(hours=9), "KST")

DAYTIME_START_HOUR = 8
DAYTIME_END_HOUR = 21  # 이 시각부터 야간(배타)
RECONFIRM_START_HOUR = 10
RECONFIRM_END_HOUR = 21
RECONFIRM_YEARS = 2
RECONFIRM_LEAD = timedelta(days=14)
STALE_DEVICE_AGE = timedelta(days=270)

# 주간 리포트: 일요일 19:00 ~ 월요일 20:00 KST (월요일은 놓친 대상 보충)
WEEKLY_OPEN = (6, time(19, 0))   # (weekday, 시각) — 월=0 … 일=6
WEEKLY_CLOSE = (0, time(20, 0))


def _require_aware(moment: datetime) -> None:
    if moment.tzinfo is None or moment.utcoffset() is None:
        raise ValueError("시간대가 있는 datetime 이어야 합니다")


def to_kst(moment: datetime) -> datetime:
    _require_aware(moment)
    return moment.astimezone(KST)


def device_zone(name: Optional[str]) -> tzinfo:
    """기기 시간대 — 없으면(2.6 이전 앱 = 한국 기기) KST. 알 수 없는 이름이면 ValueError."""
    if name is None:
        return KST
    try:
        return ZoneInfo(name)
    except (ZoneInfoNotFoundError, ValueError) as exc:
        raise ValueError(f"알 수 없는 시간대: {name}") from exc


def in_hours(moment: datetime, start_hour: int, end_hour: int, zone: tzinfo = KST) -> bool:
    """현지 시각이 [start_hour, end_hour) 안인가."""
    _require_aware(moment)
    return start_hour <= moment.astimezone(zone).hour < end_hour


def is_daytime(moment: datetime, zone: tzinfo = KST) -> bool:
    """서버 발송이 허용되는 현지 08:00~20:59 인가."""
    return in_hours(moment, DAYTIME_START_HOUR, DAYTIME_END_HOUR, zone)


# 주간 리포트 대상 학습 활동을 찾는 기간 — 앱 리포트 화면이 최근 7일을 보여 주므로 하루 여유를 둔다.
# 기기마다 시간대가 달라 "그 주 월~일"을 서버 한 번 조회로 맞출 수 없기 때문이다 (RPC 상한 8일)
WEEKLY_ACTIVITY_LOOKBACK = timedelta(days=8)

# 지구상 UTC 오프셋 범위(-12:00 ~ +14:00) — 30분 단위로 보면 모든 시간대를 덮는다
_OFFSET_STEPS = [timedelta(minutes=30 * step) for step in range(-24, 29)]


@dataclass(frozen=True)
class ReportWeek:
    """리포트 대상 주 — 현지 월요일 00:00 부터 다음 월요일 00:00 전까지."""

    start: datetime
    end: datetime
    iso_key: str  # 예: 2026-W42


def report_week_for(moment: datetime, zone: tzinfo = KST) -> ReportWeek:
    """moment 시점에 보낼 리포트의 대상 주. 현지 일요일이면 그 주, 월요일이면 지난주."""
    _require_aware(moment)
    local = moment.astimezone(zone)
    # 가장 최근 일요일(오늘 포함)이 속한 ISO 주
    days_since_sunday = (local.weekday() - 6) % 7
    sunday = local.date() - timedelta(days=days_since_sunday)
    monday = sunday - timedelta(days=6)
    start = datetime.combine(monday, time(0, 0), zone)
    iso_year, iso_week, _ = monday.isocalendar()
    return ReportWeek(start=start, end=start + timedelta(days=7), iso_key=f"{iso_year}-W{iso_week:02d}")


def weekly_report_week(moment: datetime, zone: tzinfo = KST) -> Optional[ReportWeek]:
    """현지 시각으로 주간 리포트 발송 창 안이면 대상 주, 아니면 None. 야간 제한도 함께 적용한다."""
    if not is_daytime(moment, zone):
        return None
    local = moment.astimezone(zone)
    weekday, clock = local.weekday(), local.time()
    open_day, open_time = WEEKLY_OPEN
    close_day, close_time = WEEKLY_CLOSE
    in_window = (weekday == open_day and clock >= open_time) or (weekday == close_day and clock < close_time)
    return report_week_for(moment, zone) if in_window else None


def weekly_window_open_anywhere(moment: datetime) -> bool:
    """어느 시간대에서든 지금 주간 리포트 발송 창이 열려 있는가 — 아니면 후보 조회를 건너뛴다."""
    return any(weekly_report_week(moment, timezone(offset)) is not None for offset in _OFFSET_STEPS)


def add_years(moment: datetime, years: int) -> datetime:
    """달력 기준 연 가감. 2월 29일은 평년에서 2월 28일로 맞춘다."""
    try:
        return moment.replace(year=moment.year + years)
    except ValueError:
        return moment.replace(year=moment.year + years, day=28)


def reconfirm_due_at(basis: datetime) -> datetime:
    """2년 확인 알림 기한 = 기준일(동의 또는 마지막 확인) + 2년 - 14일."""
    _require_aware(basis)
    return add_years(basis, RECONFIRM_YEARS) - RECONFIRM_LEAD


def is_reconfirm_due(basis: datetime, now: datetime) -> bool:
    _require_aware(now)
    return reconfirm_due_at(basis) <= now


def reconfirm_cutoff(now: datetime) -> datetime:
    """기준일이 이 시각 이하이면 지금 확인 알림 대상이다(gugu_push_reconfirm_candidates 인자)."""
    _require_aware(now)
    return add_years(now + RECONFIRM_LEAD, -RECONFIRM_YEARS)


def is_reconfirm_window(moment: datetime) -> bool:
    return is_daytime(moment) and in_hours(moment, RECONFIRM_START_HOUR, RECONFIRM_END_HOUR)


def stale_device_cutoff(now: datetime) -> datetime:
    _require_aware(now)
    return now - STALE_DEVICE_AGE
