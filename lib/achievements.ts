// 업적 정의 및 판정
import { GameState } from './types';
import { getLevelInfo } from './level';

export interface AchievementDef {
  id: string;
  name: string;
  description: string;
  icon: string; // lucide 아이콘 이름
  check: (s: GameState) => boolean;
}

function totalStars(s: GameState): number {
  return Object.values(s.tableMastery).reduce((a, m) => a + m.stars, 0);
}
function masteredTables(s: GameState): number {
  return Object.values(s.tableMastery).filter((m) => m.stars >= 1).length;
}

export const ACHIEVEMENTS: AchievementDef[] = [
  { id: 'first_correct', name: '첫 정답', description: '처음으로 정답을 맞혔어요', icon: 'Check', check: (s) => s.totalCorrect >= 1 },
  { id: 'correct_100', name: '백 문제 돌파', description: '누적 100문제 정답', icon: 'Target', check: (s) => s.totalCorrect >= 100 },
  { id: 'correct_500', name: '오백 문제 돌파', description: '누적 500문제 정답', icon: 'Award', check: (s) => s.totalCorrect >= 500 },
  { id: 'combo_10', name: '콤보 10', description: '한 세션에서 10연속 정답', icon: 'Zap', check: (s) => s.maxCombo >= 10 },
  { id: 'combo_20', name: '콤보 20', description: '20연속 정답 달성', icon: 'Flame', check: (s) => s.maxCombo >= 20 },
  { id: 'streak_3', name: '3일 연속', description: '3일 연속 학습', icon: 'CalendarCheck', check: (s) => s.streak >= 3 },
  { id: 'streak_7', name: '일주일 개근', description: '7일 연속 학습', icon: 'CalendarHeart', check: (s) => s.streak >= 7 },
  { id: 'streak_30', name: '한 달 개근', description: '30일 연속 학습', icon: 'Crown', check: (s) => s.streak >= 30 },
  { id: 'first_master', name: '첫 마스터', description: '한 단을 마스터(별 획득)', icon: 'Star', check: (s) => masteredTables(s) >= 1 },
  { id: 'master_all', name: '구구단 완전정복', description: '2~9단 모두 마스터', icon: 'GraduationCap', check: (s) => masteredTables(s) >= 8 },
  { id: 'stars_12', name: '별 수집가', description: '별 12개 수집', icon: 'Sparkles', check: (s) => totalStars(s) >= 12 },
  { id: 'stars_24', name: '올스타', description: '모든 단 별 3개', icon: 'Trophy', check: (s) => totalStars(s) >= 24 },
  { id: 'level_5', name: '레벨 5', description: '레벨 5 도달', icon: 'TrendingUp', check: (s) => getLevelInfo(s.totalXp).level >= 5 },
  { id: 'level_15', name: '레벨 15', description: '레벨 15 도달', icon: 'Rocket', check: (s) => getLevelInfo(s.totalXp).level >= 15 },
  { id: 'challenge_15', name: '번개 계산', description: '60초 챌린지에서 15점 달성', icon: 'Timer', check: (s) => (s.bestScores?.challenge ?? 0) >= 15 },
  { id: 'survival_20', name: '생존왕', description: '서바이벌에서 20문제 생존', icon: 'HeartPulse', check: (s) => (s.bestScores?.survival ?? 0) >= 20 },
  { id: 'mode_explorer', name: '모드 탐험가', description: '모든 게임 모드를 플레이', icon: 'Compass', check: (s) => (s.modesPlayed?.length ?? 0) >= 6 },
];

export function newlyUnlocked(state: GameState): string[] {
  const have = new Set(state.unlockedAchievements);
  return ACHIEVEMENTS.filter((a) => !have.has(a.id) && a.check(state)).map((a) => a.id);
}

export function getAchievement(id: string): AchievementDef | undefined {
  return ACHIEVEMENTS.find((a) => a.id === id);
}
