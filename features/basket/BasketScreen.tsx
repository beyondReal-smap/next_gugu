"use client";

import { useCallback, useEffect, useRef, useState, type PointerEvent } from 'react';
import Link from 'next/link';
import { ArrowLeft, ArrowRight, Heart, Pause, Play, RotateCcw, ShoppingBasket, Trophy } from 'lucide-react';
import { useReducedMotion } from 'framer-motion';
import { useAppActive } from '@/lib/native/useAppActive';
import { useWebTrial } from '@/lib/state/WebTrialProvider';
import { MAX_TABLE, MIN_TABLE } from '@/lib/problems';
import { initSound, playCorrect, playWrong, playCombo } from '@/lib/sound';
import { advanceBasket, basketLevel, BASKET_CORRECT_FEEDBACK_MS, BASKET_WIDTH, createBasket, fallDurationMs, FRUIT_CATCH_Y, FRUIT_START_Y, fruitX, moveBasket, type BasketState } from '@/lib/basket';

const TABLES = Array.from({ length: MAX_TABLE - MIN_TABLE + 1 }, (_, i) => MIN_TABLE + i);
const BEST_KEY = 'gugu.basket.best.v1';

function Orchard({ game, reducedMotion }: { game: BasketState; reducedMotion: boolean }) {
  const sceneRef = useRef<SVGSVGElement>(null);
  const [height, setHeight] = useState(380);
  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width > 0) setHeight(entry.contentRect.height / entry.contentRect.width * BASKET_WIDTH);
    });
    observer.observe(scene);
    return () => observer.disconnect();
  }, []);
  const groundOffset = height - 380;
  // 판정 후 점수가 올라가도 열매가 튀지 않도록, 판정한 높이에 고정합니다.
  const catchY = FRUIT_CATCH_Y + groundOffset;
  const y = game.outcome ? catchY : FRUIT_START_Y + (catchY - FRUIT_START_Y) * Math.min(1, game.elapsedMs / fallDurationMs(game.score));
  const celebrating = game.outcome === 'correct';
  const sad = game.outcome === 'wrong' || game.outcome === 'missed';
  const burst = celebrating ? 1 - game.feedbackMs / BASKET_CORRECT_FEEDBACK_MS : 0;

  return (
    <svg ref={sceneRef} viewBox={`0 0 360 ${height}`} className="absolute inset-0 h-full w-full" aria-hidden="true">
      <rect width="360" height={height} fill="#e6f4ec" />
      <circle cx="292" cy="57" r="30" fill="#ffdb78" />
      <g fill="#fffdf3" opacity="0.85"><path d="M23 80a16 16 0 0 1 26-15 22 22 0 0 1 41 10 12 12 0 0 1-2 24H25a10 10 0 0 1-2-19Z" /><path d="M222 130a13 13 0 0 1 23-11 18 18 0 0 1 33 7 10 10 0 0 1 0 20h-53a8 8 0 0 1-3-16Z" /></g>
      <g transform={`translate(0 ${groundOffset})`}>
        <path d="M0 286Q70 239 156 281T360 267V380H0Z" fill="#b5d7a4" />
        <path d="M0 322Q117 269 245 318T360 304V380H0Z" fill="#8fbd85" />
        <path d="M0 350Q140 310 360 352V380H0Z" fill="#daebbb" />
      </g>
      <path d={`M13 0v${height - 157}M346 0v${height - 159}`} stroke="#90734e" strokeWidth="17" />
      <path d="M13 87 48 63M346 71l-39-32" stroke="#90734e" strokeWidth="9" strokeLinecap="round" />
      <g fill="#548d61"><circle cx="0" cy="19" r="59" /><circle cx="64" cy="0" r="46" /><circle cx="316" cy="-2" r="55" /><circle cx="366" cy="35" r="53" /></g>
      <g fill="#77a875"><circle cx="-8" cy="63" r="34" /><circle cx="33" cy="24" r="34" /><circle cx="307" cy="9" r="32" /></g>
      {[32, 90, 266, 328].map((x, i) => <g key={x} transform={`translate(${x} ${height - 25 + i % 2 * 12})`}><path d="M0 0v-12" stroke="#548d61" strokeWidth="2" /><circle cy="-13" r="5" fill={i % 2 ? '#fff9de' : '#f4bd97'} /><circle cy="-13" r="2" fill="#d99139" /></g>)}

      <g data-basket-character="true" data-basket-x={game.x} transform={`translate(${game.x} ${groundOffset})`}>
        <ellipse cy="363" rx="38" ry="7" fill="#4f754d" opacity="0.18" />
        <ellipse cx="-13" cy="280" rx="8" ry="22" fill="#fff7e5" transform="rotate(-12 -13 280)" />
        <ellipse cx="13" cy="280" rx="8" ry="22" fill="#fff7e5" transform="rotate(12 13 280)" />
        <ellipse cx="-13" cy="279" rx="3.5" ry="14" fill="#efc0ad" transform="rotate(-12 -13 279)" />
        <ellipse cx="13" cy="279" rx="3.5" ry="14" fill="#efc0ad" transform="rotate(12 13 279)" />
        <ellipse cy="334" rx="24" ry="26" fill="#fff7e5" />
        <ellipse cy="308" rx="27" ry="23" fill="#fff7e5" />
        <ellipse cx="-18" cy="314" rx="5" ry="3" fill="#efb39f" /><ellipse cx="18" cy="314" rx="5" ry="3" fill="#efb39f" />
        {celebrating ? <path d="m-14 307 4-4 4 4m12 0 4-4 4 4" fill="none" stroke="#543d2b" strokeWidth="2.5" strokeLinecap="round" /> : <g fill="#543d2b"><ellipse cx="-10" cy="306" rx="2.5" ry="3.5" /><ellipse cx="10" cy="306" rx="2.5" ry="3.5" /></g>}
        <path d={sad ? 'M-5 320q5-6 10 0' : 'M-5 316q5 7 10 0'} fill="none" stroke="#543d2b" strokeWidth="2" strokeLinecap="round" />
        <ellipse cx="-16" cy="359" rx="11" ry="6" fill="#fff7e5" /><ellipse cx="16" cy="359" rx="11" ry="6" fill="#fff7e5" />
        <path d="M-25 332v-11a25 19 0 0 1 50 0v11" fill="none" stroke="#966137" strokeWidth="5" />
        <path d="m-35 329 7 27q28 9 56 0l7-27Z" fill="#c8914d" stroke="#8e5b32" strokeWidth="2" />
        <path d="M-29 338h58m-55 9h52m-39-15 3 25m10-25v28m13-28-3 25" stroke="#e6b973" strokeWidth="3" />
        <path d="M-35 329h70" stroke="#794b2a" strokeWidth="6" strokeLinecap="round" />
        <ellipse cx="-31" cy="328" rx="7" ry="5" fill="#fff7e5" /><ellipse cx="31" cy="328" rx="7" ry="5" fill="#fff7e5" />
      </g>

      {game.question.choices.map((value, index) => {
        const caught = game.caughtIndex === index && game.outcome !== null;
        const correct = value === game.question.a * game.question.b;
        const x = fruitX(index, game.elapsedMs);
        return (
          <g key={`${game.round}-${index}`} data-basket-fruit={value} data-fruit-x={x} transform={`translate(${x} ${y})`} opacity={game.outcome && !caught ? 0.35 : 1}>
            {caught && <circle r="31" fill="none" stroke={correct ? '#3d8256' : '#b36140'} strokeWidth="3" strokeDasharray="5 4" />}
            <path d="M0-21q-4-15 5-19" fill="none" stroke="#735237" strokeWidth="3" strokeLinecap="round" />
            <path d="M2-26q5-17 19-12-3 15-19 12" fill="#598e56" />
            <path d="M0-23c-32-13-37 22-15 43 9 8 11 3 15 3s8 5 16-3C38-3 30-36 0-23Z" fill={['#ffcc75', '#f6ad92', '#d5de87'][index]} stroke={['#d89a43', '#d48b73', '#a3b45c'][index]} strokeWidth="2" />
            <path d="M-16-12q-8 5-7 14" fill="none" stroke="#fff8dd" strokeWidth="4" strokeLinecap="round" opacity="0.8" />
            <text y="9" textAnchor="middle" fill="#553c27" fontSize="25" fontWeight="900" className="num">{value}</text>
          </g>
        );
      })}
      {celebrating && <g transform={`translate(0 ${groundOffset})`} fill="#fff5bc" stroke="#dca84a" strokeWidth="1.5">
        {[-2, -1, 0, 1, 2].map((i) => <path key={i} d="m0-8 2.5 5 5.5.8-4 4 .9 5.5L0 4.7l-4.9 2.6L-4 1.8-8-2.2l5.5-.8Z" transform={`translate(${game.x + i * (reducedMotion ? 17 : 15 + burst * 16)} ${260 - (reducedMotion ? 0 : Math.sin(burst * Math.PI) * 45) + Math.abs(i) * 9})`} />)}
      </g>}
    </svg>
  );
}

export function BasketScreen() {
  const { tryPlay } = useWebTrial();
  const [table, setTable] = useState<number | null>(2);
  const [game, setGame] = useState<BasketState>(() => createBasket(2, 'ready'));
  const gameRef = useRef(game);
  const directionRef = useRef(0);
  const dragRef = useRef<number | null>(null);
  const actionRef = useRef<HTMLButtonElement>(null);
  const [bests, setBests] = useState<Record<string, number>>({});
  const bestsRef = useRef<Record<string, number>>({});
  const [storageAvailable, setStorageAvailable] = useState(true);
  const appActive = useAppActive();
  const reducedMotion = useReducedMotion() ?? false;

  const update = useCallback((next: BasketState) => {
    const previous = gameRef.current;
    gameRef.current = next;
    setGame(next);
    if (!previous.outcome && next.outcome) {
      if (next.outcome === 'correct') {
        playCorrect();
        if (next.combo >= 3) playCombo(next.combo);
      } else playWrong();
    }
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
    } catch { setStorageAvailable(false); }
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
    } catch { setStorageAvailable(false); }
  }, [game.score, game.table]);

  const pause = useCallback(() => {
    directionRef.current = 0;
    dragRef.current = null;
    if (gameRef.current.phase === 'running') update({ ...gameRef.current, phase: 'paused' });
  }, [update]);

  const resume = () => update({ ...gameRef.current, phase: 'running' });

  useEffect(() => { if (!appActive) pause(); }, [appActive, pause]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.target instanceof HTMLElement && (event.target.isContentEditable || /^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName))) return;
      if (event.key === 'Escape') { pause(); return; }
      if (gameRef.current.phase !== 'running') return;
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault();
        directionRef.current = event.key === 'ArrowLeft' ? -1 : 1;
      }
    };
    const release = () => { directionRef.current = 0; };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') release();
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('blur', pause);
    window.addEventListener('pointerup', release);
    window.addEventListener('pointercancel', release);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('blur', pause);
      window.removeEventListener('pointerup', release);
      window.removeEventListener('pointercancel', release);
    };
  }, [pause]);

  useEffect(() => {
    if (game.phase !== 'running') return;
    let frame: number;
    let previous: number | null = null;
    const tick = (now: number) => {
      if (document.hidden) { pause(); return; }
      if (gameRef.current.phase !== 'running') return;
      const dt = previous === null ? 0 : Math.min(80, now - previous);
      previous = now;
      const current = gameRef.current;
      update(advanceBasket(moveBasket(current, current.x + directionRef.current * dt * 0.28), dt));
      if (gameRef.current.phase === 'running') frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [game.phase, pause, update]);

  useEffect(() => {
    if (game.phase === 'paused' || game.phase === 'over') actionRef.current?.focus();
  }, [game.phase]);

  const start = () => {
    if (!tryPlay()) return;
    initSound();
    directionRef.current = 0;
    dragRef.current = null;
    update(createBasket(table));
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
  };
  const dragTo = (event: PointerEvent<HTMLDivElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    update(moveBasket(gameRef.current, (event.clientX - bounds.left) / bounds.width * BASKET_WIDTH));
  };
  const ready = game.phase === 'ready';
  const over = game.phase === 'over';
  const paused = game.phase === 'paused';
  const playing = !ready && !over;
  const best = bests[String((playing ? game.table : table) ?? 'all')] ?? 0;
  const answer = game.question.a * game.question.b;
  const feedback = game.outcome === 'correct'
    ? game.combo >= 3 ? `${game.combo}연속! 별이 반짝반짝!` : '쏙! 정답 열매를 받았어요!'
    : game.outcome === 'wrong' ? `괜찮아요! ${game.question.a} × ${game.question.b} = ${answer}`
      : game.outcome === 'missed' ? `다음엔 받아봐요! 정답은 ${answer}`
        : '정답 열매 아래로 바구니를 옮겨요!';

  return (
    <div className={playing ? 'fixed inset-x-0 top-0 z-50 h-dvh overflow-y-auto overscroll-contain bg-bg px-[max(0.75rem,env(safe-area-inset-left))] pt-[max(0.5rem,env(safe-area-inset-top))] pb-[max(0.5rem,env(safe-area-inset-bottom))]' : 'mx-auto max-w-3xl break-keep px-4 pb-5 pt-[max(1rem,env(safe-area-inset-top))] sm:px-8'}>
      {!playing && <header className="mb-4">
        <Link href="/play" className="mb-3 inline-flex min-h-11 items-center gap-2 text-sm font-bold text-text-muted"><ArrowLeft aria-hidden="true" className="h-4 w-4" /> 홈으로</Link>
        <p className="mb-1 text-xs font-extrabold text-orange-700 dark:text-orange-300">토끼의 작은 과수원</p>
        <h1 className="text-3xl font-extrabold text-text">구구 바구니</h1>
        <p className="mt-2 text-sm text-text-muted">바구니를 움직여 달콤한 정답을 모아요.</p>
      </header>}

      <section aria-label="구구 바구니 게임" className={`mx-auto flex w-full max-w-lg flex-col overflow-hidden rounded-3xl border border-[#794124]/20 bg-surface ${playing ? 'basket-live h-full min-h-[440px]' : ''}`}>
        <div className="basket-hud flex shrink-0 flex-wrap items-center justify-between gap-2 bg-[#794124] px-4 py-2 text-[#fff5db]">
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-1.5 text-sm font-extrabold"><ShoppingBasket aria-hidden="true" className="h-5 w-5" /><span className="num" data-basket-score="true">{game.score}</span>개</span>
            <span className="flex items-center gap-1 text-xs text-[#ffe1aa]"><Trophy aria-hidden="true" className="h-3.5 w-3.5" /><span className="num">{best}</span></span>
          </div>
          <div className="flex items-center gap-2">
            <span role="img" aria-label={`남은 하트 ${game.lives}개`} className="flex gap-1">{[1, 2, 3].map((n) => <Heart key={n} aria-hidden="true" className={`h-4 w-4 ${game.lives >= n ? 'fill-[#ffbd9b] text-[#ffbd9b]' : 'text-[#b0866c]'}`} />)}</span>
            {playing && <button type="button" onClick={paused ? resume : pause} aria-label={paused ? '게임 계속하기' : '일시정지'} className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/20 hover:bg-white/10">{paused ? <Play className="h-4 w-4" /> : <Pause className="h-4 w-4" />}</button>}
          </div>
        </div>

        {playing && <div className="basket-question shrink-0 px-3 py-2 text-center" aria-live="polite" aria-atomic="true">
          <p className="text-xs font-bold text-text-muted">{game.table === null ? '전체 구구단' : `${game.table}단`} · {basketLevel(game.score)}단계</p>
          <h2 className="num my-1 text-3xl font-extrabold text-text" data-basket-question="true">{game.question.a} × {game.question.b} = {game.outcome ? answer : '?'}</h2>
          <p className="text-xs font-bold text-text-muted">{feedback}</p>
        </div>}

        <div
          data-basket-stage="true"
          aria-label="바구니 이동 영역"
          className={`basket-stage relative min-h-[180px] w-full select-none ${playing ? 'flex-1 touch-none' : 'aspect-[6/5]'}`}
          onPointerDown={(event) => {
            if (!event.isPrimary || event.button !== 0 || gameRef.current.phase !== 'running') return;
            dragRef.current = event.pointerId;
            event.currentTarget.setPointerCapture(event.pointerId);
            dragTo(event);
          }}
          onPointerMove={(event) => { if (dragRef.current === event.pointerId) dragTo(event); }}
          onPointerUp={() => { dragRef.current = null; }}
          onPointerCancel={() => { dragRef.current = null; }}
          onLostPointerCapture={() => { dragRef.current = null; }}
        >
          <Orchard game={game} reducedMotion={reducedMotion} />
          {paused && <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-[#fff8e9]/95 p-4 text-center">
            <h2 className="text-xl font-extrabold text-[#794124]">잠깐 쉬어가요</h2>
            <button ref={actionRef} type="button" onClick={resume} className="min-h-12 rounded-2xl bg-[#794124] px-6 py-3 font-extrabold text-[#fff5db]">이어서 받기</button>
            <Link href="/play" className="inline-flex min-h-11 items-center px-4 text-sm font-bold text-[#794124]">홈으로 돌아가기</Link>
          </div>}
        </div>

        {playing ? <div className="basket-controls shrink-0 px-3 py-2">
          <div className="grid grid-cols-2 gap-3">
            {([-1, 1] as const).map((direction) => <button
              key={direction} type="button" disabled={paused} aria-label={direction < 0 ? '바구니 왼쪽으로' : '바구니 오른쪽으로'}
              onPointerDown={(event) => {
                if (!event.isPrimary || event.button !== 0) return;
                event.currentTarget.setPointerCapture(event.pointerId);
                directionRef.current = direction;
                update(moveBasket(gameRef.current, gameRef.current.x + direction * 24));
              }}
              onPointerUp={() => { directionRef.current = 0; }}
              onPointerCancel={() => { directionRef.current = 0; }}
              onLostPointerCapture={() => { directionRef.current = 0; }}
              onClick={(event) => {
                // 키보드·보조기기 클릭은 한 번에 옮기고, 터치는 누르는 동안 움직입니다.
                if (event.detail === 0) update(moveBasket(gameRef.current, gameRef.current.x + direction * 58));
              }}
              className="flex min-h-14 touch-none select-none items-center justify-center gap-2 rounded-2xl bg-[#f6e8c9] font-extrabold text-[#794124] active:bg-[#ecd19e] disabled:opacity-40"
            >{direction < 0 ? <ArrowLeft aria-hidden="true" className="h-6 w-6" /> : <ArrowRight aria-hidden="true" className="h-6 w-6" />}{direction < 0 ? '왼쪽' : '오른쪽'}</button>)}
          </div>
          <p className="mt-1.5 text-center text-[11px] text-text-muted">화면을 좌우로 끌거나 방향 버튼을 꾹 눌러요</p>
        </div> : <div className="p-4 sm:p-5">
          {over ? <div role="status" className="mb-4 text-center">
            <h2 className="text-xl font-extrabold text-text">열매 {game.score}개를 모았어요!</h2>
            <p className="mt-1 text-sm text-text-muted">최고 {game.maxCombo}연속 정답 · {game.score > 0 && game.score >= (bests[String(game.table ?? 'all')] ?? 0) ? '나의 최고 기록이에요!' : '토끼와 다시 놀아볼까요?'}</p>
          </div> : <div className="mb-4 text-sm leading-relaxed text-text-muted">
            <p className="font-extrabold text-text">정답 열매 아래로, 쏙!</p>
            <p className="mt-1">바구니를 직접 움직여 정답 열매를 받아요. 다른 열매를 받거나 놓치면 하트가 하나 줄어요. 처음에는 2단부터 천천히!</p>
          </div>}
          <fieldset>
            <legend className="mb-2 text-xs font-extrabold text-text-muted">연습할 단</legend>
            <div className="grid grid-cols-3 gap-2">{[null, ...TABLES].map((value) => <button key={value ?? 'all'} type="button" aria-pressed={table === value} onClick={() => setTable(value)} className={`min-h-11 rounded-xl border px-2 text-sm font-extrabold ${table === value ? 'border-[#794124] bg-[#794124] text-[#fff5db]' : 'border-border bg-surface text-text hover:bg-surface-2'}`}>{value === null ? '전체' : `${value}단`}</button>)}</div>
          </fieldset>
          <button ref={actionRef} type="button" onClick={start} className="mt-4 flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#f5ce7b] px-4 py-3 font-extrabold text-[#633a24] hover:bg-[#efc166]">{over ? <RotateCcw aria-hidden="true" className="h-5 w-5" /> : <Play aria-hidden="true" className="h-5 w-5" fill="currentColor" />}{over ? '다시 받기' : '열매 받기 시작'}</button>
          <p className="mt-3 text-center text-xs text-text-muted">하트 3개 · 정답 3개마다 조금씩 빨라져요</p>
          <p className="mt-1 text-center text-xs text-text-muted">컴퓨터에서는 ← → 방향키로 움직여요</p>
        </div>}
      </section>
      {!storageAvailable && <p role="status" className="mx-auto mt-2 max-w-lg text-xs text-text-muted">이 브라우저에서는 최고 기록을 저장하지 못했어요.</p>}
    </div>
  );
}
