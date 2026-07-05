export function showInterstitialAd() {
  // SSR 체크
  if (typeof window === 'undefined') {
    console.log('🎯 Ad Manager: SSR environment detected, skipping ad');
    return;
  }

  console.log('🎯 Ad Manager: Attempting to show interstitial ad');

  // iOS Native Ad
  if (window?.webkit?.messageHandlers?.showInterstitialAd) {
    try {
      console.log('🎯 Ad Manager: iOS environment detected, triggering native ad');
      window.webkit.messageHandlers.showInterstitialAd.postMessage('');
      console.log('✅ Ad Manager: iOS interstitial ad request sent successfully');
      return;
    } catch (error) {
      console.error('❌ Ad Manager: Failed to show iOS interstitial ad:', error);
      console.error('Error details:', error);
    }
  }

  // Android Native Ad
  if (window?.Android?.showInterstitialAd) {
    try {
      console.log('🎯 Ad Manager: Android environment detected, triggering native ad');
      window.Android.showInterstitialAd();
      console.log('✅ Ad Manager: Android interstitial ad request sent successfully');
      return;
    } catch (error) {
      console.error('❌ Ad Manager: Failed to show Android interstitial ad:', error);
    }
  }

  // 디버깅을 위한 추가 로그
  console.log('🔍 Ad Manager: webkit available:', !!window.webkit);
  console.log('🔍 Ad Manager: messageHandlers available:', !!window.webkit?.messageHandlers);
  if (window.webkit?.messageHandlers) {
    console.log('🔍 Ad Manager: Available handlers:', Object.keys(window.webkit.messageHandlers));
  }

  console.log('⚠️ Ad Manager: No suitable ad platform found');
}