"use client";

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ArrowUpRight, Check, Footprints, Heart, Pause, Play, RotateCcw, Trophy, X } from 'lucide-react';
import { useReducedMotion } from 'framer-motion';
import { useAppActive } from '@/lib/native/useAppActive';
import { useWebTrial } from '@/lib/state/WebTrialProvider';
import { MAX_TABLE, MIN_TABLE } from '@/lib/problems';
import {
  advanceRunner, answerRunner, answerWindowMs, COLLISION_X, createRunner,
  JUMP_END, OBSTACLE_START, runnerJump, runnerLevel, runnerLeveledUp, RUNNER_SCORE_PER_LEVEL, type RunnerState,
} from '@/lib/runner';
import { RunnerArtwork, RunnerBackdrop, RunnerDino, RunnerObstacle } from './art';

const TABLES = Array.from({ length: MAX_TABLE - MIN_TABLE + 1 }, (_, i) => MIN_TABLE + i);
const BEST_KEY = 'gugu.runner.best.v1';

function RunnerScene({ game, reducedMotion }: { game: RunnerState; reducedMotion: boolean }) {
  const id = useId().replace(/:/g, '');
  const jump = runnerJump(game);
  const travel = reducedMotion ? 0 : game.distance * 20;
  const walking = !reducedMotion && game.phase !== 'ready' && game.phase !== 'over' && game.hitMs === 0 && jump === 0;
  const stride = walking ? Math.sin(travel / 18) : 0;
  const bob = walking ? Math.abs(stride) * 1.5 : 0;
  const obstacleX = game.phase === 'ready' ? 450 : game.obstacleX;
  const hurt = game.hitMs > 0 || game.phase === 'over';
  const tilt = reducedMotion ? 0 : jump > 0 ? (obstacleX > COLLISION_X ? -7 : 5) * Math.min(1, jump / 30) : 0;
  const impact = !reducedMotion && game.hitMs > 0 ? Math.sin((1400 - game.hitMs) / 45) * (game.hitMs / 1400) * 3 : 0;
  const landing = game.outcome === 'correct' && obstacleX < JUMP_END ? Math.min(1, (JUMP_END - obstacleX) / 65) : 0;
  const reward = game.combo > 0 && game.outcome === null ? Math.max(0, 1 - (OBSTACLE_START - obstacleX) / 65) : 0;

  return (
    <svg viewBox="0 0 720 260" preserveAspectRatio="xMinYMax slice" className="h-full w-full" aria-hidden="true">
      <RunnerArtwork id={id} />
      <RunnerBackdrop id={id} travel={travel} />
      <path d="M0 220q160-8 360 0t360 0v12H0Z" fill="#b4ca80" />
      <path d="M0 221h720v39H0Z" fill={`url(#${id}-soil)`} />
      <g data-runner-layer="trail" transform={`translate(${-(travel % 120)} 0)`}>
        {Array.from({ length: 7 }, (_, i) => <use key={i} href={`#${id}-trail`} x={i * 120} />)}
      </g>
      <path d="M0 257h720" stroke="#a98362" strokeWidth="6" opacity="0.25" />

      {!reducedMotion && walking && (
        <g fill="#f7e2b6" opacity="0.65">
          {[0, 1, 2].map((i) => {
            const age = ((travel + i * 16) % 48) / 48;
            return <ellipse key={i} cx={112 - age * 37} cy={220 - Math.sin(age * Math.PI) * 5} rx={2 + age * 4} ry={1 + age * 2} opacity={1 - age} />;
          })}
        </g>
      )}
      <ellipse cx="133" cy="223" rx={24 - jump / 9} ry={4 - jump / 60} fill="#355f45" opacity={0.23 - jump / 850} />
      <g data-runner-character="true" transform={`translate(${104 + impact} ${157 - jump - bob})`}>
        <RunnerDino id={id} stride={stride} airborne={jump > 0} hurt={hurt} tilt={tilt} />
      </g>

      <g transform={`translate(${obstacleX} 0)`} strokeLinecap="round" strokeLinejoin="round">
        <RunnerObstacle id={id} kind={game.round % 2 === 0 ? 'rock' : 'stump'} />
      </g>

      {!reducedMotion && jump > 15 && <g strokeLinecap="round"><path d={`M96 ${198 - jump}H81m9 8H71`} stroke="#fff6ce" strokeWidth="3" /><use href={`#${id}-sparkle`} transform={`translate(102 ${178 - jump}) rotate(${jump}) scale(.7)`} fill="#ffe49a" /><use href={`#${id}-sparkle`} transform={`translate(174 ${163 - jump}) scale(.5)`} fill="#fff9db" /></g>}
      {!reducedMotion && landing > 0 && <g data-runner-effect="landing" fill="#f5dfb5">{[-2, -1, 1, 2].map((i) => <ellipse key={i} cx={133 + i * landing * 17} cy={223 - Math.sin(landing * Math.PI) * (7 + Math.abs(i) * 2)} rx={3 + landing * 4} ry={2 + landing * 2} opacity={(1 - landing) * 0.8} />)}</g>}
      {reward > 0 && !reducedMotion && <text x="143" y={150 - (1 - reward) * 25} fill="#316649" stroke="#fff9db" strokeWidth="3" paintOrder="stroke" fontSize="17" fontWeight="900" textAnchor="middle" opacity={reward}>+1</text>}
      {reward > 0 && runnerLeveledUp(game) && <text data-runner-speedup="true" x="360" y={105 - (1 - reward) * 12} fill="#c2562b" stroke="#fff9db" strokeWidth="5" paintOrder="stroke" fontSize="34" fontWeight="900" textAnchor="middle" opacity={reducedMotion ? 1 : reward}>속도 UP!</text>}
      {game.hitMs > 0 && <g fill="#f8d170" stroke="#fff7d6" strokeWidth="0.8">{[-1, 0, 1].map((i) => <use key={i} href={`#${id}-sparkle`} transform={`translate(${136 + i * 19} ${143 - (i === 0 ? 8 : 0)}) rotate(${reducedMotion ? 0 : (1400 - game.hitMs) / 8}) scale(.65)`} />)}</g>}
    </svg>
  );
}

export function RunnerScreen() {
  const { tryPlay } = useWebTrial();
  const [table, setTable] = useState<number | null>(null);
  const [game, setGame] = useState<RunnerState>(() => createRunner(null, 'ready'));
  const gameRef = useRef(game);
  const [bests, setBests] = useState<Record<string, number>>({});
  const [storageAvailable, setStorageAvailable] = useState(true);
  const bestsRef = useRef<Record<string, number>>({});
  const appActive = useAppActive();
  const reducedMotion = useReducedMotion() ?? false;
  const actionRef = useRef<HTMLButtonElement>(null);
  const stageRef = useRef<HTMLElement>(null);

  const update = useCallback((next: RunnerState) => {
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

  const answer = useCallback((index: number) => {
    const current = gameRef.current;
    const choice = current.question.choices[index];
    if (choice !== undefined) update(answerRunner(current, choice));
  }, [update]);

  useEffect(() => {
    if (!appActive) pause();
  }, [appActive, pause]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.repeat || event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.target instanceof HTMLElement && (event.target.isContentEditable || /^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName))) return;
      if (/^[123]$/.test(event.key) && gameRef.current.phase === 'running') {
        event.preventDefault();
        answer(Number(event.key) - 1);
      } else if (event.code === 'Space' && (gameRef.current.phase === 'running' || gameRef.current.phase === 'paused')) {
        // 버튼에 포커스가 있으면 기본 키보드 클릭을 유지합니다.
        if (event.target instanceof HTMLElement && event.target.closest('button, a')) return;
        event.preventDefault();
        togglePause();
      } else if (event.key === 'Escape') {
        pause();
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('blur', pause);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('blur', pause);
    };
  }, [answer, pause, togglePause]);

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
      update(advanceRunner(gameRef.current, dt));
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
    update(createRunner(table));
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
  const correctAnswer = game.question.a * game.question.b;
  const remaining = Math.max(0, Math.min(1, (game.obstacleX - COLLISION_X) / (OBSTACLE_START - COLLISION_X)));
  const seconds = (remaining * answerWindowMs(game.score) / 1000).toFixed(1);
  const feedback = game.outcome === 'correct' ? '정답! 장애물을 넘어요.'
    : game.outcome === 'wrong' ? `아쉬워요! ${game.question.a} × ${game.question.b} = ${correctAnswer}`
      : game.outcome === 'missed' ? `시간이 다 됐어요. ${game.question.a} × ${game.question.b} = ${correctAnswer}`
        : '장애물이 오기 전에 정답을 골라요!';

  return (
    <div className="mx-auto max-w-5xl break-keep px-4 pb-5 pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-8 sm:pt-8">
      <header className={`mb-5 flex items-center justify-between gap-3 ${compactHeader}`}>
        <Link href="/play/" className="flex min-h-11 items-center gap-2 text-sm font-bold text-text-muted hover:text-text"><ArrowLeft aria-hidden="true" className="h-4 w-4" /> 홈으로</Link>
        <span className="flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-bold text-text-muted"><Footprints aria-hidden="true" className="h-3.5 w-3.5" /> 달리며 배우는 구구단</span>
      </header>

      <div className={`mb-5 flex flex-wrap items-end justify-between gap-3 ${compactHeader}`}>
        <div>
          <p className="mb-1 text-xs font-extrabold tracking-wide text-accent">정답을 고르면, 폴짝!</p>
          <h1 className="text-3xl font-extrabold tracking-tight text-text sm:text-4xl">구구 점프</h1>
        </div>
        <p className="text-sm leading-relaxed text-text-muted">작은 공룡과 함께<br className="hidden sm:block" /> 내 기록 너머로 달려요.</p>
      </div>

      <section ref={stageRef} aria-label="구구 점프 게임" className="overflow-hidden rounded-3xl border border-[#294e3d] bg-[#153f35] shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-1 px-3 py-3 text-[#f3f6e8] sm:gap-3 sm:px-6 sm:py-4">
          <div className="flex items-center gap-2 sm:gap-7">
            <div><span className="block text-[11px] font-bold text-[#c1d4c2]">넘은 장애물</span><span className="num text-2xl font-extrabold" data-runner-score="true">{game.score}<span className="ml-1 text-xs text-[#c1d4c2]">개</span></span></div>
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

        <div className="relative aspect-[36/13] min-h-[140px] w-full bg-[#bde5e6]">
          <div className="absolute inset-x-4 top-3 flex items-center justify-between gap-2 text-[11px] font-extrabold text-[#486146] sm:inset-x-6">
            <span>{ready ? '오늘의 작은 모험' : `${Math.floor(game.distance)}m 달리는 중`}</span>
            <span data-runner-speed={runnerLevel(game.score) + 1}>{game.combo >= 2 ? `${game.combo}연속 성공! · ` : ''}속도 {runnerLevel(game.score) + 1}단계</span>
          </div>
          <RunnerScene game={game} reducedMotion={reducedMotion} />
          {(paused || over) && (
            <div className="absolute inset-0 flex items-center justify-center bg-[#eff3df]/85 px-4 text-center backdrop-blur-sm">
              <div>
                <p className="text-2xl font-extrabold text-[#153f35]">{paused ? '잠깐 쉬어가요' : '멋진 달리기였어요!'}</p>
                <p className="mt-2 text-sm font-bold text-[#486146]">{paused ? '준비되면 이어서 달려요.' : `장애물 ${game.score}개 통과 · 최고 ${game.maxCombo}연속 성공`}</p>
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
                  <p className="mt-1.5 text-sm text-text-muted">{game.score}개를 넘었어요. 정답을 익히면 다음엔 더 멀리!</p>
                </div>
              ) : (
                <div className="mb-5 flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"><Footprints aria-hidden="true" className="h-5 w-5" /></span>
                  <div><h2 className="font-extrabold text-text">정답을 맞히면 자동으로 점프!</h2><p className="mt-1 text-sm leading-relaxed text-text-muted">보기 3개 중 정답을 골라요. 틀리거나 시간이 지나면 하트가 하나 줄어요.</p></div>
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
                <span>{table === null ? '전체 구구단' : `${table}단`} · {game.round + 1}번째 장애물</span>
                <span className="num">{game.outcome === null ? `${seconds}초` : game.outcome === 'correct' ? '점프!' : '정답을 기억해요'}</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true"><div className={`h-full rounded-full ${remaining < 0.25 && game.outcome === null ? 'bg-orange-500' : 'bg-emerald-600 dark:bg-emerald-400'}`} style={{ width: `${remaining * 100}%` }} /></div>
              <div className="py-3 text-center sm:py-5" aria-live="polite" aria-atomic="true">
                <h2 className="num text-4xl font-extrabold tracking-tight text-text sm:text-5xl" aria-label={`${game.question.a} 곱하기 ${game.question.b}는?`}><span>{game.question.a}</span><span className="mx-2 text-text-muted">×</span><span>{game.question.b}</span><span className="mx-2 text-text-muted">=</span><span className={game.outcome === 'correct' ? 'text-emerald-700 dark:text-emerald-300' : 'text-text-muted'}>{game.outcome ? correctAnswer : '?'}</span></h2>
                <p className={`mt-2 text-sm font-bold ${game.outcome === 'wrong' || game.outcome === 'missed' ? 'text-orange-700 dark:text-orange-300' : 'text-text-muted'}`}>{feedback}</p>
              </div>
              <div className="grid grid-cols-3 gap-2.5 sm:gap-3">
                {game.question.choices.map((choice, index) => {
                  const reveal = game.outcome !== null;
                  const correct = reveal && choice === correctAnswer;
                  const wrong = reveal && choice === game.given && choice !== correctAnswer;
                  return (
                    <button key={`${game.round}-${choice}`} type="button" onClick={() => answer(index)} disabled={!active || reveal} aria-label={`보기 ${index + 1}: ${choice}`} aria-keyshortcuts={String(index + 1)} className={`relative flex min-h-[72px] touch-manipulation items-center justify-center rounded-2xl border-2 px-3 py-2.5 transition-colors sm:min-h-24 ${correct ? 'border-emerald-600 bg-emerald-500/10 text-emerald-800 dark:text-emerald-200' : wrong ? 'border-orange-600 bg-orange-500/10 text-orange-800 dark:text-orange-200' : 'border-border bg-surface text-text [@media(hover:hover)]:enabled:hover:bg-surface-2'} ${reveal && !correct && !wrong ? 'opacity-40' : ''}`}>
                      <span className="absolute left-2.5 top-2 text-[10px] font-bold text-text-muted">{index + 1}</span><span className="num text-3xl font-extrabold sm:text-4xl">{choice}</span>{correct && <Check aria-hidden="true" className="absolute right-2 top-2 h-4 w-4" />}{wrong && <X aria-hidden="true" className="absolute right-2 top-2 h-4 w-4" />}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </section>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-1 text-xs leading-relaxed text-text-muted">
        <p>보기 터치 또는 숫자키 1 · 2 · 3 <span className="mx-1.5 text-border">/</span> Esc 일시정지</p>
        <p>{storageAvailable ? '단별 최고 기록은 이 기기에 저장돼요.' : '기록을 저장하지 못했어요. 이번 화면에서만 유지돼요.'}</p>
      </div>
      <p className="mt-2 px-1 text-xs leading-relaxed text-text-muted">장애물 {RUNNER_SCORE_PER_LEVEL}개를 넘을 때마다 속도가 한 단계 빨라져요. 화면을 벗어나면 자동으로 일시정지해요.</p>
    </div>
  );
}
