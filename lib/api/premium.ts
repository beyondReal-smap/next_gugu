import { Capacitor } from '@capacitor/core';

// 웹 배포는 API와 같은 오리진(gugu.smap.site)이라 상대 경로,
// 네이티브 웹뷰(capacitor://localhost 등)는 절대 URL이 필요하다.
const DEFAULT_NATIVE_API_BASE = 'https://gugu.smap.site/api';

function apiBase(): string {
  if (Capacitor.isNativePlatform()) {
    return process.env.NEXT_PUBLIC_API_BASE ?? DEFAULT_NATIVE_API_BASE;
  }
  return '/api';
}

export interface PremiumStatus {
  premium: boolean;
  products: string[];
  entitlements: PremiumEntitlement[];
}

export type PremiumFeatureKey = 'ai_coach' | 'longitudinal_report' | 'multi_learner' | 'adventure_pack';

export interface PremiumEntitlement {
  featureKey: PremiumFeatureKey;
  status: 'active';
  source: 'lifetime_purchase';
}

const FEATURE_KEYS = new Set<PremiumFeatureKey>([
  'ai_coach',
  'longitudinal_report',
  'multi_learner',
  'adventure_pack',
]);

function parseEntitlement(data: unknown): PremiumEntitlement {
  const d = data as Partial<PremiumEntitlement> | null;
  if (
    !d ||
    typeof d.featureKey !== 'string' ||
    !FEATURE_KEYS.has(d.featureKey as PremiumFeatureKey) ||
    d.status !== 'active' ||
    d.source !== 'lifetime_purchase'
  ) {
    throw new Error('프리미엄 권한 응답 형식이 올바르지 않습니다');
  }
  return d as PremiumEntitlement;
}

export function parsePremiumStatus(data: unknown): PremiumStatus {
  const d = data as Partial<PremiumStatus> | null;
  if (
    !d ||
    typeof d.premium !== 'boolean' ||
    !Array.isArray(d.products) ||
    !d.products.every((product) => typeof product === 'string') ||
    !Array.isArray(d.entitlements)
  ) {
    throw new Error('프리미엄 상태 응답 형식이 올바르지 않습니다');
  }
  return {
    premium: d.premium,
    products: d.products,
    entitlements: d.entitlements.map(parseEntitlement),
  };
}

export async function fetchPremiumStatus(accessToken: string): Promise<PremiumStatus> {
  const res = await fetch(`${apiBase()}/premium/status`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error(`프리미엄 상태 조회 실패 (${res.status})`);
  return parsePremiumStatus(await res.json());
}

export async function deleteAccount(accessToken: string): Promise<void> {
  const res = await fetch(`${apiBase()}/account`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error(`계정 삭제 실패 (${res.status})`);
}
