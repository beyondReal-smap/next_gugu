"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ArrowUpRight, ChevronDown, ChevronUp, Heart, Pause, Play, RotateCcw, Rows3, Trophy } from 'lucide-react';
import { useReducedMotion } from 'framer-motion';
import { useAppActive } from '@/lib/native/useAppActive';
import { useWebTrial } from '@/lib/state/WebTrialProvider';
import { MAX_TABLE, MIN_TABLE } from '@/lib/problems';
import {
  advanceLaneRunner, createLaneRunner, JUDGE_X, laneLevel, laneSpeed, leveledUp, moveLane, SCORE_PER_LEVEL, setLane as setLaneState,
  type LaneRunnerState,
} from '@/lib/laneRunner';
import { LaneScene } from './LaneScene';
import { useLaneInput } from './useLaneInput';

const TABLES = Array.from({ length: MAX_TABLE - MIN_TABLE + 1 }, (_, i) => MIN_TABLE + i);
const BEST_KEY = 'gugu.lane.best.v1';
const LANE_NAMES = ['위', '가운데', '아래'] as const;

export function LaneRunnerScreen() {
  const { tryPlay } = useWebTrial();
  const [table, setTable] = useState<number | null>(null);
  const [game, setGame] = useState<LaneRunnerState>(() => createLaneRunner(null, 'ready'));
  const gameRef = useRef(game);
  const [bests, setBests] = useState<Record<string, number>>({});
  const [storageAvailable, setStorageAvailable] = useState(true);
  const bestsRef = useRef<Record<string, number>>({});
  const appActive = useAppActive();
  const reducedMotion = useReducedMotion() ?? false;
  const actionRef = useRef<HTMLButtonElement>(null);
  const stageRef = useRef<HTMLElement>(null);

  const update = useCallback((next: LaneRunnerState) => {
    gameRef.current = next;
    setGame(next);
  }, []);

  useEffect(() => {
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(BEST_KEY) ?? '{}');
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        const valid = Object.fromEntries(Object.entries(parsed).filter(([key, value]) =>
          (key === 'all' || TABLES.some((item) => String(item) === key)) && Number.isSafeInteger(value) && value >= 0,
        ));
        bestsRef.current = valid;
        setBests(valid);
      }
    } catch {
      setStorageAvailable(false);
    }
  }, []);

  useEffect(() => {
    const key = String(game.table ?? 'all');
    if (game.score <= (bestsRef.current[key] ?? 0)) return;
    const next = { ...bestsRef.current, [key]: game.score };
    bestsRef.current = next;
    setBests(next);
    try {
      localStorage.setItem(BEST_KEY, JSON.stringify(next));
      setStorageAvailable(true);
    } catch {
      setStorageAvailable(false);
    }
  }, [game.score, game.table]);

  const getState = useCallback(() => gameRef.current, []);

  const pause = useCallback(() => {
    const current = gameRef.current;
    if (current.phase === 'running') update({ ...current, phase: 'paused' });
  }, [update]);

  const togglePause = useCallback(() => {
    const current = gameRef.current;
    if (current.phase === 'running' || current.phase === 'paused') {
      update({ ...current, phase: current.phase === 'running' ? 'paused' : 'running' });
    }
  }, [update]);

  const move = useCallback((delta: -1 | 1) => update(moveLane(gameRef.current, delta)), [update]);
  const setLane = useCallback((lane: number) => update(setLaneState(gameRef.current, lane)), [update]);
  const swipe = useLaneInput({ getState, move, setLane, togglePause, pause });

  useEffect(() => {
    if (!appActive) pause();
  }, [appActive, pause]);

  useEffect(() => {
    if (game.phase !== 'running') return;
    let frame: number;
    let previous: number | null = null;
    const tick = (now: number) => {
      if (document.hidden) {
        pause();
        return;
      }
      if (gameRef.current.phase !== 'running') return;
      // 복귀 직후나 긴 프레임 때문에 장애물이 한 번에 통과하지 않도록 제한합니다.
      const dt = previous === null ? 0 : Math.min(now - previous, 80);
      previous = now;
      update(advanceLaneRunner(gameRef.current, dt));
      if (gameRef.current.phase === 'running') frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [game.phase, pause, update]);

  useEffect(() => {
    if (game.phase === 'paused' || game.phase === 'over') actionRef.current?.focus();
  }, [game.phase]);

  const start = () => {
    if (!tryPlay()) return; // 웹 체험판 판 수 차감
    update(createLaneRunner(table));
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    if (window.matchMedia('(max-height: 740px)').matches) {
      requestAnimationFrame(() => stageRef.current?.scrollIntoView({ block: 'start' }));
    }
  };
  const ready = game.phase === 'ready';
  const over = game.phase === 'over';
  const paused = game.phase === 'paused';
  const active = game.phase === 'running';
  const compactHeader = active || paused ? '[@media(max-height:740px)]:hidden' : '';
  const best = bests[String(table ?? 'all')] ?? 0;
  const runBest = bests[String(game.table ?? 'all')] ?? 0;
  const question = game.question;
  const gate = game.gate;
  // 판정 뒤에도 게이트가 화면을 벗어날 때까지는 문제와 결과를 보여 줍니다.
  const quiz = question !== null && gate !== null;
  const correctAnswer = question ? question.a * question.b : null;
  const reveal = game.outcome === 'correct' || game.outcome === 'wrong';
  const remaining = gate ? Math.max(0, Math.min(1, (gate.x - JUDGE_X) / (gate.startX - JUDGE_X))) : 0;
  const seconds = gate ? (Math.max(0, gate.x - JUDGE_X) / laneSpeed(game.score) / 1000).toFixed(1) : '0.0';
  const feedback = game.outcome === 'correct' ? (leveledUp(game) ? `정답! 이제 속도 ${laneLevel(game.score) + 1}단계로 빨라져요.` : `정답! ${correctAnswer} 길로 통과했어요.`)
    : game.outcome === 'wrong' && question ? `아쉬워요! ${question.a} × ${question.b} = ${correctAnswer}`
      : game.outcome === 'hit' ? '쿵! 장애물에 부딪혔어요.'
        : quiz ? '정답 숫자가 있는 길로 옮겨요!'
          : '위아래로 길을 옮겨 장애물을 피해요.';
  const laneValues = quiz && gate ? gate.values : null;
  const laneSummary = laneValues ? laneValues.map((value, lane) => `${LANE_NAMES[lane]} ${value}`).join(', ') : '';

  return (
    <div className="mx-auto max-w-5xl break-keep px-4 pb-5 pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-8 sm:pt-8">
      <header className={`mb-5 flex items-center justify-between gap-3 ${compactHeader}`}>
        <Link href="/play/" className="flex min-h-11 items-center gap-2 text-sm font-bold text-text-muted hover:text-text"><ArrowLeft aria-hidden="true" className="h-4 w-4" /> 홈으로</Link>
        <span className="flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-bold text-text-muted"><Rows3 aria-hidden="true" className="h-3.5 w-3.5" /> 길을 바꾸며 배우는 구구단</span>
      </header>

      <div className={`mb-5 flex flex-wrap items-end justify-between gap-3 ${compactHeader}`}>
        <div>
          <p className="mb-1 text-xs font-extrabold tracking-wide text-accent">정답 길로, 쏙!</p>
          <h1 className="text-3xl font-extrabold tracking-tight text-text sm:text-4xl">구구 레인</h1>
        </div>
        <p className="text-sm leading-relaxed text-text-muted">장애물은 피하고<br className="hidden sm:block" /> 정답이 적힌 길로 달려요.</p>
      </div>

      <section ref={stageRef} aria-label="구구 레인 게임" className="overflow-hidden rounded-3xl border border-[#294e3d] bg-[#153f35] shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-1 px-3 py-3 text-[#f3f6e8] sm:gap-3 sm:px-6 sm:py-4">
          <div className="flex items-center gap-2 sm:gap-7">
            <div><span className="block text-[11px] font-bold text-[#c1d4c2]">통과한 정답 길</span><span className="num text-2xl font-extrabold" data-lane-score="true">{game.score}<span className="ml-1 text-xs text-[#c1d4c2]">개</span></span></div>
            <div><span className="flex items-center gap-1 text-[11px] font-bold text-[#c1d4c2]"><Trophy aria-hidden="true" className="h-3 w-3" /> {table === null ? '전체' : `${table}단`} 최고</span><span className="num text-2xl font-extrabold text-[#dbef9e]">{best}<span className="ml-1 text-xs">개</span></span></div>
          </div>
          <div className="flex items-center gap-2">
            <div role="img" aria-label={`남은 하트 ${game.lives}개`} className="flex gap-1">
              {[1, 2, 3].map((heart) => <Heart key={heart} aria-hidden="true" className={`h-[18px] w-[18px] ${heart <= game.lives ? 'fill-[#f3a88c] text-[#f3a88c]' : 'text-[#6c897b]'}`} />)}
            </div>
            <button type="button" onClick={togglePause} disabled={ready || over} aria-label={paused ? '계속 달리기' : '일시정지'} className="flex h-[44px] w-[44px] items-center justify-center rounded-xl border border-white/20 hover:bg-white/10 disabled:opacity-30">
              {paused ? <Play aria-hidden="true" className="h-4 w-4" /> : <Pause aria-hidden="true" className="h-4 w-4" />}
            </button>
          </div>
        </div>

        <div
          {...swipe}
          data-lane-stage="true"
          className={`relative aspect-[8/5] min-h-[180px] w-full sm:aspect-[2/1] select-none bg-[#bde5e6] ${active || paused ? 'touch-none' : ''}`}
        >
          <div className="pointer-events-none absolute inset-x-4 top-3 flex items-center justify-between gap-2 text-[11px] font-extrabold text-[#486146] sm:inset-x-6">
            <span>{ready ? '오늘의 작은 모험' : `${Math.floor(game.distance)}m 달리는 중`}</span>
            <span data-lane-speed={laneLevel(game.score) + 1}>{game.combo >= 2 ? `${game.combo}연속 정답 길! · ` : ''}속도 {laneLevel(game.score) + 1}단계</span>
          </div>
          <LaneScene game={game} reducedMotion={reducedMotion} />
          {(paused || over) && (
            <div className="absolute inset-0 flex items-center justify-center bg-[#eff3df]/85 px-4 text-center backdrop-blur-sm">
              <div>
                <p className="text-2xl font-extrabold text-[#153f35]">{paused ? '잠깐 쉬어가요' : '멋진 달리기였어요!'}</p>
                <p className="mt-2 text-sm font-bold text-[#486146]">{paused ? '준비되면 이어서 달려요.' : `정답 길 ${game.score}개 · 피한 장애물 ${game.dodged}개 · 최고 ${game.maxCombo}연속`}</p>
              </div>
            </div>
          )}
        </div>

        <div className="bg-surface px-4 py-4 sm:px-6 sm:py-6">
          {ready || over ? (
            <div>
              {over ? (
                <div role="status" className="mb-5 text-center">
                  <h2 className="text-lg font-extrabold text-text">{game.score > 0 && game.score >= runBest ? '나의 최고 기록을 달성했어요!' : '한 번 더, 더 멀리 가볼까요?'}</h2>
                  <p className="mt-1.5 text-sm text-text-muted">정답 길을 {game.score}개 지났어요. 정답을 익히면 다음엔 더 멀리!</p>
                </div>
              ) : (
                <div className="mb-5 flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"><Rows3 aria-hidden="true" className="h-5 w-5" /></span>
                  <div><h2 className="font-extrabold text-text">길을 직접 바꾸며 달려요!</h2><p className="mt-1 text-sm leading-relaxed text-text-muted">위아래로 길을 옮겨 장애물을 피하고, 문제가 나오면 정답 숫자가 적힌 길로 들어가요. 부딪히거나 틀리면 하트가 하나 줄어요.</p></div>
                </div>
              )}
              <fieldset>
                <legend className="mb-2.5 text-xs font-extrabold text-text-muted">달리면서 연습할 단</legend>
                <div className="grid grid-cols-5 gap-2 sm:grid-cols-9">
                  {[null, ...TABLES].map((value) => <button key={value ?? 'all'} type="button" aria-pressed={table === value} onClick={() => setTable(value)} className={`min-h-11 rounded-xl border px-2 text-sm font-extrabold transition-colors ${table === value ? 'border-[#153f35] bg-[#153f35] text-[#e6f5c1] dark:border-[#b9d783]' : 'border-border bg-surface text-text hover:bg-surface-2'}`}>{value === null ? '전체' : `${value}단`}</button>)}
                </div>
              </fieldset>
              <button ref={actionRef} type="button" onClick={start} className="mt-5 flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#dbef9e] px-4 py-3 text-base font-extrabold text-[#153f35] transition-colors hover:bg-[#cde587]">
                {over ? <RotateCcw aria-hidden="true" className="h-4 w-4" /> : <Play aria-hidden="true" className="h-4 w-4" fill="currentColor" />}{over ? '다시 달리기' : '달리기 시작'}
              </button>
            </div>
          ) : paused ? (
            <div className="py-2 text-center">
              <p className="mb-5 text-sm text-text-muted">장애물과 남은 시간이 멈춰 있어요.</p>
              <button ref={actionRef} type="button" onClick={togglePause} className="flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#dbef9e] px-4 py-3 font-extrabold text-[#153f35] hover:bg-[#cde587]"><Play aria-hidden="true" className="h-4 w-4" fill="currentColor" /> 이어서 달리기</button>
              <Link href="/learn" className="mt-3 inline-flex min-h-11 items-center gap-1 text-sm font-bold text-text-muted">학습 모드로 돌아가기 <ArrowUpRight aria-hidden="true" className="h-4 w-4" /></Link>
            </div>
          ) : (
            <div>
              <div className="mb-3 flex items-center justify-between gap-2 text-xs font-bold text-text-muted">
                <span>{table === null ? '전체 구구단' : `${table}단`} · {quiz ? '정답 길 찾기' : '장애물 피하기'}</span>
                <span className="num">{quiz ? (reveal ? (game.outcome === 'correct' ? '통과!' : '정답을 기억해요') : `${seconds}초`) : `피한 장애물 ${game.dodged}개`}</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true"><div className={`h-full rounded-full ${remaining < 0.25 && !reveal ? 'bg-orange-500' : 'bg-emerald-600 dark:bg-emerald-400'}`} style={{ width: `${quiz ? remaining * 100 : 0}%` }} /></div>
              <div className="py-3 text-center sm:py-4" aria-live="polite" aria-atomic="true">
                {quiz && question ? (
                  <h2 className="num text-4xl font-extrabold tracking-tight text-text sm:text-5xl" aria-label={`${question.a} 곱하기 ${question.b}는? ${laneSummary}`}><span>{question.a}</span><span className="mx-2 text-text-muted">×</span><span>{question.b}</span><span className="mx-2 text-text-muted">=</span><span className={game.outcome === 'correct' ? 'text-emerald-700 dark:text-emerald-300' : 'text-text-muted'}>{reveal ? correctAnswer : '?'}</span></h2>
                ) : (
                  <h2 className="flex min-h-10 items-center justify-center text-xl font-extrabold text-text sm:min-h-12 sm:text-2xl">장애물을 피해요!</h2>
                )}
                <p className={`mt-2 text-sm font-bold ${game.outcome === 'wrong' || game.outcome === 'hit' ? 'text-orange-700 dark:text-orange-300' : 'text-text-muted'}`}>{feedback}</p>
              </div>
              <div className="grid grid-cols-[1fr_auto] gap-2.5 sm:gap-3">
                <ol aria-label="길 지도" className="grid gap-1.5">
                  {LANE_NAMES.map((name, lane) => {
                    const value = laneValues ? laneValues[lane] : null;
                    const current = lane === game.lane;
                    const correct = reveal && value === correctAnswer;
                    const wrong = reveal && value === game.given && value !== correctAnswer;
                    return (
                      <li key={name} aria-current={current ? 'location' : undefined} data-lane-row={lane} className={`flex min-h-12 items-center gap-3 rounded-xl border-2 px-3 ${correct ? 'border-emerald-600 bg-emerald-500/10' : wrong ? 'border-orange-600 bg-orange-500/10' : current ? 'border-[#153f35] bg-[#dbef9e]/40 dark:border-[#b9d783]' : 'border-border bg-surface'} ${reveal && !correct && !wrong ? 'opacity-40' : ''}`}>
                        <span className="w-10 text-xs font-extrabold text-text-muted">{name}</span>
                        <span className="num flex-1 text-2xl font-extrabold text-text">{value ?? <span className="text-base text-text-muted">·</span>}</span>
                        {current && <span className="text-[11px] font-extrabold text-[#2b6a4f] dark:text-[#cfe79a]">공룡</span>}
                      </li>
                    );
                  })}
                </ol>
                <div className="grid w-20 grid-rows-2 gap-1.5 sm:w-24">
                  <button type="button" onClick={() => move(-1)} disabled={!active || game.lane === 0} aria-label="위 길로 이동" aria-keyshortcuts="ArrowUp" className="flex touch-manipulation items-center justify-center rounded-2xl border-2 border-border bg-surface text-text transition-colors disabled:opacity-35 [@media(hover:hover)]:enabled:hover:bg-surface-2"><ChevronUp aria-hidden="true" className="h-8 w-8" /></button>
                  <button type="button" onClick={() => move(1)} disabled={!active || game.lane === 2} aria-label="아래 길로 이동" aria-keyshortcuts="ArrowDown" className="flex touch-manipulation items-center justify-center rounded-2xl border-2 border-border bg-surface text-text transition-colors disabled:opacity-35 [@media(hover:hover)]:enabled:hover:bg-surface-2"><ChevronDown aria-hidden="true" className="h-8 w-8" /></button>
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-1 text-xs leading-relaxed text-text-muted">
        <p>화면을 위아래로 밀기 · ▲▼ 버튼 · 방향키 ↑↓ · 숫자키 1 · 2 · 3 <span className="mx-1.5 text-border">/</span> Esc 일시정지</p>
        <p>{storageAvailable ? '단별 최고 기록은 이 기기에 저장돼요.' : '기록을 저장하지 못했어요. 이번 화면에서만 유지돼요.'}</p>
      </div>
      <p className="mt-2 px-1 text-xs leading-relaxed text-text-muted">정답 길 {SCORE_PER_LEVEL}개를 지날 때마다 속도가 한 단계 빨라져요. 화면을 벗어나면 자동으로 일시정지해요.</p>
    </div>
  );
}
