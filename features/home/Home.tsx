"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Flame, Play, ChevronRight, Target, BookX } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { Button } from '@/components/ui/Button';
import { levelTitle } from '@/lib/level';
import Link from 'next/link';

// 홈에서 바로 시작하는 모드 (전체 랜덤)
const QUICK_MODES: GameMode[] = ['challenge', 'survival', 'missing', 'truefalse'];

export function Home() {
  const { state, levelInfo } = useGame();
  const { start } = useSession();
  const goalPct = state.dailyGoal ? state.dailyCorrect / state.dailyGoal : 0;
  const weakCount = Object.keys(state.wrongPool).length;

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
