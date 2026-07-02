// 세션 결과를 게임 상태에 반영하는 순수 계산 로직
import { GameState, SessionResult, CommitResult, TableMastery } from '../types';
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
};

// 앱 진입 시 스트릭/데일리 갱신
export function applyVisit(state: GameState): GameState {
  const today = todayStr();
  let s = { ...state };
  if (s.dailyDate !== today) {
    s.dailyDate = today;
    s.dailyCorrect = 0;
  }
  if (s.lastPlayedDate !== today) {
    if (s.lastPlayedDate === shift(-1)) s.streak = (s.streak || 0) + 1;
    else s.streak = 1;
    s.lastPlayedDate = today;
  } else if (!s.streak) {
    s.streak = 1;
  }
  return s;
}

function starsFor(accuracy: number, avgMs: number, count: number): number {
  if (count === 0) return 0;
  if (accuracy >= 0.95 && avgMs > 0 && avgMs < 3000) return 3;
  if (accuracy >= 0.8) return 2;
  return 1;
}

// 세션 결과 반영 → 새 상태 + 부가 정보(CommitResult)
export function applySession(state: GameState, result: SessionResult): { next: GameState; commit: CommitResult } {
  const today = todayStr();
  let s: GameState = { ...state };
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
      xpEarned += xpForAnswer({ mode: result.mode, combo, ms: ans.ms });
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

  // 최근 추이 (cap 20)
  s.recentAccuracy = [...s.recentAccuracy, Math.round(accuracy * 100)].slice(-20);
  s.recentAvgMs = [...s.recentAvgMs, avgMs].slice(-20);

  // 마스터리 (단 집중 세션)
  let newStars = 0;
  let improvedStars = false;
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
  const def = MODES[result.mode];
  let score: number | null = null;
  let isNewBest = false;
  if (def.scored) {
    score = correctCount;
    const prevBest = s.bestScores[result.mode] ?? 0;
    if (score > prevBest) {
      isNewBest = true;
      s.bestScores = { ...s.bestScores, [result.mode]: score };
    }
  }
  if (!s.modesPlayed.includes(result.mode)) s.modesPlayed = [...s.modesPlayed, result.mode];

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
    },
  };
}
