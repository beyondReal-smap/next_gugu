import { pickProblem, problemKey } from './problems';
import type { Problem } from './types';

export const OBSTACLE_START = 690;
export const COLLISION_X = 154;
export const JUMP_START = 280;
export const JUMP_END = 30;

export interface RunnerQuestion extends Problem {
  choices: number[];
}

export interface RunnerState {
  phase: 'ready' | 'running' | 'paused' | 'over';
  table: number | null;
  question: RunnerQuestion;
  recentKeys: string[];
  round: number;
  score: number;
  lives: number;
  combo: number;
  maxCombo: number;
  distance: number;
  obstacleX: number;
  outcome: 'correct' | 'wrong' | 'missed' | null;
  given: number | null;
  hitMs: number;
}

function shuffled(values: number[]): number[] {
  const result = [...values];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

export function runnerQuestion(table: number | null, recentKeys: string[]): RunnerQuestion {
  const problem = pickProblem({ table, wrongPool: {}, recentKeys });
  const answer = problem.a * problem.b;
  const alternatives = [...new Set([
    answer - problem.a, answer + problem.a, answer - problem.b,
    answer + problem.b, answer - 1, answer + 1,
  ])].filter((value) => value > 0 && value <= 81 && value !== answer);
  return { ...problem, choices: shuffled([answer, ...shuffled(alternatives).slice(0, 2)]) };
}

export function createRunner(table: number | null, phase: 'ready' | 'running' = 'running'): RunnerState {
  return {
    phase, table,
    question: phase === 'ready' ? { a: 2, b: 3, choices: [4, 6, 8] } : runnerQuestion(table, []),
    recentKeys: [], round: 0, score: 0, lives: 3, combo: 0, maxCombo: 0,
    distance: 0, obstacleX: OBSTACLE_START, outcome: null, given: null, hitMs: 0,
  };
}

export function answerWindowMs(score: number): number {
  return Math.max(3200, 6500 - Math.floor(score / 5) * 450);
}

export function answerRunner(state: RunnerState, choice: number): RunnerState {
  if (state.phase !== 'running' || state.outcome !== null || !state.question.choices.includes(choice)) return state;
  return {
    ...state,
    given: choice,
    outcome: choice === state.question.a * state.question.b ? 'correct' : 'wrong',
  };
}

function nextObstacle(state: RunnerState): RunnerState {
  const recentKeys = [...state.recentKeys, problemKey(state.question.a, state.question.b)].slice(-3);
  return {
    ...state, recentKeys, question: runnerQuestion(state.table, recentKeys),
    round: state.round + 1, obstacleX: OBSTACLE_START, outcome: null, given: null, hitMs: 0,
  };
}

export function advanceRunner(state: RunnerState, dt: number): RunnerState {
  if (state.phase !== 'running' || !Number.isFinite(dt) || dt <= 0) return state;
  if (state.hitMs > 0) {
    const hitMs = Math.max(0, state.hitMs - dt);
    if (hitMs > 0) return { ...state, hitMs };
    return state.lives === 0 ? { ...state, hitMs: 0, phase: 'over' } : nextObstacle(state);
  }

  const speed = state.outcome === null ? (OBSTACLE_START - COLLISION_X) / answerWindowMs(state.score) : 0.3;
  const movement = speed * dt;
  const next = { ...state, obstacleX: state.obstacleX - movement, distance: state.distance + movement / 20 };

  if (next.obstacleX <= COLLISION_X && next.outcome !== 'correct') {
    return { ...next, obstacleX: COLLISION_X, outcome: next.outcome ?? 'missed', lives: next.lives - 1, combo: 0, hitMs: 1400 };
  }
  if (next.obstacleX < -40) {
    const combo = next.combo + 1;
    return nextObstacle({ ...next, score: next.score + 1, combo, maxCombo: Math.max(next.maxCombo, combo) });
  }
  return next;
}

export function runnerJump(state: RunnerState): number {
  if (state.outcome !== 'correct' || state.obstacleX > JUMP_START || state.obstacleX < JUMP_END) return 0;
  // 장애물 위치에 점프 높이를 연결해 속도가 올라가도 같은 궤적으로 넘습니다.
  return Math.sin(Math.PI * (JUMP_START - state.obstacleX) / (JUMP_START - JUMP_END)) * 96;
}
