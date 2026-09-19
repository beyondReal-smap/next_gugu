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

  // Official Meta Pixel base snippet (init + PageView).
  /* eslint-disable prefer-rest-params, prefer-spread */
  const f = window;
  const b = document;
  const e = 'script';
  const v = 'https://connect.facebook.net/en_US/fbevents.js';
  if (f.fbq) return;
  const n = (f.fbq = function (...args: unknown[]) {
    if ((n as unknown as { callMethod?: (...a: unknown[]) => void }).callMethod) {
      (n as unknown as { callMethod: (...a: unknown[]) => void }).callMethod(...args);
    } else {
      ((n as unknown as { queue: unknown[] }).queue = (n as unknown as { queue?: unknown[] }).queue || []).push(args);
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
  /* eslint-enable prefer-rest-params, prefer-spread */

  window.fbq?.('init', META_PIXEL_ID);
  window.fbq?.('track', 'PageView');
}
