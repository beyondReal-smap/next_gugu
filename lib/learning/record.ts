// SessionResult → LearningEvent 변환 후 아웃박스 적재. eventId 는 아웃박스가 한 번만 발급한다.
import { LearningEvent, LearningMode } from '../api/learning';
import { enqueueLearningEvent } from '../sync/outbox';
import { SessionResult } from '../types';

export const CONTENT_VERSION = '1.0.0';

const API_MODES = new Set<LearningMode>([
  'practice', 'timeAttack', 'challenge', 'survival', 'missing', 'truefalse',
]);

function apiMode(mode: SessionResult['mode']): LearningMode {
  if (API_MODES.has(mode as LearningMode)) return mode as LearningMode;
  // 서버 계약에 adventure 가 없음 — 출제 단/식은 factId·tableNo 로 남긴다
  return 'practice';
}

function expectedAnswer(result: SessionResult, a: number, b: number): number {
  return result.mode === 'missing' ? b : a * b;
}

export function sessionToEvents(
  result: SessionResult,
  ids: { learnerId: string; deviceId: string; sessionId: string }
): Omit<LearningEvent, 'eventId'>[] {
  const mode = apiMode(result.mode);
  const occurredAt = new Date().toISOString();
  const seen: Record<string, number> = {};
  const events: Omit<LearningEvent, 'eventId'>[] = [];

  result.answers.forEach((ans, i) => {
    const factId = `${ans.a}x${ans.b}`;
    const key = factId;
    seen[key] = (seen[key] || 0) + 1;
    // factId 계약은 2~9단 × 1~9. 범위를 벗어나면 이 문항만 건너뛴다(세션 전체 적재 실패 방지).
    if (ans.a < 2 || ans.a > 9 || ans.b < 1 || ans.b > 9) {
      console.error('학습 이벤트 생략: factId 범위 밖', ans);
      return;
    }
    const given = ans.given;
    const submittedAnswer =
      typeof given === 'number' || typeof given === 'boolean' || (typeof given === 'string' && given)
        ? given
        : ans.correct
          ? expectedAnswer(result, ans.a, ans.b)
          : 0;
    // tableNo 는 세션 단이 아니라 문항 단. 세션 table 과 다르면 서버가 거부하므로 문항 기준.
    const tableNo = ans.a;
    events.push({
      learnerId: ids.learnerId,
      deviceId: ids.deviceId,
      sessionId: ids.sessionId,
      sequenceNo: i,
      factId,
      mode,
      tableNo,
      submittedAnswer,
      correct: ans.correct,
      responseMs: Math.max(0, Math.min(600_000, Math.round(ans.ms))),
      attemptNo: seen[key],
      occurredAt,
      contentVersion: CONTENT_VERSION,
    });
  });
  return events;
}

export function enqueueSessionEvents(
  result: SessionResult,
  ids: { learnerId: string; deviceId: string; sessionId: string }
): void {
  const events = sessionToEvents(result, ids);
  for (const ev of events) {
    enqueueLearningEvent(ev);
  }
}
