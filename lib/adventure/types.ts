// 어드벤처 모드 — 도메인 타입 (React 무의존)

export type NpcKind = 'normal' | 'boss';

// 배틀 방식 — hp: 턴제 체력전 / speed: 목표 문제 수 레이스 / counter: 시간제한 반격전
export type BattleStyle = 'hp' | 'speed' | 'counter';

export interface NpcDef {
  id: string;            // 예: 'r2-n1', 'r2-boss'
  name: string;
  kind: NpcKind;
  battle: BattleStyle;
  table: number;         // 담당 단 (2~9)
  pos: [number, number]; // 필드 좌표 [x, z]
  hp: number;            // NPC 체력 (speed에서는 미사용)
  attack: number;        // 오답 시 플레이어가 받는 데미지 (speed에서는 미사용)
  color: string;         // 캐릭터 몸통 색
  greeting: string;      // 조우 대사
}

export interface RegionTheme {
  ground: string; // 지형 색
  accent: string; // 소품(나무/바위) 색
  sky: string;    // 하늘/포그 색
}

export type DecoKind = 'tree' | 'rock' | 'crystal';

// 지역 상징물 — 지역마다 하나씩 배치되는 테마 구조물
export type LandmarkKind =
  | 'flowerbed'   // 꽃밭 (들판)
  | 'oasis'       // 오아시스 (사막)
  | 'sea'         // 바다 (해변 — 필드 가장자리 물)
  | 'ancientTree' // 고목 (숲)
  | 'snowman'     // 눈사람 (설원)
  | 'pond'        // 반딧불 연못 (늪)
  | 'volcano'     // 화산
  | 'pillars';    // 성 기둥 (성문)

export interface RegionDef {
  table: number; // 2~9 — 지역 식별자이자 담당 단
  name: string;
  deco: DecoKind;
  decoItems: [number, number, number][]; // [x, z, scale] — 지역 시드 기반 생성 (지역마다 배치 다름)
  spawn: [number, number];               // 지역별 입장 위치
  landmark: { kind: LandmarkKind; pos: [number, number] };
  theme: RegionTheme;
  npcs: NpcDef[];
}

export interface AdventureProgress {
  version: number;
  defeatedNpcs: string[]; // 격파한 NPC id 목록 (해금은 여기서 파생)
  battlesWon: number;
  battlesLost: number;
  achievements: string[]; // 해금된 어드벤처 전용 업적 id
}
