"use client";
import React from 'react';
import { Capacitor } from '@capacitor/core';
import { PRODUCT_ID, VALIDATOR_URL } from '@/lib/premiumConfig';

export type PurchaseResult = 'ok' | 'cancelled' | 'error';

/** cordova-plugin-purchase 전역 (deviceready 이후 window.CdvPurchase로 주입됨) */
declare global {
  interface Window { CdvPurchase?: any }
}

/** 검증 완료 프리미엄 캐시 — 오프라인 실행 대비 (스토어 확인으로 매 실행 보정) */
const PREMIUM_CACHE_KEY = 'gugu.premium.v1';

interface PremiumContextValue {
  /** 네이티브(iOS/Android) 여부 — false면 웹 데모 모드 */
  isNative: boolean;
  /** 스토어 연결 완료 여부 */
  storeReady: boolean;
  isPremium: boolean;
  /** 스토어에서 받아온 현지 가격 문자열 (연결 전 null) */
  price: string | null;
  paywallOpen: boolean;
  openPaywall: () => void;
  closePaywall: () => void;
  purchase: () => Promise<PurchaseResult>;
  /** 구매 복원 — 복원된 구매가 있으면 true */
  restore: () => Promise<boolean>;
}

const PremiumContext = React.createContext<PremiumContextValue | null>(null);

export function PremiumProvider({ children }: { children: React.ReactNode }) {
  const isNative = Capacitor.isNativePlatform();
  const [storeReady, setStoreReady] = React.useState(false);
  const [isPremium, setIsPremium] = React.useState(false);
  const [price, setPrice] = React.useState<string | null>(null);
  const [paywallOpen, setPaywallOpen] = React.useState(false);
  // 구매 진행 콜백 연결용 (approved→verified 이벤트와 purchase() 프라미스 연결)
  const pendingResolve = React.useRef<((r: PurchaseResult) => void) | null>(null);

  const grantPremium = React.useCallback(() => {
    setIsPremium(true);
    try { localStorage.setItem(PREMIUM_CACHE_KEY, '1'); } catch { /* 저장 실패해도 세션 내 유지 */ }
  }, []);

  const revokePremium = React.useCallback(() => {
    setIsPremium(false);
    try { localStorage.removeItem(PREMIUM_CACHE_KEY); } catch { /* noop */ }
  }, []);

  React.useEffect(() => {
    // 캐시 선반영 — 오프라인 실행에서도 이용권 유지 (스토어 연결 후 보정)
    try { if (localStorage.getItem(PREMIUM_CACHE_KEY) === '1') setIsPremium(true); } catch { /* noop */ }

    if (!isNative) return;

    const onReady = () => {
      const CdvPurchase = window.CdvPurchase;
      if (!CdvPurchase) {
        console.error('[Premium] CdvPurchase 플러그인을 찾을 수 없습니다');
        return;
      }
      const { store, ProductType, Platform, LogLevel } = CdvPurchase;
      const platform = Capacitor.getPlatform() === 'ios' ? Platform.APPLE_APPSTORE : Platform.GOOGLE_PLAY;

      store.verbosity = LogLevel.WARNINGS;
      store.register([{ id: PRODUCT_ID, type: ProductType.NON_CONSUMABLE, platform }]);
      // 자체 서버 검증 — 서버가 Apple/Google에 영수증 재확인 후 ok 응답 시에만 verified
      store.validator = VALIDATOR_URL;

      store.when()
        .productUpdated((p: any) => {
          if (p.id !== PRODUCT_ID) return;
          const pricing = p.pricing ?? p.offers?.[0]?.pricingPhases?.[0];
          if (pricing?.price) setPrice(pricing.price);
          if (p.owned) grantPremium();
        })
        // 환불 회수 — 기기 영수증 로딩이 "완료"된 시점에만 판정 (로딩 중/오프라인 오회수 방지).
        // Android는 환불 시 스토어 조회에서 즉시 빠져 다음 실행에 회수됨.
        // iOS는 기기 영수증에 환불이 늦게 반영될 수 있어 복원/재검증 시점에 회수됨.
        .receiptsReady(() => {
          const p = store.get(PRODUCT_ID, platform);
          if (p && p.owned === false) revokePremium();
        })
        .approved((tx: any) => tx.verify())
        .verified((receipt: any) => {
          receipt.finish();
          grantPremium();
          pendingResolve.current?.('ok');
          pendingResolve.current = null;
        })
        .unverified(() => {
          // 서버 검증 거부 — 프리미엄 미부여 (조작 영수증 방어)
          console.error('[Premium] 영수증 검증 거부됨');
          pendingResolve.current?.('error');
          pendingResolve.current = null;
        });

      store.initialize([platform])
        .then(() => {
          setStoreReady(true);
          const p = store.get(PRODUCT_ID, platform);
          if (p?.owned) grantPremium();
        })
        .catch((e: unknown) => console.error('[Premium] 스토어 초기화 실패:', e));
    };

    // Capacitor는 cordova 플러그인 사용 시 deviceready를 발생시킴 (이미 지난 경우 즉시 실행)
    if (window.CdvPurchase) onReady();
    else document.addEventListener('deviceready', onReady, { once: true });
  }, [isNative, grantPremium, revokePremium]);

  const purchase = React.useCallback(async (): Promise<PurchaseResult> => {
    const store = window.CdvPurchase?.store;
    const { Platform } = window.CdvPurchase ?? {};
    if (!store) return 'error';
    const platform = Capacitor.getPlatform() === 'ios' ? Platform.APPLE_APPSTORE : Platform.GOOGLE_PLAY;
    const offer = store.get(PRODUCT_ID, platform)?.getOffer();
    if (!offer) return 'error';

    return new Promise<PurchaseResult>((resolve) => {
      pendingResolve.current = resolve;
      offer.order().then((err: any) => {
        if (err) {
          pendingResolve.current = null;
          resolve(err.isError && err.code === window.CdvPurchase.ErrorCode.PAYMENT_CANCELLED ? 'cancelled' : 'error');
        }
        // err 없음 → approved→verified 이벤트가 resolve 처리
      });
    });
  }, []);

  const restore = React.useCallback(async (): Promise<boolean> => {
    const store = window.CdvPurchase?.store;
    if (!store) return false;
    try {
      await store.restorePurchases();
      const owned = !!store.get(PRODUCT_ID)?.owned;
      // 복원은 스토어에 명시적으로 물어본 결과이므로 회수까지 신뢰하고 반영
      if (owned) grantPremium();
      else revokePremium();
      return owned;
    } catch (e) {
      console.error('[Premium] 복원 실패:', e);
      return false;
    }
  }, [grantPremium, revokePremium]);

  const value: PremiumContextValue = {
    isNative,
    storeReady,
    isPremium,
    price,
    paywallOpen,
    openPaywall: React.useCallback(() => setPaywallOpen(true), []),
    closePaywall: React.useCallback(() => setPaywallOpen(false), []),
    purchase,
    restore,
  };

  return <PremiumContext.Provider value={value}>{children}</PremiumContext.Provider>;
}

export function usePremium() {
  const ctx = React.useContext(PremiumContext);
  if (!ctx) throw new Error('usePremium은 PremiumProvider 안에서만 사용할 수 있습니다');
  return ctx;
}
