// 업적(Achievement) 정의 및 판정
// 게임 누적 통계(GameStats)를 기준으로 해금 여부를 계산한다.

export interface GameStats {
  totalCorrect: number;   // 누적 정답 수
  maxCombo: number;       // 최고 콤보
  level: number;          // 현재 레벨
  masteredCount: number;  // 마스터한 단 수 (타임어택 클리어)
  starCount: number;      // 획득한 총 별 개수
  dailyStreak: number;    // 연속 출석 일수
  timeAttackClears: number; // 타임어택 클리어 횟수
}

export interface AchievementDef {
  id: string;
  name: string;
  description: string;
  icon: string;            // lucide-react 아이콘 이름
  color: string;           // 토이 컬러 (tailwind 클래스 prefix용 hex)
  check: (s: GameStats) => boolean;
}

export const ACHIEVEMENTS: AchievementDef[] = [
  {
    id: 'first_correct',
    name: '첫 정답!',
    description: '처음으로 정답을 맞혔어요',
    icon: 'Sparkles',
    color: '#F59E0B',
    check: (s) => s.totalCorrect >= 1,
  },
  {
    id: 'correct_50',
    name: '꾸준한 연습',
    description: '누적 50문제를 맞혔어요',
    icon: 'BookOpen',
    color: '#2563EB',
    check: (s) => s.totalCorrect >= 50,
  },
  {
    id: 'correct_200',
    name: '구구단 일꾼',
    description: '누적 200문제를 맞혔어요',
    icon: 'Medal',
    color: '#8B5CF6',
    check: (s) => s.totalCorrect >= 200,
  },
  {
    id: 'combo_5',
    name: '콤보 스타터',
    description: '5콤보를 달성했어요',
    icon: 'Zap',
    color: '#EC4899',
    check: (s) => s.maxCombo >= 5,
  },
  {
    id: 'combo_10',
    name: '콤보 마스터',
    description: '10콤보를 달성했어요',
    icon: 'Flame',
    color: '#F97316',
    check: (s) => s.maxCombo >= 10,
  },
  {
    id: 'combo_20',
    name: '불꽃 콤보',
    description: '20콤보를 달성했어요',
    icon: 'Crown',
    color: '#EAB308',
    check: (s) => s.maxCombo >= 20,
  },
  {
    id: 'first_clear',
    name: '첫 마스터',
    description: '타임어택으로 한 단을 마스터했어요',
    icon: 'Trophy',
    color: '#10B981',
    check: (s) => s.masteredCount >= 1,
  },
  {
    id: 'master_all',
    name: '구구단 졸업',
    description: '9단까지 모두 마스터했어요',
    icon: 'GraduationCap',
    color: '#2563EB',
    check: (s) => s.masteredCount >= 8,
  },
  {
    id: 'stars_10',
    name: '별 수집가',
    description: '별 10개를 모았어요',
    icon: 'Star',
    color: '#F59E0B',
    check: (s) => s.starCount >= 10,
  },
  {
    id: 'stars_24',
    name: '반짝반짝 마스터',
    description: '모든 단에서 별 3개를 모았어요',
    icon: 'Award',
    color: '#EC4899',
    check: (s) => s.starCount >= 24,
  },
  {
    id: 'level_5',
    name: '레벨 5 달성',
    description: '레벨 5에 도달했어요',
    icon: 'TrendingUp',
    color: '#8B5CF6',
    check: (s) => s.level >= 5,
  },
  {
    id: 'level_10',
    name: '레벨 10 달성',
    description: '레벨 10에 도달했어요',
    icon: 'Rocket',
    color: '#2563EB',
    check: (s) => s.level >= 10,
  },
  {
    id: 'daily_3',
    name: '꾸준 출석',
    description: '3일 연속 플레이했어요',
    icon: 'CalendarCheck',
    color: '#10B981',
    check: (s) => s.dailyStreak >= 3,
  },
  {
    id: 'daily_7',
    name: '일주일 개근',
    description: '7일 연속 플레이했어요',
    icon: 'Heart',
    color: '#EC4899',
    check: (s) => s.dailyStreak >= 7,
  },
];

// 현재 통계 기준으로 새로 해금된 업적 id 목록 반환
export function newlyUnlocked(stats: GameStats, alreadyUnlocked: string[]): AchievementDef[] {
  const unlockedSet = new Set(alreadyUnlocked);
  return ACHIEVEMENTS.filter((a) => !unlockedSet.has(a.id) && a.check(stats));
}

export function getAchievement(id: string): AchievementDef | undefined {
  return ACHIEVEMENTS.find((a) => a.id === id);
}
