// src/utils/hapticFeedback.js
// Capacitor 네이티브(iOS/Android)에서는 @capacitor/haptics로 실제 햅틱을 발생시키고,
// 웹/PWA에서는 Vibration API로 폴백한다.
// 공개 API(triggerHapticFeedback, HAPTIC_TYPES)는 기존 호출부 호환을 위해 유지.
import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';

export const HAPTIC_TYPES = {
  TIME_ATTACK_SUCCESS: 'timeAttackSuccess',
  TIME_ATTACK_FAIL: 'timeAttackFail',
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  IMPACT_LIGHT: 'impactLight',
  IMPACT_MEDIUM: 'impactMedium',
  IMPACT_HEAVY: 'impactHeavy',
};

export function triggerHapticFeedback(type) {
  // SSR 가드
  if (typeof window === 'undefined') return;

  // 네이티브(iOS/Android): Capacitor Haptics 사용
  if (Capacitor.isNativePlatform()) {
    // 비동기 호출이지만 fire-and-forget. 실패해도 앱 흐름을 막지 않도록 로그만 남긴다.
    nativeHaptic(type).catch((error) => {
      console.error('Capacitor haptic 실패:', error);
    });
    return;
  }

  // 웹/PWA: Vibration API 폴백 (iOS Safari는 미지원 — Android/데스크톱 브라우저용)
  if (window.navigator?.vibrate) {
    try {
      window.navigator.vibrate(getVibrationPattern(type));
    } catch (error) {
      console.error('Vibration 폴백 실패:', error);
    }
  }
}

// HAPTIC_TYPES → Capacitor Haptics 매핑.
// - 결과성 피드백(정답/오답/경고)은 notification 햅틱
// - 탭/입력 피드백은 impact 햅틱
function nativeHaptic(type) {
  switch (type) {
    case HAPTIC_TYPES.SUCCESS:
    case HAPTIC_TYPES.TIME_ATTACK_SUCCESS:
      return Haptics.notification({ type: NotificationType.Success });
    case HAPTIC_TYPES.ERROR:
    case HAPTIC_TYPES.TIME_ATTACK_FAIL:
      return Haptics.notification({ type: NotificationType.Error });
    case HAPTIC_TYPES.WARNING:
      return Haptics.notification({ type: NotificationType.Warning });
    case HAPTIC_TYPES.IMPACT_HEAVY:
      return Haptics.impact({ style: ImpactStyle.Heavy });
    case HAPTIC_TYPES.IMPACT_MEDIUM:
      return Haptics.impact({ style: ImpactStyle.Medium });
    case HAPTIC_TYPES.IMPACT_LIGHT:
    default:
      return Haptics.impact({ style: ImpactStyle.Light });
  }
}

// 웹 Vibration API 패턴(ms). 네이티브에서는 사용하지 않는다.
function getVibrationPattern(type) {
  switch (type) {
    case HAPTIC_TYPES.TIME_ATTACK_SUCCESS:
      return [150, 100, 75];
    case HAPTIC_TYPES.TIME_ATTACK_FAIL:
      return [100, 50, 100, 50, 100];
    case HAPTIC_TYPES.SUCCESS:
      return [100];
    case HAPTIC_TYPES.ERROR:
      return [100, 50, 100];
    case HAPTIC_TYPES.WARNING:
      return [50, 25, 50];
    case HAPTIC_TYPES.IMPACT_MEDIUM:
      return [75];
    case HAPTIC_TYPES.IMPACT_HEAVY:
      return [100];
    case HAPTIC_TYPES.IMPACT_LIGHT:
    default:
      return [50];
  }
}
