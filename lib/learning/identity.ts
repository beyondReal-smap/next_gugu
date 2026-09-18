// 기기 installId / 학습자 id — 서버 동기화용. 로컬 진행(gugu.progress.v1)과 분리.

const STORAGE_KEY = 'gugu.learning.identity.v1';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface LearningIdentity {
  version: 1;
  installId: string;
  learnerId: string | null;
  claimedUserId: string | null;
}

function createUuid(): string {
  if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') {
    throw new Error('안전한 UUID를 생성할 수 없습니다');
  }
  return crypto.randomUUID();
}

function isUuid(v: unknown): v is string {
  return typeof v === 'string' && UUID_PATTERN.test(v);
}

function empty(installId: string): LearningIdentity {
  return { version: 1, installId, learnerId: null, claimedUserId: null };
}

function parseIdentity(raw: string): LearningIdentity {
  const parsed = JSON.parse(raw) as Partial<LearningIdentity>;
  if (!parsed || typeof parsed !== 'object') throw new Error('학습 신원 형식이 객체가 아닙니다');
  const installId = isUuid(parsed.installId) ? parsed.installId : createUuid();
  return {
    version: 1,
    installId,
    learnerId: isUuid(parsed.learnerId) ? parsed.learnerId : null,
    claimedUserId: typeof parsed.claimedUserId === 'string' ? parsed.claimedUserId : null,
  };
}

export function readLearningIdentity(): LearningIdentity {
  if (typeof window === 'undefined') throw new Error('학습 신원은 브라우저에서만 사용할 수 있습니다');
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === null) {
    const created = empty(createUuid());
    localStorage.setItem(STORAGE_KEY, JSON.stringify(created));
    return created;
  }
  try {
    return parseIdentity(raw);
  } catch (e) {
    console.error('학습 신원 로드 실패, 재발급:', e);
    const created = empty(createUuid());
    localStorage.setItem(STORAGE_KEY, JSON.stringify(created));
    return created;
  }
}

export function writeLearningIdentity(next: LearningIdentity): void {
  if (typeof window === 'undefined') throw new Error('학습 신원은 브라우저에서만 사용할 수 있습니다');
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

export function bindLearner(userId: string, learnerId: string): LearningIdentity {
  if (!isUuid(learnerId)) throw new Error('learnerId 형식이 올바르지 않습니다');
  const cur = readLearningIdentity();
  const next: LearningIdentity = { ...cur, learnerId, claimedUserId: userId };
  writeLearningIdentity(next);
  return next;
}
