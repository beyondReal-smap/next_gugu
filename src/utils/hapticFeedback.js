// src/utils/hapticFeedback.js

export function triggerHapticFeedback(type) {
  // SSR 체크
  if (typeof window === 'undefined') {
    return;
  }

  console.log('Attempting to trigger haptic feedback:', type);

  // Android Native Haptic
  if (window?.Android?.vibrate) {
    try {
      const androidType = mapToAndroidType(type);
      window.Android.vibrate(androidType);
      console.log('Android haptic feedback triggered:', androidType);
      return;
    } catch (error) {
      console.error('Failed to trigger Android haptic feedback:', error);
    }
  }

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
      const pattern = getVibrationPattern(type);
      window.navigator.vibrate(pattern);
      console.log('Vibration feedback triggered:', type, 'pattern:', pattern);
    } catch (error) {
      console.error('Failed to trigger vibration feedback:', error);
    }
  }
}

// 안드로이드 네이티브 타입으로 매핑
function mapToAndroidType(type) {
  switch (type.toLowerCase()) {
    case 'timeattacksuccess':
      return 'complete'; // 레벨 완료와 비슷한 피드백
    case 'timeattackfail':
      return 'wrong';   // 오답과 비슷한 피드백
    case 'success':
      return 'correct'; // 정답과 비슷한 피드백
    case 'error':
      return 'wrong';   // 오답과 비슷한 피드백
    case 'warning':
      return 'click';   // 기본 클릭과 비슷한 피드백
    case 'impactlight':
      return 'click';   // 가벼운 클릭
    case 'impactmedium':
      return 'click';   // 중간 강도 클릭
    case 'impactheavy':
      return 'complete'; // 강한 피드백
    default:
      return 'click';   // 기본값
  }
}

// 진동 패턴 정의
function getVibrationPattern(type) {
  switch (type.toLowerCase()) {
    case 'timeattacksuccess':
      return [150, 100, 75];
    case 'timeattackfail':
      return [100, 50, 100, 50, 100];
    case 'success':
      return [100];
    case 'error':
      return [100, 50, 100];
    case 'warning':
      return [50, 25, 50];
    case 'impactlight':
      return [50];
    case 'impactmedium':
      return [75];
    case 'impactheavy':
      return [100];
    default:
      return [50];
  }
}

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