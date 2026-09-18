"use client";
import React from 'react';
import { MotionConfig, motion } from 'framer-motion';
import { ArrowUpRight, BookOpen, Check, ChevronRight, Flame, Footprints, Map as MapIcon, Play, RotateCcw, Swords, Target } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { totalStats } from '@/lib/adventure/progress';
import { REGIONS, bossIdFor, regionFor } from '@/lib/adventure/world';
import { dominantWrongTable } from '@/lib/problems';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { ProgressRing } from '@/components/ui/ProgressRing';
import { levelTitle } from '@/lib/level';
import Link from 'next/link';

// 홈에서 바로 시작하는 모드 (전체 랜덤)
const QUICK_MODES: GameMode[] = ['challenge', 'survival', 'missing', 'truefalse'];

export function Home() {
  const { state, levelInfo } = useGame();
  const { start } = useSession();
  const { openAdventure, progress } = useAdventure();
  const goalPct = state.dailyGoal ? state.dailyCorrect / state.dailyGoal : 0;
  const goalReached = goalPct >= 1;
  const weakCount = Object.keys(state.wrongPool).length;
  const weakTable = dominantWrongTable(state.wrongPool);
  const weakRegion = weakTable == null ? undefined : regionFor(weakTable);
  const adv = totalStats(progress);
  const clearedBoss = (table: number) => progress.defeatedNpcs.includes(bossIdFor(table));
  const nextRegion = REGIONS.find((r) => !clearedBoss(r.table));

  return (
    <MotionConfig reducedMotion="user">
      <div className="mx-auto max-w-5xl break-keep px-5 pb-[env(safe-area-inset-bottom)] pt-[max(1.5rem,env(safe-area-inset-top))] sm:px-8 sm:pt-10">
        <header className="mb-6 flex flex-wrap items-start justify-between gap-3 sm:mb-8">
          <div>
            <p className="mb-2 text-xs font-extrabold tracking-wide text-accent">구구 어드벤처</p>
            <h1 className="text-2xl font-extrabold leading-snug tracking-tight text-text sm:text-3xl">작은 연습이, 큰 자신감으로.</h1>
            <p className="mt-2 text-sm text-text-muted">오늘도 나만의 속도로 한 걸음 나아가요.</p>
          </div>
          <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-2 text-sm font-bold text-text">
            <Flame aria-hidden="true" className="h-4 w-4 text-orange-600 dark:text-orange-400" />
            <span className="num">{state.streak}일</span> 연속 학습
          </span>
        </header>

        <div className="grid gap-4 lg:grid-cols-[1.25fr_1fr]">
          {/* 첫 화면에서 학습 시작과 오늘의 진도를 함께 확인합니다. */}
          <section aria-labelledby="practice-heading" className="relative isolate flex flex-col justify-between overflow-hidden rounded-3xl bg-[#153f35] p-6 text-white sm:p-8">
            <div aria-hidden="true" className="pointer-events-none absolute -right-8 -top-10 -z-10 h-64 w-64 rounded-full border-[40px] border-white/[0.04]" />
            <div aria-hidden="true" className="pointer-events-none absolute right-5 top-5 -z-10 grid rotate-12 grid-cols-2 gap-2 text-center font-extrabold text-white/[0.08] sm:right-8">
              <span className="num text-7xl">2</span><span className="text-7xl">×</span>
              <span className="text-7xl">×</span><span className="num text-7xl">9</span>
            </div>
            <div>
              <span className="inline-flex items-center gap-2 rounded-full border border-white/20 px-3 py-1.5 text-xs font-bold text-emerald-100">
                <BookOpen aria-hidden="true" className="h-3.5 w-3.5" /> 오늘의 연습
              </span>
              <h2 id="practice-heading" className="mt-5 text-3xl font-extrabold leading-tight tracking-tight sm:text-4xl">
                오늘도 {MODES.practice.total}문제,<br />가볍게 시작해요.
              </h2>
              <p className="mt-3 max-w-xs text-sm leading-relaxed text-emerald-100">틀려도 괜찮아요. 차근차근 풀다 보면<br className="hidden sm:block" /> 어느새 구구단이 익숙해질 거예요.</p>
            </div>
            <div className="mt-6">
              <button type="button" onClick={() => start('practice', null)} className="flex min-h-14 w-full items-center justify-between gap-3 rounded-2xl bg-[#dbf39c] px-5 py-3 text-base font-extrabold text-[#153f35] transition-colors hover:bg-[#e9ffc0] active:bg-[#cce789]">
                <span className="flex items-center gap-2"><Play aria-hidden="true" className="h-4 w-4" fill="currentColor" /> 빠른 학습 시작</span>
                <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0" />
              </button>
              <p className="mt-3 text-center text-xs text-emerald-100">2~9단 골고루 · 시간 제한 없이</p>
            </div>
          </section>

          <section aria-labelledby="goal-heading" className="flex flex-col justify-between rounded-3xl border border-border bg-surface p-5 sm:p-6">
            <div className="flex items-center justify-between gap-3">
              <h2 id="goal-heading" className="flex items-center gap-2 text-base font-extrabold text-text"><Target aria-hidden="true" className="h-4 w-4 text-accent" /> 오늘의 목표</h2>
              <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${goalReached ? 'bg-success/10 text-emerald-700 dark:text-emerald-300' : 'bg-surface-2 text-text-muted'}`}>
                {goalReached ? '달성 완료' : '차곡차곡 쌓는 중'}
              </span>
            </div>
            <div className="my-6 flex flex-wrap items-center gap-5">
              <div role="progressbar" aria-label="오늘의 정답 목표" aria-valuemin={0} aria-valuemax={state.dailyGoal} aria-valuenow={Math.min(state.dailyCorrect, state.dailyGoal)} aria-valuetext={`목표 ${state.dailyGoal}개 중 ${state.dailyCorrect}개 정답`}>
                <ProgressRing value={goalPct} size={104} stroke={9}>
                  <span className="num text-3xl font-extrabold text-text">{state.dailyCorrect}</span>
                  <span className="text-xs font-bold text-text-muted">/ {state.dailyGoal}개</span>
                </ProgressRing>
              </div>
              <div className="min-w-0 flex-1 basis-28">
                <p className="text-lg font-extrabold text-text">{goalReached ? '오늘의 목표 달성!' : `정답 ${Math.max(0, state.dailyGoal - state.dailyCorrect)}개 남았어요`}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-text-muted">{goalReached ? '꾸준한 연습이 실력이 되었어요. 내일도 함께해요.' : '한 문제씩 풀다 보면 오늘의 목표에 가까워져요.'}</p>
              </div>
            </div>
            <div className="border-t border-border pt-4">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <span className="flex items-center gap-2 text-sm font-extrabold text-text">
                  <span className="num flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent" aria-label={`${levelInfo.level}레벨`}>{levelInfo.level}</span>
                  {levelTitle(levelInfo.level)}
                </span>
                <span className="num text-xs font-bold text-text-muted">{levelInfo.currentLevelXp} / {levelInfo.xpForNextLevel} 경험치</span>
              </div>
              <div role="progressbar" aria-label="다음 레벨까지 경험치" aria-valuemin={0} aria-valuemax={levelInfo.xpForNextLevel} aria-valuenow={levelInfo.currentLevelXp} className="h-2 overflow-hidden rounded-full bg-surface-2">
                <div className="h-full rounded-full bg-accent" style={{ width: `${levelInfo.progress * 100}%` }} />
              </div>
            </div>
          </section>
        </div>

        {weakCount > 0 && (
          <section aria-label="맞춤 복습" className="mt-4 rounded-2xl border border-border bg-surface p-4 sm:px-5">
            <button type="button" onClick={() => start('practice', weakTable)} className="group flex min-h-12 w-full items-center gap-3 text-left">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-orange-500/10 text-orange-700 dark:text-orange-300"><RotateCcw aria-hidden="true" className="h-5 w-5" /></span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-extrabold text-text">헷갈린 문제, 한 번 더</span>
                <span className="mt-1 block text-xs leading-relaxed text-text-muted">{weakTable ? `${weakTable}단을 집중 복습해요 · 헷갈린 식 ${weakCount}개` : `헷갈린 식 ${weakCount}개를 우선 연습해요`}</span>
              </span>
              <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0 text-text-muted transition-transform group-hover:translate-x-0.5" />
            </button>
            {weakRegion && weakTable != null && (
              <button type="button" onClick={() => openAdventure(weakTable)} className="mt-3 flex min-h-11 w-full items-center justify-between gap-2 border-t border-border pt-3 text-left text-xs font-bold text-accent">
                <span className="flex items-center gap-2"><Swords aria-hidden="true" className="h-4 w-4 shrink-0" /> {weakRegion.name}에서 {weakTable}단 대결하기</span>
                <ArrowUpRight aria-hidden="true" className="h-4 w-4 shrink-0" />
              </button>
            )}
          </section>
        )}

        <section aria-labelledby="explore-heading" className="mt-8">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 id="explore-heading" className="text-lg font-extrabold text-text">다르게 즐겨볼까요?</h2>
              <p className="mt-1 text-xs text-text-muted">탐험도, 기록 도전도. 시작하면 모두 연습이에요.</p>
            </div>
            <Link href="/learn" className="flex min-h-11 items-center gap-1 text-sm font-bold text-accent">전체 모드 <ChevronRight aria-hidden="true" className="h-4 w-4" /></Link>
          </div>
          <Link href="/runner" className="mb-4 flex min-h-24 items-center gap-4 rounded-2xl border border-emerald-600/25 bg-emerald-500/5 px-5 py-4 transition-colors hover:bg-emerald-500/10">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#153f35] text-[#dbef9e]"><Footprints aria-hidden="true" className="h-6 w-6" /></span>
            <span className="min-w-0 flex-1"><span className="flex items-center gap-2 text-base font-extrabold text-text">구구 점프 <span className="rounded-full bg-emerald-600/10 px-2 py-0.5 text-[10px] text-emerald-800 dark:text-emerald-200">새 모드</span></span><span className="mt-1 block text-sm text-text-muted">정답을 맞히면 폴짝! 장애물을 넘어 달려요.</span></span>
            <ArrowUpRight aria-hidden="true" className="h-5 w-5 shrink-0 text-emerald-700 dark:text-emerald-300" />
          </Link>
          <div className="grid gap-4 lg:grid-cols-[1fr_1.25fr]">
            <button type="button" onClick={() => openAdventure()} className="group relative flex flex-col justify-between overflow-hidden rounded-3xl border border-border bg-surface p-5 text-left transition-colors hover:bg-surface-2 sm:p-6">
              <span className="flex w-full items-start justify-between gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"><MapIcon aria-hidden="true" className="h-5 w-5" /></span>
                <span className="rounded-full bg-surface-2 px-2.5 py-1 text-xs font-bold text-text-muted">어드벤처</span>
              </span>
              <span className="mt-4 block text-xl font-extrabold leading-snug text-text">구구단 너머,<br />새로운 모험으로</span>
              <span className="mt-2 block text-sm text-text-muted">{nextRegion ? `다음 모험 · ${nextRegion.name}` : '모든 지역 정복! 다시 대결해 볼까요?'}</span>
              <span className="mt-5 flex w-full items-end justify-between gap-3">
                <span className="min-w-0 flex-1">
                  <span className="num block text-xs font-bold text-text-muted">대결 완료 {adv.defeated} / {adv.total}</span>
                  <span className="mt-2 flex gap-1.5" aria-label={`${REGIONS.filter((r) => clearedBoss(r.table)).length}개 지역 정복, 전체 ${REGIONS.length}개 지역`}>
                    {REGIONS.map((r) => <span key={r.table} aria-hidden="true" className={`h-1.5 min-w-0 max-w-4 flex-1 rounded-full ${clearedBoss(r.table) ? 'bg-emerald-600 dark:bg-emerald-400' : 'bg-border'}`} />)}
                  </span>
                </span>
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-surface-2 text-text transition-transform group-hover:translate-x-0.5"><ArrowUpRight aria-hidden="true" className="h-4 w-4" /></span>
              </span>
            </button>

            <div className="grid grid-cols-2 gap-3">
              {QUICK_MODES.map((id) => {
                const m = MODES[id];
                const Icon = MODE_ICONS[id];
                const tint = MODE_TINT[id];
                const best = m.scored ? state.bestScores[id] ?? 0 : 0;
                return (
                  <motion.button key={id} type="button" whileTap={{ scale: 0.98 }} onClick={() => start(id, null)} className="group flex min-w-0 flex-col items-start rounded-2xl border border-border bg-surface p-4 text-left transition-colors hover:bg-surface-2">
                    <span className="mb-3 flex w-full items-center justify-between gap-2">
                      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}><Icon aria-hidden="true" className="h-5 w-5" /></span>
                      <ArrowUpRight aria-hidden="true" className="h-4 w-4 shrink-0 text-text-muted transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                    </span>
                    <span className="block text-sm font-extrabold text-text">{m.name}</span>
                    <span className="mt-1 block text-xs leading-relaxed text-text-muted">{m.tagline}</span>
                    {m.scored && best > 0 && <span className="num mt-2 inline-flex items-center gap-1 text-xs font-bold text-text"><Check aria-hidden="true" className="h-3 w-3" /> 최고 {best}점</span>}
                  </motion.button>
                );
              })}
            </div>
          </div>
        </section>

        <Link href="/learn" className="my-5 flex min-h-16 items-center justify-between gap-3 rounded-2xl border border-dashed border-border px-5 py-4 text-sm font-bold text-text transition-colors hover:bg-surface">
          <span className="flex items-center gap-2"><BookOpen aria-hidden="true" className="h-4 w-4 shrink-0 text-accent" /> 단을 골라 차근차근 연습하기</span>
          <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0 text-text-muted" />
        </Link>
      </div>
    </MotionConfig>
  );
}
