"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Flame, Play, ChevronRight, Target, BookX, Map as MapIcon, Swords } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { totalStats } from '@/lib/adventure/progress';
import { REGIONS, bossIdFor } from '@/lib/adventure/world';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { Button } from '@/components/ui/Button';
import { levelTitle } from '@/lib/level';
import Link from 'next/link';

// 홈에서 바로 시작하는 모드 (전체 랜덤)
const QUICK_MODES: GameMode[] = ['challenge', 'survival', 'missing', 'truefalse'];

export function Home() {
  const { state, levelInfo } = useGame();
  const { start } = useSession();
  const { openAdventure, progress } = useAdventure();
  const goalPct = state.dailyGoal ? state.dailyCorrect / state.dailyGoal : 0;
  const weakCount = Object.keys(state.wrongPool).length;
  const adv = totalStats(progress);
  const clearedBoss = (table: number) => progress.defeatedNpcs.includes(bossIdFor(table));
  const nextRegion = REGIONS.find((r) => !clearedBoss(r.table)); // 다음 도전 지역 (전부 클리어면 undefined)

  return (
    <div className="mx-auto max-w-md px-5 pt-6 animate-fade-in">
      {/* 헤더 */}
      <div className="mb-5 flex items-center justify-between">
        <div>
          <div className="text-sm font-bold text-text-muted">안녕하세요 👋</div>
          <div className="text-xl font-extrabold text-text">오늘도 구구단 한 판!</div>
        </div>
        <div className="flex items-center gap-1.5 rounded-full bg-danger/12 px-3 py-1.5 text-danger">
          <Flame className="h-4 w-4" fill="currentColor" />
          <span className="num text-sm font-extrabold">{state.streak}일</span>
        </div>
      </div>

      {/* 데일리 골 링 */}
      <div className="mb-4 flex items-center gap-5 rounded-3xl border border-border bg-surface p-5">
        <ProgressRing value={goalPct} size={104} stroke={11}>
          <div className="num text-2xl font-extrabold text-text">{state.dailyCorrect}</div>
          <div className="text-[11px] font-bold text-text-muted">/ {state.dailyGoal}</div>
        </ProgressRing>
        <div className="flex-1">
          <div className="mb-1 flex items-center gap-1.5 text-sm font-bold text-text">
            <Target className="h-4 w-4 text-accent" /> 오늘의 목표
          </div>
          <p className="text-sm text-text-muted">
            {goalPct >= 1 ? '목표 달성! 멋져요 🎉' : `정답 ${Math.max(0, state.dailyGoal - state.dailyCorrect)}개 더 풀면 달성!`}
          </p>
        </div>
      </div>

      {/* 레벨 / XP */}
      <div className="mb-4 rounded-3xl border border-border bg-surface p-5">
        <div className="mb-2 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent text-sm font-extrabold text-accent-fg num">
              {levelInfo.level}
            </span>
            <div>
              <div className="text-sm font-extrabold text-text">Lv.{levelInfo.level}</div>
              <div className="text-xs font-bold text-text-muted">{levelTitle(levelInfo.level)}</div>
            </div>
          </div>
          <span className="num text-xs font-bold text-text-muted">{levelInfo.currentLevelXp}/{levelInfo.xpForNextLevel} XP</span>
        </div>
        <div className="h-2.5 overflow-hidden rounded-full bg-surface-2">
          <motion.div className="h-full rounded-full bg-accent gauge-stripes" animate={{ width: `${levelInfo.progress * 100}%` }} transition={{ type: 'spring', stiffness: 120, damping: 20 }} />
        </div>
      </div>

      {/* 빠른 시작 */}
      <Button variant="primary" size="lg" className="mb-3 w-full" onClick={() => start('practice', null)}>
        <Play className="h-5 w-5" fill="currentColor" /> 빠른 학습 시작
      </Button>

      {/* 어드벤처 — 히어로 카드 (3D 월드 탐험 모드) */}
      <motion.button
        whileTap={{ scale: 0.97 }}
        onClick={openAdventure}
        className="relative mb-3 w-full overflow-hidden rounded-3xl bg-gradient-to-br from-indigo-500 via-violet-500 to-purple-600 p-5 text-left text-white shadow-lg shadow-indigo-500/30"
      >
        {/* 배경 장식 — 빛 원 + 별 반짝임 */}
        <span className="pointer-events-none absolute -right-10 -top-12 h-40 w-40 rounded-full bg-white/10" />
        <span className="pointer-events-none absolute -bottom-14 right-14 h-32 w-32 rounded-full bg-white/10" />
        <motion.span
          animate={{ opacity: [0.3, 1, 0.3] }}
          transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute left-[55%] top-4 h-1.5 w-1.5 rounded-full bg-white/80"
        />
        <motion.span
          animate={{ opacity: [1, 0.25, 1] }}
          transition={{ duration: 3.1, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute right-24 bottom-6 h-1 w-1 rounded-full bg-white/70"
        />

        <div className="relative flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <span className="mb-1.5 inline-flex items-center gap-1 rounded-full bg-white/20 px-2.5 py-1 text-[11px] font-extrabold tracking-wide">
              <MapIcon className="h-3.5 w-3.5" /> 어드벤처
            </span>
            <span className="block text-xl font-extrabold leading-snug">
              3D 월드를 탐험하며<br />구구단 대결!
            </span>
            <span className="num mt-2 flex items-center gap-1.5 text-xs font-bold text-white/90">
              <Swords className="h-3.5 w-3.5" /> 격파 {adv.defeated}/{adv.total}
              {nextRegion && <span className="truncate">· 다음 모험 {nextRegion.name}</span>}
            </span>
            {/* 지역 진행 도트 — 클리어한 지역만 밝게 */}
            <span className="mt-2.5 flex items-center gap-1.5">
              {REGIONS.map((r) => (
                <span
                  key={r.table}
                  className={`h-1.5 rounded-full transition-all ${clearedBoss(r.table) ? 'w-4 bg-white' : 'w-1.5 bg-white/35'}`}
                />
              ))}
            </span>
          </div>

          {/* 마스코트 — 월드 플레이어와 같은 캡슐+눈 언어, 둥실 애니메이션 */}
          <motion.span
            animate={{ y: [0, -7, 0] }}
            transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
            className="relative flex h-24 w-16 shrink-0 items-end justify-center"
          >
            <span className="absolute bottom-0 h-2.5 w-11 rounded-full bg-black/25 blur-[2px]" />
            <span className="mb-2 flex h-[4.6rem] w-[3.2rem] items-start justify-center rounded-full bg-white pt-[1.1rem] shadow-xl">
              <span className="flex gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-white shadow-[inset_0_0_0_3px_rgba(32,36,44,0.95)]" />
                <span className="h-2.5 w-2.5 rounded-full bg-white shadow-[inset_0_0_0_3px_rgba(32,36,44,0.95)]" />
              </span>
            </span>
          </motion.span>
          <ChevronRight className="h-5 w-5 shrink-0 self-center opacity-90" />
        </div>
      </motion.button>

      {/* 취약 문제 복습 — 오답 풀이 쌓였을 때만 */}
      {weakCount > 0 && (
        <button
          onClick={() => start('practice', null)}
          className="mb-3 flex w-full items-center gap-3 rounded-2xl border border-danger/25 bg-danger/10 px-5 py-4 text-left transition-colors hover:bg-danger/15"
        >
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-danger/15 text-danger">
            <BookX className="h-5 w-5" />
          </span>
          <span className="flex-1">
            <span className="block font-bold text-text">취약 문제 복습</span>
            <span className="block text-sm text-text-muted">헷갈렸던 문제 {weakCount}개가 우선 출제돼요</span>
          </span>
          <ChevronRight className="h-5 w-5 shrink-0 text-text-muted" />
        </button>
      )}

      {/* 게임 모드 바로가기 */}
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-bold text-text-muted">게임 모드</span>
        <Link href="/learn" className="flex items-center text-xs font-bold text-accent">
          전체 보기 <ChevronRight className="h-3.5 w-3.5" />
        </Link>
      </div>
      <div className="mb-4 grid grid-cols-2 gap-2.5">
        {QUICK_MODES.map((id, i) => {
          const m = MODES[id];
          const Icon = MODE_ICONS[id];
          const tint = MODE_TINT[id];
          const best = m.scored ? state.bestScores[id] ?? 0 : 0;
          return (
            <motion.button
              key={id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
              whileTap={{ scale: 0.96 }}
              onClick={() => start(id, null)}
              className="relative flex flex-col items-start gap-2 rounded-2xl border border-border bg-surface p-3.5 text-left transition-colors hover:bg-surface-2"
            >
              <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}>
                <Icon className="h-5 w-5" strokeWidth={2.4} />
              </span>
              <span>
                <span className="block text-sm font-extrabold text-text">{m.name}</span>
                <span className="block text-xs text-text-muted">{m.tagline}</span>
              </span>
              {m.scored && best > 0 && (
                <span className="num absolute right-2.5 top-2.5 rounded-full bg-warning/15 px-2 py-0.5 text-[10px] font-extrabold text-warning">
                  최고 {best}
                </span>
              )}
            </motion.button>
          );
        })}
      </div>

      <Link href="/learn" className="mb-4 flex items-center justify-between rounded-2xl border border-border bg-surface px-5 py-4 text-sm font-bold text-text transition-colors hover:bg-surface-2">
        단 선택해서 학습하기
        <ChevronRight className="h-5 w-5 text-text-muted" />
      </Link>
    </div>
  );
}
