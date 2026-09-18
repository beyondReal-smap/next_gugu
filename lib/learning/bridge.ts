// 세션 커밋 → 동기화 계층. GameProvider 가 호출하고 LearningSync 가 구독한다.
import { SessionResult } from '../types';

type Listener = (result: SessionResult) => void;

let listener: Listener | null = null;

export function setSessionCommitListener(fn: Listener | null): void {
  listener = fn;
}

export function notifySessionCommitted(result: SessionResult): void {
  if (!listener) return;
  try {
    listener(result);
  } catch (e) {
    console.error('학습 동기화 리스너 실패:', e);
  }
}
