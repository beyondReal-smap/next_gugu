// 어드벤처 진행 — 격파/해금 계산 (순수 함수)
import { AdventureProgress, RegionDef } from './types';
import { REGIONS, bossIdFor } from './world';
import { newlyUnlockedAdv } from './achievements';

export const DEFAULT_PROGRESS: AdventureProgress = {
  version: 1,
  defeatedNpcs: [],
  battlesWon: 0,
  battlesLost: 0,
  achievements: [],
};

export function isNpcDefeated(p: AdventureProgress, npcId: string): boolean {
  return p.defeatedNpcs.includes(npcId);
}

// 첫 지역(2단)은 항상 열림, 이후는 직전 지역 보스 격파 시 해금
export function isRegionUnlocked(p: AdventureProgress, table: number): boolean {
  if (table <= REGIONS[0].table) return true;
  return isNpcDefeated(p, bossIdFor(table - 1));
}

// 배틀 결과 반영 + 어드벤처 전용 업적 판정 → 새 진행 상태와 신규 해금 업적 id
export function applyBattle(
  p: AdventureProgress,
  npcId: string,
  won: boolean
): { next: AdventureProgress; unlocked: string[] } {
  let next: AdventureProgress = won
    ? {
        ...p,
        battlesWon: p.battlesWon + 1,
        defeatedNpcs: isNpcDefeated(p, npcId) ? p.defeatedNpcs : [...p.defeatedNpcs, npcId],
      }
    : { ...p, battlesLost: p.battlesLost + 1 };
  const unlocked = newlyUnlockedAdv(next);
  if (unlocked.length) next = { ...next, achievements: [...next.achievements, ...unlocked] };
  return { next, unlocked };
}

export interface RegionStats {
  defeated: number;
  total: number;
  bossDefeated: boolean;
}

export function regionStats(p: AdventureProgress, region: RegionDef): RegionStats {
  const defeated = region.npcs.filter((n) => isNpcDefeated(p, n.id)).length;
  return {
    defeated,
    total: region.npcs.length,
    bossDefeated: isNpcDefeated(p, bossIdFor(region.table)),
  };
}

// 전체 격파 현황 (홈 카드 표시용)
export function totalStats(p: AdventureProgress): { defeated: number; total: number } {
  const total = REGIONS.reduce((s, r) => s + r.npcs.length, 0);
  return { defeated: Math.min(p.defeatedNpcs.length, total), total };
}
