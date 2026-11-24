"use client";
import { useEffect } from 'react';
import { useGame } from '@/lib/state/GameProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { syncReminders } from '@/lib/native/reminders';
import { useAppActive } from '@/lib/native/useAppActive';

// 보호자 설정·일일 진행이 바뀔 때, 그리고 포그라운드 복귀 때 로컬 알림을 다시 맞춤
export function ReminderSync() {
  const { state, loaded } = useGame();
  const { loaded: prefsLoaded, role, reminderEnabled, reminderHour } = usePrefs();
  const active = useAppActive();

  useEffect(() => {
    if (!loaded || !prefsLoaded) return;
    if (!active) return;
    void syncReminders({
      role,
      enabled: reminderEnabled,
      hour: reminderHour,
      dailyCorrect: state.dailyCorrect,
      dailyGoal: state.dailyGoal,
      lastPlayedDate: state.lastPlayedDate,
    }).then((r) => {
      if (!r.ok && r.reason !== 'denied' && r.reason !== 'web-skip') {
        console.error('로컬 알림 동기화 실패:', r.reason);
      }
    });
  }, [
    loaded,
    prefsLoaded,
    active,
    role,
    reminderEnabled,
    reminderHour,
    state.dailyCorrect,
    state.dailyGoal,
    state.lastPlayedDate,
  ]);

  return null;
}
