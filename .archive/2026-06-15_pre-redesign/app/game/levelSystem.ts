// 레벨 / 경험치(XP) 시스템
// 누적 XP를 레벨로 환산한다. 레벨이 오를수록 필요 XP가 점증하는 곡선을 사용.

export interface LevelInfo {
  level: number;          // 현재 레벨 (1부터)
  totalXp: number;        // 누적 XP
  currentLevelXp: number; // 현재 레벨에서 쌓은 XP
  xpForNextLevel: number; // 다음 레벨까지 필요한 총 XP
  progress: number;       // 0~1 진행률
}

// 레벨 L → (L-1)로 올라오기 위해 필요한 XP (L>=1)
// 레벨 1: 0, 레벨 2: 80, 레벨 3: 140 ... 50 + level*30
function xpToReachLevel(level: number): number {
  // 1레벨까지 누적 0, 그 이후 각 레벨 구간 비용 = 50 + (level-1)*30
  let total = 0;
  for (let l = 1; l < level; l++) {
    total += 50 + (l - 1) * 30;
  }
  return total;
}

// 한 레벨을 올리는 데 필요한 구간 XP
function xpForLevelSpan(level: number): number {
  return 50 + (level - 1) * 30;
}

export function getLevelInfo(totalXp: number): LevelInfo {
  const xp = Math.max(0, Math.floor(totalXp));
  let level = 1;
  // 다음 레벨 누적 임계치를 넘는 동안 레벨업
  while (xp >= xpToReachLevel(level + 1)) {
    level += 1;
    if (level > 999) break; // 안전장치
  }
  const base = xpToReachLevel(level);
  const span = xpForLevelSpan(level);
  const currentLevelXp = xp - base;
  return {
    level,
    totalXp: xp,
    currentLevelXp,
    xpForNextLevel: span,
    progress: Math.min(1, currentLevelXp / span),
  };
}

// 정답 1개에 대한 XP 계산 (모드/콤보 보너스 반영)
export function xpForCorrect(params: {
  mode: 'practice' | 'timeAttack';
  combo: number;
}): number {
  const base = 10;
  // 콤보 보너스: 콤보 3 이상부터 단계적 가산 (최대 +15)
  const comboBonus = Math.min(15, Math.max(0, params.combo - 2) * 2);
  // 타임어택은 난이도 보너스
  const modeBonus = params.mode === 'timeAttack' ? 5 : 0;
  return base + comboBonus + modeBonus;
}

// 칭호: 레벨대별 타이틀
export function levelTitle(level: number): string {
  if (level >= 30) return '구구단 전설';
  if (level >= 20) return '구구단 마스터';
  if (level >= 14) return '곱셈 챔피언';
  if (level >= 9) return '숫자 박사';
  if (level >= 5) return '구구단 탐험가';
  if (level >= 3) return '새싹 계산가';
  return '구구단 새내기';
}
