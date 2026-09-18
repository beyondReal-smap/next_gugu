"use client";
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import {
  fetchPremiumStatus,
  parsePremiumStatus,
  PremiumEntitlement,
  PremiumFeatureKey,
  PremiumStatus,
} from '../api/premium';
import { useAuth } from './AuthProvider';
import '../../app/types/webkit'; // window.webkit / window.Android / 프리미엄 콜백 전역 타입

// 레거시 네이티브 셸(purchaseManager)과 같은 키 — 기존 구매자의 로컬 상태 유지
const NATIVE_STATUS_STORAGE_KEY = 'PREMIUM_STATUS';
const ACCOUNT_CACHE_STORAGE_KEY = 'gugu.premium.account.v1';
// 평생권도 환불·철회 상태를 주기적으로 확인하도록 오프라인 사용을 7일로 제한한다.
const ACCOUNT_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

interface LocalPremium {
  isPremium: boolean;
  purchaseDate?: string;
  transactionId?: string;
}

interface AccountPremiumCache {
  version: 1;
  userId: string;
  verifiedAt: number;
  expiresAt: number;
  status: PremiumStatus;
}

interface PremiumContextValue {
  /** 서버 응답 또는 유효한 마지막 서버 캐시 기준 프리미엄 */
  isPremium: boolean;
  /** 계정에 연결된 프리미엄 (웹·타 기기 동기화 경로) */
  accountPremium: boolean;
  /** 서버가 부여한 기능별 권한 */
  entitlements: PremiumEntitlement[];
  /** 서버 권한 응답 기준 기능 사용 가능 여부 */
  hasFeature: (featureKey: PremiumFeatureKey) => boolean;
  /** 네이티브 로컬 구매 흔적 — 표시 전용이며 권한 판정에는 사용하지 않음 */
  localPurchaseDetected: boolean;
  /** 서버 상태 재조회 (로그인 시에만 동작) */
  refresh: () => Promise<void>;
  /** 스토어 구매 복원 — 네이티브 브릿지 호출, 웹에서는 서버 재조회 */
  restorePurchases: () => Promise<void>;
  /** 프리미엄 구매 — 네이티브 브릿지 호출 (웹 결제는 미지원) */
  purchase: () => void;
}

const PremiumContext = createContext<PremiumContextValue | null>(null);

function readLocal(): LocalPremium {
  try {
    const raw = localStorage.getItem(NATIVE_STATUS_STORAGE_KEY);
    if (!raw) return { isPremium: false };
    const parsed = JSON.parse(raw) as Partial<LocalPremium>;
    if (
      typeof parsed.isPremium !== 'boolean' ||
      (parsed.purchaseDate !== undefined && typeof parsed.purchaseDate !== 'string') ||
      (parsed.transactionId !== undefined && typeof parsed.transactionId !== 'string')
    ) {
      throw new Error('로컬 구매 표시 캐시 형식이 올바르지 않습니다');
    }
    return { isPremium: parsed.isPremium, purchaseDate: parsed.purchaseDate, transactionId: parsed.transactionId };
  } catch (e) {
    console.error('로컬 구매 표시 캐시 로드 실패:', e);
    return { isPremium: false };
  }
}

function writeLocal(v: LocalPremium) {
  try {
    localStorage.setItem(NATIVE_STATUS_STORAGE_KEY, JSON.stringify(v));
  } catch (e) {
    console.error('로컬 구매 표시 캐시 저장 실패:', e);
  }
}

function readAccountCache(userId: string): PremiumStatus | null {
  try {
    const raw = localStorage.getItem(ACCOUNT_CACHE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<AccountPremiumCache>;
    if (
      parsed.version !== 1 ||
      parsed.userId !== userId ||
      typeof parsed.verifiedAt !== 'number' ||
      typeof parsed.expiresAt !== 'number' ||
      !Number.isFinite(parsed.verifiedAt) ||
      !Number.isFinite(parsed.expiresAt) ||
      parsed.verifiedAt > parsed.expiresAt ||
      parsed.expiresAt <= Date.now()
    ) {
      return null;
    }
    return parsePremiumStatus(parsed.status);
  } catch (e) {
    console.error('계정 프리미엄 캐시 로드 실패:', e);
    return null;
  }
}

function writeAccountCache(userId: string, status: PremiumStatus) {
  const verifiedAt = Date.now();
  const cache: AccountPremiumCache = {
    version: 1,
    userId,
    verifiedAt,
    expiresAt: verifiedAt + ACCOUNT_CACHE_TTL_MS,
    status,
  };
  try {
    localStorage.setItem(ACCOUNT_CACHE_STORAGE_KEY, JSON.stringify(cache));
  } catch (e) {
    console.error('계정 프리미엄 캐시 저장 실패:', e);
  }
}

function hasActiveEntitlement(status: PremiumStatus | null): boolean {
  return Boolean(status?.premium && status.entitlements.some((entitlement) => entitlement.status === 'active'));
}

export function PremiumProvider({ children }: { children: React.ReactNode }) {
  const { user, getAccessToken } = useAuth();
  const [local, setLocal] = useState<LocalPremium>({ isPremium: false });
  const [accountStatus, setAccountStatus] = useState<PremiumStatus | null>(null);

  const accountPremium = hasActiveEntitlement(accountStatus);
  const entitlements = accountStatus?.entitlements ?? [];
  const hasFeature = useCallback(
    (featureKey: PremiumFeatureKey) => Boolean(accountStatus?.premium) && Boolean(accountStatus?.entitlements.some(
      (entitlement) => entitlement.featureKey === featureKey && entitlement.status === 'active'
    )),
    [accountStatus]
  );

  const refresh = useCallback(async () => {
    const token = await getAccessToken();
    if (!token || !user) {
      setAccountStatus(null);
      return;
    }
    const status = await fetchPremiumStatus(token);
    setAccountStatus(status);
    writeAccountCache(user.id, status);
  }, [getAccessToken, user]);

  // 전역 콜백(마운트 1회 등록)에서 항상 최신 refresh를 부르기 위한 ref
  const refreshRef = React.useRef(refresh);
  useEffect(() => { refreshRef.current = refresh; }, [refresh]);

  // 초기 로컬 상태 + 네이티브 셸이 호출하는 전역 콜백 등록
  useEffect(() => {
    setLocal(readLocal());

    window.setPremiumStatus = (isPremium, purchaseDate, transactionId) => {
      const next = { isPremium, purchaseDate, transactionId };
      writeLocal(next);
      setLocal(next);
    };
    window.onPremiumPurchaseSuccess = () => {
      void refreshRef.current().catch((e) => console.error('구매 후 프리미엄 조회 실패:', e));
    };
    window.onPremiumRestoreSuccess = () => {
      void refreshRef.current().catch((e) => console.error('복원 후 프리미엄 조회 실패:', e));
    };

    // 네이티브에 현재 상태 질의 (iOS: storeKit 초기화 / Android: 동기 조회)
    if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage('initialize');
    } else if (window.Android?.getPremiumStatus) {
      const isPremium = window.Android.getPremiumStatus();
      setLocal((prev) => ({ ...prev, isPremium: prev.isPremium || isPremium }));
    }

    return () => {
      delete window.setPremiumStatus;
      delete window.onPremiumPurchaseSuccess;
      delete window.onPremiumRestoreSuccess;
    };
    // refreshRef는 렌더마다 최신으로 유지되므로 의존성 불필요
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 로그인/로그아웃 시 계정 프리미엄 동기화
  useEffect(() => {
    if (!user) {
      setAccountStatus(null);
      return;
    }
    setAccountStatus(readAccountCache(user.id));
    void refresh().catch((e) => console.error('계정 프리미엄 조회 실패:', e));
  }, [user, refresh]);

  const restorePurchases = useCallback(async () => {
    if (window.webkit?.messageHandlers?.restorePurchases) {
      window.webkit.messageHandlers.restorePurchases.postMessage('');
    } else if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage('restore');
    } else if (window.Android?.restorePurchases) {
      window.Android.restorePurchases();
    } else {
      // 웹: 복원할 스토어가 없으므로 계정 상태만 재조회
      await refresh();
    }
  }, [refresh]);

  const purchase = useCallback(() => {
    if (window.webkit?.messageHandlers?.handlePremiumPurchase) {
      window.webkit.messageHandlers.handlePremiumPurchase.postMessage('');
    } else if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage('purchase');
    } else if (window.Android?.purchasePremium) {
      window.Android.purchasePremium();
    } else {
      throw new Error('웹에서는 아직 구매를 지원하지 않습니다. 앱에서 구매해 주세요.');
    }
  }, []);

  return (
    <PremiumContext.Provider
      value={{
        isPremium: accountPremium,
        accountPremium,
        entitlements,
        hasFeature,
        localPurchaseDetected: local.isPremium,
        refresh,
        restorePurchases,
        purchase,
      }}
    >
      {children}
    </PremiumContext.Provider>
  );
}

export function usePremium(): PremiumContextValue {
  const ctx = useContext(PremiumContext);
  if (!ctx) throw new Error('usePremium must be used within PremiumProvider');
  return ctx;
}
