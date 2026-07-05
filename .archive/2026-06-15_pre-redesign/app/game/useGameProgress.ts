import { useState, useEffect, useCallback, useRef } from 'react';
import { getLevelInfo, xpForCorrect, LevelInfo } from './levelSystem';
import { GameStats, newlyUnlocked, AchievementDef } from './achievements';

// 게임 진행 상태 (기존 'multiplicationGame' 저장소와 분리)
const STORAGE_KEY = 'guguGameProgress';

interface ProgressState {
  totalXp: number;
  tableStars: Record<number, number>; // 단별 별점 0~3
  unlockedAchievements: string[];
  totalCorrect: number;
  maxCombo: number;
  timeAttackClears: number;
  dailyStreak: number;
  lastPlayedDate: string; // YYYY-MM-DD
}

const initialState: ProgressState = {
  totalXp: 0,
  tableStars: {},
  unlockedAchievements: [],
  totalCorrect: 0,
  maxCombo: 0,
  timeAttackClears: 0,
  dailyStreak: 0,
  lastPlayedDate: '',
};

function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function yesterdayStr(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function computeStats(s: ProgressState): GameStats {
  const starCount = Object.values(s.tableStars).reduce((a, b) => a + b, 0);
  const masteredCount = Object.values(s.tableStars).filter((v) => v >= 1).length;
  return {
    totalCorrect: s.totalCorrect,
    maxCombo: s.maxCombo,
    level: getLevelInfo(s.totalXp).level,
    masteredCount,
    starCount,
    dailyStreak: s.dailyStreak,
    timeAttackClears: s.timeAttackClears,
  };
}

export interface CorrectResult {
  xpGained: number;
  leveledUp: boolean;
  newLevel: number;
  unlocked: AchievementDef[];
}

export interface ClearResult {
  stars: number;
  improved: boolean;
  unlocked: AchievementDef[];
}

export function useGameProgress() {
  const [state, setState] = useState<ProgressState>(initialState);
  const [loaded, setLoaded] = useState(false);
  const stateRef = useRef(state);
  stateRef.current = state;

  // 최초 로드 + 출석 갱신
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      let s: ProgressState = raw ? { ...initialState, ...JSON.parse(raw) } : { ...initialState };

      // 출석 스트릭 갱신
      const today = todayStr();
      if (s.lastPlayedDate !== today) {
        if (s.lastPlayedDate === yesterdayStr()) {
          s.dailyStreak = (s.dailyStreak || 0) + 1;
        } else {
          s.dailyStreak = 1;
        }
        s.lastPlayedDate = today;
      } else if (!s.dailyStreak) {
        s.dailyStreak = 1;
      }

      setState(s);
    } catch (e) {
      console.error('진행상태 로드 실패:', e);
    } finally {
      setLoaded(true);
    }
  }, []);

  // 영속화
  useEffect(() => {
    if (!loaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      console.error('진행상태 저장 실패:', e);
    }
  }, [state, loaded]);

  // 정답 기록: XP/콤보/정답수 갱신 → 레벨업·업적 감지
  const recordCorrect = useCallback(
    (params: { mode: 'practice' | 'timeAttack'; combo: number; table: number }): CorrectResult => {
      const prev = stateRef.current;
      const prevLevel = getLevelInfo(prev.totalXp).level;
      const xpGained = xpForCorrect({ mode: params.mode, combo: params.combo });

      const next: ProgressState = {
        ...prev,
        totalXp: prev.totalXp + xpGained,
        totalCorrect: prev.totalCorrect + 1,
        maxCombo: Math.max(prev.maxCombo, params.combo),
      };
      const newLevel = getLevelInfo(next.totalXp).level;
      const leveledUp = newLevel > prevLevel;

      const unlocked = newlyUnlocked(computeStats(next), next.unlockedAchievements);
      if (unlocked.length) {
        next.unlockedAchievements = [...next.unlockedAchievements, ...unlocked.map((u) => u.id)];
      }

      setState(next);
      return { xpGained, leveledUp, newLevel, unlocked };
    },
    [],
  );

  // 단 클리어(타임어택 성공) 기록: 별점 부여
  const recordTableClear = useCallback(
    (table: number, stars: number): ClearResult => {
      const prev = stateRef.current;
      const prevStars = prev.tableStars[table] || 0;
      const improved = stars > prevStars;
      const bestStars = Math.max(prevStars, stars);

      const next: ProgressState = {
        ...prev,
        tableStars: { ...prev.tableStars, [table]: bestStars },
        timeAttackClears: prev.timeAttackClears + 1,
      };

      const unlocked = newlyUnlocked(computeStats(next), next.unlockedAchievements);
      if (unlocked.length) {
        next.unlockedAchievements = [...next.unlockedAchievements, ...unlocked.map((u) => u.id)];
      }

      setState(next);
      return { stars: bestStars, improved, unlocked };
    },
    [],
  );

  const resetProgress = useCallback(() => {
    const today = todayStr();
    setState({ ...initialState, dailyStreak: 1, lastPlayedDate: today });
  }, []);

  const levelInfo: LevelInfo = getLevelInfo(state.totalXp);
  const stats = computeStats(state);

  return {
    loaded,
    levelInfo,
    tableStars: state.tableStars,
    starCount: stats.starCount,
    masteredCount: stats.masteredCount,
    unlockedAchievements: state.unlockedAchievements,
    dailyStreak: state.dailyStreak,
    totalCorrect: state.totalCorrect,
    maxCombo: state.maxCombo,
    recordCorrect,
    recordTableClear,
    resetProgress,
  };
}
