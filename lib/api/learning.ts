import { Capacitor } from '@capacitor/core';

const DEFAULT_NATIVE_API_BASE = 'https://gugu.smap.site/api';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FACT_PATTERN = /^[2-9]x[1-9]$/;
const CONTENT_VERSION_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;
const TIMEZONE_SUFFIX_PATTERN = /(?:Z|[+-]\d{2}:\d{2})$/i;
const LEARNING_EVENT_KEYS = new Set([
  'eventId',
  'learnerId',
  'deviceId',
  'sessionId',
  'sequenceNo',
  'factId',
  'mode',
  'tableNo',
  'submittedAnswer',
  'correct',
  'responseMs',
  'attemptNo',
  'occurredAt',
  'contentVersion',
]);
const MODES = new Set<LearningMode>([
  'practice',
  'timeAttack',
  'challenge',
  'survival',
  'missing',
  'truefalse',
]);

function apiBase(): string {
  if (Capacitor.isNativePlatform()) {
    return process.env.NEXT_PUBLIC_API_BASE ?? DEFAULT_NATIVE_API_BASE;
  }
  return '/api';
}

export type LearningMode =
  | 'practice'
  | 'timeAttack'
  | 'challenge'
  | 'survival'
  | 'missing'
  | 'truefalse';

export interface LearningEvent {
  eventId: string;
  learnerId: string;
  deviceId: string;
  sessionId: string;
  sequenceNo: number;
  factId: string;
  mode: LearningMode;
  tableNo: number | null;
  submittedAnswer: number | boolean | string;
  correct: boolean;
  responseMs: number;
  attemptNo: number;
  occurredAt: string;
  contentVersion: string;
}

export interface RejectedLearningEvent {
  eventId: string;
  code: string;
}

export interface LearningEventBatchResponse {
  acceptedEventIds: string[];
  duplicateEventIds: string[];
  rejected: RejectedLearningEvent[];
  serverCursor: number;
}

export interface LearningSnapshot {
  learnerId: string;
  revision: number;
  serverCursor: number;
  state: Record<string, unknown>;
  updatedAt: string;
}

export interface LearningStateResponse {
  snapshots: LearningSnapshot[];
  serverCursor: number;
}

export interface LearningClaimCandidate {
  learnerId: string;
  displayName: string;
  updatedAt: string;
}

export interface LearningClaimResponse {
  action: 'create_learner' | 'attach_to_existing' | 'conflict';
  candidates: LearningClaimCandidate[];
  learnerId: string | null;
}

export interface LearningClaimConfirmResponse {
  learnerId: string;
}

export class LearningApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    options?: ErrorOptions
  ) {
    super(message, options);
    this.name = 'LearningApiError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function isTableNo(value: unknown): value is number | null {
  return value === null || isNonNegativeInteger(value) && value >= 2 && value <= 9;
}

function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && TIMEZONE_SUFFIX_PATTERN.test(value) && Number.isFinite(Date.parse(value));
}

function parseUuidArray(value: unknown, field: string): string[] {
  if (!Array.isArray(value) || !value.every(isUuid)) {
    throw new Error(`${field} 형식이 올바르지 않습니다`);
  }
  return value;
}

export function validateLearningEvent(value: unknown): LearningEvent {
  if (!isRecord(value)) throw new Error('학습 이벤트가 객체가 아닙니다');
  if (Object.keys(value).some((key) => !LEARNING_EVENT_KEYS.has(key))) {
    throw new Error('학습 이벤트에 알 수 없는 필드가 있습니다');
  }
  const {
    eventId,
    learnerId,
    deviceId,
    sessionId,
    sequenceNo,
    factId,
    mode,
    tableNo,
    submittedAnswer,
    correct,
    responseMs,
    attemptNo,
    occurredAt,
    contentVersion,
  } = value;

  if (!isUuid(eventId) || !isUuid(learnerId) || !isUuid(deviceId) || !isUuid(sessionId)) {
    throw new Error('학습 이벤트 UUID 형식이 올바르지 않습니다');
  }
  if (!isNonNegativeInteger(sequenceNo) || sequenceNo > 10_000) {
    throw new Error('sequenceNo 범위가 올바르지 않습니다');
  }
  if (typeof factId !== 'string' || !FACT_PATTERN.test(factId)) {
    throw new Error('factId 형식이 올바르지 않습니다');
  }
  if (typeof mode !== 'string' || !MODES.has(mode as LearningMode)) {
    throw new Error('학습 모드가 올바르지 않습니다');
  }
  if (!isTableNo(tableNo)) {
    throw new Error('tableNo 범위가 올바르지 않습니다');
  }
  if (tableNo !== null && tableNo !== Number(factId.split('x', 1)[0])) {
    throw new Error('tableNo와 factId의 단 번호가 일치하지 않습니다');
  }
  if (
    !(
      typeof submittedAnswer === 'number' && Number.isSafeInteger(submittedAnswer) ||
      typeof submittedAnswer === 'boolean' ||
      typeof submittedAnswer === 'string' && submittedAnswer.trim().length > 0 && submittedAnswer.length <= 32
    )
  ) {
    throw new Error('submittedAnswer 형식이 올바르지 않습니다');
  }
  if (typeof correct !== 'boolean') throw new Error('correct가 불리언이 아닙니다');
  if (!isNonNegativeInteger(responseMs) || responseMs > 600_000) {
    throw new Error('responseMs 범위가 올바르지 않습니다');
  }
  if (!isNonNegativeInteger(attemptNo) || attemptNo < 1 || attemptNo > 100) {
    throw new Error('attemptNo 범위가 올바르지 않습니다');
  }
  if (!isIsoDate(occurredAt)) throw new Error('occurredAt 형식이 올바르지 않습니다');
  if (typeof contentVersion !== 'string' || !CONTENT_VERSION_PATTERN.test(contentVersion)) {
    throw new Error('contentVersion 형식이 올바르지 않습니다');
  }

  return {
    eventId,
    learnerId,
    deviceId,
    sessionId,
    sequenceNo,
    factId,
    mode: mode as LearningMode,
    tableNo,
    submittedAnswer,
    correct,
    responseMs,
    attemptNo,
    occurredAt,
    contentVersion,
  };
}

function parseBatchResponse(value: unknown): LearningEventBatchResponse {
  if (!isRecord(value) || !isNonNegativeInteger(value.serverCursor)) {
    throw new Error('학습 배치 응답 형식이 올바르지 않습니다');
  }
  if (!Array.isArray(value.rejected)) {
    throw new Error('학습 배치 거부 목록 형식이 올바르지 않습니다');
  }
  const rejected = value.rejected.map((item) => {
    if (!isRecord(item) || !isUuid(item.eventId) || typeof item.code !== 'string' || !item.code) {
      throw new Error('학습 배치 거부 항목 형식이 올바르지 않습니다');
    }
    return { eventId: item.eventId, code: item.code };
  });
  return {
    acceptedEventIds: parseUuidArray(value.acceptedEventIds, 'acceptedEventIds'),
    duplicateEventIds: parseUuidArray(value.duplicateEventIds, 'duplicateEventIds'),
    rejected,
    serverCursor: value.serverCursor,
  };
}

function parseStateResponse(value: unknown): LearningStateResponse {
  if (!isRecord(value) || !Array.isArray(value.snapshots) || !isNonNegativeInteger(value.serverCursor)) {
    throw new Error('학습 상태 응답 형식이 올바르지 않습니다');
  }
  const snapshots = value.snapshots.map((item) => {
    if (
      !isRecord(item) ||
      !isUuid(item.learnerId) ||
      !isNonNegativeInteger(item.revision) || item.revision < 1 ||
      !isNonNegativeInteger(item.serverCursor) ||
      !isRecord(item.state) ||
      !isIsoDate(item.updatedAt)
    ) {
      throw new Error('학습 스냅샷 형식이 올바르지 않습니다');
    }
    return {
      learnerId: item.learnerId,
      revision: item.revision,
      serverCursor: item.serverCursor,
      state: item.state,
      updatedAt: item.updatedAt,
    };
  });
  return { snapshots, serverCursor: value.serverCursor };
}

function parseClaimResponse(value: unknown): LearningClaimResponse {
  if (
    !isRecord(value) ||
    !['create_learner', 'attach_to_existing', 'conflict'].includes(String(value.action)) ||
    !Array.isArray(value.candidates) ||
    value.learnerId !== null && !isUuid(value.learnerId)
  ) {
    throw new Error('게스트 기록 귀속 응답 형식이 올바르지 않습니다');
  }
  const candidates = value.candidates.map((item) => {
    if (
      !isRecord(item) ||
      !isUuid(item.learnerId) ||
      typeof item.displayName !== 'string' || !item.displayName ||
      !isIsoDate(item.updatedAt)
    ) {
      throw new Error('학습자 후보 형식이 올바르지 않습니다');
    }
    return {
      learnerId: item.learnerId,
      displayName: item.displayName,
      updatedAt: item.updatedAt,
    };
  });
  return {
    action: value.action as LearningClaimResponse['action'],
    candidates,
    learnerId: value.learnerId,
  };
}

function parseClaimConfirmResponse(value: unknown): LearningClaimConfirmResponse {
  if (!isRecord(value) || !isUuid(value.learnerId)) {
    throw new Error('게스트 기록 귀속 확정 응답 형식이 올바르지 않습니다');
  }
  return { learnerId: value.learnerId };
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

export async function uploadLearningEvents(
  accessToken: string,
  events: LearningEvent[]
): Promise<LearningEventBatchResponse> {
  if (!accessToken) throw new Error('학습 이벤트 업로드에 액세스 토큰이 필요합니다');
  if (!Array.isArray(events) || events.length < 1 || events.length > 100) {
    throw new Error('학습 이벤트 배치는 1~100건이어야 합니다');
  }
  const validatedEvents = events.map(validateLearningEvent);
  const response = await fetch(`${apiBase()}/v1/learning/events:batch`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ events: validatedEvents }),
  });
  return parseBatchResponse(await readSuccessJson(response));
}

export async function fetchLearningState(
  accessToken: string,
  learnerId?: string
): Promise<LearningStateResponse> {
  if (!accessToken) throw new Error('학습 상태 조회에 액세스 토큰이 필요합니다');
  if (learnerId !== undefined && !isUuid(learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  const query = learnerId ? `?learnerId=${encodeURIComponent(learnerId)}` : '';
  const response = await fetch(`${apiBase()}/v1/learning/state${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  return parseStateResponse(await readSuccessJson(response));
}

export async function inspectLearningClaim(
  accessToken: string,
  installId: string,
  localEventCount: number
): Promise<LearningClaimResponse> {
  if (!accessToken) throw new Error('게스트 기록 귀속에 액세스 토큰이 필요합니다');
  if (!isUuid(installId)) throw new Error('installId 형식이 올바르지 않습니다');
  if (!isNonNegativeInteger(localEventCount) || localEventCount > 1_000_000) {
    throw new Error('localEventCount 범위가 올바르지 않습니다');
  }
  const response = await fetch(`${apiBase()}/v1/learning/claim`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ installId, localEventCount }),
  });
  return parseClaimResponse(await readSuccessJson(response));
}

export async function confirmLearningClaim(
  accessToken: string,
  installId: string,
  learnerId: string
): Promise<LearningClaimConfirmResponse> {
  if (!accessToken) throw new Error('게스트 기록 귀속 확정에 액세스 토큰이 필요합니다');
  if (!isUuid(installId)) throw new Error('installId 형식이 올바르지 않습니다');
  if (!isUuid(learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  const response = await fetch(`${apiBase()}/v1/learning/claim/confirm`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ installId, learnerId }),
  });
  return parseClaimConfirmResponse(await readSuccessJson(response));
}
