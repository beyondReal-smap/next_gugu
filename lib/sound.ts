// 효과음 — Web Audio 합성 (모던하고 절제된 톤). 외부 음원 불필요.
type Win = Window & { webkitAudioContext?: typeof AudioContext };

let ctx: AudioContext | null = null;
let enabled = true;
const KEY = 'gugu.sound';

function ac(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!ctx) {
    const w = window as Win;
    const AC = window.AudioContext || w.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
  }
  if (ctx.state === 'suspended') void ctx.resume();
  return ctx;
}

function tone(freq: number, start: number, dur: number, type: OscillatorType = 'sine', gain = 0.12) {
  const a = ac();
  if (!a) return;
  const t = a.currentTime + start;
  const o = a.createOscillator();
  const g = a.createGain();
  o.type = type;
  o.frequency.setValueAtTime(freq, t);
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(gain, t + 0.01);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g);
  g.connect(a.destination);
  o.start(t);
  o.stop(t + dur + 0.02);
}

export function initSound() {
  if (typeof window === 'undefined') return;
  const s = localStorage.getItem(KEY);
  if (s !== null) enabled = s === '1';
  ac();
}
export function isSoundEnabled() { return enabled; }
export function setSoundEnabled(v: boolean) {
  enabled = v;
  if (typeof window !== 'undefined') localStorage.setItem(KEY, v ? '1' : '0');
  if (v) tone(660, 0, 0.08, 'sine', 0.1);
}

export const playTap = () => enabled && tone(330, 0, 0.04, 'sine', 0.05);
export const playCorrect = () => {
  if (!enabled) return;
  tone(587.33, 0, 0.09, 'sine', 0.12);   // D5
  tone(880, 0.07, 0.12, 'sine', 0.1);    // A5
};
export const playWrong = () => {
  if (!enabled) return;
  tone(220, 0, 0.14, 'sine', 0.09);
  tone(174.61, 0.09, 0.18, 'sine', 0.08);
};
export const playCombo = (combo: number) => {
  if (!enabled) return;
  const base = 523.25;
  const step = Math.min(combo, 12);
  tone(base * Math.pow(2, step / 12), 0, 0.1, 'triangle', 0.1);
};
export const playLevelUp = () => {
  if (!enabled) return;
  [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(f, i * 0.09, 0.18, 'triangle', 0.12));
};
export const playComplete = () => {
  if (!enabled) return;
  [523.25, 659.25, 783.99].forEach((f, i) => tone(f, i * 0.1, 0.2, 'sine', 0.11));
};
