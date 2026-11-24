// Capacitor Haptics 우선, Vibration API 폴백. 기존 hapticFeedback.js 는 삭제하지 않음.
const KEY = 'gugu.haptic';

export const HAPTIC_TYPES = {
  TIME_ATTACK_SUCCESS: 'timeAttackSuccess',
  TIME_ATTACK_FAIL: 'timeAttackFail',
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  IMPACT_LIGHT: 'impactLight',
  IMPACT_MEDIUM: 'impactMedium',
  IMPACT_HEAVY: 'impactHeavy',
} as const;

export function isHapticEnabled(): boolean {
  if (typeof window === 'undefined') return true;
  const s = localStorage.getItem(KEY);
  if (s === null) return true;
  return s === '1';
}

export function setHapticEnabled(v: boolean): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(KEY, v ? '1' : '0');
}

function vibratePattern(type: string): number[] {
  switch (type.toLowerCase()) {
    case 'success':
    case 'timeattacksuccess':
      return [80];
    case 'error':
    case 'timeattackfail':
      return [80, 40, 80];
    case 'impactheavy':
      return [120];
    default:
      return [40];
  }
}

export async function triggerHapticFeedback(type: string): Promise<void> {
  if (typeof window === 'undefined') return;
  if (!isHapticEnabled()) return;

  try {
    const { Capacitor } = await import('@capacitor/core');
    if (Capacitor.isNativePlatform()) {
      const { Haptics, ImpactStyle, NotificationType } = await import('@capacitor/haptics');
      const t = type.toLowerCase();
      if (t === 'success' || t === 'timeattacksuccess') {
        await Haptics.notification({ type: NotificationType.Success });
        return;
      }
      if (t === 'error' || t === 'timeattackfail') {
        await Haptics.notification({ type: NotificationType.Error });
        return;
      }
      if (t === 'impactheavy') {
        await Haptics.impact({ style: ImpactStyle.Heavy });
        return;
      }
      await Haptics.impact({ style: ImpactStyle.Light });
      return;
    }
  } catch (e) {
    console.error('Capacitor 햅틱 실패, Vibration API로 시도:', e);
  }

  if (window.navigator?.vibrate) {
    window.navigator.vibrate(vibratePattern(type));
  }
}
