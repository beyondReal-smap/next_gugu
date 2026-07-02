"use client";
import React, { useEffect, useRef, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Flame, Heart, Check } from 'lucide-react';
import { GameMode, Problem, AnswerRecord, SessionResult, CommitResult } from '@/lib/types';
import { pickProblem, problemKey, makeStatement, Statement } from '@/lib/problems';
import { MODES } from '@/lib/modes';
import { useGame } from '@/lib/state/GameProvider';
import { triggerHapticFeedback, HAPTIC_TYPES } from '@/src/utils/hapticFeedback';
import * as sound from '@/lib/sound';
import { Keypad } from './Keypad';
import { OxPad } from './OxPad';
import { ResultScreen } from './ResultScreen';

function perfNow(): number {
  return typeof performance !== 'undefined' ? performance.now() : 0;
}

interface SessionScreenProps {
  mode: GameMode;
  table: number | null;
  onExit: () => void;
}

// 경과 시간 표시 (스피드런) — 본체와 분리해 200ms 리렌더가 세션 화면 전체를 다시 그리지 않게 함
function SpeedTimer({ startRef }: { startRef: React.MutableRefObject<number> }) {
  const [t, setT] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setT(perfNow() - startRef.current), 200);
    return () => clearInterval(id);
  }, [startRef]);
  return <span className="num w-14 text-right text-sm font-bold text-text-muted">{(t / 1000).toFixed(1)}s</span>;
}

// 남은 시간 바+숫자 (60초 챌린지) — 만료 시 onExpire 1회 호출. 리렌더 격리 목적으로 분리
function CountdownTimer({
  startRef,
  limitMs,
  onExpire,
}: {
  startRef: React.MutableRefObject<number>;
  limitMs: number;
  onExpire: () => void;
}) {
  const [left, setLeft] = useState(limitMs);
  useEffect(() => {
    const id = setInterval(() => {
      const l = Math.max(0, limitMs - (perfNow() - startRef.current));
      setLeft(l);
      if (l <= 0) {
        clearInterval(id);
        onExpire();
      }
    }, 100);
    return () => clearInterval(id);
  }, [startRef, limitMs, onExpire]);

  const sec = Math.ceil(left / 1000);
  const urgent = sec <= 10;
  return (
    <>
      <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-surface-2">
        <div
          className={`h-full rounded-full transition-[width] duration-100 ease-linear ${urgent ? 'bg-danger' : 'bg-accent'}`}
          style={{ width: `${(left / limitMs) * 100}%` }}
        />
      </div>
      <span className={`num w-12 text-right text-sm font-bold ${urgent ? 'text-danger animate-pulse' : 'text-text-muted'}`}>
        {sec}s
      </span>
    </>
  );
}

// 목숨 표시 (서바이벌)
function Hearts({ lives, max }: { lives: number; max: number }) {
  return (
    <div className="flex items-center gap-1" aria-label={`남은 목숨 ${lives}/${max}`}>
      {Array.from({ length: max }, (_, i) => {
        const alive = i < lives;
        return (
          <motion.span key={i} animate={{ scale: alive ? 1 : 0.8, opacity: alive ? 1 : 0.3 }}>
            <Heart className={`h-5 w-5 ${alive ? 'text-danger' : 'text-text-muted'}`} fill={alive ? 'currentColor' : 'none'} />
          </motion.span>
        );
      })}
    </div>
  );
}

export function SessionScreen({ mode, table, onExit }: SessionScreenProps) {
  const { state, commitSession } = useGame();
  const def = MODES[mode];

  // 첫 문제/문장은 렌더 전에 1회만 생성 (문제와 OX 문장의 짝 보장)
  const initRef = useRef<{ p: Problem; st: Statement | null } | null>(null);
  if (initRef.current === null) {
    const p = pickProblem({ table, wrongPool: state.wrongPool, recentKeys: [] });
    initRef.current = { p, st: mode === 'truefalse' ? makeStatement(p) : null };
  }

  // 표시용 상태 (최소)
  const [problem, setProblemState] = useState<Problem>(initRef.current.p);
  const [statement, setStatementState] = useState<Statement | null>(initRef.current.st);
  const [input, setInputState] = useState('');
  const [combo, setCombo] = useState(0);
  const [score, setScore] = useState(0); // 정답 수 (timed/lives 표시용)
  const [lives, setLives] = useState(def.lives ?? 0);
  const [feedback, setFeedback] = useState<null | 'correct' | 'wrong'>(null);
  const [idx, setIdx] = useState(0);
  const [done, setDone] = useState<{ result: SessionResult; commit: CommitResult } | null>(null);

  // 진행 상태 ref (closure 안정 / 리렌더 무관)
  const wrongPoolRef = useRef(state.wrongPool);
  const problemRef = useRef<Problem>(problem);
  const statementRef = useRef<Statement | null>(statement);
  const inputRef = useRef('');
  const comboRef = useRef(0);
  const scoreRef = useRef(0);
  const livesRef = useRef(def.lives ?? 0);
  const idxRef = useRef(0);
  const lockRef = useRef(false);
  const doneRef = useRef(false);
  const answersRef = useRef<AnswerRecord[]>([]);
  const wrongListRef = useRef<Problem[]>([]);
  const queueRef = useRef<Problem[]>([]); // 오답 재도전 큐
  const recentRef = useRef<string[]>([]);
  const maxComboRef = useRef(0);
  const qStartRef = useRef(0);
  const sessionStartRef = useRef(0);
  const sessionIdRef = useRef(0); // 재시작 후 이전 세션의 지연 타이머 무효화
  doneRef.current = !!done;

  // 세션 시작 시각
  useEffect(() => {
    sessionStartRef.current = perfNow();
    qStartRef.current = perfNow();
  }, []);

  // 상태+ref 동기 헬퍼
  const setProblem = (p: Problem) => { problemRef.current = p; setProblemState(p); };
  const setStatement = (st: Statement | null) => { statementRef.current = st; setStatementState(st); };
  const setInput = (v: string) => { inputRef.current = v; setInputState(v); };

  // 기대 정답: 빈칸 추리는 곱하는 수(b), 그 외는 곱셈 결과
  const expectedFor = (p: Problem) => (mode === 'missing' ? p.b : p.a * p.b);

  // 진행 함수들을 ref에 매 렌더 갱신 → setTimeout/이벤트가 항상 최신 로직 호출 (stale closure 방지)
  const fns = useRef({
    goNext: () => {},
    finish: () => {},
    submit: (_v: string) => {},
    submitOX: (_c: boolean) => {},
    resolve: (_correct: boolean) => {},
  });

  fns.current.goNext = () => {
    recentRef.current = [...recentRef.current, problemKey(problemRef.current.a, problemRef.current.b)].slice(-4);
    // 오답 재도전 큐 우선 소진 → 이후 가중 랜덤
    const queued = queueRef.current.shift();
    const p = queued ?? pickProblem({ table, wrongPool: wrongPoolRef.current, recentKeys: recentRef.current });
    setProblem(p);
    setStatement(mode === 'truefalse' ? makeStatement(p) : null);
    setInput('');
    setFeedback(null);
    qStartRef.current = perfNow();
    lockRef.current = false;
  };

  fns.current.finish = () => {
    if (doneRef.current) return;
    doneRef.current = true;
    const result: SessionResult = {
      mode,
      table,
      answers: answersRef.current,
      maxCombo: maxComboRef.current,
      durationMs: Math.round(perfNow() - sessionStartRef.current),
    };
    const commit = commitSession(result);
    setDone({ result, commit });
  };

  // 정오답 공통 처리 (키패드/OX 공용)
  fns.current.resolve = (correct: boolean) => {
    const prob = problemRef.current;
    const ms = Math.round(perfNow() - qStartRef.current);
    answersRef.current.push({ a: prob.a, b: prob.b, correct, ms });

    if (correct) {
      const c = comboRef.current + 1;
      comboRef.current = c;
      setCombo(c);
      if (c > maxComboRef.current) maxComboRef.current = c;
      scoreRef.current += 1;
      setScore(scoreRef.current);
      setFeedback('correct');
      sound.playCorrect();
      if (c >= 3) sound.playCombo(c);
      triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
    } else {
      comboRef.current = 0;
      setCombo(0);
      wrongListRef.current.push({ a: prob.a, b: prob.b });
      setFeedback('wrong');
      sound.playWrong();
      triggerHapticFeedback(HAPTIC_TYPES.ERROR);
      if (def.kind === 'lives') {
        livesRef.current -= 1;
        setLives(livesRef.current);
      }
    }

    const last = def.kind === 'fixed' && idxRef.current + 1 >= def.total;
    const outOfLives = def.kind === 'lives' && livesRef.current <= 0;
    const sid = sessionIdRef.current;
    window.setTimeout(() => {
      if (doneRef.current || sid !== sessionIdRef.current) return;
      if (last || outOfLives) {
        fns.current.finish();
      } else {
        idxRef.current += 1;
        setIdx(idxRef.current);
        fns.current.goNext();
      }
    }, correct ? 380 : 1050);
  };

  fns.current.submit = (value: string) => {
    if (!value) { lockRef.current = false; return; }
    const parsed = parseInt(value, 10);
    if (isNaN(parsed)) { lockRef.current = false; return; }
    lockRef.current = true;
    fns.current.resolve(parsed === expectedFor(problemRef.current));
  };

  fns.current.submitOX = (choice: boolean) => {
    if (lockRef.current || doneRef.current) return;
    const st = statementRef.current;
    if (!st) return;
    lockRef.current = true;
    fns.current.resolve(choice === st.isTrue);
  };

  // 안정적 입력 핸들러 (빈 deps + ref 참조)
  const handleInput = useCallback((n: number) => {
    if (lockRef.current || doneRef.current) return;
    const cur = inputRef.current;
    if (cur.length >= 3) return;
    const next = cur + String(n);
    setInput(next);
    const ans = mode === 'missing' ? problemRef.current.b : problemRef.current.a * problemRef.current.b;
    if (next.length >= String(ans).length) {
      lockRef.current = true; // 즉시 잠금 — 중복/추가 입력 방지
      window.setTimeout(() => fns.current.submit(next), 90);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = useCallback(() => {
    if (lockRef.current || doneRef.current) return;
    setInput(inputRef.current.slice(0, -1));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleManualSubmit = useCallback(() => {
    if (lockRef.current || doneRef.current || !inputRef.current) return;
    lockRef.current = true;
    fns.current.submit(inputRef.current);
  }, []);

  const handleOX = useCallback((choice: boolean) => {
    fns.current.submitOX(choice);
  }, []);

  const handleExpire = useCallback(() => {
    fns.current.finish();
  }, []);

  // 물리 키보드 (한 번만 등록 — 핸들러 안정)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (doneRef.current) return;
      if (mode === 'truefalse') {
        if (e.key === 'o' || e.key === 'O' || e.key === 'ArrowLeft') handleOX(true);
        else if (e.key === 'x' || e.key === 'X' || e.key === 'ArrowRight') handleOX(false);
        return;
      }
      if (e.key >= '0' && e.key <= '9') handleInput(parseInt(e.key, 10));
      else if (e.key === 'Backspace') handleDelete();
      else if (e.key === 'Enter') handleManualSubmit();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mode, handleInput, handleDelete, handleManualSubmit, handleOX]);

  // 결과 화면
  if (done) {
    const wrongCount = wrongListRef.current.length;
    const restart = (retryWrong: boolean) => {
      const retained = retryWrong ? [...wrongListRef.current] : [];
      queueRef.current = retained.slice(1); // 첫 문제 제외한 나머지는 큐로
      answersRef.current = [];
      wrongListRef.current = [];
      recentRef.current = [];
      maxComboRef.current = 0;
      comboRef.current = 0;
      scoreRef.current = 0;
      livesRef.current = def.lives ?? 0;
      idxRef.current = 0;
      lockRef.current = false;
      doneRef.current = false;
      sessionIdRef.current += 1;
      wrongPoolRef.current = state.wrongPool;
      sessionStartRef.current = perfNow();
      qStartRef.current = perfNow();
      const first = retained[0] ?? pickProblem({ table, wrongPool: wrongPoolRef.current, recentKeys: [] });
      setProblem(first);
      setStatement(mode === 'truefalse' ? makeStatement(first) : null);
      setInput('');
      setCombo(0);
      setScore(0);
      setLives(livesRef.current);
      setFeedback(null);
      setIdx(0);
      setDone(null);
    };
    return (
      <div className="h-full overflow-y-auto">
        <ResultScreen
          result={done.result}
          commit={done.commit}
          wrongCount={wrongCount}
          onAgain={() => restart(false)}
          onRetryWrong={() => restart(true)}
          onClose={onExit}
        />
      </div>
    );
  }

  const answer = problem.a * problem.b;
  const showAnswer = feedback === 'wrong';

  return (
    <div className="flex h-full flex-col px-5 pt-4 pb-[calc(env(safe-area-inset-bottom)+1.25rem)]">
      {/* 상단 바 — 모드별 진행 위젯 */}
      <div className="mb-4 flex items-center gap-3">
        <button onClick={onExit} aria-label="세션 종료" className="shrink-0 text-text-muted hover:text-text">
          <X className="h-6 w-6" />
        </button>

        {def.kind === 'fixed' && (
          <>
            <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-surface-2">
              <motion.div
                className="h-full rounded-full bg-accent"
                animate={{ width: `${(idx / def.total) * 100}%` }}
                transition={{ type: 'spring', stiffness: 120, damping: 20 }}
              />
            </div>
            {mode === 'timeAttack'
              ? <SpeedTimer startRef={sessionStartRef} />
              : <span className="num w-10 text-right text-sm font-bold text-text-muted">{idx + 1}/{def.total}</span>}
          </>
        )}

        {def.kind === 'timed' && def.timeLimitMs != null && (
          <CountdownTimer startRef={sessionStartRef} limitMs={def.timeLimitMs} onExpire={handleExpire} />
        )}

        {def.kind === 'lives' && (
          <>
            <div className="flex-1" />
            <Hearts lives={lives} max={def.lives ?? 3} />
          </>
        )}
      </div>

      {/* 점수(무제한 모드) + 콤보 배지 */}
      <div className="relative mb-2 flex h-7 items-center justify-center">
        {def.kind !== 'fixed' && (
          <div className="absolute left-0 flex items-center gap-1 rounded-full bg-surface-2 px-2.5 py-1 text-sm font-extrabold text-text">
            <Check className="h-4 w-4 text-success" strokeWidth={3} />
            <span className="num">{score}</span>
          </div>
        )}
        <AnimatePresence>
          {combo >= 2 && (
            <motion.div
              key={combo}
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex items-center gap-1 rounded-full bg-danger/15 px-3 py-1 text-sm font-bold text-danger"
            >
              <Flame className="h-4 w-4" fill="currentColor" /> {combo} 콤보
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* 문제 */}
      <div className="flex flex-1 flex-col items-center justify-center">
        <motion.div
          key={`${problem.a}-${problem.b}-${idx}`}
          initial={{ scale: 0.9, opacity: 0, y: 8 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 22 }}
          className={`num flex items-baseline gap-3 text-6xl font-extrabold ${feedback === 'wrong' ? 'animate-shake' : feedback === 'correct' ? 'animate-pop' : ''}`}
        >
          {mode === 'truefalse' && statement ? (
            <>
              <span className="text-text">{problem.a}</span>
              <span className="text-text-muted">×</span>
              <span className="text-text">{problem.b}</span>
              <span className="text-text-muted">=</span>
              <span className={feedback === 'correct' ? 'text-success' : feedback === 'wrong' ? 'text-danger' : 'text-text'}>
                {statement.shown}
              </span>
            </>
          ) : mode === 'missing' ? (
            <>
              <span className="text-text">{problem.a}</span>
              <span className="text-text-muted">×</span>
              <span className={showAnswer ? 'text-success' : input ? 'text-accent' : 'text-border'}>
                {showAnswer ? problem.b : input || '?'}
              </span>
              <span className="text-text-muted">=</span>
              <span className="text-text">{answer}</span>
            </>
          ) : (
            <>
              <span className="text-text">{problem.a}</span>
              <span className="text-text-muted">×</span>
              <span className="text-text">{problem.b}</span>
              <span className="text-text-muted">=</span>
              <span className={showAnswer ? 'text-success' : input ? 'text-accent' : 'text-border'}>
                {showAnswer ? answer : input || '?'}
              </span>
            </>
          )}
        </motion.div>
        {showAnswer && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mt-3 text-sm font-bold text-text-muted">
            {mode === 'truefalse' && statement
              ? statement.isTrue
                ? `맞는 식이었어요 — ${problem.a} × ${problem.b} = ${answer}`
                : `${problem.a} × ${problem.b} = ${answer} — 틀린 식이에요`
              : mode === 'missing'
                ? `빈칸은 ${problem.b} — ${problem.a} × ${problem.b} = ${answer}`
                : `정답은 ${answer} 이에요`}
          </motion.div>
        )}
      </div>

      {/* 입력 패드 */}
      {mode === 'truefalse' ? (
        <OxPad onAnswer={handleOX} />
      ) : (
        <Keypad
          onInput={handleInput}
          onDelete={handleDelete}
          onSubmit={handleManualSubmit}
          canSubmit={!!input}
        />
      )}
    </div>
  );
}
