import { Capacitor } from '@capacitor/core';

// 네이티브 앱 안에서 실행 중인지. Capacitor 번들 앱과, StoreKit 브릿지를 가진 iOS 커스텀 셸
// (스토어 빌드는 별도 프로젝트 — PremiumProvider 참고) 둘 다 앱으로 본다. 클라이언트에서만 호출.
export function isNativeShell(): boolean {
  return Capacitor.isNativePlatform() || Boolean(window.webkit?.messageHandlers?.storeKit);
}
