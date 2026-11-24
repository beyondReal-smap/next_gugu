import { problemKey } from './problems';
import { runnerQuestion, type RunnerQuestion } from './runner';

// 구구 레인: 레인 3개를 오가며 장애물을 피하고, 정답 숫자가 적힌 레인으로 통과합니다.
export type Lane = 0 | 1 | 2; // 0=위(먼 쪽), 2=아래(가까운 쪽)
export const LANES: readonly Lane[] = [0, 1, 2];

export const SPAWN_X = 740;   // 장애물·게이트가 나타나는 x
export const JUDGE_X = 150;   // 공룡 몸통과 겹쳐 판정하는 x
export const REMOVE_X = -90;  // 화면 밖으로 사라지는 x
export const HIT_MS = 1400;   // 충돌·오답 뒤 월드 정지 시간
export const LANE_ANIM_MS = 140;
export const NEXT_CYCLE_X = JUDGE_X - 120; // 판정한 게이트가 이만큼 지나가면 다음 장애물을 내보냅니다.

export interface LaneObstacle {
  id: number;
  lane: Lane;
  x: number;
  kind: 'rock' | 'stump';
}

export interface LaneGate {
  x: number;
  startX: number;
  values: number[]; // values[lane]
}

export interface LaneRunnerState {
  phase: 'ready' | 'running' | 'paused' | 'over';
  segment: 'dodge' | 'quiz';
  table: number | null;
  lane: Lane;
  laneFrom: number; // 전환 시작 시점의 화면상 레인 위치
  laneAnimMs: number;
  obstacles: LaneObstacle[];
  nextId: number;
  spawnInMs: number;
  spawned: number;
  lastFree: Lane[];
  question: RunnerQuestion | null;
  gate: LaneGate | null;
  recentKeys: string[];
  outcome: 'correct' | 'wrong' | 'hit' | null;
  given: number | null;
  score: number;
  dodged: number;
  combo: number;
  maxCombo: number;
  lives: number;
  distance: number;
  hitMs: number;
}

export const SCORE_PER_LEVEL = 3; // 정답 길 3개마다 한 단계 빨라집니다.

export function laneLevel(score: number): number {
  return Math.floor(score / SCORE_PER_LEVEL);
}

// 방금 통과한 정답으로 속도 단계가 올랐는지
export function leveledUp(state: LaneRunnerState): boolean {
  return state.outcome === 'correct' && state.score > 0 && state.score % SCORE_PER_LEVEL === 0;
}

// 장애물이 나타나서 판정선에 닿기까지 걸리는 시간
export function travelMs(score: number): number {
  return Math.max(1900, 3400 - laneLevel(score) * 250);
}

// 문제가 뜬 뒤 게이트가 판정선에 닿기까지 걸리는 시간 (항상 travelMs 이상이라 게이트는 화면 밖에서 등장)
export function quizWindowMs(score: number): number {
  return Math.max(3500, 5000 - laneLevel(score) * 200);
}

export function spawnGapMs(score: number): number {
  return Math.max(750, 1200 - laneLevel(score) * 70);
}

export function obstaclesPerCycle(score: number): number {
  return Math.min(4, 2 + Math.floor(laneLevel(score) / 2));
}

export function laneSpeed(score: number): number {
  return (SPAWN_X - JUDGE_X) / travelMs(score);
}

export function createLaneRunner(table: number | null, phase: 'ready' | 'running' = 'running'): LaneRunnerState {
  return {
    phase, segment: 'dodge', table,
    lane: 1, laneFrom: 1, laneAnimMs: 0,
    obstacles: [], nextId: 0, spawnInMs: 800, spawned: 0, lastFree: [...LANES],
    question: null, gate: null, recentKeys: [], outcome: null, given: null,
    score: 0, dodged: 0, combo: 0, maxCombo: 0, lives: 3, distance: 0, hitMs: 0,
  };
}

export function setLane(state: LaneRunnerState, lane: number): LaneRunnerState {
  if (state.phase !== 'running' || !LANES.includes(lane as Lane) || lane === state.lane) return state;
  return { ...state, lane: lane as Lane, laneFrom: laneOffset(state), laneAnimMs: LANE_ANIM_MS };
}

export function moveLane(state: LaneRunnerState, delta: -1 | 1): LaneRunnerState {
  return setLane(state, state.lane + delta);
}

// 화면에 그릴 공룡의 레인 위치(0~2 사이 실수). 레인 전환을 부드럽게 보간합니다.
export function laneOffset(state: LaneRunnerState): number {
  const t = state.laneAnimMs / LANE_ANIM_MS;
  return state.lane + (state.laneFrom - state.lane) * t * t;
}

function pick<T>(values: readonly T[]): T {
  return values[Math.floor(Math.random() * values.length)];
}

// 한 열의 장애물 배치. 빈 레인은 직전 열의 빈 레인에서 한 칸 이내로만 둡니다.
function spawnColumn(state: LaneRunnerState): LaneRunnerState {
  const level = laneLevel(state.score);
  const double = level >= 2 && Math.random() < 0.25;
  let blocked: Lane[];
  if (double) {
    const reachable = LANES.filter((lane) => state.lastFree.some((free) => Math.abs(free - lane) <= 1));
    const free = pick(reachable);
    blocked = LANES.filter((lane) => lane !== free);
  } else {
    // 절반 남짓은 지금 레인을 막아 직접 움직이게 합니다.
    blocked = [Math.random() < 0.55 ? state.lane : pick(LANES)];
  }
  const kind: LaneObstacle['kind'] = state.spawned % 2 === 0 ? 'rock' : 'stump';
  const obstacles = blocked.map((lane, i) => ({ id: state.nextId + i, lane, x: SPAWN_X, kind }));
  return {
    ...state,
    obstacles: [...state.obstacles, ...obstacles],
    nextId: state.nextId + obstacles.length,
    spawned: state.spawned + 1,
    spawnInMs: spawnGapMs(state.score),
    lastFree: LANES.filter((lane) => !blocked.includes(lane)),
  };
}

function startQuiz(state: LaneRunnerState): LaneRunnerState {
  const question = runnerQuestion(state.table, state.recentKeys);
  const startX = JUDGE_X + laneSpeed(state.score) * quizWindowMs(state.score);
  return {
    ...state,
    segment: 'quiz',
    question,
    recentKeys: [...state.recentKeys, problemKey(question.a, question.b)].slice(-3),
    gate: { x: startX, startX, values: question.choices },
    outcome: null, given: null,
  };
}

// 판정이 끝난 게이트는 화면 밖으로 나갈 때까지 보여 주고, 장애물은 바로 이어서 내보냅니다.
function startDodge(state: LaneRunnerState): LaneRunnerState {
  return { ...state, segment: 'dodge', spawned: 0, spawnInMs: 0, lastFree: [...LANES] };
}

export function advanceLaneRunner(state: LaneRunnerState, dt: number): LaneRunnerState {
  if (state.phase !== 'running' || !Number.isFinite(dt) || dt <= 0) return state;
  const laneAnimMs = Math.max(0, state.laneAnimMs - dt);

  if (state.hitMs > 0) {
    const hitMs = Math.max(0, state.hitMs - dt);
    if (hitMs > 0) return { ...state, hitMs, laneAnimMs };
    if (state.lives === 0) return { ...state, hitMs: 0, laneAnimMs, phase: 'over' };
    return { ...state, hitMs: 0, laneAnimMs, outcome: state.outcome === 'hit' ? null : state.outcome };
  }

  const movement = laneSpeed(state.score) * dt;
  let next: LaneRunnerState = { ...state, laneAnimMs, distance: state.distance + movement / 20 };

  // 장애물: 판정선을 넘는 프레임에 한 번만 판정합니다(dt와 무관).
  let hit = false;
  let dodged = 0;
  const obstacles: LaneObstacle[] = [];
  for (const obstacle of state.obstacles) {
    const x = obstacle.x - movement;
    if (obstacle.x > JUDGE_X && x <= JUDGE_X) {
      if (obstacle.lane === state.lane && !hit) {
        hit = true;
        continue; // 부딪힌 장애물은 치웁니다.
      }
      dodged += 1;
    }
    if (x > REMOVE_X) obstacles.push({ ...obstacle, x });
  }
  // 부딪힌 열의 나머지 장애물은 피한 것으로 세지 않습니다.
  next = { ...next, obstacles, dodged: next.dodged + (hit ? 0 : dodged) };
  if (hit) {
    return { ...next, lives: next.lives - 1, combo: 0, outcome: 'hit', hitMs: HIT_MS };
  }

  const gate = next.gate;
  if (gate) {
    const x = gate.x - movement;
    if (gate.x > JUDGE_X && x <= JUDGE_X && next.question && next.segment === 'quiz') {
      const given = gate.values[next.lane];
      const correctAnswer = next.question.a * next.question.b;
      if (given === correctAnswer) {
        const combo = next.combo + 1;
        return { ...next, gate: { ...gate, x }, given, outcome: 'correct', score: next.score + 1, combo, maxCombo: Math.max(next.maxCombo, combo) };
      }
      return { ...next, gate: { ...gate, x: JUDGE_X }, given, outcome: 'wrong', lives: next.lives - 1, combo: 0, hitMs: HIT_MS };
    }
    next = x < REMOVE_X
      ? { ...next, gate: null, question: null, given: null, outcome: next.outcome === 'hit' ? 'hit' : null }
      : { ...next, gate: { ...gate, x } };
    if (next.segment === 'quiz' && x < NEXT_CYCLE_X) return startDodge(next);
  }

  if (next.segment === 'dodge') {
    const spawnInMs = next.spawnInMs - dt;
    if (spawnInMs > 0) return { ...next, spawnInMs };
    // 마지막 장애물 뒤 한 간격이 지나면 곧바로 문제를 냅니다.
    return next.spawned < obstaclesPerCycle(next.score) ? spawnColumn(next) : startQuiz(next);
  }
  return next;
}
