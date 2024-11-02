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
            console.warn('Unknown haptic type:', type);
            window.navigator.vibrate(50);
        }
        console.log('Vibration feedback triggered:', type);
      } catch (error) {
        console.error('Failed to trigger vibration feedback:', error);
      }
    }
  }