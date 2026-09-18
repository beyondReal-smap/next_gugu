import {
  LearningEvent,
  LearningEventBatchResponse,
  RejectedLearningEvent,
  uploadLearningEvents,
  validateLearningEvent,
} from '../api/learning';

const STORAGE_KEY = 'gugu.learning.outbox.v1';
const MAX_OUTBOX_EVENTS = 5_000;
const BATCH_SIZE = 100;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface LearningOutboxState {
  version: 1;
  events: LearningEvent[];
  serverCursor: number;
  rejected: RejectedLearningEvent[];
}

const EMPTY_STATE: LearningOutboxState = {
  version: 1,
  events: [],
  serverCursor: 0,
  rejected: [],
};

let flushInFlight: Promise<LearningEventBatchResponse | null> | null = null;

function storage(): Storage {
  if (typeof window === 'undefined') throw new Error('학습 아웃박스는 브라우저에서만 사용할 수 있습니다');
  return window.localStorage;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseRejected(value: unknown): RejectedLearningEvent {
  if (
    !isRecord(value) ||
    typeof value.eventId !== 'string' || !UUID_PATTERN.test(value.eventId) ||
    typeof value.code !== 'string' ||
    !value.code
  ) {
    throw new Error('학습 아웃박스 거부 기록이 올바르지 않습니다');
  }
  return { eventId: value.eventId, code: value.code };
}

function parseState(raw: string): LearningOutboxState {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (cause) {
    throw new Error('학습 아웃박스 JSON 파싱에 실패했습니다', { cause });
  }
  if (
    !isRecord(parsed) ||
    parsed.version !== 1 ||
    !Array.isArray(parsed.events) ||
    typeof parsed.serverCursor !== 'number' ||
    !Number.isSafeInteger(parsed.serverCursor) ||
    parsed.serverCursor < 0 ||
    !Array.isArray(parsed.rejected)
  ) {
    throw new Error('학습 아웃박스 저장 형식이 올바르지 않습니다');
  }
  const events = parsed.events.map(validateLearningEvent);
  if (events.length > MAX_OUTBOX_EVENTS) throw new Error('학습 아웃박스 상한을 초과했습니다');
  if (new Set(events.map((event) => event.eventId)).size !== events.length) {
    throw new Error('학습 아웃박스에 중복 eventId가 있습니다');
  }
  return {
    version: 1,
    events,
    serverCursor: parsed.serverCursor,
    rejected: parsed.rejected.map(parseRejected),
  };
}

function readState(): LearningOutboxState {
  const raw = storage().getItem(STORAGE_KEY);
  if (raw === null) return { ...EMPTY_STATE, events: [], rejected: [] };
  return parseState(raw);
}

function writeState(state: LearningOutboxState): void {
  storage().setItem(STORAGE_KEY, JSON.stringify(state));
}

function createEventId(): string {
  if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') {
    throw new Error('안전한 학습 이벤트 UUID를 생성할 수 없습니다');
  }
  return crypto.randomUUID();
}

export function readLearningOutbox(): Readonly<LearningOutboxState> {
  return readState();
}

export function enqueueLearningEvent(
  input: Omit<LearningEvent, 'eventId'> & { eventId?: string }
): LearningEvent {
  const eventId = input.eventId === undefined ? createEventId() : input.eventId;
  const event = validateLearningEvent({ ...input, eventId });
  const state = readState();
  const existing = state.events.find((item) => item.eventId === event.eventId);
  if (existing) {
    if (JSON.stringify(existing) !== JSON.stringify(event)) {
      throw new Error('같은 아이디의 학습 이벤트 내용이 다릅니다');
    }
    return existing;
  }
  if (state.events.length >= MAX_OUTBOX_EVENTS) {
    throw new Error(`학습 아웃박스 상한(${MAX_OUTBOX_EVENTS}건)을 초과했습니다`);
  }
  writeState({ ...state, events: [...state.events, event] });
  return event;
}

async function runFlush(accessToken: string): Promise<LearningEventBatchResponse | null> {
  const initial = readState();
  const batch = initial.events.slice(0, BATCH_SIZE);
  if (batch.length === 0) return null;

  const response = await uploadLearningEvents(accessToken, batch);
  const batchIds = new Set(batch.map((event) => event.eventId));
  const completedIds = new Set([...response.acceptedEventIds, ...response.duplicateEventIds]);
  for (const item of response.rejected) {
    if (!batchIds.has(item.eventId)) {
      throw new Error('서버가 현재 배치에 없는 거부 이벤트를 반환했습니다');
    }
  }
  for (const eventId of completedIds) {
    if (!batchIds.has(eventId)) {
      throw new Error('서버가 현재 배치에 없는 완료 이벤트를 반환했습니다');
    }
  }

  // 업로드 중 추가된 이벤트는 재독하여 보존한다.
  const current = readState();
  const latestRejections = new Map(current.rejected.map((item) => [item.eventId, item]));
  for (const item of response.rejected) latestRejections.set(item.eventId, item);
  for (const eventId of completedIds) latestRejections.delete(eventId);
  writeState({
    version: 1,
    events: current.events.filter((event) => !completedIds.has(event.eventId)),
    serverCursor: Math.max(current.serverCursor, response.serverCursor),
    // 거부된 이벤트는 자동 삭제하지 않고 재시도 가능한 큐에 남겨둔다.
    rejected: [...latestRejections.values()],
  });
  return response;
}

export async function flushLearningOutbox(
  accessToken: string
): Promise<LearningEventBatchResponse | null> {
  if (!accessToken) throw new Error('학습 아웃박스 업로드에 액세스 토큰이 필요합니다');
  if (flushInFlight) return flushInFlight;
  const pending = runFlush(accessToken);
  flushInFlight = pending;
  try {
    return await pending;
  } finally {
    if (flushInFlight === pending) flushInFlight = null;
  }
}
