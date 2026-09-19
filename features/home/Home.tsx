"use client";
import React from 'react';
import { MotionConfig, motion } from 'framer-motion';
import { ArrowUpRight, BookOpen, Check, ChevronRight, Flame, Footprints, Map as MapIcon, Play, RotateCcw, Rows3, Swords, Target, Trophy, type LucideIcon } from 'lucide-react';
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

// 홈의 플레이 모드 묶음 — 놀이 방식별로 모읍니다. 세션 모드는 전체 랜덤으로 바로 시작합니다.
const LEARN_MODES: GameMode[] = ['missing', 'truefalse'];
const RECORD_MODES: GameMode[] = ['timeAttack', 'challenge', 'survival'];

interface ModeGroupDef {
  id: string;
  title: string;
  desc: string;
  icon: LucideIcon;
  tint: string;
}

const MODE_GROUPS: [ModeGroupDef, ModeGroupDef, ModeGroupDef, ModeGroupDef] = [
  { id: 'modes-learn', title: '차근차근 배우기', desc: '시간 제한 없이 원리부터 익혀요', icon: BookOpen, tint: 'text-accent' },
  { id: 'modes-record', title: '기록 도전', desc: '속도와 집중력으로 최고 기록 경신', icon: Trophy, tint: 'text-amber-500' },
  { id: 'modes-run', title: '달리기 게임', desc: '공룡과 함께 달리며 정답 찾기', icon: Footprints, tint: 'text-emerald-600 dark:text-emerald-400' },
  { id: 'modes-adventure', title: '모험', desc: '3D 월드를 탐험하며 대결', icon: MapIcon, tint: 'text-indigo-500' },
];

function modeMeta(id: GameMode): string {
  const m = MODES[id];
  if (m.kind === 'timed' && m.timeLimitMs) return `${m.timeLimitMs / 1000}초 제한`;
  if (m.kind === 'lives' && m.lives) return `하트 ${m.lives}개`;
  return `${m.total}문제`;
}

function ModeGroup({ group, children }: { group: ModeGroupDef; children: React.ReactNode }) {
  const Icon = group.icon;
  return (
    <section id={group.id} aria-labelledby={`${group.id}-title`} className="scroll-mt-6 pt-5">
      <div className="mb-3 flex items-baseline gap-2">
        <h3 id={`${group.id}-title`} className="flex items-center gap-1.5 text-sm font-extrabold text-text"><Icon aria-hidden="true" className={`h-4 w-4 ${group.tint}`} /> {group.title}</h3>
        <p className="min-w-0 truncate text-xs text-text-muted">{group.desc}</p>
      </div>
      {children}
    </section>
  );
}

// 모바일은 한 줄 목록형, sm 이상은 카드형으로 보여 줍니다.
const MODE_CARD_CLASS = 'group flex min-h-16 min-w-0 items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3 text-left transition-colors hover:bg-surface-2 sm:min-h-28 sm:flex-col sm:items-start sm:gap-0 sm:p-4';

function ModeCardBody({ icon: Icon, tint, name, tagline, meta }: { icon: LucideIcon; tint: { text: string; bg: string }; name: string; tagline: string; meta: React.ReactNode }) {
  return (
    <>
      <span className="flex shrink-0 sm:mb-3 sm:w-full sm:items-center sm:justify-between sm:gap-2">
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}><Icon aria-hidden="true" className="h-5 w-5" /></span>
        <ArrowUpRight aria-hidden="true" className="hidden h-4 w-4 shrink-0 text-text-muted transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 sm:block" />
      </span>
      <span className="min-w-0 flex-1 sm:flex sm:w-full sm:flex-col">
        <span className="flex flex-wrap items-baseline gap-x-2 sm:block">
          <span className="text-sm font-extrabold text-text">{name}</span>
          <span className="text-xs leading-relaxed text-text-muted sm:mt-1 sm:block">{tagline}</span>
        </span>
        <span className="num mt-0.5 block text-[11px] font-bold text-text-muted sm:mt-auto sm:pt-3">{meta}</span>
      </span>
      <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 text-text-muted sm:hidden" />
    </>
  );
}

function SessionModeCard({ id, best = 0, onStart }: { id: GameMode; best?: number; onStart: () => void }) {
  const m = MODES[id];
  return (
    <motion.button type="button" whileTap={{ scale: 0.98 }} onClick={onStart} className={MODE_CARD_CLASS}>
      <ModeCardBody
        icon={MODE_ICONS[id]}
        tint={MODE_TINT[id]}
        name={m.name}
        tagline={m.tagline}
        meta={best > 0 ? <span className="inline-flex items-center gap-1 text-text"><Check aria-hidden="true" className="h-3 w-3" /> 최고 {best}점 · {modeMeta(id)}</span> : `${modeMeta(id)} · 전체 랜덤`}
      />
    </motion.button>
  );
}

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

        <section aria-labelledby="modes-heading" className="mt-10">
          <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 id="modes-heading" className="text-lg font-extrabold text-text">어떻게 놀아볼까요?</h2>
              <p className="mt-1 text-xs text-text-muted">배우기부터 달리기까지, 모두 구구단 연습이 돼요.</p>
            </div>
            <Link href="/learn" className="flex min-h-11 items-center gap-1 text-sm font-bold text-accent">단 골라서 하기 <ChevronRight aria-hidden="true" className="h-4 w-4" /></Link>
          </div>
          {/* 모바일에서 원하는 묶음으로 바로 이동 */}
          <nav aria-label="플레이 모드 묶음" className="-mx-5 mb-2 flex gap-2 overflow-x-auto px-5 pb-1 sm:mx-0 sm:px-0">
            {MODE_GROUPS.map((g) => (
              <a key={g.id} href={`#${g.id}`} className="flex min-h-10 shrink-0 items-center gap-1.5 rounded-full border border-border bg-surface px-3.5 text-xs font-extrabold text-text transition-colors hover:bg-surface-2">
                <g.icon aria-hidden="true" className={`h-3.5 w-3.5 ${g.tint}`} /> {g.title}
              </a>
            ))}
          </nav>

          <ModeGroup group={MODE_GROUPS[0]}>
            <div className="grid gap-2.5 sm:grid-cols-3 sm:gap-3">
              <Link href="/learn" className={MODE_CARD_CLASS}>
                <ModeCardBody icon={MODE_ICONS.practice} tint={MODE_TINT.practice} name="학습" tagline="원하는 단을 골라 또박또박" meta={`${MODES.practice.total}문제 · 단 선택`} />
              </Link>
              {LEARN_MODES.map((id) => <SessionModeCard key={id} id={id} onStart={() => start(id, null)} />)}
            </div>
          </ModeGroup>

          <ModeGroup group={MODE_GROUPS[1]}>
            <div className="grid gap-2.5 sm:grid-cols-3 sm:gap-3">
              {RECORD_MODES.map((id) => <SessionModeCard key={id} id={id} best={MODES[id].scored ? state.bestScores[id] ?? 0 : 0} onStart={() => start(id, null)} />)}
            </div>
          </ModeGroup>

          <ModeGroup group={MODE_GROUPS[2]}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Link href="/runner" className="group flex min-h-24 items-center gap-4 rounded-2xl border border-emerald-600/25 bg-emerald-500/5 px-5 py-4 transition-colors hover:bg-emerald-500/10">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#153f35] text-[#dbef9e]"><Footprints aria-hidden="true" className="h-6 w-6" /></span>
                <span className="min-w-0 flex-1"><span className="block text-base font-extrabold text-text">구구 점프</span><span className="mt-1 block text-sm text-text-muted">정답을 고르면 폴짝! 장애물을 넘어요.</span></span>
                <ArrowUpRight aria-hidden="true" className="h-5 w-5 shrink-0 text-emerald-700 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 dark:text-emerald-300" />
              </Link>
              <Link href="/lane-runner" className="group flex min-h-24 items-center gap-4 rounded-2xl border border-emerald-600/25 bg-emerald-500/5 px-5 py-4 transition-colors hover:bg-emerald-500/10">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#153f35] text-[#dbef9e]"><Rows3 aria-hidden="true" className="h-6 w-6" /></span>
                <span className="min-w-0 flex-1"><span className="flex items-center gap-2 text-base font-extrabold text-text">구구 레인 <span className="rounded-full bg-emerald-600/10 px-2 py-0.5 text-[10px] text-emerald-800 dark:text-emerald-200">새 모드</span></span><span className="mt-1 block text-sm text-text-muted">길을 바꿔 피하고, 정답 길로 쏙!</span></span>
                <ArrowUpRight aria-hidden="true" className="h-5 w-5 shrink-0 text-emerald-700 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 dark:text-emerald-300" />
              </Link>
            </div>
          </ModeGroup>

          <ModeGroup group={MODE_GROUPS[3]}>
            <button type="button" onClick={() => openAdventure()} className="group relative flex w-full flex-col gap-4 overflow-hidden rounded-3xl border border-border bg-surface p-5 text-left transition-colors hover:bg-surface-2 sm:flex-row sm:items-center sm:p-6">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-500/15 text-indigo-600 dark:text-indigo-300"><MapIcon aria-hidden="true" className="h-6 w-6" /></span>
              <span className="min-w-0 flex-1">
                <span className="block text-lg font-extrabold leading-snug text-text">구구단 너머, 새로운 모험으로</span>
                <span className="mt-1 block text-sm text-text-muted">3D 월드의 주민과 구구단으로 대결해요. {nextRegion ? `다음 모험 · ${nextRegion.name}` : '모든 지역 정복! 다시 대결해 볼까요?'}</span>
              </span>
              <span className="w-full shrink-0 sm:w-48">
                <span className="num block text-xs font-bold text-text-muted">대결 완료 {adv.defeated} / {adv.total}</span>
                <span className="mt-2 flex gap-1.5" aria-label={`${REGIONS.filter((r) => clearedBoss(r.table)).length}개 지역 정복, 전체 ${REGIONS.length}개 지역`}>
                  {REGIONS.map((r) => <span key={r.table} aria-hidden="true" className={`h-1.5 min-w-0 flex-1 rounded-full ${clearedBoss(r.table) ? 'bg-emerald-600 dark:bg-emerald-400' : 'bg-border'}`} />)}
                </span>
              </span>
              <ArrowUpRight aria-hidden="true" className="absolute right-5 top-5 h-5 w-5 text-text-muted transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 sm:static" />
            </button>
          </ModeGroup>
        </section>
      </div>
    </MotionConfig>
  );
}
