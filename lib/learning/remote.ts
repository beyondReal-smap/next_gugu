// 코치·적응형 계획·장기 리포트 클라이언트.
// web/lib/api/** 는 다른 워커 담당이라 여기에 둔다.
import { Capacitor } from '@capacitor/core';
import { LearningApiError } from '../api/learning';

const DEFAULT_NATIVE_API_BASE = 'https://gugu.smap.site/api';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FACT_PATTERN = /^[2-9]x[1-9]$/;
const TIMEZONE_SUFFIX_PATTERN = /(?:Z|[+-]\d{2}:\d{2})$/i;

function apiBase(): string {
  if (Capacitor.isNativePlatform()) {
    return process.env.NEXT_PUBLIC_API_BASE ?? DEFAULT_NATIVE_API_BASE;
  }
  return '/api';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

async function throwApiError(response: Response): Promise<never> {
  const text = await response.text();
  if (!text) {
    throw new LearningApiError(response.status, 'EMPTY_ERROR_RESPONSE', `학습 API 요청 실패 (${response.status})`);
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (cause) {
    throw new LearningApiError(
      response.status,
      'INVALID_ERROR_RESPONSE',
      `학습 API 오류 응답 파싱 실패 (${response.status})`,
      { cause }
    );
  }
  const detail = isRecord(parsed) && isRecord(parsed.detail) ? parsed.detail : null;
  const code = detail && typeof detail.code === 'string' ? detail.code : 'LEARNING_API_ERROR';
  const message = detail && typeof detail.message === 'string'
    ? detail.message
    : `학습 API 요청 실패 (${response.status})`;
  throw new LearningApiError(response.status, code, message);
}

async function readSuccessJson(response: Response): Promise<unknown> {
  if (!response.ok) return throwApiError(response);
  try {
    return await response.json();
  } catch (cause) {
    throw new LearningApiError(
      response.status,
      'INVALID_SUCCESS_RESPONSE',
      '학습 API 성공 응답이 JSON이 아닙니다',
      { cause }
    );
  }
}

export interface CoachExplainResponse {
  hint: string;
  checkQuestion: string;
}

export async function explainWrongAnswer(
  accessToken: string,
  input: {
    learnerId: string;
    factId: string;
    submittedAnswer: number | boolean | string;
    attemptNo: number;
  }
): Promise<CoachExplainResponse> {
  if (!accessToken) throw new Error('코치 요청에 액세스 토큰이 필요합니다');
  if (!isUuid(input.learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  if (!FACT_PATTERN.test(input.factId)) throw new Error('factId 형식이 올바르지 않습니다');
  if (!Number.isSafeInteger(input.attemptNo) || input.attemptNo < 1 || input.attemptNo > 100) {
    throw new Error('attemptNo 범위가 올바르지 않습니다');
  }
  const response = await fetch(`${apiBase()}/v1/coach/explain`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      learnerId: input.learnerId,
      factId: input.factId,
      submittedAnswer: input.submittedAnswer,
      attemptNo: input.attemptNo,
      locale: 'ko-KR',
    }),
  });
  const raw = await readSuccessJson(response);
  if (!isRecord(raw) || typeof raw.hint !== 'string' || !raw.hint.trim()
    || typeof raw.checkQuestion !== 'string' || !raw.checkQuestion.trim()) {
    throw new Error('코치 응답 형식이 올바르지 않습니다');
  }
  return { hint: raw.hint.trim(), checkQuestion: raw.checkQuestion.trim() };
}

export type SelectionReason = 'overdue' | 'unseen' | 'reinforcement';

export interface PracticePlanItem {
  factId: string;
  selectionReason: SelectionReason;
}

export interface PracticePlanResponse {
  planId: string;
  items: PracticePlanItem[];
}

const REASONS = new Set<SelectionReason>(['overdue', 'unseen', 'reinforcement']);

export async function createPracticePlan(
  accessToken: string,
  input: { learnerId: string; count: number }
): Promise<PracticePlanResponse> {
  if (!accessToken) throw new Error('복습 계획 요청에 액세스 토큰이 필요합니다');
  if (!isUuid(input.learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  if (!Number.isSafeInteger(input.count) || input.count < 5 || input.count > 30) {
    throw new Error('계획 문항 수는 5~30이어야 합니다');
  }
  const response = await fetch(`${apiBase()}/v1/practice/plan`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ learnerId: input.learnerId, count: input.count }),
  });
  const raw = await readSuccessJson(response);
  if (!isRecord(raw) || !isUuid(raw.planId) || !Array.isArray(raw.items)) {
    throw new Error('복습 계획 응답 형식이 올바르지 않습니다');
  }
  const items: PracticePlanItem[] = raw.items.map((item) => {
    if (!isRecord(item) || typeof item.factId !== 'string' || !FACT_PATTERN.test(item.factId)
      || typeof item.selectionReason !== 'string' || !REASONS.has(item.selectionReason as SelectionReason)) {
      throw new Error('복습 계획 항목 형식이 올바르지 않습니다');
    }
    return { factId: item.factId, selectionReason: item.selectionReason as SelectionReason };
  });
  if (items.length < 5) throw new Error('복습 계획 문항이 너무 적습니다');
  return { planId: raw.planId, items };
}

export function selectionReasonLabel(reason: SelectionReason): string {
  if (reason === 'overdue') return '복습 예정';
  if (reason === 'reinforcement') return '약한 문제';
  return '새 문제';
}

export interface LearnerReport {
  weakFacts: { factId: string; misses: number; accuracy: number }[];
  accuracyTrend: { date: string; value: number }[];
  speedTrend: { date: string; value: number }[];
  dueReviews: { factId: string; dueAt: string }[];
  recommendations: string[];
}

function parseTrend(value: unknown, field: string): { date: string; value: number }[] {
  if (!Array.isArray(value)) throw new Error(`${field} 형식이 올바르지 않습니다`);
  return value.map((item) => {
    if (!isRecord(item) || typeof item.date !== 'string' || !item.date
      || typeof item.value !== 'number' || !Number.isFinite(item.value) || item.value < 0) {
      throw new Error(`${field} 항목 형식이 올바르지 않습니다`);
    }
    return { date: item.date, value: item.value };
  });
}

export async function fetchLearnerReport(
  accessToken: string,
  learnerId: string,
  range: { from: string; to: string }
): Promise<LearnerReport> {
  if (!accessToken) throw new Error('장기 리포트 요청에 액세스 토큰이 필요합니다');
  if (!isUuid(learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(range.from) || !/^\d{4}-\d{2}-\d{2}$/.test(range.to)) {
    throw new Error('조회 기간 형식이 올바르지 않습니다');
  }
  const query = `from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}`;
  const response = await fetch(`${apiBase()}/v1/reports/learners/${encodeURIComponent(learnerId)}?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const raw = await readSuccessJson(response);
  if (!isRecord(raw) || !Array.isArray(raw.weakFacts) || !Array.isArray(raw.dueReviews)
    || !Array.isArray(raw.recommendations)) {
    throw new Error('장기 리포트 응답 형식이 올바르지 않습니다');
  }
  const weakFacts = raw.weakFacts.map((item) => {
    if (!isRecord(item) || typeof item.factId !== 'string' || !FACT_PATTERN.test(item.factId)
      || typeof item.misses !== 'number' || item.misses < 0
      || typeof item.accuracy !== 'number' || item.accuracy < 0 || item.accuracy > 1) {
      throw new Error('약한 식 항목 형식이 올바르지 않습니다');
    }
    return { factId: item.factId, misses: item.misses, accuracy: item.accuracy };
  });
  const dueReviews = raw.dueReviews.map((item) => {
    if (!isRecord(item) || typeof item.factId !== 'string' || !FACT_PATTERN.test(item.factId)
      || typeof item.dueAt !== 'string' || !TIMEZONE_SUFFIX_PATTERN.test(item.dueAt)) {
      throw new Error('복습 예정 항목 형식이 올바르지 않습니다');
    }
    return { factId: item.factId, dueAt: item.dueAt };
  });
  const recommendations = raw.recommendations.map((item) => {
    if (typeof item !== 'string' || !item.trim()) throw new Error('권고 문구 형식이 올바르지 않습니다');
    return item.trim();
  });
  return {
    weakFacts,
    accuracyTrend: parseTrend(raw.accuracyTrend, 'accuracyTrend'),
    speedTrend: parseTrend(raw.speedTrend, 'speedTrend'),
    dueReviews,
    recommendations,
  };
}
