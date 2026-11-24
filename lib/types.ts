// 구구단 학습 앱 — 도메인 타입

export type GameMode = 'practice' | 'timeAttack' | 'challenge' | 'survival' | 'missing' | 'truefalse' | 'adventure';
export type Theme = 'light' | 'dark';
export type UserRole = 'child' | 'guardian';
export type FontScale = 1 | 1.15 | 1.3;
export type GraphicsQuality = 'auto' | 'smooth' | 'battery';

export interface Problem {
  a: number; // 단 (2~9, 확장 가능)
  b: number; // 곱하는 수 (1~9)
}

export interface AnswerRecord {
  a: number;
  b: number;
  correct: boolean;
  ms: number; // 응답 시간(ms)
  given?: number | boolean; // 제출한 답 (동기화용, 구기록은 없음)
}

export interface SessionResult {
  mode: GameMode;
  table: number | null; // 단일 단 집중이면 단 번호, 혼합이면 null
  answers: AnswerRecord[];
  maxCombo: number;
  durationMs: number;
  partial?: boolean; // 중도 이탈 부분 커밋 — 한 판 완료 통계와 구분
  xpScale?: number;  // 재대결 등 XP 체감 (1=기본)
}

export interface TableMastery {
  stars: number;        // 0~3
  bestAccuracy: number; // 0~1
  bestAvgMs: number;    // 최고(최저) 평균 응답시간
  plays: number;
}

// 날짜별 집계 — 부모 주간 리포트용 링버퍼 항목 (최근 56일)
export interface DayLogEntry {
  date: string; // YYYY-MM-DD
  correct: number;
  wrong: number;
  msSum: number;
  misses: Record<string, number>;
}

// GET /api/progress/weekly 와 동일한 로컬 파생 형태
export interface WeeklyReport {
  from: string;
  to: string;
  daysActive: number;
  correct: number;
  wrong: number;
  avgMs: number;
  weakKeys: { key: string; misses: number }[];
  stars: Record<number, number>;
  adventure: { defeated: number; total: number; bosses: number };
  roadmapStep: number;
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
  // 주간 리포트용 날짜별 링버퍼 (없으면 빈 배열)
  dayLog: DayLogEntry[];
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
  partial: boolean;       // 중도 이탈 부분 커밋
}
