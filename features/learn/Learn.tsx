"use client";
import React, { useState } from 'react';
import Link from 'next/link';
import { MotionConfig, motion } from 'framer-motion';
import { Check, ChevronRight, Footprints, Play, Shuffle, Star, Trophy } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODE_LIST, MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { Stars } from '@/components/ui/Stars';
import { MIN_TABLE, MAX_TABLE } from '@/lib/problems';

const TABLES = Array.from({ length: MAX_TABLE - MIN_TABLE + 1 }, (_, i) => MIN_TABLE + i);

export function Learn() {
  const { state } = useGame();
  const { start } = useSession();
  const [mode, setMode] = useState<GameMode>('practice');
  const def = MODES[mode];
  const ModeIcon = MODE_ICONS[mode];
  const tint = MODE_TINT[mode];
  const totalStars = TABLES.reduce((total, table) => total + (state.tableMastery[table]?.stars ?? 0), 0);
  const masteredTables = TABLES.filter((table) => state.tableMastery[table]?.stars === 3).length;

  return (
    <MotionConfig reducedMotion="user">
      <div className="mx-auto max-w-5xl break-keep px-5 pb-[env(safe-area-inset-bottom)] pt-[max(1.5rem,env(safe-area-inset-top))] sm:px-8 sm:pt-10">
        <header className="mb-6 flex flex-wrap items-start justify-between gap-3 sm:mb-8">
          <div>
            <p className="mb-2 text-xs font-extrabold tracking-wide text-accent">나에게 맞는 연습</p>
            <h1 className="text-2xl font-extrabold tracking-tight text-text sm:text-3xl">어떤 구구단을 풀어볼까요?</h1>
            <p className="mt-2 text-sm text-text-muted">모드를 고르고, 연습할 단을 눌러 시작해요.</p>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-2 text-sm font-bold text-text" aria-label={`모은 별 ${totalStars}개, 전체 ${TABLES.length * 3}개`}>
            <Star aria-hidden="true" className="h-4 w-4 text-amber-600 dark:text-amber-400" fill="currentColor" />
            <span className="num">{totalStars} / {TABLES.length * 3}</span>
          </span>
        </header>

        <div className="grid items-start gap-6 pb-5 lg:grid-cols-[0.85fr_1.3fr] lg:gap-8">
          <section aria-labelledby="mode-heading">
            <h2 id="mode-heading" className="mb-3 flex items-center gap-2 text-sm font-extrabold text-text"><span aria-hidden="true" className="num flex h-6 w-6 items-center justify-center rounded-full bg-surface-2 text-xs text-text-muted">1</span> 모드 선택</h2>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(6rem,1fr))] gap-2.5 lg:grid-cols-2">
              {MODE_LIST.map((m) => {
                const active = m.id === mode;
                const Icon = MODE_ICONS[m.id];
                const modeTint = MODE_TINT[m.id];
                return (
                  <motion.button key={m.id} type="button" whileTap={{ scale: 0.98 }} onClick={() => setMode(m.id)} aria-pressed={active} aria-controls="learning-options" aria-label={`${m.name}: ${m.tagline}`} className={`relative flex min-w-0 flex-col items-start rounded-2xl border p-3 text-left transition-colors sm:p-4 ${active ? 'border-accent bg-accent/10 ring-1 ring-accent' : 'border-border bg-surface hover:bg-surface-2'}`}>
                    <span className={`mb-2.5 flex h-9 w-9 items-center justify-center rounded-xl ${active ? 'bg-accent text-accent-fg' : `${modeTint.bg} ${modeTint.text}`}`}><Icon aria-hidden="true" className="h-5 w-5" /></span>
                    {active && <Check aria-hidden="true" className="absolute right-2.5 top-2.5 h-3.5 w-3.5 text-accent" />}
                    <span className="block text-sm font-extrabold text-text">{m.name}</span>
                    <span className="mt-1 hidden text-xs leading-relaxed text-text-muted lg:block">{m.tagline}</span>
                  </motion.button>
                );
              })}
            </div>
            <p className="mt-3 text-xs leading-relaxed text-text-muted">속도보다 정확하게, 처음이라면 학습 모드부터.</p>
            <Link href="/runner" className="mt-4 flex items-center gap-3 rounded-2xl border border-emerald-600/25 bg-emerald-500/5 p-4 transition-colors hover:bg-emerald-500/10">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#153f35] text-[#dbef9e]"><Footprints aria-hidden="true" className="h-5 w-5" /></span>
              <span className="min-w-0 flex-1"><span className="block text-sm font-extrabold text-text">구구 점프</span><span className="mt-1 block text-xs leading-relaxed text-text-muted">정답을 골라 장애물 넘기</span></span>
              <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-text-muted" />
            </Link>
          </section>

          <section id="learning-options" aria-labelledby="selection-heading" className="min-w-0">
            <h2 id="selection-heading" className="mb-3 flex items-center gap-2 text-sm font-extrabold text-text"><span aria-hidden="true" className="num flex h-6 w-6 items-center justify-center rounded-full bg-surface-2 text-xs text-text-muted">2</span> {def.supportsTable ? '연습할 단 선택' : '기록에 도전하기'}</h2>
            <div className="mb-4 rounded-2xl border border-border bg-surface p-4 sm:p-5">
              <div className="flex items-center gap-3">
                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}><ModeIcon aria-hidden="true" className="h-5 w-5" /></span>
                <div aria-live="polite" aria-atomic="true">
                  <h3 className="text-base font-extrabold text-text">{def.name}</h3>
                  <p className="mt-1 text-xs leading-relaxed text-text-muted">{def.detail}</p>
                </div>
              </div>
              {def.supportsTable && (
                <p className="mt-3 border-t border-border pt-3 text-xs font-bold text-accent">한 번에 {def.total}문제 · 단을 누르면 바로 시작해요</p>
              )}
            </div>

            {def.supportsTable ? (
              <div>
                <button type="button" onClick={() => start(mode, null)} className="mb-4 flex min-h-16 w-full items-center gap-3 rounded-2xl bg-accent px-4 py-4 text-left text-accent-fg transition-[filter] hover:brightness-110 sm:px-5">
                  <Shuffle aria-hidden="true" className="h-5 w-5 shrink-0" />
                  <span className="min-w-0 flex-1"><span className="block text-sm font-extrabold">전체 랜덤으로 시작</span><span className="mt-1 block text-xs">{MIN_TABLE}~{MAX_TABLE}단을 골고루 섞어서</span></span>
                  <ChevronRight aria-hidden="true" className="h-5 w-5 shrink-0" />
                </button>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-2">
                  {TABLES.map((table) => {
                    const mastery = state.tableMastery[table];
                    const stars = mastery?.stars ?? 0;
                    const mastered = stars === 3;
                    return (
                      <motion.button key={table} type="button" whileTap={{ scale: 0.98 }} onClick={() => start(mode, table)} aria-label={`${table}단 ${def.name} 시작, 별 ${stars}개, 최대 3개`} className={`group flex min-w-0 flex-col items-start rounded-2xl border p-4 text-left transition-colors ${mastered ? 'border-emerald-600/30 bg-emerald-500/5 hover:bg-emerald-500/10' : 'border-border bg-surface hover:bg-surface-2'}`}>
                        <span className="mb-3 flex w-full items-center justify-between gap-2"><span className="num text-3xl font-extrabold leading-none text-text">{table}<span className="ml-1 text-sm font-bold text-text-muted">단</span></span><ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-text-muted transition-transform group-hover:translate-x-0.5" /></span>
                        <span aria-hidden="true"><Stars value={stars} size={15} /></span>
                        <span className={`mt-2 text-xs font-bold ${mastered ? 'text-emerald-700 dark:text-emerald-300' : 'text-text-muted'}`}>{mastered ? '완벽하게 익혔어요' : mastery?.plays ? '조금씩 익히는 중' : '첫 별을 모아봐요'}</span>
                      </motion.button>
                    );
                  })}
                </div>
                <p className="mt-4 flex items-center gap-1.5 text-xs text-text-muted"><Star aria-hidden="true" className="h-3.5 w-3.5 shrink-0" /> {masteredTables > 0 ? `${TABLES.length}개 단 중 ${masteredTables}개를 완벽하게 익혔어요.` : '단별 연습을 마치고 나만의 별을 모아요.'}</p>
              </div>
            ) : (
              <div className="rounded-3xl border border-border bg-surface p-5 sm:p-6">
                <div className="flex items-center gap-2 text-sm font-bold text-text-muted"><Trophy aria-hidden="true" className="h-4 w-4 text-amber-600 dark:text-amber-400" /> 내 최고 기록</div>
                <div className={`num mb-2 mt-4 font-extrabold tracking-tight text-text ${(state.bestScores[mode] ?? 0) > 0 ? 'text-5xl' : 'text-xl'}`}>
                  {(state.bestScores[mode] ?? 0) > 0 ? <>{state.bestScores[mode]}<span className="ml-1 text-lg text-text-muted">점</span></> : '아직 기록이 없어요'}
                </div>
                <p className="mb-6 text-sm text-text-muted">{(state.bestScores[mode] ?? 0) > 0 ? '지난번의 나를 넘어볼까요?' : '첫 도전으로 나만의 기록을 만들어봐요.'}</p>
                <div className="mb-5 flex flex-wrap gap-2 text-xs font-bold text-text-muted"><span className="rounded-lg bg-surface-2 px-3 py-2">{MIN_TABLE}~{MAX_TABLE}단 전체 랜덤</span><span className="rounded-lg bg-surface-2 px-3 py-2">{def.kind === 'timed' ? `${(def.timeLimitMs ?? 0) / 1000}초 동안 도전` : `기회는 ${def.lives}번`}</span></div>
                <button type="button" onClick={() => start(mode, null)} className="flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-accent px-4 py-3 font-extrabold text-accent-fg transition-[filter] hover:brightness-110"><Play aria-hidden="true" className="h-4 w-4 shrink-0" fill="currentColor" /> {def.name} 시작</button>
              </div>
            )}
          </section>
        </div>
      </div>
    </MotionConfig>
  );
}
