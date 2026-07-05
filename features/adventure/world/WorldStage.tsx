"use client";
// three 청크를 어드벤처 진입 시에만 로드 — 코어 번들 무영향
import dynamic from 'next/dynamic';

export const WorldStage = dynamic(() => import('./WorldCanvas'), {
  ssr: false,
  loading: () => (
    <div className="flex h-full flex-col items-center justify-center gap-3 bg-bg">
      <div className="h-8 w-8 animate-spin rounded-full border-[3px] border-surface-2 border-t-accent" />
      <div className="text-sm font-bold text-text-muted">월드 불러오는 중…</div>
    </div>
  ),
});
