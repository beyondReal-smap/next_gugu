# 구구 어드벤처 — 네이티브 iOS 전환 계획

> Capacitor(Next.js 웹뷰) → SwiftUI + RealityKit 완전 네이티브 재작성
> 결정: 새 Xcode 프로젝트 / 3D 월드는 RealityKit / iOS 우선

## 배경

- 현재: Next.js 15 + React 19 + three.js(R3F) + framer-motion, Capacitor로 iOS/Android 래핑
- 규모: 63개 파일 / 약 5,600줄
- 전환 이유(전부): 성능/부드러움, 앱 완성도, 심사/정책 리스크, 유지보수/기술부채

## 핵심 판단

도메인 로직(`lib/`)이 UI와 순수하게 분리돼 있어 **Swift로 거의 1:1 이식** 가능(위험 낮음).
난관은 **3D 어드벤처 월드**(three.js → RealityKit)와 **IAP**(cordova-plugin-purchase → StoreKit 2).

## 목표 구조 (새 Xcode 프로젝트: `Gugu`)

```
Gugu/
├── GuguApp.swift                 # @main 진입점 + 루트 환경
├── Core/                         # 순수 Swift 도메인 (UI 무의존) ← lib/ 이식
│   ├── Models.swift              # types.ts
│   ├── Modes.swift               # modes.ts
│   ├── Problems.swift            # problems.ts (출제 엔진)
│   ├── Level.swift               # level.ts (XP/레벨)
│   ├── Achievements.swift        # achievements.ts
│   ├── Commit.swift              # commit.ts (applySession/applyVisit)
│   ├── PremiumConfig.swift       # premiumConfig.ts
│   └── Adventure/                # lib/adventure/ 이식
│       ├── AdventureModels.swift
│       ├── World.swift           # REGIONS 정적 데이터
│       ├── Battle.swift
│       └── Progress.swift
├── Store/                        # @Observable 상태 (Provider 이식)
│   ├── GameStore.swift           # 진행 상태 + UserDefaults 영속화
│   ├── AdventureStore.swift
│   ├── PremiumStore.swift        # StoreKit 2
│   └── ThemeStore.swift
├── Services/
│   ├── Haptics.swift             # UIImpactFeedbackGenerator
│   ├── Sound.swift               # AVFoundation
│   └── Persistence.swift         # Codable JSON
├── DesignSystem/                 # components/ui 이식
│   ├── Theme.swift               # 컬러/타이포 토큰 (globals.css/tailwind)
│   ├── Components.swift          # Button/Card/ProgressRing/Segmented/TabBar/Stars
│   └── Feedback.swift            # Confetti/LevelUp/AchievementToast
├── Features/                     # features/ 이식
│   ├── Onboarding/
│   ├── Home/
│   ├── Learn/
│   ├── Profile/
│   ├── Session/                  # 6개 모드 (Keypad/OxPad/Result)
│   ├── Paywall/
│   └── Adventure/
│       ├── AdventureFlow.swift   # map→world→battle 상태머신
│       ├── RegionMap.swift
│       ├── World/
│       │   ├── WorldView.swift        # RealityView
│       │   ├── WorldEntities.swift    # Terrain/NPC/Player/Landmark
│       │   ├── CameraFollow.swift     # 3인칭 팔로우
│       │   ├── EncounterWatcher.swift # 근접 감지(히스테리시스)
│       │   └── Joystick.swift
│       └── Battle/               # hp/speed/counter + 보스 분노
└── Resources/
    ├── Assets.xcassets           # 아이콘/스플래시 (기존 재사용)
    └── Fonts/                    # SUITE (기존 재사용)
```

## 단계 (Phase)

- **P0 스캐폴딩**: 새 Xcode 프로젝트 생성, 번들ID(`site.smap.gugudan`)·폰트·에셋·앱아이콘 세팅, 시뮬레이터 빈 앱 빌드 확인
- **P1 Core 이식**: 도메인 로직 전체를 순수 Swift로 이식 + XCTest 단위 검증 (위험 0, 결정적 로직)
- **P2 셸+홈**: DesignSystem 토큰, TabView 앱셸, 온보딩, Home 화면
- **P3 세션**: 6개 게임 모드 학습 루프(Keypad/OX/빈칸/타이머/목숨) + 결과 화면 — 앱의 핵심
- **P4 학습+프로필**: Learn(단 선택/마스터리), Profile(통계/업적/설정)
- **P5 결제**: Paywall UI + StoreKit 2(구매/복원/엔타이틀먼트, 서버 영수증 검증 유지)
- **P6 어드벤처 월드**: RegionMap + RealityKit 3D 탐험(지형/NPC/플레이어/카메라/조이스틱/조우)
- **P7 배틀+폴리시**: 3종 배틀 + 보스 분노, 햅틱/사운드/애니메이션, QA·심사 대비

## 유지 사항 (기능 패리티)

- 번들ID `site.smap.gugudan`, 상품ID `site.smap.next.premium`(비소모성)
- 프리미엄 모드: challenge/survival/missing/truefalse (practice/timeAttack 무료)
- 서버 영수증 검증: `https://gugu.smap.site/api/iap/cdv-validate` (StoreKit 2 서명검증으로 대체 검토)
- 법적 링크(약관/개인정보/계정삭제), 스트릭/데일리골, 마스터리 별점, 8지역 어드벤처
- 저장 데이터 키: `gugu.progress.v1` 등 — 기존 웹 사용자 마이그레이션은 별도 논의(웹→네이티브는 저장소가 달라 자동 이전 불가)

## 위험 / 논의 필요

1. **영수증 검증 방식**: 기존 서버 검증 유지 vs StoreKit 2 온디바이스 서명검증. 서버 재사용 시 요청 포맷 재확인 필요
2. **RealityKit 자산**: three.js는 코드로 지오메트리 생성(박스/원기둥). RealityKit도 MeshResource 프로시저럴 생성으로 동일 접근 → 별도 3D 모델링 불필요
3. **Android**: 이번 범위 제외(iOS 우선). 추후 동일 도메인 로직 재사용 가능하나 UI는 별개
4. **기존 사용자 진행상황**: 웹뷰 localStorage → 네이티브 이전 경로 없음(신규 설치 취급). 필요 시 서버 동기화 설계 별도
