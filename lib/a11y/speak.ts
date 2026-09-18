// 문제 TTS — Web Speech API. 외부 음원 불필요.
// 켜짐/꺼짐은 PrefsProvider(gugu.prefs.v1)가 담당한다.

const ONES = ['', '일', '이', '삼', '사', '오', '육', '칠', '팔', '구'];

/** 1~99를 한국어 수사로. 구구단 범위(최대 81)만 사용. */
export function koreanNumber(n: number): string {
  if (!Number.isInteger(n) || n < 0 || n > 99) {
    throw new Error(`koreanNumber: 0~99 정수만 지원합니다 (받은 값: ${n})`);
  }
  if (n === 0) return '영';
  if (n < 10) return ONES[n];
  const tens = Math.floor(n / 10);
  const ones = n % 10;
  const tenWord = tens === 1 ? '십' : `${ONES[tens]}십`;
  return ones === 0 ? tenWord : `${tenWord}${ONES[ones]}`;
}

export function problemUtterance(a: number, b: number): string {
  return `${koreanNumber(a)} 곱하기 ${koreanNumber(b)}`;
}

function speakRaw(text: string, label: string): void {
  if (typeof window === 'undefined') return;
  if (!('speechSynthesis' in window)) {
    console.error(`${label} 실패: 이 환경은 speechSynthesis를 지원하지 않습니다`);
    return;
  }
  if (!text.trim()) {
    console.error(`${label} 실패: 읽을 문구가 비어 있습니다`);
    return;
  }
  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.lang = 'ko-KR';
  u.rate = 0.92;
  u.onerror = (ev) => {
    console.error(`${label} 실패:`, ev.error);
  };
  window.speechSynthesis.speak(u);
}

export function speakProblem(a: number, b: number): void {
  speakRaw(problemUtterance(a, b), '문제 낭독');
}

/** 코치 힌트 등 자유 문구. Wave 1 TTS 규칙(ko-KR, cancel 후 재생)을 그대로 쓴다. */
export function speakText(text: string): void {
  speakRaw(text, '문구 낭독');
}

export function stopSpeaking(): void {
  if (typeof window === 'undefined') return;
  if (!('speechSynthesis' in window)) return;
  window.speechSynthesis.cancel();
}
