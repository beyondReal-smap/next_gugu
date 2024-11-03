// src/utils/hapticFeedback.js

export function triggerHapticFeedback(type) {
  // SSR 체크
  if (typeof window === 'undefined') {
    return;
  }

  console.log('Attempting to trigger haptic feedback:', type);

  // iOS Native Haptic
  if (window?.webkit?.messageHandlers?.hapticFeedbackHandler) {
    try {
      window.webkit.messageHandlers.hapticFeedbackHandler.postMessage(type);
      console.log('iOS haptic feedback triggered:', type);
      return;
    } catch (error) {
      console.error('Failed to trigger iOS haptic feedback:', error);
    }
  }

  // Fallback to Vibration API
  if (window.navigator?.vibrate) {
    try {
      switch (type.toLowerCase()) {
        case 'timeattacksuccess':
          // 성공 패턴: 긴 진동 후 짧은 진동
          window.navigator.vibrate([150, 100, 75]);
          break;

        case 'timeattackfail':
          // 실패 패턴: 짧은 진동 세 번
          window.navigator.vibrate([100, 50, 100, 50, 100]);
          break;

        case 'success':
          window.navigator.vibrate(100);
          break;

        case 'error':
          window.navigator.vibrate([100, 50, 100]);
          break;

        case 'warning':
          window.navigator.vibrate([50, 25, 50]);
          break;

        case 'impactlight':
          window.navigator.vibrate(50);
          break;

        case 'impactmedium':
          window.navigator.vibrate(75);
          break;

        case 'impactheavy':
          window.navigator.vibrate(100);
          break;

        default:
          console.warn('Unknown haptic type:', type);
          window.navigator.vibrate(50);
      }
      console.log('Vibration feedback triggered:', type);
    } catch (error) {
      console.error('Failed to trigger vibration feedback:', error);
    }
  }
}

// 사용 예시
export const HAPTIC_TYPES = {
  TIME_ATTACK_SUCCESS: 'timeAttackSuccess',
  TIME_ATTACK_FAIL: 'timeAttackFail',
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  IMPACT_LIGHT: 'impactLight',
  IMPACT_MEDIUM: 'impactMedium',
  IMPACT_HEAVY: 'impactHeavy'
};