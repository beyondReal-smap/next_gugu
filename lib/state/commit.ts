// 세션 결과를 게임 상태에 반영하는 순수 계산 로직
import { GameState, SessionResult, CommitResult, TableMastery, DayLogEntry } from '../types';
import { getLevelInfo, xpForAnswer } from '../level';
import { MODES } from '../modes';
import { updateWrongPool } from '../problems';
import { newlyUnlocked } from '../achievements';

export function todayStr(d: Date = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function shift(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return todayStr(d);
}

export const DEFAULT_STATE: GameState = {
  version: 1,
  totalXp: 0,
  streak: 0,
  lastPlayedDate: '',
  dailyGoal: 20,
  dailyDate: '',
  dailyCorrect: 0,
  tableMastery: {},
  unlockedAchievements: [],
  totalCorrect: 0,
  totalWrong: 0,
  maxCombo: 0,
  recentAccuracy: [],
  recentAvgMs: [],
  wrongPool: {},
  bestScores: {},
  modesPlayed: [],
  onboarded: false,
  dayLog: [],
};

const DAY_LOG_CAP = 56; // 8주

export function normalizeDayLog(raw: unknown): DayLogEntry[] {
  if (!Array.isArray(raw)) return [];
  const out: DayLogEntry[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const d = item as Partial<DayLogEntry>;
    if (typeof d.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d.date)) continue;
    const misses: Record<string, number> = {};
    if (d.misses && typeof d.misses === 'object') {
      for (const [k, v] of Object.entries(d.misses)) {
        if (typeof v === 'number' && v > 0) misses[k] = v;
      }
    }
    out.push({
      date: d.date,
      correct: typeof d.correct === 'number' ? d.correct : 0,
      wrong: typeof d.wrong === 'number' ? d.wrong : 0,
      msSum: typeof d.msSum === 'number' ? d.msSum : 0,
      misses,
    });
  }
  return out.slice(-DAY_LOG_CAP);
}

function appendDayLog(log: DayLogEntry[], result: SessionResult, date: string): DayLogEntry[] {
  const next = [...log];
  let i = next.findIndex((d) => d.date === date);
  if (i < 0) {
    next.push({ date, correct: 0, wrong: 0, msSum: 0, misses: {} });
    i = next.length - 1;
  }
  const bucket: DayLogEntry = { ...next[i], misses: { ...next[i].misses } };
  for (const ans of result.answers) {
    if (ans.correct) bucket.correct += 1;
    else {
      bucket.wrong += 1;
      const key = `${ans.a}x${ans.b}`;
      bucket.misses[key] = (bucket.misses[key] || 0) + 1;
    }
    bucket.msSum += ans.ms;
  }
  next[i] = bucket;
  next.sort((a, b) => a.date.localeCompare(b.date));
  return next.slice(-DAY_LOG_CAP);
}

// 앱 진입 시 데일리 골 날짜만 갱신.
// 스트릭은 학습 자격(세션 완료 또는 일일 정답 하한)이 있을 때만 qualifyStreak 로 올린다.
// 기존 lastPlayedDate/streak 값은 소급해서 깎지 않는다.
export function applyVisit(state: GameState): GameState {
  const today = todayStr();
  let s = { ...state, dayLog: normalizeDayLog(state.dayLog) };
  if (s.dailyDate !== today) {
    s.dailyDate = today;
    s.dailyCorrect = 0;
  }
  return s;
}

// 오늘 스트릭 자격 부여. 이미 오늘 자격이면 그대로.
function qualifyStreak(s: GameState): GameState {
  const today = todayStr();
  if (s.lastPlayedDate === today) return s;
  const next = { ...s };
  if (s.lastPlayedDate === shift(-1)) next.streak = (s.streak || 0) + 1;
  else next.streak = 1;
  next.lastPlayedDate = today;
  return next;
}

function starsFor(accuracy: number, avgMs: number, count: number): number {
  if (count === 0) return 0;
  if (accuracy >= 0.95 && avgMs > 0 && avgMs < 3000) return 3;
  if (accuracy >= 0.8) return 2;
  return 1;
}

// 세션 결과 반영 → 새 상태 + 부가 정보(CommitResult)
// partial=true 이면 XP·오답풀·일일 정답만 반영하고, 한 판 완료 통계(별/신기록/추이/모드 탐험)는 건너뛴다.
export function applySession(state: GameState, result: SessionResult): { next: GameState; commit: CommitResult } {
  const today = todayStr();
  let s: GameState = { ...state };
  const partial = result.partial === true;
  // 날짜 경계
  if (s.dailyDate !== today) { s.dailyDate = today; s.dailyCorrect = 0; }

  const prevLevel = getLevelInfo(s.totalXp).level;
  const prevDailyCorrect = s.dailyCorrect;

  // XP (콤보 재구성) + 통계 + 오답풀
  let xpEarned = 0;
  let combo = 0;
  let correctCount = 0;
  let wrongPool = { ...s.wrongPool };
  let msSum = 0;
  for (const ans of result.answers) {
    if (ans.correct) {
      combo += 1;
      correctCount += 1;
      const raw = xpForAnswer({ mode: result.mode, combo, ms: ans.ms });
      const scale = result.xpScale != null && result.xpScale >= 0 ? result.xpScale : 1;
      xpEarned += Math.max(0, Math.round(raw * scale));
    } else {
      combo = 0;
    }
    msSum += ans.ms;
    wrongPool = updateWrongPool(wrongPool, ans.a, ans.b, ans.correct);
  }
  const wrongCount = result.answers.length - correctCount;
  const accuracy = result.answers.length ? correctCount / result.answers.length : 0;
  const avgMs = result.answers.length ? Math.round(msSum / result.answers.length) : 0;

  s.totalXp += xpEarned;
  s.totalCorrect += correctCount;
  s.totalWrong += wrongCount;
  s.maxCombo = Math.max(s.maxCombo, result.maxCombo);
  s.wrongPool = wrongPool;
  s.dailyCorrect = s.dailyCorrect + correctCount;
  s.dayLog = appendDayLog(s.dayLog ?? [], result, today);

  let newStars = 0;
  let improvedStars = false;
  let score: number | null = null;
  let isNewBest = false;
  const def = MODES[result.mode];

  if (!partial) {
    // 최근 추이 (cap 20) — 한 판을 끝냈을 때만
    s.recentAccuracy = [...s.recentAccuracy, Math.round(accuracy * 100)].slice(-20);
    s.recentAvgMs = [...s.recentAvgMs, avgMs].slice(-20);

    // 마스터리 (단 집중 세션)
    if (result.table != null) {
      const prev: TableMastery = s.tableMastery[result.table] || { stars: 0, bestAccuracy: 0, bestAvgMs: 0, plays: 0 };
      const sessionStars = starsFor(accuracy, avgMs, result.answers.length);
      const stars = Math.max(prev.stars, sessionStars);
      improvedStars = stars > prev.stars;
      newStars = stars;
      s.tableMastery = {
        ...s.tableMastery,
        [result.table]: {
          stars,
          bestAccuracy: Math.max(prev.bestAccuracy, accuracy),
          bestAvgMs: prev.bestAvgMs === 0 ? avgMs : Math.min(prev.bestAvgMs, avgMs),
          plays: prev.plays + 1,
        },
      };
    }

    // 모드 기록 — 점수형 모드(챌린지/서바이벌) 최고 기록 + 플레이한 모드
    if (def.scored) {
      score = correctCount;
      const prevBest = s.bestScores[result.mode] ?? 0;
      if (score > prevBest) {
        isNewBest = true;
        s.bestScores = { ...s.bestScores, [result.mode]: score };
      }
    }
    if (!s.modesPlayed.includes(result.mode)) s.modesPlayed = [...s.modesPlayed, result.mode];

    // 세션 1회 완료 → 스트릭 자격
    s = qualifyStreak(s);
  }

  // 부분 커밋이어도 오늘 정답이 하한에 도달하면 스트릭 자격
  const streakFloor = Math.min(s.dailyGoal, 10);
  if (s.dailyCorrect >= streakFloor) s = qualifyStreak(s);

  // 업적
  const unlocked = newlyUnlocked(s);
  if (unlocked.length) s.unlockedAchievements = [...s.unlockedAchievements, ...unlocked];

  const newLevel = getLevelInfo(s.totalXp).level;
  const goalReached = prevDailyCorrect < s.dailyGoal && s.dailyCorrect >= s.dailyGoal;

  return {
    next: s,
    commit: {
      xpEarned,
      leveledUp: newLevel > prevLevel,
      newLevel,
      unlocked,
      table: result.table,
      newStars,
      improvedStars,
      goalReached,
      score,
      isNewBest,
      partial,
    },
  };
}
