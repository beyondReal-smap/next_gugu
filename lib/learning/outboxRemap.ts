// claim 확정 후 큐의 learnerId 를 서버 id 로 맞춘다.
// outbox.ts 는 쓰기 API 가 eventId 불변+내용 불가라, 미수락 이벤트의 learnerId 만 같은 eventId 로 고친다.
import { validateLearningEvent } from '../api/learning';
import { readLearningOutbox } from '../sync/outbox';

const STORAGE_KEY = 'gugu.learning.outbox.v1';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function remapOutboxLearnerId(learnerId: string): { rewritten: number; remaining: number } {
  if (typeof learnerId !== 'string' || !UUID_PATTERN.test(learnerId)) {
    throw new Error('learnerId 형식이 올바르지 않습니다');
  }
  if (typeof window === 'undefined') throw new Error('아웃박스 재바인딩은 브라우저에서만 가능합니다');

  const state = readLearningOutbox();
  let rewritten = 0;
  const events = state.events.map((event) => {
    if (event.learnerId === learnerId) return event;
    rewritten += 1;
    return validateLearningEvent({ ...event, learnerId });
  });
  if (rewritten === 0) return { rewritten: 0, remaining: events.length };

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
    version: 1,
    events,
    serverCursor: state.serverCursor,
    rejected: state.rejected,
  }));
  return { rewritten, remaining: events.length };
}
