"use client";
// 배틀 화면 — 3종 방식 지원. 종료 시 기존 commitSession 파이프라인으로 XP/별점/업적 반영.
//  hp:      턴제 체력전 (정답=공격, 오답=피격) — 보스는 HP 50% 이하에서 분노(공격력 1.5배)
//  speed:   스피드 레이스 (NPC AI와 목표 정답 수 경주, 먼저 도달하면 승리)
//  counter: 반격전 (문제별 제한시간, 시간 초과=오답 취급 피격)
// 진행 로직은 SessionScreen과 동일하게 fns/ref 기반 (stale closure·중복 입력 방지)
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Flame, Swords, Sparkles, RotateCcw, Crown, Unlock } from 'lucide-react';
import { Problem, AnswerRecord, CommitResult } from '@/lib/types';
import { pickProblem, problemKey } from '@/lib/problems';
import { NpcDef } from '@/lib/adventure/types';
import {
  PLAYER_MAX_HP,
  damageFor,
  toSessionResult,
  RACE_TARGET,
  racePaceMs,
  counterLimitMs,
  BOSS_ENRAGE_RATIO,
  enragedAttack,
  BATTLE_STYLE_NAME,
} from '@/lib/adventure/battle';
import { regionFor } from '@/lib/adventure/world';
import { getAchievement } from '@/lib/achievements';
import { getAdvAchievement } from '@/lib/adventure/achievements';
import { useGame } from '@/lib/state/GameProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { triggerHapticFeedback, HAPTIC_TYPES } from '@/src/utils/hapticFeedback';
import * as sound from '@/lib/sound';
import { Keypad } from '@/features/session/Keypad';
import { Button } from '@/components/ui/Button';
import { Stars } from '@/components/ui/Stars';
import { Confetti } from '@/components/feedback/Confetti';
import { LevelUpOverlay } from '@/components/feedback/LevelUpOverlay';
import { AchievementToast } from '@/components/feedback/AchievementToast';

function perfNow(): number {
  return typeof performance !== 'undefined' ? performance.now() : 0;
}

// 코어 업적 + 어드벤처 전용 업적을 하나의 토스트로
function resolveAchievement(id: string) {
  return getAchievement(id) ?? getAdvAchievement(id);
}

type Phase = 'intro' | 'play' | 'end';

interface Floater {
  id: number;
  text: string;
}

interface BattleEnd {
  won: boolean;
  commit: CommitResult;
  advUnlocked: string[];
}

interface BattleScreenProps {
  npc: NpcDef;
  onWorld: () => void; // 월드로 복귀 (승패 무관)
  onRetry: () => void; // 다시 도전 (부모가 key 교체로 재마운트)
  onFlee: () => void;  // 중도 이탈 — 커밋 없음
}

// 배틀 아바타 — 월드 캐릭터와 같은 색/눈 언어
function Avatar({ color, size, boss, hit, enraged }: { color: string; size: number; boss?: boolean; hit: number; enraged?: boolean }) {
  return (
    <motion.span
      key={hit} // 피격마다 넉백 재생
      initial={hit > 0 ? { x: -10, rotate: -6 } : false}
      animate={{ x: 0, rotate: 0, scale: enraged ? 1.08 : 1 }}
      transition={{ type: 'spring', stiffness: 500, damping: 14 }}
      className={`relative flex shrink-0 items-center justify-center rounded-full ${enraged ? 'ring-2 ring-danger' : ''}`}
      style={{ backgroundColor: color, width: size, height: size }}
    >
      <span className="flex gap-[15%]">
        <span className="h-[18%] w-[18%] min-h-2 min-w-2 rounded-full bg-white shadow-[inset_0_0_0_2.5px_rgba(32,36,44,0.9)]" />
        <span className="h-[18%] w-[18%] min-h-2 min-w-2 rounded-full bg-white shadow-[inset_0_0_0_2.5px_rgba(32,36,44,0.9)]" />
      </span>
      {boss && <Crown className="absolute -top-3 h-6 w-6 text-warning" fill="currentColor" />}
    </motion.span>
  );
}

function HpBar({ hp, max, tone }: { hp: number; max: number; tone: 'npc' | 'player' }) {
  const pct = Math.max(0, (hp / max) * 100);
  const color = tone === 'player' ? 'bg-success' : pct > 40 ? 'bg-danger' : 'bg-warning';
  return (
    <div className="flex items-center gap-2">
      <div className="h-3 flex-1 overflow-hidden rounded-full bg-surface-2">
        <div className={`h-full rounded-full ${color} transition-[width] duration-300 ease-out`} style={{ width: `${pct}%` }} />
      </div>
      <span className="num w-16 text-right text-xs font-extrabold text-text-muted">
        {Math.max(0, hp)}/{max}
      </span>
    </div>
  );
}

// 스피드 레이스 진행 바
function RaceBar({ score, target, tone }: { score: number; target: number; tone: 'npc' | 'player' }) {
  const pct = Math.min(100, (score / target) * 100);
  return (
    <div className="flex items-center gap-2">
      <div className="h-3 flex-1 overflow-hidden rounded-full bg-surface-2">
        <div
          className={`h-full rounded-full ${tone === 'player' ? 'bg-accent' : 'bg-warning'} transition-[width] duration-300 ease-out`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="num w-16 text-right text-xs font-extrabold text-text-muted">
        {score}/{target}
      </span>
    </div>
  );
}

// 반격전 문제별 카운트다운 — 리렌더 격리를 위해 분리 (SessionScreen 타이머 교훈)
function QuestionTimer({ qKey, limitMs, onExpire }: { qKey: number; limitMs: number; onExpire: () => void }) {
  const [left, setLeft] = useState(limitMs);
  useEffect(() => {
    setLeft(limitMs);
    const start = perfNow();
    const id = setInterval(() => {
      const l = Math.max(0, limitMs - (perfNow() - start));
      setLeft(l);
      if (l <= 0) {
        clearInterval(id);
        onExpire();
      }
    }, 100);
    return () => clearInterval(id);
  }, [qKey, limitMs, onExpire]);

  const urgent = left <= 2000;
  return (
    <div className="mb-2 flex items-center gap-2 px-1">
      <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface-2">
        <div
          className={`h-full rounded-full transition-[width] duration-100 ease-linear ${urgent ? 'bg-danger' : 'bg-accent'}`}
          style={{ width: `${(left / limitMs) * 100}%` }}
        />
      </div>
      <span className={`num w-11 text-right text-xs font-extrabold ${urgent ? 'text-danger animate-pulse' : 'text-text-muted'}`}>
        {(left / 1000).toFixed(1)}s
      </span>
    </div>
  );
}

export function BattleScreen({ npc, onWorld, onRetry, onFlee }: BattleScreenProps) {
  const { state, commitSession } = useGame();
  const { recordBattle } = useAdventure();
  const isBoss = npc.kind === 'boss';
  const style = npc.battle;
  const counterLimit = counterLimitMs(npc.table);

  // 첫 문제는 렌더 전에 1회만 생성
  const initRef = useRef<Problem | null>(null);
  if (initRef.current === null) {
    initRef.current = pickProblem({ table: npc.table, wrongPool: state.wrongPool, recentKeys: [] });
  }

  const [phase, setPhase] = useState<Phase>('intro');
  const [problem, setProblemState] = useState<Problem>(initRef.current);
  const [input, setInputState] = useState('');
  const [combo, setCombo] = useState(0);
  const [qIdx, setQIdx] = useState(0);
  const [npcHp, setNpcHp] = useState(npc.hp);
  const [playerHp, setPlayerHp] = useState(PLAYER_MAX_HP);
  const [npcScore, setNpcScore] = useState(0);       // speed 전용
  const [playerScore, setPlayerScore] = useState(0); // speed 전용
  const [enraged, setEnraged] = useState(false);     // 보스 분노
  const [timedOut, setTimedOut] = useState(false);   // counter 시간초과 표시
  const [feedback, setFeedback] = useState<null | 'correct' | 'wrong'>(null);
  const [npcFloat, setNpcFloat] = useState<Floater | null>(null);
  const [playerFloat, setPlayerFloat] = useState<Floater | null>(null);
  const [npcHit, setNpcHit] = useState(0);
  const [playerHit, setPlayerHit] = useState(0);
  const [end, setEnd] = useState<BattleEnd | null>(null);
  const [confetti, setConfetti] = useState(0);
  const [levelUp, setLevelUp] = useState(false);

  // 진행 상태 ref
  const phaseRef = useRef<Phase>('intro');
  const problemRef = useRef<Problem>(problem);
  const inputRef = useRef('');
  const comboRef = useRef(0);
  const npcHpRef = useRef(npc.hp);
  const playerHpRef = useRef(PLAYER_MAX_HP);
  const npcScoreRef = useRef(0);
  const playerScoreRef = useRef(0);
  const enragedRef = useRef(false);
  const lockRef = useRef(false);
  const doneRef = useRef(false);
  const decidedRef = useRef(false); // 승패 확정 래치 — 피드백 딜레이 중 NPC 페이스 타이머의 승리 선점 방지
  const answersRef = useRef<AnswerRecord[]>([]);
  const recentRef = useRef<string[]>([]);
  const maxComboRef = useRef(0);
  const floatIdRef = useRef(0);
  const wrongPoolRef = useRef(state.wrongPool);
  const qStartRef = useRef(0);
  const battleStartRef = useRef(0);
  phaseRef.current = phase;

  const setProblem = (p: Problem) => { problemRef.current = p; setProblemState(p); };
  const setInput = (v: string) => { inputRef.current = v; setInputState(v); };

  // 인트로 → 전투 시작
  useEffect(() => {
    const t = window.setTimeout(() => {
      battleStartRef.current = perfNow();
      qStartRef.current = perfNow();
      setPhase('play');
    }, 1500);
    return () => window.clearTimeout(t);
  }, []);

  // 도망가기 계약(이탈 시 커밋 없음) 보장 — 언마운트 후 살아남은 지연 타이머의 finish/커밋 차단
  useEffect(() => () => { doneRef.current = true; }, []);

  const fns = useRef({
    goNext: () => {},
    finish: (_won: boolean) => {},
    resolve: (_correct: boolean, _ms: number) => {},
    submit: (_v: string) => {},
    timeout: () => {},
  });

  fns.current.goNext = () => {
    recentRef.current = [...recentRef.current, problemKey(problemRef.current.a, problemRef.current.b)].slice(-4);
    const p = pickProblem({ table: npc.table, wrongPool: wrongPoolRef.current, recentKeys: recentRef.current });
    setProblem(p);
    setInput('');
    setFeedback(null);
    setTimedOut(false);
    setQIdx((n) => n + 1);
    qStartRef.current = perfNow();
    lockRef.current = false;
  };

  fns.current.finish = (won: boolean) => {
    if (doneRef.current) return;
    doneRef.current = true;
    const result = toSessionResult(npc, answersRef.current, maxComboRef.current, Math.round(perfNow() - battleStartRef.current));
    const commit = commitSession(result);
    const advUnlocked = recordBattle(npc.id, won);
    setEnd({ won, commit, advUnlocked });
    setPhase('end');
    if (won) {
      // 보스 격파(지역 클리어)는 팡파레, 일반 승리는 완주음
      if (isBoss) sound.playLevelUp();
      else sound.playComplete();
      setConfetti((n) => n + 1);
      triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
    }
    if (commit.leveledUp) window.setTimeout(() => setLevelUp(true), 700);
  };

  // 정오답 공통 처리 — 방식별 분기
  fns.current.resolve = (correct: boolean, ms: number) => {
    const prob = problemRef.current;
    answersRef.current.push({ a: prob.a, b: prob.b, correct, ms });
    floatIdRef.current += 1;

    if (correct) {
      const c = comboRef.current + 1;
      comboRef.current = c;
      setCombo(c);
      if (c > maxComboRef.current) maxComboRef.current = c;
      setFeedback('correct');
      sound.playCorrect();
      if (c >= 3) sound.playCombo(c);
      triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);

      if (style === 'speed') {
        playerScoreRef.current += 1;
        setPlayerScore(playerScoreRef.current);
        setNpcHit((n) => n + 1); // 추격 연출
      } else {
        const dmg = damageFor(ms, c);
        npcHpRef.current = Math.max(0, npcHpRef.current - dmg);
        setNpcHp(npcHpRef.current);
        setNpcFloat({ id: floatIdRef.current, text: `-${dmg}` });
        setNpcHit((n) => n + 1);
        // 보스 분노 — HP 절반 이하로 처음 떨어지는 순간 (경고 큐는 정답 효과음과 겹치지 않게 지연)
        if (isBoss && !enragedRef.current && npcHpRef.current > 0 && npcHpRef.current <= npc.hp * BOSS_ENRAGE_RATIO) {
          enragedRef.current = true;
          setEnraged(true);
          window.setTimeout(() => {
            if (doneRef.current) return;
            sound.playWrong();
            triggerHapticFeedback(HAPTIC_TYPES.ERROR);
          }, 450);
        }
      }
    } else {
      comboRef.current = 0;
      setCombo(0);
      setFeedback('wrong');
      sound.playWrong();
      triggerHapticFeedback(HAPTIC_TYPES.ERROR);

      if (style !== 'speed') {
        const atk = isBoss && enragedRef.current ? enragedAttack(npc.attack) : npc.attack;
        playerHpRef.current = Math.max(0, playerHpRef.current - atk);
        setPlayerHp(playerHpRef.current);
        setPlayerFloat({ id: floatIdRef.current, text: `-${atk}` });
        setPlayerHit((n) => n + 1);
      }
    }

    const playerWon = style === 'speed' ? playerScoreRef.current >= RACE_TARGET : npcHpRef.current <= 0;
    const playerLost = style === 'speed' ? false : playerHpRef.current <= 0; // speed 패배는 NPC 페이스 타이머가 판정
    // "먼저 도달하면 승리" 보장: 판정은 동기로 확정하고, 연출 딜레이 후 finish만 지연 실행
    if (playerWon || playerLost) decidedRef.current = true;
    window.setTimeout(() => {
      if (doneRef.current) return;
      if (playerWon) fns.current.finish(true);
      else if (playerLost) fns.current.finish(false);
      else fns.current.goNext();
    }, correct ? 550 : 1250);
  };

  fns.current.submit = (value: string) => {
    if (!value) { lockRef.current = false; return; }
    const parsed = parseInt(value, 10);
    if (isNaN(parsed)) { lockRef.current = false; return; }
    lockRef.current = true;
    const ms = Math.round(perfNow() - qStartRef.current);
    fns.current.resolve(parsed === problemRef.current.a * problemRef.current.b, ms);
  };

  // 반격전 시간 초과 — 오답 취급 (정답 노출 + 피격)
  fns.current.timeout = () => {
    if (phaseRef.current !== 'play' || lockRef.current || doneRef.current) return;
    lockRef.current = true;
    setTimedOut(true);
    fns.current.resolve(false, counterLimit);
  };

  // 스피드 레이스 — NPC가 일정 간격으로 문제를 "풀어" 추격
  useEffect(() => {
    if (style !== 'speed' || phase !== 'play') return;
    const id = window.setInterval(() => {
      if (doneRef.current || decidedRef.current) return; // 플레이어가 이미 승리를 확정했으면 추격 중단
      npcScoreRef.current += 1;
      setNpcScore(npcScoreRef.current);
      if (npcScoreRef.current >= RACE_TARGET) {
        decidedRef.current = true;
        fns.current.finish(false);
      }
    }, racePaceMs(npc.table));
    return () => window.clearInterval(id);
  }, [style, phase, npc.table]);

  // 안정적 입력 핸들러 (빈 deps + ref)
  const handleInput = useCallback((n: number) => {
    if (phaseRef.current !== 'play' || lockRef.current || doneRef.current) return;
    const cur = inputRef.current;
    if (cur.length >= 3) return;
    const next = cur + String(n);
    setInput(next);
    const ans = problemRef.current.a * problemRef.current.b;
    if (next.length >= String(ans).length) {
      lockRef.current = true;
      window.setTimeout(() => fns.current.submit(next), 90);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = useCallback(() => {
    if (phaseRef.current !== 'play' || lockRef.current || doneRef.current) return;
    setInput(inputRef.current.slice(0, -1));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleManualSubmit = useCallback(() => {
    if (phaseRef.current !== 'play' || lockRef.current || doneRef.current || !inputRef.current) return;
    lockRef.current = true;
    fns.current.submit(inputRef.current);
  }, []);

  const handleTimeout = useCallback(() => {
    fns.current.timeout();
  }, []);

  // 물리 키보드
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key >= '0' && e.key <= '9') handleInput(parseInt(e.key, 10));
      else if (e.key === 'Backspace') handleDelete();
      else if (e.key === 'Enter') handleManualSubmit();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [handleInput, handleDelete, handleManualSubmit]);

  const answer = problem.a * problem.b;
  const showAnswer = feedback === 'wrong';
  const nextRegion = isBoss ? regionFor(npc.table + 1) : undefined;
  const worldCleared = isBoss && npc.table === 9;

  // 인트로 규칙 안내
  const ruleText =
    style === 'speed'
      ? `먼저 ${RACE_TARGET}문제를 맞히면 승리!`
      : style === 'counter'
        ? `${Math.round(counterLimit / 1000)}초 안에 못 풀면 반격당해요!`
        : isBoss
          ? 'HP가 절반이 되면 분노해요 — 조심!'
          : '정답이면 공격, 오답이면 반격!';

  return (
    <div className="relative flex h-full flex-col px-5 pt-[calc(env(safe-area-inset-top)+0.75rem)] pb-[calc(env(safe-area-inset-bottom)+1.25rem)]">
      <Confetti trigger={confetti} />
      {end && <AchievementToast ids={[...end.commit.unlocked, ...end.advUnlocked]} token={confetti + 1} resolve={resolveAchievement} />}
      <LevelUpOverlay show={levelUp} level={end?.commit.newLevel ?? 0} onClose={() => setLevelUp(false)} />

      {/* 상단 바 */}
      <div className="mb-3 flex items-center gap-3">
        <button onClick={onFlee} aria-label="배틀에서 도망가기" className="shrink-0 text-text-muted hover:text-text">
          <X className="h-6 w-6" />
        </button>
        <span className="text-sm font-bold text-text-muted">
          {isBoss ? '보스 대결' : BATTLE_STYLE_NAME[style]} · <span className="num">{npc.table}</span>단
        </span>
        <AnimatePresence>
          {combo >= 2 && (
            <motion.span
              key={combo}
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ opacity: 0 }}
              className="ml-auto flex items-center gap-1 rounded-full bg-danger/15 px-3 py-1 text-sm font-bold text-danger"
            >
              <Flame className="h-4 w-4" fill="currentColor" /> {combo} 콤보
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* NPC 패널 */}
      <div className="relative mb-2 flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3">
        <Avatar color={npc.color} size={52} boss={isBoss} hit={npcHit} enraged={enraged} />
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex items-center gap-1.5">
            <span className="truncate text-sm font-extrabold text-text">{npc.name}</span>
            {isBoss && <span className="shrink-0 rounded-full bg-danger/15 px-2 py-0.5 text-[10px] font-extrabold text-danger">보스</span>}
            {enraged && (
              <motion.span
                initial={{ scale: 0.5 }}
                animate={{ scale: 1 }}
                className="flex shrink-0 items-center gap-0.5 rounded-full bg-danger px-2 py-0.5 text-[10px] font-extrabold text-white"
              >
                <Flame className="h-3 w-3" fill="currentColor" /> 분노
              </motion.span>
            )}
          </div>
          {style === 'speed' ? (
            <RaceBar score={npcScore} target={RACE_TARGET} tone="npc" />
          ) : (
            <HpBar hp={npcHp} max={npc.hp} tone="npc" />
          )}
        </div>
        <AnimatePresence>
          {npcFloat && (
            <motion.span
              key={npcFloat.id}
              initial={{ opacity: 1, y: 0, scale: 1.15 }}
              animate={{ opacity: 0, y: -26 }}
              transition={{ duration: 0.7, ease: 'easeOut' }}
              className="num absolute right-4 top-0 text-lg font-extrabold text-danger"
            >
              {npcFloat.text}
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* 반격전 카운트다운 */}
      {style === 'counter' && phase === 'play' && !feedback && (
        <QuestionTimer qKey={qIdx} limitMs={counterLimit} onExpire={handleTimeout} />
      )}

      {/* 문제 */}
      <div className="flex flex-1 flex-col items-center justify-center">
        <motion.div
          key={`${problem.a}-${problem.b}-${qIdx}`}
          initial={{ scale: 0.9, opacity: 0, y: 8 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 22 }}
          className={`num flex items-baseline gap-3 text-6xl font-extrabold ${feedback === 'wrong' ? 'animate-shake' : feedback === 'correct' ? 'animate-pop' : ''}`}
        >
          <span className="text-text">{problem.a}</span>
          <span className="text-text-muted">×</span>
          <span className="text-text">{problem.b}</span>
          <span className="text-text-muted">=</span>
          <span className={showAnswer ? 'text-success' : input ? 'text-accent' : 'text-border'}>
            {showAnswer ? answer : input || '?'}
          </span>
        </motion.div>
        {showAnswer && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mt-3 text-sm font-bold text-text-muted">
            {timedOut
              ? `시간 초과! 정답은 ${answer} — ${npc.name}의 반격!`
              : style === 'speed'
                ? `정답은 ${answer} 이에요`
                : `정답은 ${answer} — ${npc.name}의 반격!`}
          </motion.div>
        )}
      </div>

      {/* 플레이어 패널 */}
      <div className="relative mb-3 flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3">
        <Avatar color="#6366f1" size={44} hit={playerHit} />
        <div className="min-w-0 flex-1">
          <div className="mb-1 text-sm font-extrabold text-text">나</div>
          {style === 'speed' ? (
            <RaceBar score={playerScore} target={RACE_TARGET} tone="player" />
          ) : (
            <HpBar hp={playerHp} max={PLAYER_MAX_HP} tone="player" />
          )}
        </div>
        <AnimatePresence>
          {playerFloat && (
            <motion.span
              key={playerFloat.id}
              initial={{ opacity: 1, y: 0, scale: 1.15 }}
              animate={{ opacity: 0, y: -26 }}
              transition={{ duration: 0.7, ease: 'easeOut' }}
              className="num absolute right-4 top-0 text-lg font-extrabold text-danger"
            >
              {playerFloat.text}
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* 키패드 */}
      <Keypad onInput={handleInput} onDelete={handleDelete} onSubmit={handleManualSubmit} canSubmit={!!input} />

      {/* 인트로 오버레이 */}
      <AnimatePresence>
        {phase === 'intro' && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-4 bg-bg/95 px-8 backdrop-blur-sm"
          >
            <motion.div initial={{ scale: 0.6, y: 12 }} animate={{ scale: 1, y: 0 }} transition={{ type: 'spring', stiffness: 260, damping: 18 }}>
              <Avatar color={npc.color} size={88} boss={isBoss} hit={0} />
            </motion.div>
            <div className="text-center">
              <div className="mb-1 text-xl font-extrabold text-text">{npc.name}</div>
              <p className="text-sm font-bold text-text-muted">“{npc.greeting}”</p>
            </div>
            <div className="rounded-full bg-surface-2 px-4 py-1.5 text-xs font-extrabold text-text-muted">
              {BATTLE_STYLE_NAME[style]} — {ruleText}
            </div>
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ delay: 0.5, type: 'spring', stiffness: 400, damping: 12 }}
              className="flex items-center gap-2 rounded-full bg-danger px-5 py-2 text-lg font-extrabold text-white"
            >
              <Swords className="h-5 w-5" /> 대결 시작!
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 결과 오버레이 */}
      <AnimatePresence>
        {phase === 'end' && end && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="absolute inset-0 z-20 flex items-center justify-center bg-bg/95 px-6 backdrop-blur-sm"
          >
            <motion.div
              initial={{ scale: 0.92, y: 16, opacity: 0 }}
              animate={{ scale: 1, y: 0, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 260, damping: 20 }}
              className="w-full rounded-3xl border border-border bg-surface p-6"
            >
              <div className="mb-4 flex flex-col items-center gap-3 text-center">
                <Avatar color={end.won ? '#6366f1' : npc.color} size={72} boss={isBoss && !end.won} hit={0} />
                <div>
                  <h2 className="text-2xl font-extrabold text-text">
                    {end.won ? (worldCleared ? '월드 클리어! 👑' : '승리! 🎉') : '아쉬운 패배…'}
                  </h2>
                  <p className="mt-1 text-sm font-bold text-text-muted">
                    {end.won
                      ? worldCleared
                        ? '모든 지역을 정복했어요 — 진정한 구구단 정복자!'
                        : `${npc.name}을(를) 물리쳤어요!`
                      : '괜찮아요, 답을 익히고 다시 도전해요'}
                  </p>
                </div>
              </div>

              {/* 보스 격파 → 다음 지역 해금 안내 */}
              {end.won && isBoss && !worldCleared && nextRegion && (
                <motion.div
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 }}
                  className="mb-4 flex items-center justify-center gap-1.5 rounded-2xl bg-accent/12 px-4 py-3 text-sm font-extrabold text-accent"
                >
                  <Unlock className="h-4 w-4" /> {nextRegion.name}({nextRegion.table}단)이 열렸어요!
                </motion.div>
              )}

              <div className="mb-4 grid grid-cols-2 gap-2.5">
                <div className="rounded-2xl bg-surface-2 px-4 py-3">
                  <div className="mb-0.5 flex items-center gap-1 text-xs font-bold text-text-muted">
                    <Sparkles className="h-3.5 w-3.5 text-warning" /> 획득 XP
                  </div>
                  <div className="num text-xl font-extrabold text-text">+{end.commit.xpEarned}</div>
                </div>
                <div className="rounded-2xl bg-surface-2 px-4 py-3">
                  <div className="mb-0.5 flex items-center gap-1 text-xs font-bold text-text-muted">
                    <Flame className="h-3.5 w-3.5 text-danger" /> 최고 콤보
                  </div>
                  <div className="num text-xl font-extrabold text-text">{maxComboRef.current}</div>
                </div>
              </div>

              {end.commit.table != null && (
                <div className="mb-4 flex items-center justify-between rounded-2xl bg-surface-2 px-4 py-3">
                  <div>
                    <div className="text-sm font-bold text-text">{end.commit.table}단 마스터리</div>
                    {end.commit.improvedStars && <div className="text-xs font-bold text-accent">새 기록!</div>}
                  </div>
                  <Stars value={end.commit.newStars} size={22} />
                </div>
              )}

              <div className="flex flex-col gap-2">
                {!end.won && (
                  <Button variant="primary" size="lg" onClick={onRetry}>
                    <RotateCcw className="h-5 w-5" /> 다시 도전
                  </Button>
                )}
                <Button variant={end.won ? 'primary' : 'surface'} size="lg" onClick={onWorld}>
                  월드로 돌아가기
                </Button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
