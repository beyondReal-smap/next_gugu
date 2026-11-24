// 배틀 순수 로직 — 체력전(hp)/스피드 레이스(speed)/반격전(counter) 공통 상수·계산
import { AnswerRecord, GameMode, SessionResult } from '../types';
import { BattleStyle, NpcDef } from './types';

export const PLAYER_MAX_HP = 100;

// 정답 데미지: 기본 20 + 속도 보너스(2초 미만 +8, 3.5초 미만 +4) + 콤보 보너스(콤보당 +2, 최대 +10)
export function damageFor(ms: number, combo: number): number {
  const speedBonus = ms > 0 && ms < 2000 ? 8 : ms < 3500 ? 4 : 0;
  const comboBonus = Math.min(10, Math.max(0, combo - 1) * 2);
  return 20 + speedBonus + comboBonus;
}

// --- 스피드 레이스: 먼저 목표 정답 수에 도달하면 승리 ---
export const RACE_TARGET = 7;
// NPC가 1문제를 "푸는" 간격 — 단이 높을수록 빨라짐 (2단 6.5초 → 9단 3.7초)
export function racePaceMs(table: number): number {
  return 6500 - (table - 2) * 400;
}

// --- 반격전: 제한시간 안에 못 풀면 오답 취급(반격) --- (2단 8초 → 9단 5.2초)
export function counterLimitMs(table: number): number {
  return 8000 - (table - 2) * 400;
}

// --- 보스 분노: HP가 비율 이하로 떨어지면 공격력 1.5배 ---
export const BOSS_ENRAGE_RATIO = 0.5;
export function enragedAttack(attack: number): number {
  return Math.round(attack * 1.5);
}

// UI 공용 배틀 방식 이름
export const BATTLE_STYLE_NAME: Record<BattleStyle, string> = {
  hp: '체력전',
  speed: '스피드 레이스',
  counter: '반격전',
};

// 재대결 XP 배율 — 막지는 않되 무한 농장을 줄인다
export const REMATCH_XP_SCALE = 0.3;

export function battleMode(_npc: NpcDef): GameMode {
  return 'adventure';
}

export function toSessionResult(
  npc: NpcDef,
  answers: AnswerRecord[],
  maxCombo: number,
  durationMs: number,
  opts: { partial?: boolean; rematch?: boolean } = {}
): SessionResult {
  return {
    mode: battleMode(npc),
    table: npc.table,
    answers,
    maxCombo,
    durationMs,
    ...(opts.partial ? { partial: true } : {}),
    ...(opts.rematch ? { xpScale: REMATCH_XP_SCALE } : {}),
  };
}
