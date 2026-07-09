"use client";
import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Map as MapIcon, Zap, Sparkles, Coffee, Infinity as InfinityIcon, Smartphone } from 'lucide-react';
import { usePremium } from '@/lib/state/PremiumProvider';
import { FALLBACK_PRICE, STORE_URLS, LEGAL_URLS } from '@/lib/premiumConfig';

import { Button } from '@/components/ui/Button';

const BENEFITS = [
  { icon: MapIcon, title: '3D 어드벤처 전 지역', desc: '8개 지역 탐험 · 주민/보스 구구단 대결' },
  { icon: Zap, title: '게임 모드 전부 해제', desc: '60초 챌린지 · 서바이벌 · 빈칸 추리 · OX 퀴즈' },
  { icon: InfinityIcon, title: '한 번 결제, 평생 소장', desc: '추가 결제 없음 · 업데이트 콘텐츠도 전부 포함' },
];

export function Paywall() {
  const { isNative, storeReady, isPremium, price: storePrice, paywallOpen, closePaywall, purchase, restore } = usePremium();
  const [busy, setBusy] = React.useState(false);
  const [notice, setNotice] = React.useState<string | null>(null);

  // 구매 완료(외부 갱신 포함) 시 자동 닫힘
  React.useEffect(() => {
    if (isPremium && paywallOpen) closePaywall();
  }, [isPremium, paywallOpen, closePaywall]);

  const price = storePrice ?? FALLBACK_PRICE;

  const onPurchase = async () => {
    if (!storeReady) {
      setNotice('스토어 연결 준비 중이에요. 잠시 후 다시 시도해주세요.');
      return;
    }
    setBusy(true);
    setNotice(null);
    const result = await purchase();
    setBusy(false);
    if (result === 'error') setNotice('구매에 실패했어요. 잠시 후 다시 시도해주세요.');
    // 'ok'는 isPremium 갱신 → 위 effect가 닫음, 'cancelled'는 조용히 유지
  };

  const onRestore = async () => {
    setBusy(true);
    setNotice(null);
    const ok = await restore();
    setBusy(false);
    setNotice(ok ? '구매가 복원되었어요! 🎉' : '복원할 구매 내역이 없어요.');
  };

  const openExternal = (url: string) => { if (url) window.open(url, '_blank'); };

  return (
    <AnimatePresence>
      {paywallOpen && (
        <motion.div
          initial={{ opacity: 0, y: 32 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 32 }}
          transition={{ type: 'spring', stiffness: 320, damping: 30 }}
          className="fixed inset-0 z-[60] mx-auto max-w-md overflow-y-auto bg-bg"
        >
          <div className="flex min-h-full flex-col px-5 pt-[calc(env(safe-area-inset-top)+0.75rem)] pb-[calc(env(safe-area-inset-bottom)+1.25rem)]">
            <button onClick={closePaywall} aria-label="닫기" className="self-end p-1 text-text-muted hover:text-text">
              <X className="h-6 w-6" />
            </button>

            {/* 히어로 — 커피 한 잔 값 후킹 */}
            <div className="mb-5 text-center">
              <motion.span
                animate={{ rotate: [0, -8, 8, 0] }}
                transition={{ duration: 2.8, repeat: Infinity, ease: 'easeInOut' }}
                className="mb-3 inline-flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-amber-400 to-orange-500 shadow-lg shadow-amber-500/30"
              >
                <Coffee className="h-8 w-8 text-white" strokeWidth={2.2} />
              </motion.span>
              <h2 className="text-2xl font-extrabold leading-snug text-text">
                커피 한 잔 값으로,<br />아이의 구구단 실력을
              </h2>
              <p className="mt-2 text-sm font-bold text-text-muted">
                딱 한 번 {price} — 모든 기능을 평생 이용해요
              </p>
            </div>

            {/* 혜택 */}
            <div className="mb-5 space-y-2.5">
              {BENEFITS.map(({ icon: Icon, title, desc }) => (
                <div key={title} className="flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/12 text-accent">
                    <Icon className="h-5 w-5" strokeWidth={2.4} />
                  </span>
                  <div>
                    <div className="text-sm font-extrabold text-text">{title}</div>
                    <div className="text-xs text-text-muted">{desc}</div>
                  </div>
                </div>
              ))}
            </div>

            {isNative ? (
              <>
                {/* 가격 카드 — 단일 상품 */}
                <div className="relative mb-4 rounded-3xl border-2 border-accent bg-accent/8 p-5 text-center">
                  <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-amber-400 px-3 py-1 text-[11px] font-extrabold text-amber-950 shadow">
                    ☕ 커피 한 잔 값
                  </span>
                  <div className="mt-1 flex items-end justify-center gap-1.5">
                    <span className="num text-4xl font-extrabold text-text">{price}</span>
                    <span className="mb-1.5 text-sm font-bold text-text-muted">· 딱 한 번</span>
                  </div>
                  <p className="mt-1 text-xs font-bold text-text-muted">정기 결제 아니에요 — 한 번 사면 끝!</p>
                </div>

                <Button variant="primary" size="lg" className="w-full" onClick={onPurchase} disabled={busy}>
                  <Sparkles className="h-5 w-5" /> {busy ? '처리 중…' : '평생 이용권 시작하기'}
                </Button>

                {!storeReady && (
                  <p className="mt-2 text-center text-[11px] font-bold text-warning">
                    스토어 연결 준비 중 — 가격은 결제 화면에서 최종 확인돼요.
                  </p>
                )}
                {notice && <p className="mt-2 text-center text-xs font-bold text-accent">{notice}</p>}

                <div className="mt-4 flex items-center justify-center gap-4 pb-2 text-[11px] font-bold text-text-muted">
                  <button onClick={onRestore} disabled={busy} className="underline">구매 복원</button>
                  <button onClick={() => openExternal(LEGAL_URLS.terms)} className="underline">이용약관</button>
                  <button onClick={() => openExternal(LEGAL_URLS.privacy)} className="underline">개인정보처리방침</button>
                </div>
              </>
            ) : (
              /* 웹 데모 — 결제 대신 앱 다운로드 안내 */
              <div className="rounded-3xl border border-border bg-surface p-5 text-center">
                <span className="mb-2 inline-flex h-11 w-11 items-center justify-center rounded-2xl bg-accent/12 text-accent">
                  <Smartphone className="h-6 w-6" />
                </span>
                <p className="mb-3 text-sm font-bold text-text">
                  평생 이용권은 앱에서 구매할 수 있어요
                </p>
                {STORE_URLS.appStore || STORE_URLS.playStore ? (
                  <div className="flex flex-col gap-2">
                    {STORE_URLS.appStore && (
                      <Button variant="primary" className="w-full" onClick={() => openExternal(STORE_URLS.appStore)}>
                        App Store에서 받기
                      </Button>
                    )}
                    {STORE_URLS.playStore && (
                      <Button variant="primary" className="w-full" onClick={() => openExternal(STORE_URLS.playStore)}>
                        Google Play에서 받기
                      </Button>
                    )}
                  </div>
                ) : (
                  <p className="text-xs font-bold text-text-muted">📱 스토어 출시 준비 중이에요</p>
                )}
              </div>
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
