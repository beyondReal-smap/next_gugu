export const META_PIXEL_ID =
  process.env.NEXT_PUBLIC_META_PIXEL_ID?.trim() || '2105591250833042';

const SCRIPT_ID = 'meta-pixel-src';

declare global {
  interface Window {
    fbq?: (...args: unknown[]) => void;
    _fbq?: (...args: unknown[]) => void;
  }
}

/** Meta Pixel Base + PageView. 웹 브라우저 전용(네이티브 웹뷰에서는 호출하지 말 것). */
export function enableMetaPixel(): void {
  if (typeof window === 'undefined') return;
  if (document.getElementById(SCRIPT_ID)) return;

  const f = window;
  const b = document;
  const e = 'script';
  const v = 'https://connect.facebook.net/en_US/fbevents.js';
  if (f.fbq) {
    f.fbq('init', META_PIXEL_ID);
    f.fbq('track', 'PageView');
    return;
  }

  const n = (f.fbq = function (...args: unknown[]) {
    const fn = n as unknown as {
      callMethod?: (...a: unknown[]) => void;
      queue: unknown[];
    };
    if (fn.callMethod) {
      fn.callMethod(...args);
    } else {
      (fn.queue = fn.queue || []).push(args);
    }
  }) as unknown as {
    (...args: unknown[]): void;
    callMethod?: (...a: unknown[]) => void;
    queue: unknown[];
    push: (...args: unknown[]) => void;
    loaded: boolean;
    version: string;
  };
  if (!f._fbq) f._fbq = n;
  n.push = n;
  n.loaded = true;
  n.version = '2.0';
  n.queue = [];
  const t = b.createElement(e) as HTMLScriptElement;
  t.id = SCRIPT_ID;
  t.async = true;
  t.src = v;
  t.onerror = () => {
    console.error('Meta Pixel 스크립트 로드 실패');
  };
  const s = b.getElementsByTagName(e)[0];
  s?.parentNode?.insertBefore(t, s);

  window.fbq?.('init', META_PIXEL_ID);
  window.fbq?.('track', 'PageView');
}

/** 표준 이벤트 (PageView, ViewContent, Lead, Purchase 등). */
export function trackMetaEvent(
  eventName: string,
  params?: Record<string, unknown>,
): void {
  if (typeof window === 'undefined') return;
  if (typeof window.fbq !== 'function') return;
  if (params) window.fbq('track', eventName, params);
  else window.fbq('track', eventName);
}

/** 커스텀 이벤트 (play_start, app_store_click 등). */
export function trackMetaCustom(
  eventName: string,
  params?: Record<string, unknown>,
): void {
  if (typeof window === 'undefined') return;
  if (typeof window.fbq !== 'function') return;
  if (params) window.fbq('trackCustom', eventName, params);
  else window.fbq('trackCustom', eventName);
}
