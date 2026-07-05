// 어드벤처 월드 — 지역/NPC 정적 데이터의 단일 출처
// 지역마다 스폰·NPC 배치·보스 위치·랜드마크가 다르고, 소품은 지역 시드 기반으로 생성된다.
import { BattleStyle, LandmarkKind, NpcDef, NpcKind, RegionDef } from './types';

// 필드 경계 (±): 플레이어 이동 clamp와 지형 크기의 기준
export const FIELD_BOUND = 16;
// 조우 판정 거리 / 해제 거리 (히스테리시스)
export const ENCOUNTER_DIST = 2.6;
export const ENCOUNTER_EXIT_DIST = 3.6;

export function bossIdFor(table: number): string {
  return `r${table}-boss`;
}

// 난이도 스케일 — 단이 높을수록 튼튼하고 아프게
function npcHp(kind: NpcKind, table: number): number {
  return kind === 'boss' ? 190 + (table - 2) * 8 : 110 + (table - 2) * 6;
}
function npcAttack(kind: NpcKind, table: number): number {
  return kind === 'boss' ? 30 + (table - 2) : 22 + (table - 2);
}

// 슬롯별 배틀 방식 — 지역마다 체력전/스피드/반격전 하나씩 (보스는 항상 체력전+분노)
const NORMAL_STYLES: BattleStyle[] = ['hp', 'speed', 'counter'];

// 지역별 지형 레이아웃 — 배치 패턴이 겹치지 않게 손으로 설계
interface RegionLayout {
  spawn: [number, number];
  slots: [[number, number], [number, number], [number, number]]; // 일반 NPC 3
  boss: [number, number];
  landmark: { kind: LandmarkKind; pos: [number, number] };
}

const LAYOUTS: Record<number, RegionLayout> = {
  // 개방 산개형 — 첫 지역은 단순하게 (꽃밭은 스폰 전방 시야 안)
  2: { spawn: [0, 12], slots: [[-8, 3], [7, -1], [-2, -8]], boss: [0, -14], landmark: { kind: 'flowerbed', pos: [6, 5] } },
  // 대각 능선형 — 좌상단 입장, 우하단 보스
  3: { spawn: [-10, 12], slots: [[-11, 1], [-2, -4], [7, -9]], boss: [12, -13], landmark: { kind: 'oasis', pos: [8, 4] } },
  // 해안선형 — 왼쪽이 바다, 물가를 따라 내려감
  4: { spawn: [10, 12], slots: [[6, 2], [-1, -5], [8, -8]], boss: [-4, -13], landmark: { kind: 'sea', pos: [-17, 0] } },
  // 지그재그 숲길형
  5: { spawn: [0, 13], slots: [[-9, 6], [9, 0], [-9, -7]], boss: [6, -13], landmark: { kind: 'ancientTree', pos: [2, 3] } },
  // 반원 포위형 — 좌우로 넓게 벌어진 주민들 (눈사람은 스폰 전방 시야 안)
  6: { spawn: [0, 12], slots: [[-11, -2], [0, -6], [11, -2]], boss: [0, -13], landmark: { kind: 'snowman', pos: [6, 4] } },
  // S자 곡선형 — 연못을 돌아서 진행
  7: { spawn: [-8, 13], slots: [[6, 7], [-7, -1], [7, -8]], boss: [-6, -13], landmark: { kind: 'pond', pos: [0, 4] } },
  // 외길 종대형 — 좁게 흐르는 협곡 느낌
  8: { spawn: [0, 13], slots: [[-4, 6], [4, -1], [-4, -7]], boss: [2, -14], landmark: { kind: 'volcano', pos: [-11, -11] } },
  // 성 진입로형 — 기둥 사이를 지나 성문 앞 보스
  9: { spawn: [0, 13.5], slots: [[-6, 6], [6, 1], [-6, -5]], boss: [0, -13], landmark: { kind: 'pillars', pos: [0, -9] } },
};

// 결정적 시드 난수 (mulberry32) — 같은 지역은 항상 같은 소품 배치
function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// 지역 소품 생성 — NPC/스폰/랜드마크를 피해 12~16개 배치
function genDecoItems(table: number, layout: RegionLayout): [number, number, number][] {
  const rand = mulberry32(table * 1013 + 77);
  const blocked: { p: [number, number]; r: number }[] = [
    ...layout.slots.map((p) => ({ p, r: 3.4 })),
    { p: layout.boss, r: 3.8 },
    { p: layout.spawn, r: 3.4 },
    { p: layout.landmark.pos, r: 5 },
  ];
  const items: [number, number, number][] = [];
  const count = 12 + Math.floor(rand() * 5); // 12~16
  let guard = 0;
  while (items.length < count && guard++ < 400) {
    const x = (rand() * 2 - 1) * 15;
    const z = (rand() * 2 - 1) * 15;
    if (blocked.some(({ p, r }) => Math.hypot(p[0] - x, p[1] - z) < r)) continue;
    if (items.some(([ix, iz]) => Math.hypot(ix - x, iz - z) < 2.2)) continue;
    items.push([Math.round(x * 10) / 10, Math.round(z * 10) / 10, Math.round((0.7 + rand() * 0.8) * 100) / 100]);
  }
  return items;
}

interface NpcSeed {
  name: string;
  color: string;
  greeting?: string;
}

function buildNpcs(table: number, layout: RegionLayout, normals: NpcSeed[], boss: NpcSeed): NpcDef[] {
  const list: NpcDef[] = normals.map((seed, i) => ({
    id: `r${table}-n${i + 1}`,
    name: seed.name,
    kind: 'normal',
    battle: NORMAL_STYLES[i],
    table,
    pos: layout.slots[i],
    hp: npcHp('normal', table),
    attack: npcAttack('normal', table),
    color: seed.color,
    greeting: seed.greeting ?? `${table}단으로 승부하자!`,
  }));
  list.push({
    id: bossIdFor(table),
    name: boss.name,
    kind: 'boss',
    battle: 'hp',
    table,
    pos: layout.boss,
    hp: npcHp('boss', table),
    attack: npcAttack('boss', table),
    color: boss.color,
    greeting: boss.greeting ?? `이 지역의 보스다. ${table}단, 각오는 됐지?`,
  });
  return list;
}

interface RegionSeed {
  table: number;
  name: string;
  deco: RegionDef['deco'];
  theme: RegionDef['theme'];
  normals: NpcSeed[];
  boss: NpcSeed;
}

function buildRegion(seed: RegionSeed): RegionDef {
  const layout = LAYOUTS[seed.table];
  return {
    table: seed.table,
    name: seed.name,
    deco: seed.deco,
    decoItems: genDecoItems(seed.table, layout),
    spawn: layout.spawn,
    landmark: layout.landmark,
    theme: seed.theme,
    npcs: buildNpcs(seed.table, layout, seed.normals, seed.boss),
  };
}

export const REGIONS: RegionDef[] = [
  buildRegion({
    table: 2,
    name: '새싹 들판',
    deco: 'tree',
    theme: { ground: '#7ec850', accent: '#3e9b4f', sky: '#bfe3ff' },
    normals: [
      { name: '토끼 로로', color: '#f4f1ea' },
      { name: '다람쥐 콩이', color: '#c98a4b' },
      { name: '두더지 두두', color: '#8a6f5c' },
    ],
    boss: { name: '들판지기 곰곰', color: '#7a5230', greeting: '들판을 지나가려면 2단쯤은 술술 나와야지!' },
  }),
  buildRegion({
    table: 3,
    name: '노을 사막',
    deco: 'rock',
    theme: { ground: '#e8c07a', accent: '#c98d52', sky: '#ffd9a0' },
    normals: [
      { name: '여우 사샤', color: '#e07b39' },
      { name: '도마뱀 리지', color: '#a3b86c' },
      { name: '미어캣 모모', color: '#d9b38c' },
    ],
    boss: { name: '사막의 왕 카오', color: '#b3541e', greeting: '사막의 태양보다 뜨거운 3단 대결이다!' },
  }),
  buildRegion({
    table: 4,
    name: '파도 해변',
    deco: 'rock',
    theme: { ground: '#f2e2b8', accent: '#35a7c2', sky: '#a7dcf0' },
    normals: [
      { name: '게 집게', color: '#e2593b' },
      { name: '갈매기 끼룩', color: '#eef2f5' },
      { name: '거북 토토', color: '#4a9b6e' },
    ],
    boss: { name: '파도술사 옥토', color: '#5a6ec7', greeting: '파도처럼 밀려오는 4단을 견뎌봐라!' },
  }),
  buildRegion({
    table: 5,
    name: '버섯 숲',
    deco: 'tree',
    theme: { ground: '#4e8f57', accent: '#c96f4a', sky: '#a8d8b0' },
    normals: [
      { name: '버섯돌이 뽀삐', color: '#d95d4e' },
      { name: '올빼미 부엉', color: '#8d7a63' },
      { name: '사슴 다다', color: '#b98a5a' },
    ],
    boss: { name: '숲의 현자 무무', color: '#5b7a4a', greeting: '숲의 지혜를 시험하마. 5단이다!' },
  }),
  buildRegion({
    table: 6,
    name: '눈꽃 설원',
    deco: 'crystal',
    theme: { ground: '#e8f1f7', accent: '#9cc3d5', sky: '#d7e9f5' },
    normals: [
      { name: '펭귄 핑핑', color: '#3c4652' },
      { name: '물개 살살', color: '#9aa7b5' },
      { name: '눈토끼 하양', color: '#ffffff' },
    ],
    boss: { name: '얼음마녀 서리', color: '#6fa8dc', greeting: '6단이 얼어붙기 전에 답해 보시지!' },
  }),
  buildRegion({
    table: 7,
    name: '반딧불 늪',
    deco: 'tree',
    theme: { ground: '#4f6b52', accent: '#86c06c', sky: '#55666e' },
    normals: [
      { name: '개구리 팝팝', color: '#6cbf5a' },
      { name: '반딧불 반반', color: '#ffe066' },
      { name: '수달 찰랑', color: '#8a6f52' },
    ],
    boss: { name: '늪의 마법사 그림', color: '#4a5d7a', greeting: '늪에서 빠져나가고 싶다면 7단을 외워라!' },
  }),
  buildRegion({
    table: 8,
    name: '용암 화산',
    deco: 'rock',
    theme: { ground: '#6b4a44', accent: '#e2593b', sky: '#f0a884' },
    normals: [
      { name: '도롱뇽 라바', color: '#e2593b' },
      { name: '박쥐 까망', color: '#3c3744' },
      { name: '불꽃정령 화르', color: '#ff8c42' },
    ],
    boss: { name: '용암대장 부글', color: '#b3341e', greeting: '펄펄 끓는 8단 맛 좀 볼래?' },
  }),
  buildRegion({
    table: 9,
    name: '별빛 성',
    deco: 'crystal',
    theme: { ground: '#5a5d8a', accent: '#ffd166', sky: '#2e3160' },
    normals: [
      { name: '기사 은별', color: '#c0c8d8' },
      { name: '마법사 루나', color: '#8a6fd1' },
      { name: '유령 휘휘', color: '#dfe6f0' },
    ],
    boss: { name: '구구단 대마왕', color: '#4a3670', greeting: '드디어 왔군. 9단… 최후의 대결이다!' },
  }),
];

export function regionFor(table: number): RegionDef | undefined {
  return REGIONS.find((r) => r.table === table);
}
