"use client";
import React, { useState } from 'react';
import { LogIn, LogOut, RefreshCw, Trash2, UserRound, BadgeCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/lib/state/AuthProvider';
import { usePremium } from '@/lib/state/PremiumProvider';
import { deleteAccount } from '@/lib/api/premium';
import { AuthScreen } from './AuthScreen';

// 프로필 탭의 계정 섹션 — 로그인 CTA / 계정 정보 / 구매 복원 / 로그아웃 / 계정 삭제(5.1.1(v))
export function AccountSection() {
  const { user, available, signOut, getAccessToken } = useAuth();
  const { isPremium, accountPremium, localPurchaseDetected, restorePurchases, refresh } = usePremium();
  const [authOpen, setAuthOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Supabase 미구성 빌드(로컬 개발 등)에서는 섹션 자체를 숨김 — 게스트 플로우 무영향
  if (!available) return null;

  const run = async (fn: () => Promise<void>, doneMsg?: string) => {
    setBusy(true);
    setMessage(null);
    try {
      await fn();
      if (doneMsg) setMessage(doneMsg);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '요청에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  const onRestore = () =>
    run(async () => {
      await restorePurchases();
      await refresh();
    }, '구매 복원을 요청했습니다. 잠시 후 상태가 갱신됩니다.');

  const onDelete = () =>
    run(async () => {
      const token = await getAccessToken();
      if (!token) throw new Error('로그인이 필요합니다');
      await deleteAccount(token);
      await signOut();
      setConfirmDelete(false);
    }, '계정이 삭제되었습니다.');

  return (
    <>
      <div className="mb-2 text-sm font-bold text-text-muted">계정</div>
      <div className="mb-4 overflow-hidden rounded-2xl border border-border bg-surface">
        {!user ? (
          <button
            onClick={() => setAuthOpen(true)}
            className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2"
          >
            <span className="text-text-muted"><LogIn className="h-5 w-5" /></span>
              <span className="flex-1">
                <span className="block font-bold text-text">로그인</span>
                <span className="block text-xs text-text-muted">
                  {localPurchaseDetected ? '구매 확인을 위해 로그인 후 복원해요' : '구매 내역을 웹·다른 기기와 공유해요'}
                </span>
            </span>
            <span className="text-sm font-bold text-accent">시작</span>
          </button>
        ) : (
          <>
            <div className="flex items-center gap-3 px-5 py-4">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent/10 text-accent">
                <UserRound className="h-5 w-5" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="truncate font-bold text-text">{user.email ?? '소셜 계정'}</div>
                <div className="flex items-center gap-1 text-xs font-bold text-text-muted">
                  {isPremium ? (
                    <><BadgeCheck className="h-3.5 w-3.5 text-accent" /> 프리미엄{accountPremium ? ' · 계정 연동됨' : ''}</>
                  ) : localPurchaseDetected ? (
                    '구매 확인 필요 · 로그인 후 복원해 주세요'
                  ) : (
                    '무료 이용 중'
                  )}
                </div>
              </div>
            </div>
            <div className="h-px bg-border" />
            <ActionRow icon={<RefreshCw className="h-5 w-5" />} label="구매 복원" hint="스토어 영수증으로 프리미엄 되찾기" onClick={onRestore} disabled={busy} />
            <div className="h-px bg-border" />
            <ActionRow icon={<LogOut className="h-5 w-5" />} label="로그아웃" onClick={() => run(() => signOut())} disabled={busy} />
            <div className="h-px bg-border" />
            {!confirmDelete ? (
              <ActionRow icon={<Trash2 className="h-5 w-5" />} label="계정 삭제" hint="구매 기록 연결이 해제됩니다" danger onClick={() => setConfirmDelete(true)} disabled={busy} />
            ) : (
              <div className="flex gap-2 px-5 py-3">
                <Button variant="surface" size="sm" className="flex-1" onClick={() => setConfirmDelete(false)} disabled={busy}>취소</Button>
                <Button variant="danger" size="sm" className="flex-1" onClick={onDelete} disabled={busy}>삭제 확인</Button>
              </div>
            )}
          </>
        )}
        {message && <div className="px-5 pb-3 text-xs font-bold text-text-muted">{message}</div>}
      </div>

      <AuthScreen open={authOpen} onClose={() => setAuthOpen(false)} />
    </>
  );
}

function ActionRow({ icon, label, hint, danger, onClick, disabled }: {
  icon: React.ReactNode; label: string; hint?: string; danger?: boolean;
  onClick: () => void; disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2 disabled:opacity-40"
    >
      <span className={danger ? 'text-danger' : 'text-text-muted'}>{icon}</span>
      <span className="flex-1">
        <span className={`block font-bold ${danger ? 'text-danger' : 'text-text'}`}>{label}</span>
        {hint && <span className="block text-xs text-text-muted">{hint}</span>}
      </span>
    </button>
  );
}
