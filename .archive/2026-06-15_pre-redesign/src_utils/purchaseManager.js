export async function handlePremiumPurchase() {
  if (typeof window === 'undefined') {
    console.log('🎯 Purchase Manager: SSR environment detected, skipping purchase');
    return;
  }

  console.log('🎯 Purchase Manager: Attempting to trigger premium purchase');

  // iOS Native Purchase
  if (window?.webkit?.messageHandlers?.handlePremiumPurchase) {
    try {
      console.log('🎯 Purchase Manager: iOS environment detected, triggering native purchase');
      
      return new Promise((resolve, reject) => {
        // 성공 시 콜백
        window.onPremiumPurchaseSuccess = () => {
          console.log('✅ Purchase Manager: iOS premium purchase successful');
          resolve();
          delete window.onPremiumPurchaseSuccess;
          delete window.onPremiumPurchaseFailure;
        };

        // 실패 시 콜백
        window.onPremiumPurchaseFailure = (error) => {
          console.log('❌ Purchase Manager: Purchase failed, closing modal');
          // 모달 닫기 함수 호출
          window.closePaymentModal?.();
          // 또는 커스텀 이벤트 발생
          window.dispatchEvent(new CustomEvent('closePaymentModal'));
          
          resolve();  // 또는 reject() 대신 resolve()
          delete window.onPremiumPurchaseSuccess;
          delete window.onPremiumPurchaseFailure;
        };

        window.webkit.messageHandlers.handlePremiumPurchase.postMessage('');
      });
    } catch (error) {
      console.log('❌ Purchase Manager: Error occurred, closing modal');
      window.closePaymentModal?.();
      // 또는 커스텀 이벤트 발생
      window.dispatchEvent(new CustomEvent('closePaymentModal'));
      throw error;
    }
  }

  // Android Native Purchase (필요한 경우)
  if (window?.Android?.handlePremiumPurchase) {
    // ... Android 구현 ...
  }

  console.log('⚠️ Purchase Manager: No suitable purchase platform found');
  window.closePaymentModal?.();
  // 또는 커스텀 이벤트 발생
  window.dispatchEvent(new CustomEvent('closePaymentModal'));
  throw new Error('No suitable purchase platform found');
}