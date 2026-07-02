// 레벨 / 경험치(XP) 시스템
import { GameMode } from './types';
import { MODES } from './modes';

export interface LevelInfo {
  level: number;
  totalXp: number;
  currentLevelXp: number;
  xpForNextLevel: number;
  progress: number; // 0~1
}

// 레벨 L에 도달하기 위한 누적 XP
function xpToReachLevel(level: number): number {
  let total = 0;
  for (let l = 1; l < level; l++) total += 60 + (l - 1) * 35;
  return total;
}
function xpForLevelSpan(level: number): number {
  return 60 + (level - 1) * 35;
}

export function getLevelInfo(totalXp: number): LevelInfo {
  const xp = Math.max(0, Math.floor(totalXp));
  let level = 1;
  while (xp >= xpToReachLevel(level + 1)) {
    level += 1;
    if (level > 999) break;
  }
  const base = xpToReachLevel(level);
  const span = xpForLevelSpan(level);
  return {
    level,
    totalXp: xp,
    currentLevelXp: xp - base,
    xpForNextLevel: span,
    progress: Math.min(1, (xp - base) / span),
  };
}

// 정답 1개의 XP (모드/콤보/속도 보너스) — 모드 보너스는 레지스트리에서
export function xpForAnswer(params: { mode: GameMode; combo: number; ms: number }): number {
  const base = 10;
  const comboBonus = Math.min(15, Math.max(0, params.combo - 2) * 2);
  const speedBonus = params.ms > 0 && params.ms < 2000 ? 5 : params.ms < 3500 ? 2 : 0;
  return base + comboBonus + speedBonus + MODES[params.mode].xpBonus;
}

export function levelTitle(level: number): string {
  if (level >= 30) return '구구단 레전드';
  if (level >= 22) return '연산 마스터';
  if (level >= 15) return '곱셈 챔피언';
  if (level >= 10) return '숫자 전략가';
  if (level >= 6) return '구구단 러너';
  if (level >= 3) return '성장하는 학습자';
  return '구구단 입문자';
}
