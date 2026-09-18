"use client";
import React, { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X, Mail, Lock } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/lib/state/AuthProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import type { SocialProvider } from '@/lib/auth/socialLogin';

interface AuthScreenProps {
  open: boolean;
  onClose: () => void;
}

type EmailMode = 'signin' | 'signup';

// 소셜(Apple/Google) 로그인 — 2026-07-06 결정으로 보류: 결제는 IAP 전용, 로그인은 이메일만 노출.
// 프로바이더 미설정 상태로 노출하면 클릭 시 에러만 발생하므로 숨긴다.
// 다시 열려면 docs/AUTH_SETUP.md §2·3 콘솔 설정 후 true로 변경.
// (Google 등 서드파티 로그인 노출 시 Apple 버튼을 최상단에 함께 노출해야 함 — App Store 4.8)
const SOCIAL_LOGIN_ENABLED = false;

// 로그인 모달
export function AuthScreen({ open, onClose }: AuthScreenProps) {
  const { signInEmail, signUpEmail, signInSocial } = useAuth();
  const { role } = usePrefs();
  const canSignUp = role === 'guardian';
  const [mode, setMode] = useState<EmailMode>('signin');
  const emailMode: EmailMode = canSignUp ? mode : 'signin';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그인에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  const social = (provider: SocialProvider) =>
    run(async () => {
      await signInSocial(provider);
      // 웹 OAuth는 리다이렉트로 떠나므로 close는 네이티브 성공 시에만 의미 있음
      onClose();
    });

  const submitEmail = () =>
    run(async () => {
      if (!email.trim() || !password) throw new Error('이메일과 비밀번호를 입력해 주세요');
      if (emailMode === 'signin') {
        await signInEmail(email.trim(), password);
        onClose();
      } else {
        if (!canSignUp) throw new Error('가입은 보호자만 할 수 있어요');
        const { needsConfirm } = await signUpEmail(email.trim(), password);
        if (needsConfirm) {
          setNotice('확인 메일을 보냈습니다. 메일함에서 인증 후 로그인해 주세요.');
        } else {
          onClose();
        }
      }
    });

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          className="fixed inset-0 z-[90] flex items-end justify-center bg-bg/70 backdrop-blur-md sm:items-center"
          onClick={onClose}
        >
          <motion.div
            initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 40, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 320, damping: 26 }}
            className="w-full max-w-md rounded-t-3xl border border-border bg-surface p-6 shadow-2xl sm:rounded-3xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <div className="text-lg font-extrabold text-text">로그인</div>
              <button onClick={onClose} className="rounded-xl p-1.5 text-text-muted hover:bg-surface-2" aria-label="닫기">
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mb-1 text-sm text-text-muted">
              구매 내역을 계정에 연결하면 웹과 다른 기기에서도 프리미엄을 사용할 수 있어요.
            </div>

            {SOCIAL_LOGIN_ENABLED && (
              <>
                {/* 소셜 로그인 — Apple 우선 노출 (4.8) */}
                <div className="mt-4 flex flex-col gap-2.5">
                  <button
                    onClick={() => social('apple')}
                    disabled={busy}
                    className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-black font-bold text-white transition-[filter] hover:brightness-110 disabled:pointer-events-none disabled:opacity-40 dark:bg-white dark:text-black"
                  >
                    <AppleIcon /> Apple로 계속하기
                  </button>
                  <button
                    onClick={() => social('google')}
                    disabled={busy}
                    className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl border border-border bg-surface font-bold text-text transition-colors hover:bg-surface-2 disabled:pointer-events-none disabled:opacity-40"
                  >
                    <GoogleIcon /> Google로 계속하기
                  </button>
                </div>

                <div className="my-4 flex items-center gap-3">
                  <div className="h-px flex-1 bg-border" />
                  <span className="text-xs font-bold text-text-muted">또는 이메일로</span>
                  <div className="h-px flex-1 bg-border" />
                </div>
              </>
            )}

            {/* 이메일 로그인/가입 */}
            <div className="mt-4 flex flex-col gap-2.5">
              <label className="flex items-center gap-2 rounded-2xl border border-border bg-surface-2 px-4">
                <Mail className="h-4 w-4 shrink-0 text-text-muted" />
                <input
                  type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  placeholder="이메일" autoComplete="email"
                  className="h-12 w-full bg-transparent text-[15px] font-medium text-text outline-none placeholder:text-text-muted"
                />
              </label>
              <label className="flex items-center gap-2 rounded-2xl border border-border bg-surface-2 px-4">
                <Lock className="h-4 w-4 shrink-0 text-text-muted" />
                <input
                  type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  placeholder="비밀번호 (6자 이상)"
                  autoComplete={emailMode === 'signin' ? 'current-password' : 'new-password'}
                  className="h-12 w-full bg-transparent text-[15px] font-medium text-text outline-none placeholder:text-text-muted"
                />
              </label>

              {error && <div className="text-sm font-bold text-danger">{error}</div>}
              {notice && <div className="text-sm font-bold text-accent">{notice}</div>}

              <Button onClick={submitEmail} disabled={busy} className="mt-1 w-full" size="lg">
                {emailMode === 'signin' ? '이메일로 로그인' : '이메일로 가입하기'}
              </Button>
              {canSignUp && (
                <button
                  onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null); setNotice(null); }}
                  className="py-1 text-sm font-bold text-text-muted hover:text-text"
                >
                  {mode === 'signin' ? '계정이 없으신가요? 가입하기' : '이미 계정이 있으신가요? 로그인'}
                </button>
              )}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function AppleIcon() {
  return (
    <svg className="h-4 w-4 fill-current" viewBox="0 0 24 24" aria-hidden>
      <path d="M16.36 12.76c.02 2.9 2.55 3.86 2.58 3.87-.02.07-.4 1.38-1.33 2.73-.8 1.17-1.63 2.33-2.94 2.36-1.29.02-1.7-.76-3.17-.76s-1.93.73-3.14.78c-1.27.05-2.23-1.26-3.04-2.42C3.66 16.94 2.4 12.6 4.1 9.72c.84-1.43 2.34-2.34 3.97-2.36 1.24-.03 2.41.83 3.17.83.76 0 2.18-1.03 3.68-.88.63.03 2.39.25 3.52 1.91-.09.06-2.1 1.23-2.08 3.54zM13.93 5.72c.67-.81 1.12-1.94.99-3.06-.96.04-2.13.64-2.82 1.45-.62.72-1.16 1.87-1.02 2.97 1.08.08 2.17-.55 2.85-1.36z" />
    </svg>
  );
}

function GoogleIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden>
      <path fill="#4285F4" d="M23.5 12.3c0-.9-.1-1.5-.3-2.2H12v4.1h6.5c-.1 1.1-.8 2.7-2.4 3.8l-.02.15 3.5 2.7.24.02c2.2-2 3.5-5 3.5-8.6z" />
      <path fill="#34A853" d="M12 24c3.2 0 5.9-1 7.9-2.9l-3.7-2.9c-1 .7-2.4 1.2-4.2 1.2-3.2 0-5.9-2.1-6.8-5l-.14.01-3.6 2.8-.05.13C3.3 21.3 7.3 24 12 24z" />
      <path fill="#FBBC05" d="M5.2 14.4c-.3-.7-.4-1.5-.4-2.4 0-.8.2-1.6.4-2.4l-.01-.16-3.7-2.8-.12.06C.5 8.2 0 10 0 12s.5 3.8 1.4 5.3l3.8-2.9z" />
      <path fill="#EB4335" d="M12 4.6c2.3 0 3.8 1 4.7 1.8l3.4-3.3C18 1.2 15.2 0 12 0 7.3 0 3.3 2.7 1.4 6.7l3.8 2.9c1-2.9 3.6-5 6.8-5z" />
    </svg>
  );
}
