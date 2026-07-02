// 구구단 학습 앱 — 도메인 타입

export type GameMode = 'practice' | 'timeAttack' | 'challenge' | 'survival' | 'missing' | 'truefalse';
export type Theme = 'light' | 'dark';

export interface Problem {
  a: number; // 단 (2~9, 확장 가능)
  b: number; // 곱하는 수 (1~9)
}

export interface AnswerRecord {
  a: number;
  b: number;
  correct: boolean;
  ms: number; // 응답 시간(ms)
}

export interface SessionResult {
  mode: GameMode;
  table: number | null; // 단일 단 집중이면 단 번호, 혼합이면 null
  answers: AnswerRecord[];
  maxCombo: number;
  durationMs: number;
}

export interface TableMastery {
  stars: number;        // 0~3
  bestAccuracy: number; // 0~1
  bestAvgMs: number;    // 최고(최저) 평균 응답시간
  plays: number;
}

export interface GameState {
  version: number;
  totalXp: number;
  // 리텐션
  streak: number;
  lastPlayedDate: string; // YYYY-MM-DD
  dailyGoal: number;      // 하루 목표 정답 수
  dailyDate: string;      // 오늘 날짜
  dailyCorrect: number;   // 오늘 누적 정답
  // 진행
  tableMastery: Record<number, TableMastery>;
  unlockedAchievements: string[];
  // 통계
  totalCorrect: number;
  totalWrong: number;
  maxCombo: number;
  recentAccuracy: number[]; // 최근 세션 정확도(스파크라인용, 최대 20)
  recentAvgMs: number[];    // 최근 세션 평균속도
  // 출제 보조: 오답 가중 풀 (key=`a*b`)
  wrongPool: Record<string, number>;
  // 모드 기록
  bestScores: Partial<Record<GameMode, number>>; // 점수형 모드(챌린지/서바이벌) 최고 기록
  modesPlayed: GameMode[];                       // 플레이해 본 모드 (업적용)
  // 설정
  onboarded: boolean;
}

export interface CommitResult {
  xpEarned: number;
  leveledUp: boolean;
  newLevel: number;
  unlocked: string[];     // 새로 해금된 업적 id
  table: number | null;
  newStars: number;       // 이번에 갱신된 별점(단 집중 세션일 때)
  improvedStars: boolean;
  goalReached: boolean;   // 이번 세션으로 데일리 골 달성
  score: number | null;   // 점수형 모드(챌린지/서바이벌)의 이번 점수 = 정답 수
  isNewBest: boolean;     // 최고 기록 갱신 여부
}
