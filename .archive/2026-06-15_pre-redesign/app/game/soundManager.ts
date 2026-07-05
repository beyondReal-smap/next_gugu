// 효과음 매니저 — Web Audio API로 합성음을 생성 (외부 음원 파일 불필요)
// 사용자 제스처 이후에만 AudioContext가 활성화되므로 lazy 초기화한다.

type Win = Window & { webkitAudioContext?: typeof AudioContext };

let ctx: AudioContext | null = null;
let enabled = true;
const STORAGE_KEY = 'guguSoundEnabled';

function getCtx(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!ctx) {
    const w = window as Win;
    const AC = window.AudioContext || w.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
  }
  if (ctx.state === 'suspended') {
    void ctx.resume();
  }
  return ctx;
}

// 단일 톤 재생 (주파수, 시작 오프셋, 길이, 음색, 음량)
function tone(
  freq: number,
  start: number,
  duration: number,
  type: OscillatorType = 'sine',
  gain = 0.18,
) {
  const audio = getCtx();
  if (!audio) return;
  const t0 = audio.currentTime + start;
  const osc = audio.createOscillator();
  const g = audio.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  // 부드러운 attack/decay 엔벨로프
  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(gain, t0 + 0.012);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(g);
  g.connect(audio.destination);
  osc.start(t0);
  osc.stop(t0 + duration + 0.02);
}

export function initSound() {
  if (typeof window === 'undefined') return;
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved !== null) enabled = saved === '1';
  // 제스처 컨텍스트에서 호출되면 오디오 컨텍스트를 깨워둔다
  getCtx();
}

export function isSoundEnabled() {
  return enabled;
}

export function setSoundEnabled(v: boolean) {
  enabled = v;
  if (typeof window !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, v ? '1' : '0');
  }
  if (v) tone(880, 0, 0.12, 'triangle', 0.15); // 켤 때 확인음
}

// 키패드 탭
export function playClick() {
  if (!enabled) return;
  tone(420, 0, 0.05, 'square', 0.07);
}

// 정답: 밝은 2음 상승
export function playCorrect() {
  if (!enabled) return;
  tone(659.25, 0, 0.1, 'triangle', 0.16); // E5
  tone(987.77, 0.08, 0.16, 'triangle', 0.16); // B5
}

// 오답: 낮은 부저 2음
export function playWrong() {
  if (!enabled) return;
  tone(196, 0, 0.16, 'sawtooth', 0.1); // G3
  tone(146.83, 0.1, 0.22, 'sawtooth', 0.1); // D3
}

// 콤보: 콤보 수에 따라 음정이 올라가는 상승음
export function playCombo(combo: number) {
  if (!enabled) return;
  const base = 523.25; // C5
  const step = Math.min(combo, 12);
  tone(base * Math.pow(2, step / 12), 0, 0.14, 'triangle', 0.17);
  tone(base * Math.pow(2, (step + 4) / 12), 0.07, 0.16, 'sine', 0.14);
}

// 별 획득: 반짝이는 고음
export function playStar() {
  if (!enabled) return;
  tone(1318.51, 0, 0.08, 'triangle', 0.14); // E6
  tone(1567.98, 0.07, 0.12, 'triangle', 0.14); // G6
  tone(2093.0, 0.14, 0.16, 'sine', 0.12); // C7
}

// 레벨업: 팡파레 아르페지오
export function playLevelUp() {
  if (!enabled) return;
  const notes = [523.25, 659.25, 783.99, 1046.5]; // C5 E5 G5 C6
  notes.forEach((f, i) => tone(f, i * 0.1, 0.2, 'triangle', 0.17));
  tone(1318.51, 0.4, 0.3, 'sine', 0.14); // 마무리 E6
}

// 단계 클리어/축하 팡파레
export function playFanfare() {
  if (!enabled) return;
  const notes = [523.25, 523.25, 523.25, 659.25, 783.99]; // C C C E G
  const times = [0, 0.12, 0.24, 0.4, 0.56];
  notes.forEach((f, i) => tone(f, times[i], 0.22, 'triangle', 0.17));
}
