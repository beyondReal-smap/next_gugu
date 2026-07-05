# 3D 어드벤처 모드 구현 계획

> 2026-07-05 확정. 사용자 결정: 진짜 3D(R3F) / 턴제 HP 배틀 / 홈 카드 진입 / 프리미티브 도형 아트.

## 컨셉

3D 월드에서 캐릭터를 조작해 탐험 → NPC 근접 시 "대결하기" → 구구단 턴제 HP 배틀 → 승리 시 NPC 격파, 보스 격파 시 다음 지역(단) 해금. 8개 지역 = 2~9단 1:1 매핑.

## 핵심 결정

| 항목 | 결정 |
|------|------|
| 렌더 | three.js + @react-three/fiber v9 (drei 제외 — RC 호환 리스크/번들 최소화, 라벨은 캔버스 스프라이트) |
| 로드 | `next/dynamic(ssr:false)`로 어드벤처 진입 시에만 three 청크 로드 (코어 번들 무영향) |
| 배틀 | 턴제 HP. 정답=공격(속도/콤보 데미지 보너스), 오답=피격. HP 0 → 승패 |
| 커밋 | 일반 NPC=`practice`, 보스=`timeAttack`으로 `commitSession()` → GameMode 유니온 무변경, XP/별점/업적 자동 반영 |
| table | NPC 담당 단 → 마스터리 별점이 학습 탭과 공유됨 |
| 진행 저장 | 별도 `AdventureProvider` + localStorage `gugu.adventure.v1` (defeatedNpcs 기반, 해금은 파생 계산) |
| 진입점 | 홈 카드 → AppShell 풀스크린 오버레이(z-50, 세션 오버레이와 동일 패턴) |
| 월드 구조 | 지역 선택 맵(2D UI) → 지역별 소규모 3D 필드(일반 NPC 2~3 + 보스 1). 한 번에 한 지역만 로드 |
| 조작 | 가상 조이스틱(터치, ref 기록) + WASD/화살표. 카메라 3인칭 팔로우 |
| 성능 | dpr [1,1.5], 이동은 useFrame+ref(setState 금지 — SessionScreen 교훈), 배틀 중 월드 언마운트 |

## 파일 트리

```
lib/adventure/                    # 신규 도메인 (React 무의존)
  types.ts                        # RegionDef/NpcDef/AdventureProgress/BattleConfig
  world.ts                        # 8지역 + NPC 정적 데이터 (단 매핑, 좌표, 테마색, HP)
  progress.ts                     # DEFAULT, isRegionUnlocked, applyBattleWin, regionProgress
  battle.ts                       # 턴제 HP 순수 로직 + toSessionResult
lib/state/AdventureProvider.tsx   # 진행 영속화 + 오버레이 active 상태
features/adventure/
  index.ts                        # 공개 API
  AdventureScreen.tsx             # 오버레이 루트 (map→world→battle 상태 머신)
  RegionMap.tsx                   # 지역 선택 (2D)
  world/
    WorldStage.tsx                # dynamic(ssr:false) 래퍼
    WorldCanvas.tsx               # R3F Canvas + 씬 (지형/플레이어/NPC/카메라)
    Player.tsx / Npc.tsx / Terrain.tsx
    controls.ts                   # 조이스틱+키보드 입력 → ref
    spriteLabel.ts                # 캔버스 텍스처 라벨
  ui/
    Joystick.tsx / WorldHud.tsx / EncounterPrompt.tsx
  battle/
    BattleScreen.tsx              # Keypad 재사용, HP바, 연출
```

**수정하는 기존 파일 (3개만)**: `app/ClientLayout.tsx`(Provider 1줄), `components/AppShell.tsx`(오버레이 수 줄), `features/home/Home.tsx`(카드 ~10줄)

## 배틀 밸런스 (초안)

- 플레이어 HP 100. 일반 NPC HP 60~80, 보스 120
- 정답 데미지: 20 + 속도(2s 미만 +10 / 3.5s 미만 +5) + 콤보(콤보당 +2, 최대 +10)
- 오답 시 NPC 반격: 일반 25, 보스 30 → 4번 틀리면 패배
- 출제: `pickProblem({ table: npc.table, wrongPool, recentKeys })` (오답가중 재사용)

## Phase

- [x] Phase 0: three+R3F 설치, `next build`(정적 export) 통과 확인
- [x] Phase 1 MVP: 도메인+Provider → 월드(이동/조우) → 배틀 → 커밋 연동 → 통합(홈/AppShell)
- [x] Phase 2: 8지역 데이터/보스 잠금/해금, 격파 표시(회색+✓), 사운드/햅틱 연결 (Phase 1과 함께 완료)
- [x] 검증: 빌드 통과 + E2E(이동→조우→배틀 승리→XP/별점/업적/격파 반영) + 기존 모드 회귀 없음 → verifier 점검

## Phase 3 (2026-07-05 완료)

- [x] 배틀 변형: 슬롯별 스타일 — n1=체력전(hp), n2=스피드 레이스(speed, 먼저 7문제·NPC 페이스 6.5s→3.7s), n3=반격전(counter, 제한 8s→5.2s 초과=피격), 보스=hp
- [x] 커밋 모드: speed·보스=timeAttack, 그 외=practice
- [x] 보스 분노: HP 50% 이하 → 공격력 1.5배 + 배지/링/사운드 연출
- [x] 전용 업적 8종 (`lib/adventure/achievements.ts`, 저장은 gugu.adventure.v1): 첫 승리/스피드 레이서/침착한 반격/보스 사냥꾼/백전노장(10승)/절반의 정복(4지역)/구구단 정복자(8지역)/완전 정복(32). AchievementToast에 옵셔널 resolve prop 추가(하위 호환)로 동일 토스트 재사용. RegionMap 헤더에 업적 칩 표시
- [x] 클리어 연출: 보스 승리 시 팡파레(playLevelUp)+다음 지역 해금 배너, 9단 보스 = "월드 클리어! 👑"
- [x] E2E: 스피드(7/7 vs 1/7 승리), 반격전(시간초과 -22), 보스(분노 후 오답 -45=30×1.5), 해금 배너, 업적 토스트/칩/저장 모두 확인
- [x] verifier P1 수정: 스피드 레이스 승리 선점 경합 — resolve()에서 승패 판정 즉시 `decidedRef` 동기 래치, NPC 페이스 interval이 래치 조회 (피드백 딜레이 550ms 창에서 NPC가 승리를 가로채지 못함)
- [x] verifier P2 수정: 로드 시 업적 backfill(`newlyUnlockedAdv`) — 업적 도입 전 저장소도 조용히 소급 해금 (E2E 확인)
- [x] 분노 경고음 450ms 지연 — 정답 효과음과 중첩 방지

## 지역 배치 다양화 (2026-07-05, 사용자 피드백 "단마다 똑같은 배치라 흥미 저하")

- [x] 지역별 고유 레이아웃 8종 (`world.ts` LAYOUTS): 스폰/주민 3좌표/보스 좌표를 지역마다 다르게 — 산개형(2)/대각 능선형(3)/해안선형(4)/지그재그형(5)/반원 포위형(6)/S자형(7)/외길 종대형(8)/성 진입로형(9)
- [x] 소품 시드 랜덤화: mulberry32(table 시드) 결정적 생성, 지역당 12~16개·크기 0.7~1.5, NPC(3.4)/보스(3.8)/스폰(3.4)/랜드마크(5) 반경 회피 + 소품 간 2.2 간격
- [x] 지역 랜드마크 8종 (Terrain, 정적 프리미티브): 꽃밭/오아시스/바다(필드 밖 시각 전용)/고목/눈사람/반딧불 연못/화산/성 기둥
- [x] RegionDef 확장: decoItems/spawn/landmark. SPAWN_POS·NPC_SLOTS·BOSS_SLOT·DECO_LAYOUT 공용 상수 제거
- [x] 스타일 배정(n1=hp/n2=speed/n3=counter)은 유지 → 업적 판정 무영향, 기존 격파 진행(id 기반) 호환

## 모바일 스크롤/줌 수정 (2026-07-05, 사용자 피드백)

- [x] 확대 차단: `app/layout.tsx`에 viewport export(maximum-scale=1, user-scalable=no, viewport-fit=cover) + `html/body/button { touch-action: pan-x pan-y }` (iOS 사파리는 meta 무시하므로 CSS 이중 차단, 스크롤 팬은 허용)
- [x] 스크롤 체이닝 차단: AppShell에서 오버레이(세션/어드벤처) 열림 동안 body overflow hidden 잠금+복원, RegionMap 리스트 `overscroll-contain`
- [x] 터치 에뮬레이션 검증: 홈 스크롤 274px·리스트 294px 정상, 오버레이 중 body scrollY 0 고정, 닫기 후 복원

## 검증 결과 (2026-07-05)

- 빌드: ✓ (홈 First Load 156kB, three는 지연 청크 368K+352K로 분리)
- E2E(Playwright, dist 서빙): 홈 카드→지역맵→월드 로드→키보드/조이스틱 이동→조우→배틀 4문제 승리→XP+66/2단 ⭐3/업적 발화→격파 표시→localStorage 반영 모두 ✓
- 기존 모드 회귀: 학습 세션/프로필 정상, 콘솔 에러 0 ✓
- 주의: 헤드리스 GPU 스톨(ReadPixels) 환경에서 R3F 루프가 일시 정지한 사례 1회 — 실기기에서는 미재현 조건, 관찰 필요

## 리스크 메모

- R3F v9 peer `^19` vs 고정 `19.0.0-rc-69d4b800`: yarn 경고 예상(설치는 진행). 빌드 실패 시 즉시 보고
- Capacitor file:// 에셋: 외부 에셋 없음(프리미티브+합성 사운드)이라 리스크 낮음
- lucide `Infinity` import 금지, `usePathname()` null 가드 (기존 교훈)
