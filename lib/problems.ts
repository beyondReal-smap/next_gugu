// 출제 엔진 — 오답 가중 + 최근 출제 회피 (경량, 풀 SRS 아님)
import { Problem } from './types';

export const MIN_TABLE = 2;
export const MAX_TABLE = 9;
export const MIN_B = 1;
export const MAX_B = 9;

export function problemKey(a: number, b: number): string {
  return `${a}x${b}`;
}

// 한 단(table)의 후보 식
function candidatesForTable(table: number): Problem[] {
  const list: Problem[] = [];
  for (let b = MIN_B; b <= MAX_B; b++) list.push({ a: table, b });
  return list;
}

// 혼합(전 범위) 후보
function candidatesAll(): Problem[] {
  const list: Problem[] = [];
  for (let a = MIN_TABLE; a <= MAX_TABLE; a++)
    for (let b = MIN_B; b <= MAX_B; b++) list.push({ a, b });
  return list;
}

interface PickOpts {
  table: number | null;          // null이면 혼합
  wrongPool: Record<string, number>;
  recentKeys: string[];          // 최근 출제된 키(회피)
}

// 가중 무작위 선택. 오답 풀 가중치 ↑, 최근 출제는 가중치 ↓.
export function pickProblem({ table, wrongPool, recentKeys }: PickOpts): Problem {
  const pool = table == null ? candidatesAll() : candidatesForTable(table);
  const recent = new Set(recentKeys.slice(-3));

  const weighted = pool.map((p) => {
    const key = problemKey(p.a, p.b);
    let w = 1 + (wrongPool[key] || 0) * 2.2; // 오답 가중
    if (recent.has(key)) w *= 0.15;          // 직전 출제 회피
    return { p, w };
  });

  const total = weighted.reduce((s, x) => s + x.w, 0);
  // 결정적 무작위 대신 누적 분포 + Math.random (클라이언트 전용)
  let r = Math.random() * total;
  for (const x of weighted) {
    r -= x.w;
    if (r <= 0) return x.p;
  }
  return weighted[weighted.length - 1].p;
}

// OX 퀴즈 — 화면에 표시할 식 (맞는 식 또는 그럴듯한 오답)
export interface Statement {
  shown: number;   // 표시되는 곱셈 결과
  isTrue: boolean; // 실제로 맞는 식인지
}

export function makeStatement(p: Problem): Statement {
  const answer = p.a * p.b;
  if (Math.random() < 0.5) return { shown: answer, isTrue: true };
  // 흔히 헷갈리는 "한 끗 차이" 오답 후보
  const candidates = [p.a * (p.b + 1), p.a * (p.b - 1), (p.a + 1) * p.b, (p.a - 1) * p.b]
    .filter((v) => v > 0 && v !== answer);
  const shown = candidates[Math.floor(Math.random() * candidates.length)];
  return { shown, isTrue: false };
}

// 오답 풀 갱신
export function updateWrongPool(
  pool: Record<string, number>,
  a: number,
  b: number,
  correct: boolean
): Record<string, number> {
  const key = problemKey(a, b);
  const cur = pool[key] || 0;
  const next = { ...pool };
  if (correct) {
    const v = Math.max(0, cur - 1);
    if (v === 0) delete next[key];
    else next[key] = v;
  } else {
    next[key] = Math.min(6, cur + 2);
  }
  return next;
}
