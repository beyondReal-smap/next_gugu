// 어드벤처 전용 업적 — AdventureProgress 기반 판정 (코어 업적과 저장소/판정 분리)
import { AdventureProgress } from './types';
import { REGIONS, bossIdFor } from './world';

export interface AdvAchievementDef {
  id: string;
  name: string;
  description: string;
  icon: string; // lucide 아이콘 이름 (AchievementToast와 동일 규약)
  check: (p: AdventureProgress) => boolean;
}

function clearedRegions(p: AdventureProgress): number {
  return REGIONS.filter((r) => p.defeatedNpcs.includes(bossIdFor(r.table))).length;
}
function defeatedBySlot(p: AdventureProgress, slot: string): boolean {
  return p.defeatedNpcs.some((id) => id.endsWith(`-${slot}`));
}

export const ADV_ACHIEVEMENTS: AdvAchievementDef[] = [
  { id: 'adv_first_win', name: '첫 승리', description: '어드벤처에서 첫 대결 승리', icon: 'Swords', check: (p) => p.battlesWon >= 1 },
  { id: 'adv_speed_win', name: '스피드 레이서', description: '스피드 레이스에서 승리', icon: 'Gauge', check: (p) => defeatedBySlot(p, 'n2') },
  { id: 'adv_counter_win', name: '침착한 반격', description: '반격전에서 승리', icon: 'Timer', check: (p) => defeatedBySlot(p, 'n3') },
  { id: 'adv_first_boss', name: '보스 사냥꾼', description: '첫 보스 격파', icon: 'Crown', check: (p) => clearedRegions(p) >= 1 },
  { id: 'adv_wins_10', name: '백전노장', description: '대결 10승 달성', icon: 'Medal', check: (p) => p.battlesWon >= 10 },
  { id: 'adv_half_world', name: '절반의 정복', description: '지역 4개 클리어', icon: 'Flag', check: (p) => clearedRegions(p) >= 4 },
  { id: 'adv_world_clear', name: '구구단 정복자', description: '8개 지역 보스를 모두 격파', icon: 'Trophy', check: (p) => clearedRegions(p) >= REGIONS.length },
  { id: 'adv_all_npcs', name: '완전 정복', description: '모든 주민과 보스를 격파', icon: 'Gem', check: (p) => REGIONS.every((r) => r.npcs.every((n) => p.defeatedNpcs.includes(n.id))) },
];

export function newlyUnlockedAdv(p: AdventureProgress): string[] {
  const have = new Set(p.achievements);
  return ADV_ACHIEVEMENTS.filter((a) => !have.has(a.id) && a.check(p)).map((a) => a.id);
}

export function getAdvAchievement(id: string): AdvAchievementDef | undefined {
  return ADV_ACHIEVEMENTS.find((a) => a.id === id);
}
