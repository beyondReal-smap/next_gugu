"use client";
import React, { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { usePremium } from '@/lib/state/PremiumProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import type { PremiumFeatureKey } from '@/lib/api/premium';

interface PaywallProps {
  open: boolean;
  onClose: () => void;
  /** 아이가 보호자에게 보여주는 안내. 구매 버튼 없음 */
  showToParent?: boolean;
}

const FEATURES: { key: PremiumFeatureKey; title: string; body: string }[] = [
  { key: 'ai_coach', title: '친절한 힌트', body: '같은 식을 두 번 틀렸을 때 더 쉬운 말로 도와줘요.' },
  { key: 'longitudinal_report', title: '오래 보는 리포트', body: '여러 주 동안의 정확도와 약한 식을 보호자가 볼 수 있어요.' },
  { key: 'multi_learner', title: '여러 아이 기록', body: '한 계정에서 아이별 기록을 나눠 둘 수 있어요.' },
  { key: 'adventure_pack', title: '어드벤처 보따리', body: '지금 월드는 그대로 두고, 나중에 더 많은 모험이 열려요.' },
];

export function Paywall({ open, onClose, showToParent = false }: PaywallProps) {
  const { hasFeature, purchase, restorePurchases } = usePremium();
  const { role } = usePrefs();
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const canBuy = !showToParent && role === 'guardian';

  const onBuy = () => {
    setMessage(null);
    try {
      purchase();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '구매를 시작할 수 없습니다');
    }
  };

  const onRestore = async () => {
    setBusy(true);
    setMessage(null);
    try {
      await restorePurchases();
      setMessage('복원을 요청했어요. 잠시 후 상태가 바뀝니다.');
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '복원에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          className="fixed inset-0 z-[90] flex items-end justify-center bg-bg/70 backdrop-blur-md sm:items-center"
          role="dialog"
          aria-modal="true"
          aria-labelledby="paywall-title"
        >
          <motion.div
            initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }}
            className="w-full max-w-md rounded-t-3xl border border-border bg-surface p-6 shadow-2xl sm:rounded-3xl"
          >
            <div id="paywall-title" className="mb-1 flex items-center gap-2 text-lg font-extrabold text-text">
              <Sparkles className="h-5 w-5 text-accent" />
              {showToParent ? '보호자에게 보여 주세요' : '보호자 프리미엄'}
            </div>
            <p className="mb-4 text-sm font-bold text-text-muted">
              2~9단 학습, 기본 복습, 이 기기 주간 리포트는 그대로 쓸 수 있어요. 아이의 공부는 막지 않아요.
            </p>

            <ul className="mb-4 flex flex-col gap-2">
              {FEATURES.map((f) => {
                const on = hasFeature(f.key);
                return (
                  <li key={f.key} className="rounded-2xl border border-border bg-surface-2 px-4 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-extrabold text-text">{f.title}</span>
                      <span className="text-xs font-bold text-accent">{on ? '사용 중' : '보호자 기능'}</span>
                    </div>
                    <p className="mt-1 text-xs font-bold text-text-muted">{f.body}</p>
                  </li>
                );
              })}
            </ul>

            {canBuy ? (
              <div className="flex flex-col gap-2">
                <Button variant="primary" size="lg" className="w-full" onClick={onBuy} disabled={busy}>
                  앱에서 평생권 구매
                </Button>
                <Button variant="surface" size="md" className="w-full" onClick={() => { void onRestore(); }} disabled={busy}>
                  구매 복원
                </Button>
                <Button variant="ghost" size="md" className="w-full" onClick={onClose}>닫기</Button>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                <p className="text-sm font-bold text-text">
                  결제는 보호자만 할 수 있어요. 이 화면을 그대로 보여 주세요.
                </p>
                <Button variant="primary" size="lg" className="w-full" onClick={onClose}>알겠어요</Button>
              </div>
            )}
            {message && <p className="mt-3 text-xs font-bold text-text-muted">{message}</p>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
