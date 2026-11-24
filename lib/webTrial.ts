// 웹 체험판 제한 — 웹(/play)은 하루 판 수를 제한하고, 넘으면 앱 설치로 안내한다.
// 기기 로컬 저장이라 저장소를 지우면 초기화되는 '부드러운' 제한이다(설치 유도 목적, 보안 장치 아님).
export const WEB_TRIAL_DAILY_LIMIT = 3;

const STORAGE_KEY = 'gugu.webTrial.v1';

interface TrialRecord {
  date: string; // 로컬 날짜 YYYY-MM-DD
  count: number;
}

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// 외부 저장소 경계 검증 — 형태가 깨졌거나 날짜가 지났으면 오늘 0판으로 본다
function read(): TrialRecord {
  const fresh = { date: today(), count: 0 };
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return fresh;
  try {
    const parsed = JSON.parse(raw) as Partial<TrialRecord>;
    if (parsed.date !== fresh.date || typeof parsed.count !== 'number' || parsed.count < 0) return fresh;
    return { date: parsed.date, count: Math.floor(parsed.count) };
  } catch (e) {
    console.error('웹 체험 기록 파싱 실패 — 초기화:', e);
    return fresh;
  }
}

export function remainingPlays(): number {
  return Math.max(0, WEB_TRIAL_DAILY_LIMIT - read().count);
}

/** 한 판을 차감한다. 이미 한도를 다 썼으면 차감하지 않고 false. */
export function consumePlay(): boolean {
  const rec = read();
  if (rec.count >= WEB_TRIAL_DAILY_LIMIT) return false;
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ date: rec.date, count: rec.count + 1 }));
  return true;
}
