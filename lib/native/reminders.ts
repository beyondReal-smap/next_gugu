// 로컬 알림만. 서버 푸시/FCM 토큰은 수집하지 않는다.
import { UserRole } from '../types';

export const DAILY_REMINDER_ID = 7101;
export const STREAK_REMINDER_ID = 7102;

export interface ReminderInput {
  role: UserRole;
  enabled: boolean;
  hour: number; // 0–23
  dailyCorrect: number;
  dailyGoal: number;
  lastPlayedDate: string; // YYYY-MM-DD, 스트릭 자격일
}

function todayStr(d = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function yesterdayStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return todayStr(d);
}

function nextAtHour(hour: number, dayOffset = 0): Date {
  const d = new Date();
  d.setDate(d.getDate() + dayOffset);
  d.setHours(hour, 0, 0, 0);
  return d;
}

function clampHour(h: number): number {
  if (!Number.isInteger(h) || h < 0 || h > 23) return 19;
  return h;
}

export async function syncReminders(input: ReminderInput): Promise<{ ok: boolean; reason: string }> {
  if (typeof window === 'undefined') return { ok: false, reason: 'ssr' };

  let LocalNotifications: typeof import('@capacitor/local-notifications').LocalNotifications;
  let Capacitor: typeof import('@capacitor/core').Capacitor;
  try {
    ({ Capacitor } = await import('@capacitor/core'));
    ({ LocalNotifications } = await import('@capacitor/local-notifications'));
  } catch (e) {
    console.error('로컬 알림 모듈을 불러오지 못했습니다:', e);
    return { ok: false, reason: 'module-missing' };
  }

  if (!Capacitor.isNativePlatform()) {
    return { ok: true, reason: 'web-skip' };
  }

  try {
    await LocalNotifications.cancel({
      notifications: [{ id: DAILY_REMINDER_ID }, { id: STREAK_REMINDER_ID }],
    });
  } catch (e) {
    console.error('기존 알림 취소 실패:', e);
  }

  if (input.role !== 'guardian') {
    return { ok: true, reason: 'child-no-permission' };
  }
  if (!input.enabled) {
    return { ok: true, reason: 'disabled' };
  }

  const perm = await LocalNotifications.requestPermissions();
  if (perm.display !== 'granted') {
    return { ok: false, reason: 'denied' };
  }

  const hour = clampHour(input.hour);
  const today = todayStr();
  const goalMet = input.dailyCorrect >= input.dailyGoal;
  const toSchedule: { id: number; title: string; body: string; at: Date }[] = [];

  if (!goalMet) {
    let at = nextAtHour(hour, 0);
    if (at.getTime() <= Date.now()) at = nextAtHour(hour, 1);
    toSchedule.push({
      id: DAILY_REMINDER_ID,
      title: '구구단 한 판 할까요?',
      body: `오늘 목표 ${input.dailyGoal}개 중 ${input.dailyCorrect}개 했어요`,
      at,
    });
  }

  const streakAtRisk =
    input.lastPlayedDate === yesterdayStr() &&
    input.dailyCorrect < Math.min(input.dailyGoal, 10);
  if (streakAtRisk) {
    const riskHour = Math.min(21, hour + 2);
    let at = nextAtHour(riskHour, 0);
    if (at.getTime() <= Date.now()) {
      // 오늘 시각이 지났으면 위험 알림은 보내지 않음 (내일은 이미 스트릭이 끊긴 뒤)
    } else {
      toSchedule.push({
        id: STREAK_REMINDER_ID,
        title: '연속 학습이 끊어질 수 있어요',
        body: '오늘 조금만 더 풀면 스트릭이 이어져요',
        at,
      });
    }
  }

  if (toSchedule.length === 0) return { ok: true, reason: 'nothing-to-schedule' };

  await LocalNotifications.schedule({
    notifications: toSchedule.map((n) => ({
      id: n.id,
      title: n.title,
      body: n.body,
      schedule: { at: n.at, allowWhileIdle: true },
      extra: { kind: n.id === STREAK_REMINDER_ID ? 'streak-risk' : 'daily' },
    })),
  });
  return { ok: true, reason: 'scheduled' };
}
