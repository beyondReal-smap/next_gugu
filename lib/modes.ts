// 게임 모드 레지스트리 — 모드별 규칙/메타데이터의 단일 출처
import { GameMode } from './types';

export type ModeKind = 'fixed' | 'timed' | 'lives';

export interface ModeDef {
  id: GameMode;
  name: string;
  tagline: string;              // 모드 카드 한 줄 소개
  detail: string;               // 모드 선택 시 하단 설명
  kind: ModeKind;               // fixed=문제 수 고정, timed=제한 시간, lives=목숨제
  total: number;                // fixed: 세션 문제 수 (timed/lives는 무제한이라 미사용)
  timeLimitMs: number | null;   // timed 전용
  lives: number | null;         // lives 전용
  supportsTable: boolean;       // 단 선택 지원 (false면 전체 랜덤 전용)
  scored: boolean;              // 최고 기록(점수=정답 수) 추적
  xpBonus: number;              // 정답당 추가 XP
}

export const MODES: Record<GameMode, ModeDef> = {
  practice: {
    id: 'practice',
    name: '학습',
    tagline: '차근차근 기본기',
    detail: '압박 없이 또박또박 — 틀린 문제는 다시 나와요.',
    kind: 'fixed', total: 10, timeLimitMs: null, lives: null,
    supportsTable: true, scored: false, xpBonus: 0,
  },
  timeAttack: {
    id: 'timeAttack',
    name: '스피드런',
    tagline: '10문제 최고 속도',
    detail: '속도가 곧 점수! 빠르게 풀수록 XP가 올라가요.',
    kind: 'fixed', total: 10, timeLimitMs: null, lives: null,
    supportsTable: true, scored: false, xpBonus: 4,
  },
  challenge: {
    id: 'challenge',
    name: '60초 챌린지',
    tagline: '제한 시간 최대 점수',
    detail: '60초 동안 몇 문제나 풀 수 있을까요? 최고 기록에 도전!',
    kind: 'timed', total: 0, timeLimitMs: 60_000, lives: null,
    supportsTable: false, scored: true, xpBonus: 4,
  },
  survival: {
    id: 'survival',
    name: '서바이벌',
    tagline: '기회는 단 3번',
    detail: '목숨 3개로 버티기! 틀릴 때마다 하트가 하나씩 사라져요.',
    kind: 'lives', total: 0, timeLimitMs: null, lives: 3,
    supportsTable: false, scored: true, xpBonus: 3,
  },
  missing: {
    id: 'missing',
    name: '빈칸 추리',
    tagline: '? 에 들어갈 수는',
    detail: '곱셈을 거꾸로! 빈칸의 수를 찾으면 XP 보너스가 커요.',
    kind: 'fixed', total: 10, timeLimitMs: null, lives: null,
    supportsTable: true, scored: false, xpBonus: 6,
  },
  truefalse: {
    id: 'truefalse',
    name: 'OX 퀴즈',
    tagline: '맞으면 O, 틀리면 X',
    detail: '식이 맞는지 순간 판단! 헷갈리는 한 끗 차이를 가려내요.',
    kind: 'fixed', total: 10, timeLimitMs: null, lives: null,
    supportsTable: true, scored: false, xpBonus: 0,
  },
};

export const MODE_LIST: ModeDef[] = [
  MODES.practice,
  MODES.timeAttack,
  MODES.challenge,
  MODES.survival,
  MODES.missing,
  MODES.truefalse,
];
