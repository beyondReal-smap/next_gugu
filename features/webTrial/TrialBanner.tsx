"use client";
import React from 'react';
import Link from 'next/link';
import { useWebTrial } from '@/lib/state/WebTrialProvider';
import { WEB_TRIAL_DAILY_LIMIT } from '@/lib/webTrial';

// 웹 체험판 상단 안내 — 남은 판 수와 설치 링크(랜딩). 네이티브 앱에서는 렌더하지 않는다.
export function TrialBanner() {
  const { limited, remaining } = useWebTrial();
  if (!limited || remaining === null) return null;
  return (
    <div className="bg-indigo-600 px-4 pt-[env(safe-area-inset-top)] text-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 py-2 text-xs font-bold sm:text-sm">
        <span>
          웹 체험판 · 오늘 <span className="text-amber-300">{remaining}</span>/{WEB_TRIAL_DAILY_LIMIT}판 남음
        </span>
        <Link href="/" className="shrink-0 rounded-full bg-white px-3 py-1.5 text-indigo-700 hover:bg-indigo-50">
          앱 설치하고 무제한
        </Link>
      </div>
    </div>
  );
}
