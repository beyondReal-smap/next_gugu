// 서버 스냅샷 → 로컬 GameState. 이벤트 집계만 보수적으로 합친다. XP/별/스트릭/어드벤처는 지우지 않는다.
import { GameState } from '../types';

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

export function mergeServerSnapshot(local: GameState, state: Record<string, unknown>): GameState {
  const next: GameState = { ...local, wrongPool: { ...local.wrongPool } };

  const totalCorrect = state.totalCorrect;
  const totalWrong = state.totalWrong;
  if (typeof totalCorrect === 'number' && Number.isFinite(totalCorrect) && totalCorrect > next.totalCorrect) {
    next.totalCorrect = Math.floor(totalCorrect);
  }
  if (typeof totalWrong === 'number' && Number.isFinite(totalWrong) && totalWrong > next.totalWrong) {
    next.totalWrong = Math.floor(totalWrong);
  }

  if (isRecord(state.facts)) {
    for (const [key, raw] of Object.entries(state.facts)) {
      if (!/^[2-9]x[1-9]$/.test(key) || !isRecord(raw)) continue;
      const wrong = raw.wrong;
      if (typeof wrong !== 'number' || wrong <= 0) continue;
      const cur = next.wrongPool[key] || 0;
      if (cur === 0) next.wrongPool[key] = Math.min(6, Math.floor(wrong));
    }
  }
  return next;
}
