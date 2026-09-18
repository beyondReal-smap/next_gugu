"use client";
import React from 'react';
import { X } from 'lucide-react';
import { APP_NAME, StorePlatform, detectStorePlatform } from '@/features/landing/stores';
import { StoreButtons } from '@/features/landing/StoreButtons';
import { WEB_TRIAL_DAILY_LIMIT } from '@/lib/webTrial';

export type InstallReason = 'limit' | 'adventure';

const COPY: Record<InstallReason, { title: string; body: string }> = {
  limit: {
    title: '오늘 웹 체험을 다 했어요',
    body: `웹에서는 하루 ${WEB_TRIAL_DAILY_LIMIT}판까지 해볼 수 있어요. 앱을 설치하면 횟수 제한 없이 계속할 수 있어요.`,
  },
  adventure: {
    title: '3D 어드벤처는 앱에서 만나요',
    body: '3D 월드를 탐험하며 주민과 구구단 대결을 펼치는 어드벤처 모드는 앱 전용이에요.',
  },
};

export function InstallPrompt({ reason, onClose }: { reason: InstallReason; onClose: () => void }) {
  const [platform, setPlatform] = React.useState<StorePlatform>('other');
  React.useEffect(() => setPlatform(detectStorePlatform()), []);

  // Esc로 닫기
  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const { title, body } = COPY[reason];
  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center bg-slate-950/60 p-4 sm:items-center" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="install-title"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-md break-keep rounded-3xl bg-gradient-to-br from-indigo-600 to-violet-700 p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] text-white shadow-2xl sm:p-8"
      >
        <button type="button" onClick={onClose} aria-label="닫기" className="absolute right-3 top-3 flex h-11 w-11 items-center justify-center rounded-full text-white/80 hover:bg-white/10">
          <X aria-hidden="true" className="h-5 w-5" />
        </button>
        <div className="flex items-center gap-2.5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/landing/app-icon.webp" alt="" width={40} height={40} className="h-10 w-10 rounded-xl ring-1 ring-white/30" />
          <span className="font-extrabold">{APP_NAME}</span>
        </div>
        <h2 id="install-title" className="mt-5 text-2xl font-extrabold tracking-tight">{title}</h2>
        <p className="mt-2 text-sm leading-relaxed text-indigo-100">{body}</p>
        <StoreButtons platform={platform} className="mt-6" />
      </div>
    </div>
  );
}
