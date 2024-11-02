// src/utils/hapticFeedback.js
export function triggerHapticFeedback(type) {
    // iOS 네이티브 햅틱 피드백
    if (window.webkit?.messageHandlers?.hapticFeedbackHandler) {
      console.log('Triggering iOS haptic feedback:', type);
      window.webkit.messageHandlers.hapticFeedbackHandler.postMessage(type);
      return;
    }
    
    // Android/기타 브라우저 진동
    if (typeof window !== 'undefined' && window.navigator?.vibrate) {
      console.log('Triggering vibration feedback');
      switch (type) {
        case 'success':
          window.navigator.vibrate(100);
          break;
        case 'error':
          window.navigator.vibrate([100, 50, 100]);
          break;
        case 'warning':
          window.navigator.vibrate([50, 25, 50]);
          break;
        case 'impactLight':
          window.navigator.vibrate(50);
          break;
        case 'impactMedium':
          window.navigator.vibrate(75);
          break;
        case 'impactHeavy':
          window.navigator.vibrate(100);
          break;
        default:
          window.navigator.vibrate(50);
      }
      return;
    }
    
    console.log('No haptic feedback available on this device/browser');
  }
  
  // MultiplicationGame.tsx에서의 사용 예시
  // 기존의 triggerHapticFeedback 함수를 수정
  const handleAnswerCheck = () => {
    if (correct) {
      triggerHapticFeedback('success');
      // 나머지 정답 처리 로직...
    } else {
      triggerHapticFeedback('error');
      // 나머지 오답 처리 로직...
    }
  };