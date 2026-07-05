"use client";
// 월드 상단 HUD — 뒤로가기 / 지역명 / 격파 현황
import React from 'react';
import { ArrowLeft, Swords } from 'lucide-react';
import { RegionDef } from '@/lib/adventure/types';
import { RegionStats } from '@/lib/adventure/progress';

interface WorldHudProps {
  region: RegionDef;
  stats: RegionStats;
  onBack: () => void;
}

export function WorldHud({ region, stats, onBack }: WorldHudProps) {
  return (
    <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex items-center gap-2.5 px-4 pt-[calc(env(safe-area-inset-top)+0.75rem)]">
      <button
        onClick={onBack}
        aria-label="지역 선택으로"
        className="pointer-events-auto flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-black/35 text-white backdrop-blur-sm"
      >
        <ArrowLeft className="h-5 w-5" />
      </button>
      <div className="rounded-full bg-black/35 px-4 py-2 text-sm font-extrabold text-white backdrop-blur-sm">
        {region.name} · <span className="num">{region.table}</span>단
      </div>
      <div className="ml-auto flex items-center gap-1.5 rounded-full bg-black/35 px-3.5 py-2 text-sm font-bold text-white backdrop-blur-sm">
        <Swords className="h-4 w-4" />
        <span className="num">{stats.defeated}/{stats.total}</span>
      </div>
    </div>
  );
}
