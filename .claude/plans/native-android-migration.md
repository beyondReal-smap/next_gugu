# 구구 어드벤처 — 네이티브 Android 전환 계획

> Capacitor(웹뷰) → Jetpack Compose + Filament(SceneView) 완전 네이티브 재작성
> iOS 네이티브(`native-ios/`)와 **기능·디자인 1:1 패리티** 목표. 어드벤처 고도화(별 조각/상점/포털/클리어 연출) 포함.

## 전제

- iOS에서 이미 완료한 것: 도메인 로직 검증(테스트 20개), 화면 설계, 3D 씬 구성, 게임 밸런스 → **Android는 "재설계"가 아니라 "이식"**. Swift 코드가 곧 스펙.
- 기존 Capacitor `android/`는 참조·보존. 신규는 `native-android/`.
- 업로드 키스토어(`android/keystore/gugu-upload.jks`) 재사용 → 기존 앱 업데이트로 배포 가능.

## 기술 매핑 (iOS → Android)

| 영역 | iOS (완료) | Android (계획) |
|---|---|---|
| UI | SwiftUI | **Jetpack Compose** (Material3 위에 커스텀 토큰) |
| 상태 | @Observable 클래스 | **StateFlow/mutableStateOf 기반 Store 클래스** (동일 구조) |
| 영속화 | UserDefaults + Codable JSON | **DataStore(Preferences) + kotlinx.serialization** — 저장 키 동일(`gugu.progress.v1` 등) |
| 결제 | StoreKit 2 온디바이스 | **Play Billing Library 7** — 기존 상품 `site.smap.next.premium`(INAPP) 재사용, acknowledge 기반 |
| 3D | RealityKit RealityView | **SceneView 2.x (Filament)** — Node 계층 + 프로시저럴 지오메트리 |
| 게임 루프 | CADisplayLink | **Choreographer** frame callback |
| 사운드 | AVAudioEngine 소스노드 톤 합성 | **AudioTrack** 스트리밍 톤 합성 (동일 주파수 테이블) |
| 햅틱 | UIFeedbackGenerator | **Vibrator / HapticFeedbackConstants** |
| 아이콘 | SF Symbols | **Material Icons** + 이모지 (상점 등 동일) |
| 폰트 | SUITE ttf (변환본 재사용) | 동일 ttf → `res/font/` |
| 빌드 | xcodegen + xcodebuild | **Gradle Kotlin DSL** |
| 테스트 | XCTest 20개 | **JUnit(kotlin.test)** 동수 이상 패리티 |

### 3D 엔진 선택 근거 (SceneView/Filament)

- 필요한 것: 컬러 프리미티브 + 램버트 조명 + 팔로우 카메라 + 빌보드 라벨 — PBR 게임엔진급 불필요
- SceneView: Compose `Scene` 컴포저블, Node 계층(Entity 대응), Sphere/Cylinder/Plane 내장. **Cone/Capsule은 커스텀 Geometry로 직접 생성**(정점 코드 ~100줄), 텍스트 라벨은 Canvas→비트맵 텍스처 평면
- 대안(자체 OpenGL ES): 의존성 0·minSdk 23 유지 가능하나 미니 렌더러 자작(~1,200줄) 부담 → 차선
- 제약: SceneView minSdk **28** (Android 9, 2018+) — 실사용 커버리지 ~97%, 아동용 앱 타깃으로 수용 가능

## 프로젝트 구조

```
native-android/
├── settings.gradle.kts / build.gradle.kts / gradle wrapper
└── app/
    ├── build.gradle.kts        # applicationId site.smap.gugudan, versionCode 10, versionName 2.0.1
    └── src/
        ├── main/java/site/smap/gugudan/
        │   ├── MainActivity.kt              # setContent { RootScreen() }
        │   ├── core/                        # 순수 Kotlin 도메인 (iOS Core/ 1:1)
        │   │   ├── Models.kt Modes.kt Level.kt Problems.kt
        │   │   ├── Achievements.kt Commit.kt PremiumConfig.kt
        │   │   └── adventure/ (Models, World, Battle, Progress, AdvAchievements, Shop)
        │   ├── store/                       # GameStore, AdventureStore, SessionStore,
        │   │   │                            # PremiumStore(Billing), ThemeStore, Router
        │   ├── services/                    # Persistence(DataStore), Sound(AudioTrack), Haptics
        │   ├── designsystem/                # 색 토큰(라이트/다크), GGButton/ProgressRing/…, Feedback
        │   └── features/
        │       ├── onboarding/ home/ learn/ profile/ session/ paywall/
        │       └── adventure/ (RegionMap, ShopScreen, Overlays, battle/, world/)
        │           └── world/ (WorldScene, WorldEntities, WorldRuntime, Joystick)
        ├── main/res/ (font/SUITE*, mipmap 아이콘, values)
        └── test/ (CoreTests — iOS 테스트 20개 패리티)
```

## 단계 (Phase) — iOS와 동일 리듬, 각 단계 빌드+테스트+에뮬레이터 스크린샷 검증

- **A0 스캐폴딩**: Gradle 프로젝트(Kotlin 2.x, AGP 8.x, Compose BOM), 폰트/아이콘 이식, Pixel 9 Pro 에뮬레이터 빈 앱 실행
- **A1 Core 이식**: Swift Core 12파일 → Kotlin, JUnit 테스트 패리티(20+) — 결정성(mulberry32 등) 교차 검증
- **A2 셸+홈**: 디자인 토큰·공용 컴포넌트, DataStore 영속화, 하단 네비(홈/학습/프로필), 온보딩, 홈
- **A3 세션**: 6개 게임 모드 엔진+화면, AudioTrack 사운드 합성, 햅틱, 결과 화면
- **A4 학습+프로필**: 단 선택/마스터리, 통계/업적/설정(테마·효과음·초기화)
- **A5 결제**: 페이월 + Play Billing 7 (queryPurchases 엔타이틀먼트, acknowledge, 복원) — UI/로직 완성, 실결제 E2E는 내부 테스트 트랙에서
- **A6 3D 어드벤처**: SceneView 월드 — 지형/소품/랜드마크/NPC/플레이어(색상·모자)/조이스틱/팔로우캠/조우 + **별 조각/나침반/입장 배너/포털**
- **A7 배틀+상점+폴리시**: 배틀 3종+보스 분노, 꾸미기 상점, 클리어 연출, 전체 QA

## 유지 사항 (패리티 체크리스트)

- applicationId `site.smap.gugudan` / 상품 `site.smap.next.premium` / 프리미엄 4모드 경계 동일
- 저장 키 동일(`gugu.progress.v1`, `gugu.adventure.v1`, `gugu.theme`, `gugu.premium.v1`, `gugu.sound`)
- 게임 밸런스 상수 전부 동일(XP/데미지/레이스 페이스/별 조각 가격/상점 카탈로그)
- DEBUG 테스트 훅 동일 컨셉(프리미엄 강제/NPC 스폰/자동걷기/R2 클리어) — 에뮬레이터 E2E 자동 검증용
- 법적 링크/온보딩 문구/한국어 카피 동일

## 위험 및 대응

1. **SceneView 커스텀 지오메트리**(Cone/Capsule/포털) — A6 최우선 스파이크로 검증, 막히면 정점 직접 생성(레퍼런스 있음)
2. **Play Billing 실결제 검증 불가(에뮬레이터)** — 로직은 상태머신 단위 테스트, 실결제는 내부 테스트 트랙+라이선스 테스터로 별도
3. **versionCode**: 기존 2 → 신규 10부터 (Capacitor 마지막 빌드와 충돌 방지 여유)
4. **기존 웹뷰 사용자 학습 데이터**: iOS와 동일하게 신규 취급(구매는 Play 복원으로 유지). 필요 시 WebView localStorage 1회 마이그레이션 브리지는 후속 과제
5. **에뮬레이터 GPU**: Filament는 에뮬레이터(호스트 GPU) 동작 확인됨 — A0에서 조기 확인
