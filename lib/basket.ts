import { problemKey } from './problems';
import { runnerQuestion, type RunnerQuestion } from './runner';

export const BASKET_WIDTH = 360;
export const BASKET_MIN_X = 36;
export const BASKET_MAX_X = BASKET_WIDTH - BASKET_MIN_X;
export const FRUIT_START_Y = 58;
export const FRUIT_CATCH_Y = 304;
export const BASKET_CORRECT_FEEDBACK_MS = 450;

export interface BasketState {
  phase: 'ready' | 'running' | 'paused' | 'over';
  table: number | null;
  question: RunnerQuestion;
  recentKeys: string[];
  x: number;
  round: number;
  elapsedMs: number;
  feedbackMs: number;
  outcome: 'correct' | 'wrong' | 'missed' | null;
  caughtIndex: number | null;
  score: number;
  lives: number;
  combo: number;
  maxCombo: number;
}

export function createBasket(table: number | null, phase: 'ready' | 'running' = 'running'): BasketState {
  return {
    phase, table,
    question: phase === 'ready' ? { a: 2, b: 3, choices: [4, 6, 8] } : runnerQuestion(table, []),
    recentKeys: [], x: BASKET_WIDTH / 2, round: 0, elapsedMs: 0, feedbackMs: 0,
    outcome: null, caughtIndex: null, score: 0, lives: 3, combo: 0, maxCombo: 0,
  };
}

export function basketLevel(score: number): number {
  return Math.floor(score / 3) + 1;
}

export function fallDurationMs(score: number): number {
  return Math.max(2200, 3000 - (basketLevel(score) - 1) * 300);
}

export function fruitX(index: number, elapsedMs: number): number {
  return 64 + index * 116 + Math.sin(elapsedMs / 750 + index * 2) * 12;
}

export function moveBasket(state: BasketState, x: number): BasketState {
  if (state.phase !== 'running' || state.outcome !== null || !Number.isFinite(x)) return state;
  return { ...state, x: Math.max(BASKET_MIN_X, Math.min(BASKET_MAX_X, x)) };
}

export function advanceBasket(state: BasketState, dt: number): BasketState {
  if (state.phase !== 'running' || !Number.isFinite(dt) || dt <= 0) return state;
  if (state.outcome !== null) {
    const feedbackMs = Math.max(0, state.feedbackMs - dt);
    if (feedbackMs > 0) return { ...state, feedbackMs };
    if (state.lives === 0) return { ...state, feedbackMs: 0, phase: 'over' };
    const recentKeys = [...state.recentKeys, problemKey(state.question.a, state.question.b)].slice(-4);
    return {
      ...state, recentKeys, question: runnerQuestion(state.table, recentKeys),
      round: state.round + 1, elapsedMs: 0, feedbackMs: 0, outcome: null, caughtIndex: null,
    };
  }

  const duration = fallDurationMs(state.score);
  const elapsedMs = Math.min(duration, state.elapsedMs + dt);
  if (elapsedMs < duration) return { ...state, elapsedMs };
  // 열매가 바구니 높이를 지나는 순간 한 번만 판정합니다.
  const caughtIndex = state.question.choices.findIndex((_, index) => Math.abs(fruitX(index, duration) - state.x) <= 40);
  const correct = caughtIndex >= 0 && state.question.choices[caughtIndex] === state.question.a * state.question.b;
  const combo = correct ? state.combo + 1 : 0;
  return {
    ...state, elapsedMs, caughtIndex: caughtIndex < 0 ? null : caughtIndex,
    outcome: correct ? 'correct' : caughtIndex >= 0 ? 'wrong' : 'missed',
    feedbackMs: correct ? BASKET_CORRECT_FEEDBACK_MS : 1500,
    score: state.score + (correct ? 1 : 0), lives: state.lives - (correct ? 0 : 1),
    combo, maxCombo: Math.max(state.maxCombo, combo),
  };
}
