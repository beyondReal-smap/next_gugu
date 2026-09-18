// 스토어 등록 정보 — 2026-09-18 iTunes lookup / Play 상세 페이지로 확인한 실제 값.
// 번들 ID·패키지명은 site.smap.gugudan, 스토어 등록명은 '구구 어드벤처'.
export const APP_NAME = '구구 어드벤처';

export const APP_STORE_URL = 'https://apps.apple.com/kr/app/id6737556680';
export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=site.smap.gugudan';

export type StorePlatform = 'ios' | 'android' | 'other';

// 방문자 기기에 맞는 스토어 버튼을 앞세우기 위한 판별. iPadOS 13+는 데스크톱 Safari UA를
// 쓰므로 터치 지원 Mac 형태로 한 번 더 확인한다.
export function detectStorePlatform(): StorePlatform {
  const ua = navigator.userAgent;
  if (/android/i.test(ua)) return 'android';
  if (/iphone|ipad|ipod/i.test(ua)) return 'ios';
  if (/macintosh/i.test(ua) && navigator.maxTouchPoints > 1) return 'ios';
  return 'other';
}
