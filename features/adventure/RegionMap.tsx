"use client";
// 지역 선택 맵 — 8지역(2~9단), 직전 지역 보스 격파 시 해금
import React from 'react';
import { motion } from 'framer-motion';
import * as Icons from 'lucide-react';
import { X, Lock, Crown, Swords, ChevronRight } from 'lucide-react';
import { RegionDef } from '@/lib/adventure/types';
import { REGIONS } from '@/lib/adventure/world';
import { isRegionUnlocked, regionStats, totalStats } from '@/lib/adventure/progress';
import { ADV_ACHIEVEMENTS } from '@/lib/adventure/achievements';
import { useAdventure } from '@/lib/state/AdventureProvider';

interface RegionMapProps {
  onSelect: (region: RegionDef) => void;
  onExit: () => void;
}

export function RegionMap({ onSelect, onExit }: RegionMapProps) {
  const { progress } = useAdventure();
  const total = totalStats(progress);

  return (
    <div className="flex h-full flex-col">
      {/* 헤더 */}
      <div className="px-5 pt-[calc(env(safe-area-inset-top)+1rem)]">
        <div className="mb-1 flex items-center justify-between">
          <h1 className="text-2xl font-extrabold text-text">어드벤처</h1>
          <button onClick={onExit} aria-label="어드벤처 닫기" className="text-text-muted hover:text-text">
            <X className="h-6 w-6" />
          </button>
        </div>
        <p className="mb-3 text-sm text-text-muted">지역을 탐험하고 주민들과 구구단 대결을 펼쳐요</p>
        <div className="mb-3 flex items-center gap-3">
          <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-surface-2">
            <div
              className="h-full rounded-full bg-accent transition-[width] duration-500"
              style={{ width: `${(total.defeated / total.total) * 100}%` }}
            />
          </div>
          <span className="num flex items-center gap-1 text-xs font-extrabold text-text-muted">
            <Swords className="h-3.5 w-3.5" /> {total.defeated}/{total.total}
          </span>
        </div>

        {/* 어드벤처 전용 업적 */}
        <div className="mb-4 flex flex-wrap gap-1.5">
          {ADV_ACHIEVEMENTS.map((a) => {
            const unlocked = progress.achievements.includes(a.id);
            const Icon = (Icons as unknown as Record<string, Icons.LucideIcon>)[a.icon] ?? Icons.Award;
            return (
              <span
                key={a.id}
                title={a.description}
                className={`flex items-center gap-1 rounded-full px-2 py-1 text-[10px] font-extrabold
                  ${unlocked ? 'bg-warning/15 text-warning' : 'bg-surface-2 text-text-muted opacity-60'}`}
              >
                <Icon className="h-3 w-3" strokeWidth={2.6} /> {a.name}
              </span>
            );
          })}
        </div>
      </div>

      {/* 지역 목록 — overscroll-contain: 리스트 끝에서 스크롤이 배경으로 새지 않게 */}
      <div className="flex-1 overflow-y-auto overscroll-contain px-5 pb-[calc(env(safe-area-inset-bottom)+1.5rem)]">
        <div className="flex flex-col gap-2.5">
          {REGIONS.map((region, i) => {
            const unlocked = isRegionUnlocked(progress, region.table);
            const stats = regionStats(progress, region);
            return (
              <motion.button
                key={region.table}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.04 }}
                whileTap={unlocked ? { scale: 0.97 } : undefined}
                disabled={!unlocked}
                onClick={() => onSelect(region)}
                className={`flex items-center gap-3.5 rounded-3xl border border-border bg-surface p-4 text-left
                  transition-colors ${unlocked ? 'hover:bg-surface-2' : 'opacity-55'}`}
              >
                {/* 지역 스와치 — 월드 테마 색 미리보기 */}
                <span
                  className="relative flex h-14 w-14 shrink-0 items-end justify-center overflow-hidden rounded-2xl"
                  style={{ backgroundColor: region.theme.sky }}
                >
                  <span className="h-5 w-full" style={{ backgroundColor: region.theme.ground }} />
                  <span
                    className="absolute bottom-3 h-4 w-4 rounded-full"
                    style={{ backgroundColor: region.theme.accent }}
                  />
                  {!unlocked && (
                    <span className="absolute inset-0 flex items-center justify-center bg-black/35">
                      <Lock className="h-5 w-5 text-white" />
                    </span>
                  )}
                </span>

                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="truncate font-extrabold text-text">{region.name}</span>
                    <span className="num shrink-0 rounded-full bg-accent/12 px-2 py-0.5 text-[10px] font-extrabold text-accent">
                      {region.table}단
                    </span>
                    {stats.bossDefeated && <Crown className="h-4 w-4 shrink-0 text-warning" fill="currentColor" />}
                  </span>
                  <span className="mt-0.5 block text-sm text-text-muted">
                    {unlocked ? (
                      <>주민 격파 <span className="num font-bold">{stats.defeated}/{stats.total}</span>{stats.bossDefeated && ' · 클리어!'}</>
                    ) : (
                      '이전 지역 보스를 이기면 열려요'
                    )}
                  </span>
                </span>

                {unlocked && <ChevronRight className="h-5 w-5 shrink-0 text-text-muted" />}
              </motion.button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
