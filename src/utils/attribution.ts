/**
 * 광고 유입 식별자(fbclid, utm_*) 저장 + 스토어 링크 조립 + 설치 클릭 발사.
 *
 * - 랜딩 URL의 fbclid / utm_* 를 localStorage에 30일 보관(마지막 유입 우선)해서, 다른 페이지(/play/ 등)에서
 *   InstallPrompt를 열어도 같은 출처로 귀속한다.
 * - Android: Play 스토어 URL의 `referrer` 파라미터(설치 후 Play Install Referrer API로 앱이 읽음).
 * - iOS: 앱 스토어는 임의 파라미터를 앱에 전달하지 않는다. App Store Connect 캠페인 링크(pt/ct)만 가능하고,
 *   `pt`(공급자 토큰)가 있어야 동작하므로 NEXT_PUBLIC_APPSTORE_PROVIDER_TOKEN 이 설정된 경우에만 붙인다.
 * - 서버 로그: navigator.sendBeacon → /api/install-click (scripts/install-click-server.mjs).
 *
 * 순수 함수(파싱/조립)는 window 없이도 동작해서 단독 테스트할 수 있다.
 */
import { trackMetaCustom, trackMetaEvent } from './metaPixel';

export type StoreKind = 'ios' | 'android';

export const ATTR_KEYS = [
  'fbclid',
  'utm_source',
  'utm_medium',
  'utm_campaign',
  'utm_content',
  'utm_term',
] as const;
export type AttrKey = (typeof ATTR_KEYS)[number];

export type Attribution = Partial<Record<AttrKey, string>> & {
  /** 픽셀 쿠키(_fbc / _fbp). 없으면 fbclid로 _fbc 형식을 조립한다. */
  fbc?: string;
  fbp?: string;
  /** 저장 시각(ms) */
  ts?: number;
};

const STORAGE_KEY = 'gugu_attr_v1';
const TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_VALUE_LEN = 200;

const INSTALL_ENDPOINT =
  process.env.NEXT_PUBLIC_INSTALL_CLICK_ENDPOINT?.trim() || '/api/install-click';
const APPSTORE_PROVIDER_TOKEN = process.env.NEXT_PUBLIC_APPSTORE_PROVIDER_TOKEN?.trim() || '';

function clean(v: string | null | undefined): string | undefined {
  if (!v) return undefined;
  const t = v.trim().slice(0, MAX_VALUE_LEN);
  return t || undefined;
}

/** location.search 문자열에서 광고 식별자만 뽑는다. */
export function parseAttribution(search: string): Attribution {
  const out: Attribution = {};
  let params: URLSearchParams;
  try {
    params = new URLSearchParams(search);
  } catch {
    return out;
  }
  for (const k of ATTR_KEYS) {
    const v = clean(params.get(k));
    if (v) out[k] = v;
  }
  return out;
}

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return m ? clean(decodeURIComponent(m[1])) : undefined;
}

/** 저장된 유입 정보(만료 시 null). */
export function readStoredAttribution(now = Date.now()): Attribution | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const a = JSON.parse(raw) as Attribution;
    if (!a || typeof a !== 'object') return null;
    if (a.ts && now - a.ts > TTL_MS) {
      window.localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return a;
  } catch {
    return null;
  }
}

/**
 * 현재 URL의 fbclid/utm_* 를 저장하고 현재 유입 정보를 돌려준다(여러 번 호출해도 안전).
 * URL에 식별자가 있으면 덮어쓰고(마지막 유입 우선), 없으면 저장값을 유지한다.
 */
export function captureAttribution(): Attribution {
  if (typeof window === 'undefined') return {};
  const now = Date.now();
  const fromUrl = parseAttribution(window.location.search);
  let attr: Attribution = readStoredAttribution(now) ?? {};

  if (Object.keys(fromUrl).length > 0) {
    attr = { ...fromUrl, ts: now };
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(attr));
    } catch {
      // 사생활 보호 모드 등 저장 불가 → 이번 페이지 내에서만 사용
    }
  }

  // 픽셀 쿠키는 매번 최신값을 덧붙인다(저장하지 않음).
  const fbp = readCookie('_fbp');
  let fbc = readCookie('_fbc');
  if (!fbc && attr.fbclid) fbc = `fb.1.${attr.ts ?? now}.${attr.fbclid}`;
  return { ...attr, ...(fbc ? { fbc } : {}), ...(fbp ? { fbp } : {}) };
}

export interface StoreUrlOptions {
  placement?: string;
  clickId?: string;
}

/** Google Play `referrer` 값(URL 인코딩 전). 인코딩 후 900자 이내로 유지한다. */
export function buildPlayReferrer(attr: Attribution, opts: StoreUrlOptions = {}): string {
  const hasPaid = !!attr.fbclid;
  const entries: Array<[string, string | undefined]> = [
    ['utm_source', attr.utm_source ?? (hasPaid ? 'facebook' : 'gugu_web')],
    ['utm_medium', attr.utm_medium ?? (hasPaid ? 'paid_social' : 'landing')],
    ['utm_campaign', attr.utm_campaign],
    ['utm_content', attr.utm_content],
    ['utm_term', attr.utm_term],
    ['click_id', opts.clickId],
    ['placement', opts.placement],
    ['fbclid', attr.fbclid],
  ];
  const parts: string[] = [];
  for (const [k, v] of entries) {
    if (!v) continue;
    const part = `${k}=${encodeURIComponent(v)}`;
    // 이 문자열은 다시 한 번 인코딩되어 URL에 들어가므로 여유를 둔다.
    if (encodeURIComponent(parts.concat(part).join('&')).length > 900) continue;
    parts.push(part);
  }
  return parts.join('&');
}

/** App Store 캠페인 토큰(ct): 영숫자/-/_ 만, 최대 100자. */
export function sanitizeCampaignToken(v: string | undefined): string {
  return (v ?? '').replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 100);
}

export function buildStoreUrl(
  store: StoreKind,
  baseUrl: string,
  attr: Attribution,
  opts: StoreUrlOptions = {},
): string {
  try {
    const u = new URL(baseUrl);
    if (store === 'android') {
      const ref = buildPlayReferrer(attr, opts);
      if (ref) u.searchParams.set('referrer', ref);
    } else if (APPSTORE_PROVIDER_TOKEN) {
      u.searchParams.set('pt', APPSTORE_PROVIDER_TOKEN);
      u.searchParams.set('ct', sanitizeCampaignToken(attr.utm_campaign) || 'gugu_web');
      u.searchParams.set('mt', '8');
    }
    return u.toString();
  } catch {
    return baseUrl;
  }
}

export function newClickId(): string {
  try {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  } catch {
    // fallthrough
  }
  return `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
}

export interface InstallClickInput {
  store: StoreKind;
  placement: string;
  clickId: string;
  href: string;
}

/**
 * 설치/스토어 CTA 클릭 처리: Meta Pixel(Lead + install_click + 기존 app_store_click) + 서버 로그.
 * 링크 이동을 막지 않는다(preventDefault 없음, 서버 전송은 sendBeacon / keepalive).
 */
export function trackInstallClick(input: InstallClickInput): void {
  if (typeof window === 'undefined') return;
  const attr = captureAttribution();

  const params: Record<string, unknown> = {
    content_name: input.store === 'ios' ? 'app_store' : 'play_store',
    store: input.store,
    placement: input.placement,
    click_id: input.clickId,
  };
  for (const k of ATTR_KEYS) if (attr[k]) params[k] = attr[k];

  try {
    // 표준 Lead(최적화 후보) + 커스텀 install_click. 같은 eventID로 추후 CAPI 중복제거 가능.
    trackMetaEvent('Lead', params, { eventID: `lead_${input.clickId}` });
    trackMetaCustom('install_click', params, { eventID: `ic_${input.clickId}` });
    // 이전 PR에서 정의한 이벤트명 유지(기존 대시보드 호환)
    trackMetaCustom('app_store_click', params);
  } catch {
    // 측정 실패가 이동을 막으면 안 된다
  }

  const payload = JSON.stringify({
    click_id: input.clickId,
    store: input.store,
    placement: input.placement,
    page: window.location.pathname,
    href: input.href,
    fbclid: attr.fbclid,
    utm_source: attr.utm_source,
    utm_medium: attr.utm_medium,
    utm_campaign: attr.utm_campaign,
    utm_content: attr.utm_content,
    utm_term: attr.utm_term,
    fbc: attr.fbc,
    fbp: attr.fbp,
    attr_ts: attr.ts,
  });

  try {
    // text/plain 이면 CORS preflight 없이 전송된다(서버는 본문을 JSON으로 파싱).
    const blob = new Blob([payload], { type: 'text/plain;charset=UTF-8' });
    if (navigator.sendBeacon && navigator.sendBeacon(INSTALL_ENDPOINT, blob)) return;
  } catch {
    // fallthrough
  }
  try {
    void fetch(INSTALL_ENDPOINT, {
      method: 'POST',
      body: payload,
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
      keepalive: true,
    }).catch(() => undefined);
  } catch {
    // ignore
  }
}
