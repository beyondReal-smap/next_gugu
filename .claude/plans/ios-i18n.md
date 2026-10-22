# 구현 계획 초안: 구구 어드벤처 iOS 영어(en)·일본어(ja) 현지화

> 상태: **확정 (2026-10-22 대표님 "추천대로 진행") — 8-3 16건 모두 추천안 채택. M1 진행 중**. 확정: en+ja / 2~9단 유지 / ElevenLabs 언어별 / iOS 먼저.

> **진행 기록**
> - 2026-10-22 M1 완료: project.yml(SWIFT_EMIT_LOC_STRINGS·LOCALIZATION_PREFERS_STRING_CATALOGS, CFBundleDevelopmentRegion en, CFBundleLocalizations ko/en/ja, 테스트 스킴 language ko/KR), `Gugu/Resources/Localizable.xcstrings`(빈 카탈로그), `Gugu/Core/AppLanguage.swift`, en/ja InfoPlist.strings, `Theme.swift` SUITE.fontName(ja → HiraginoSans-W3/W6/W7), `GuguTests/LocalizationTests.swift`. 전체 188 테스트 통과, -testLanguage fr→en·ja→ja·en→en 확인, 시뮬레이터 ko 화면 회귀 없음.
> - **3-2 변경**: ja 폰트를 `.system(size:)` 대신 `.custom("HiraginoSans-W*")`로 — `.system(size:)`는 Dynamic Type 크기 조절을 받지 않아 SUITE(.custom)와 동작이 달라지기 때문.
> - 2026-10-22 M2 완료: Core(Modes·Achievements·AdventureAchievements·Level·Battle·Shop·World·Hints·WeeklyReport·ReminderPlanner·PushCopy/PushDateText·LearningApi 안내) `String(localized:)` 전환, 요일·시각·날짜 언어별 서식(ko 는 기존 형식 유지), ₩ 폴백 가격 제거(`PremiumStore.price: String?`, 페이월·프로필·설정 처리). `tools/i18n/catalog.py`(sync·apply·check), `tools/i18n/glossary.csv`, Core 199키 en/ja 초벌(needs_review). `CoreLocalizationTests` ko/en/ja/fr 통과, 전체 197 테스트 통과.
> - **발견·해결**: 원문(ko) 항목이 카탈로그에 없으면 ko.lproj 표가 생성되지 않고 iOS 가 개발 지역(en) 표로 넘어가 **한국어 기기에 영어가 나옴**. `catalog.py sync` 가 ko 항목을 모두 translated 로 채우고, `LocalizationTests.testEveryLanguageHasCompiledStringTable` 로 막음. → 문자열을 추가·수정하면 반드시 `python3 tools/i18n/catalog.py sync` 후 커밋.
> - 2026-10-22 M3 완료: `EnglishReading`(기수·문장형), `JapaneseReading`(히라가나 수 읽기 + 九九 72줄 표), `SpokenMath`(언어 switch, language 인자로 테스트), `VoiceLines(language:)`. 접근성 라벨·오답 설명 7곳 교체(TableSheet·Basket·Runner·LaneRunner·Result·Battle·Session), 표 화면 읽기 열 en 숨김. `ReadingTests` + VoiceLinesTests(세 언어 552키 동일·문장 중복 없음) — 전체 204 테스트 통과. sync 가 stale 키 삭제.
>   - M4 로 넘긴 것: ○×クイズ 카드 tagline 잘림("あってたら○、ちがった…"), "2단 Practice 시작" 같은 조합 문구, " = " 등 식 조각 키(verbatim 처리).
>   - M6 전까지 en/ja 에서 읽어주기를 켜면 **한국어 음성 파일이 재생됨**(Speech 가 아직 번들 루트를 찾음) — M6 폴더 전환 때 해결.
> - 2026-10-22 M4 완료(개발): 화면 문구 전부 카탈로그화 — String 인자 182곳 `String(localized:)`, 보간 안 한글 9곳·온보딩 조각 문장(AttributedString **강조**)·OX 기호(en ✓/✗)·초 서식(`Seconds`)·표 읽기 라벨 정리, 설정 "언어" 행(iOS 설정 열기). `catalog.py lint`(한글 누출 검사) 추가 → 0건. 서식 전용 키는 shouldTranslate=false 자동. en/ja **665/665 초벌**(서브에이전트 3배치, 용어 통일: 장애물=じゃまもの, Sign in/out). 204 테스트 통과, 시뮬레이터 en/ja 홈·학습·표·프로필·설정·세션 확인.
>   - 원어민 검수 때 볼 것: "단" en 단위("2 times table"), "개" en 빈 값(점프·바구니 점수 옆), "추천" en "Pick", "이용권" 설정 구역 en "Purchases", "커피 한 잔 값" 문구, 목표 4단계(Easy/Normal/Hard/Challenge), "정확도" ja 正かいりつ(보호자 리포트에도), 보호자 화면 ja 일반 한자 vs 아이 화면 1~2학년 한자.
>   - 미확인 화면: 페이월·어드벤처 3D·미니게임 3종·결과·계정 연결 — 번역은 들어갔고 레이아웃은 M9 원어민 TestFlight 때 확인.
> - 2026-10-22 M6 준비: `Voice/ko/` 폴더 참조 전환(git mv 553개, pbxproj −2,229줄), `Speech.url(for:language:)` subdirectory, `phrases.py` en/ja(앱과 동일 규칙), `generate.py --lang`(Android 는 ko 만 기존 경로), `qa.py --lang`(en 알파벳·ja pykakasi 히라가나 비교). VoiceLinesTests: ko 필수, en/ja 는 팩 없으면 skip(출시 전 필수). 205 테스트(1 skip) 통과. 크레딧 102k 남음(10/29 리셋) — en 20,214자+ja 11,959자라 **상위 플랜 불필요**. 샘플 6종(en: Nichalia·Ginger·Katie / ja: Konoha·Mitsuki·Jun) → 대표님 선택 대기.
> - 2026-10-22 M7(코드, **미배포**): 앱 — `PromoVisibility`(KOR 스토어프런트만 광고 토글, 켠 사람은 철회 위해 계속 표시, `Storefront.current`), 공지 토픽 언어별(`notice`/`notice-ios` 는 ko 유지, `notice-en(-ios)`·`notice-ja(-ios)`), `PushApi.localeTag` = 앱 언어+기기 지역. 서버 — `push_templates` 언어별 문구(ko/en/ja, marketing_reconfirm 은 ko 만) + `language_for_locale`, 광고어·학습수치 검사 ja/en 확장, `push_schedule` 기기 시간대(zoneinfo, 없으면 KST), `run_weekly_report` 현지 일19~월20·언어별 묶음 발송·활동 조회 최근 8일, dedupe ko 키 유지·en/ja 는 `:<lang>`, `push_admin --lang`, README 콘솔 규칙. migration `20261022_push_i18n.sql`(RPC devices 에 locale·timeZone, 순서 무관 호환). API 152·iOS 208(1 skip) 통과.
>   - 남은 M7: 배포(API 재시작 + migration 적용 — 대표님 확인 후), OTP 메일 3개 언어 병기(Supabase 대시보드 템플릿 — 확인 후), 영·일 개인정보처리방침·약관·삭제 안내 페이지 초안 + `PremiumConfig.Legal` 언어별 URL, 법률 검토.
> - 2026-10-22 M6 생성: 대표님 선택 en=Nichalia(XfNU2rGpBa01ckF309OY) · ja=Mitsuki(gARvXPexe5VF3cKZBian). en 11.7MB·ja 12.7MB. 자동 검수 en 받아쓰기 552/552(길이만 7줄), ja 497/552 — **ja 조사 "は" 앞 띄어쓰기 규칙 도입**(붙이면 "ごは"→ご飯, "いちは さん"→13 으로 들림, 480줄 재생성). 남은 ja 55줄(九九 46·빈칸 5·○× 4)은 받아쓰기 한계 → 원어민 청취 목록. iOS 209 테스트(skip 0).
> - 2026-10-22 M7 반영(대표님 승인): API 배포(ready ok, zoneinfo 확인) → migration 20261022_push_i18n 트랜잭션 리허설·롤백 → `psql -1` 적용(anon 권한 유지 확인). Supabase 인증 메일(로그인·이메일 연결) 3개 언어 병기 적용(Management API, 재조회 일치) — 원본 `docs/auth-email/`, 이전판 `previous-ko/`. 영·일 법적 문서 초안 `docs/legal-drafts/{en,ja}/`(DRAFT 배너, LEGAL REVIEW 주석 privacy 15·terms 5) — 게시 전 법률 검토. 앱 `PremiumConfig.Legal` 언어별 URL(/en/·/ja/, 아직 404 — 게시 전 출시 금지).
> - 2026-10-22 M8 진행: 등록정보 초안 `store-assets/listing-2.6.0/listing.json`(en-US·en-GB·ja, 길이 제한 통과, 이름 충돌 없음), 스크린샷 합성 `store-assets/screenshots-2.6.0/compose.py`(언어별 캡션, ja 캡션 폰트 M PLUS Rounded 1c OFL `store-assets/fonts/`). 원본 촬영(서브에이전트, Store iPhone 17 Pro Max·iPad Pro 13) 1차에서 버그 발견 → 수정: 영어 "개" 노출(빈 번역 → `SpokenMath.countSuffix` nil, check 가 빈 번역도 실패), 일본어 줄 간격(루트 `.lineSpacing(4)` ja), 3D 이름표 자동 축소, 나침반 2줄, 지역 칩·점수판·연습 버튼·최고 기록 한 줄+축소, ja 문구 단축. 재촬영 중. ASC 반영(기본 언어 en-US·현지화·IAP)은 2.6.0 버전 생성 후 대표님 확인 받고.
> - 2026-10-22 **3-2 재변경(일본어 폰트)**: 시스템 Hiragino 는 SwiftUI 한 줄 상자에 행간(leading)이 빠져 17pt 에서 줄 높이 17pt(SUITE 21.2pt) → 쌓인 글이 붙어 보임(스크린샷 검수에서 발견). **M PLUS Rounded 1c(OFL) 서브셋을 앱에 내장**(6굵기 1.2MB, 878자 — `tools/i18n/subset_ja_font.py`, 원본 google/fonts 커밋 고정), PostScript 이름 `RoundedMplus1c-*`. `catalog.py check` 가 일본어 번역 글자의 서브셋 포함 여부까지 검사(이모지 제외). 임시로 넣었던 루트 `.lineSpacing` 제거. 209 테스트 통과.
> - 2026-10-22 M8 스크린샷 완료: 원본 28장(raw/{ios,ipad}-{en,ja}, 3차 촬영 — 한국어 노출·잘림 없음, 남은 건 ja 단어 중간 줄바꿈 2건 "見/られる"·"アプ/リ" 원어민 판단) → 합성 `store-assets/screenshots-2.6.0/{en,ja}/{ios-phone(8),ios-ipad(6)}`. 촬영 자동화 스크립트(drive.py 등)는 스크래치패드(세션 한정).
> - ⚠️ 이 상태로 출시하면 ja 기기에 한국어 문구 + Hiragino 폰트가 보인다. 번역 완료(M5) 전에는 이 트리로 핫픽스 출시 금지.

> 범위: 네이티브 iOS(`native-ios`) 우선 진행. Android는 iOS가 확정된 뒤 같은 번역으로 2차에 진행.
> 확정 사항: 언어는 ko(개발 언어 유지) + en + ja. 학습 범위는 2~9단 유지. 언어별 ElevenLabs 음성 팩을 만듦.
> 이 문서는 읽기 전용으로 분석해 쓴 초안입니다. 코드는 수정하지 않았습니다.

---

## 0. 요약 (핵심 먼저)

- **문자열**: `Localizable.xcstrings` 하나를 새로 만들고 한국어 원문을 키로 씀. SwiftUI 리터럴(`Text("…")`, `Label`, `.accessibilityLabel("…")`)은 빌드할 때 자동으로 추출됩니다. 손으로 고쳐야 하는 곳은 Core 모델 문자열(모드·업적·레벨·월드·상점·힌트·리포트·알림)과, `String` 타입 파라미터를 받는 컴포넌트를 부르는 곳입니다.
- **조사·낭독 규칙은 언어마다 따로 둠**: 기존 `KoreanReading`은 그대로 두고 `EnglishReading`, `JapaneseReading`(九九 전통 읽기 72줄 표)을 추가합니다. 언어 선택은 `SpokenMath`라는 switch 하나가 맡습니다. 언어가 3개라 프로토콜이나 팩토리는 만들지 않습니다.
- **언어 결정의 단일 출처는 `AppLanguage`** (`Bundle.main.preferredLocalizations` 기준). 화면 문자열, 음성 팩, 폰트, 서버로 보내는 locale이 모두 이 값을 따릅니다.
- **미지원 언어 폴백**: 런타임 `CFBundleDevelopmentRegion`은 **en**으로 바꾸고, 문자열 카탈로그의 원문 언어(project `developmentRegion`)는 **ko로 유지**하는 안을 추천합니다.
- **폰트**: SUITE는 일본어 글리프가 없습니다. 모든 폰트가 `Font.suite()` 한 곳을 지나므로(`Theme.swift:61-67`) ja일 때만 시스템 폰트로 분기합니다. 용량은 늘지 않습니다.
- **음성**: `Resources/Voice/{ko,en,ja}/` 폴더 참조로 바꾸고 `Speech`가 `subdirectory`로 언어를 고릅니다. 세 언어를 모두 앱에 넣어 약 +22MB가 늘어납니다. On-Demand Resources(ODR)는 쓰지 않는 안을 추천합니다(오프라인 원칙 때문).
- **법무·서버**: 해외(KOR 이외 스토어프런트)에서는 광고성 푸시 토글을 숨기는 안을 추천합니다. 서버 템플릿을 locale별로 나누고, 주간 리포트는 기기 시간대 기준으로 보냅니다. 영어·일본어 방침은 번역에 더해 COPPA·GDPR·APPI 조항을 보강해야 합니다.
- **스토어**: ASC 기본 언어(primaryLocale)를 ko에서 **en-US로 바꾸는 안을 추천**합니다. 지금 상태로는 프랑스·독일 등 미지원 국가의 이용자에게 한국어 등록정보가 보입니다.
- **작업량**: 개발 실작업 약 4~5주. 번역 검수, 음성, 법무, 스토어를 병렬로 돌리면 달력 기준 6~8주(목표 버전 2.6.0).

---

## 1. 문자열 현지화 방식

### 1-1. 현황 (근거)

| 항목 | 근거 |
|---|---|
| 개발 언어 ko, 지원 언어 ko만 | `native-ios/project.yml:8`, `:49-51` / `Gugu.xcodeproj/project.pbxproj:2353-2358` (knownRegions = Base, ko) |
| 현지화 리소스는 표시 이름 하나뿐 | `native-ios/Gugu/ko.lproj/InfoPlist.strings:2` |
| `SWIFT_EMIT_LOC_STRINGS` 미설정 | pbxproj에서 검색 결과 0건 → 자동 추출이 꺼져 있을 가능성이 높으므로 명시해야 함 |
| 한국어 리터럴 822건 / 73개 파일 (고유 494종) | 그중 `print(` 96건(14개 파일)은 개발 로그라 **번역 대상이 아님** |
| SwiftUI 리터럴은 이미 LocalizedStringKey | 예: `OnboardingView.swift:29,41,81`, `RootView.swift:236-242` 탭 라벨, `OxPad.swift:29` |
| `String` 타입이라 자동 현지화되지 않는 곳 | `ConfirmSheet.swift:9-12`, `SettingsView.swift:430,448,466,508`(Row 컴포넌트), `PhasePlaceholder.swift:5-8`, `OnboardingView.swift:11`, `PaywallView.swift:18` |
| Core 모델 문자열 | `Modes.swift:28-60`, `Achievements.swift:39-55`, `Level.swift:55-63`, `World.swift:127,140,173-250`, `Shop.swift:23-35`, `Hints.swift:37-59`, `WeeklyReport.swift:45-59,123-125`, `ReminderPlanner.swift:44-51,115-133`, `PushModels.swift:214-273` |

### 1-2. 도입 방법 (xcodegen 호환)

1. `project.yml`
   - `settings.base`에 `SWIFT_EMIT_LOC_STRINGS: YES`, `LOCALIZATION_PREFERS_STRING_CATALOGS: YES` 추가
   - `info.properties`: `CFBundleLocalizations: [ko, en, ja]`, `CFBundleDevelopmentRegion: en` (2장 참고)
   - `options.developmentLanguage: ko`는 **유지** (카탈로그 원문 = 한국어)
2. `Gugu/Resources/Localizable.xcstrings` 추가. xcodegen은 `sources: Gugu` 아래 파일을 리소스로 넣습니다. xcstrings 지원 여부는 설치된 xcodegen 버전으로 확인해야 합니다(2.38 이상에서 지원된 것으로 알고 있으나 **검증 필요**).
3. `Gugu/en.lproj/InfoPlist.strings`, `Gugu/ja.lproj/InfoPlist.strings`를 추가합니다(기존 lproj 패턴을 그대로 따름). lproj 폴더가 생기면 xcodegen이 knownRegions에 en/ja를 자동으로 넣으므로 카탈로그 편집기에도 언어가 나타납니다.
4. 빌드 한 번으로 리터럴이 카탈로그에 채워집니다. 그 뒤 `xcodebuild -exportLocalizations`로 XLIFF를 내보내 번역 단계(4장)로 넘깁니다.

### 1-3. 키 정책

- **기본은 한국어 원문을 키로** 씀 (Xcode 기본 동작이고 diff가 가장 작음).
- **같은 한국어가 다른 뜻으로 쓰이는 경우만 의미 키 + defaultValue**를 씁니다. 예: `"학습"`은 탭 이름(`RootView.swift:239`)이면서 모드 이름(`Modes.swift:28`)입니다. 영어로는 "Learn"과 "Practice"로 갈라야 하므로 다음처럼 둡니다.
  `String(localized: "mode.practice.name", defaultValue: "학습", comment: "학습 탭의 모드 카드 이름")`
- 짧은 단어나 버튼에는 반드시 `comment:`를 붙입니다(화면 위치, 최대 길이). 번역 품질을 크게 좌우합니다.
- 숫자만 있는 식(`ResultView.swift:218` `Text("\(a.a) × \(a.b) = …")`, `SessionView.swift:142,366`)은 `Text(verbatim:)`으로 바꿔 카탈로그에 군더더기 키가 생기지 않게 합니다.

### 1-4. Core 계층을 `String(localized:)`로 바꾸는 범위

| 대상 | 방식 | 비고 |
|---|---|---|
| `Modes.all` name/tagline/detail | `String(localized:)` | 저장은 `GameMode` id 기반이라 표시 이름이 바뀌어도 안전 |
| `Achievements.all` name/description | 같음 | 저장은 id 기반 (`Achievements.swift:59`) |
| `Level.title` | 같음 | |
| `World` 지역·NPC 이름·인사말 | 같음 | NPC id가 `"r\(table)-n\(i+1)"`(`World.swift:118`)이라 이름을 바꿔도 진행 데이터는 영향 없음 |
| `Shop` 색·모자 이름 | 같음 | id 기반 |
| `Hints.coach` title/scaffold | 같음 + 보간 | 식(`70 − 7 = ?`)은 그대로 |
| `WeeklyReport.parentTip`, `weekdayLabel` | 문구는 `String(localized:)`, 요일은 `Calendar.veryShortWeekdaySymbols`(locale 지정) | `weekdayLabel`에 하드코딩된 `["일","월",…]`(`WeeklyReport.swift:124`) 제거 |
| `ReminderPlanner.message`, `hourLabel` | 문구는 `String(localized:)`, 시각은 `Date.FormatStyle` | `"오전 \(hour)시"` 수작업 포맷(`:45-50`) 대체 |
| `PushCopy`, `PushDateText` | 문구는 `String(localized:)`, 날짜는 `locale: ko_KR` 고정(`PushModels.swift:228`)을 앱 언어로 | "Android와 글자 단위로 같다"(`:235`)는 원칙은 2차 때 같은 카탈로그에서 내보내는 방식으로 유지 |
| `LearningApi.PermissionDenial.message`, `ApiError.errorDescription` 중 사용자에게 보이는 것 | `String(localized:)` | `LearningApi.swift:70-96` |
| `print(...)` 로그 | **바꾸지 않음** | |

모델이 `static let`이라 앱 실행 중 한 번만 평가되는데, 문제는 없습니다. iOS에서 앱별 언어를 바꾸면 시스템이 앱을 다시 시작하기 때문입니다.

### 1-5. 보간·복수형·숫자/날짜

- 보간: `"\(n)문제 중 \(i)번째"` 같은 형태는 카탈로그에 `%lld` 형식으로 들어갑니다. 언어마다 어순이 달라도 위치 인자로 해결됩니다.
- 복수형: ko/ja는 복수형 구분이 없고 **en만 "Vary by Plural"**이 필요합니다. 대상은 `"정답 \(n)개 남았어요"`(`ReminderPlanner.swift:117`), `"남은 하트 \(n)개"`(`SessionView.swift:391`), `"\(n)일 연속"` 등입니다.
- 날짜·시각: `DateFormatter` 고정 패턴을 `setLocalizedDateFormatFromTemplate` 또는 `Date.FormatStyle`로 바꿉니다. `WeeklyReport`의 날짜 키("YYYY-MM-DD", UTC 계산)는 내부 값이라 그대로 둡니다.
- 가격: `PremiumStore.price`의 폴백 `"₩4,400"`(`PremiumConfig.swift:13`, `PremiumStore.swift:22`) 때문에 해외에서는 상품을 불러오기 전에 원화 가격이 보입니다. **폴백 가격을 없애고 상품을 불러오기 전에는 가격 자리를 비우거나 로딩 표시**를 하는 안을 추천합니다(오표시를 막고 fail-fast 원칙에도 맞음).

### 1-6. 한국어 조사 하드코딩 (확인 결과)

| 위치 | 내용 | 대응 |
|---|---|---|
| `KoreanReading.swift:44-93` | 은/는, 이/가, 이에요/예요, 로/으로 자동 선택 | ko 전용으로 **그대로 유지** |
| `SessionView.swift:313,315`, `BattleView.swift:236-237` | `"정답은 \(withCopula(n))"`, `notButIs` | `SpokenMath.answerIs / notButIs`로 바꿔 언어별 분기 |
| `ResultView.swift:228`, `TableSheet.swift:112`, `LaneRunnerView.swift:368`, `RunnerView.swift:333`, `BasketView.swift:189` | VoiceOver `"a 곱하기 \(withTopic(b))"` | `SpokenMath.label(a,b)` |
| `SessionView.swift:215`, `BattleView.swift:219` | 문제 낭독문을 접근성 라벨로 재사용 | `SpokenMath.question` |
| `WeeklyReport.swift:50` | `withCopula` | `SpokenMath`에 위임하거나 언어별 문구로 |
| `BattleView.swift:300` | `"\(npc.name)을(를) 물리쳤어요!"`: 조사를 괄호로 병기 | ko는 `KoreanReading`에 목적격 헬퍼를 추가해 자연스럽게 고칠 수 있음(선택). en/ja는 보간 문구로 처리 |
| `KoreanReading.toward` | 앱 안에 호출처가 없고 테스트에서만 사용 | 언급만 함(삭제하지 않음) |

### 1-7. 테스트 영향 (GuguTests)

- 한국어 문자열 103건 / 8개 파일. 문자열 단언이 많은 곳은 `UxCoreTests`(56건: KoreanReading, Hints, WeeklyReport), `VoiceLinesTests`(8건), `ReminderTests`(8건), `AuthFlowTests:79-82`, `PushTests:127-128`입니다.
- 시뮬레이터 기본 언어는 보통 en이라, 현지화를 적용하면 **기존 한국어 단언이 깨집니다**. 대응은 다음과 같습니다.
  1. xcodegen 스킴 `test:`에 `language: ko`, `region: KR` 지정 (XcodeGen TestAction의 language/region 옵션, **버전별 지원 여부 확인**). 기존 테스트는 손대지 않고 통과시킵니다.
  2. 언어별 테스트를 새로 둡니다. 순수 함수에 `lang: AppLanguage` 인자(기본값 `.current`)를 받게 하고, 카탈로그 문자열은 `LocalizedStringResource(…, locale: Locale(identifier: "ja"))`로 resolve해서 단언합니다.
  3. 카탈로그 완전성 테스트: 앱 번들의 `ko/en/ja.lproj` 컴파일 결과에서 키 집합을 비교하거나, `tools/i18n/check.py`가 `Localizable.xcstrings`에서 `state != translated`인 항목을 찾아 실패시킵니다.
  4. **한국어 누출 린트**: Swift 소스에서 한글 리터럴을 검색하되 `print(`, 주석, `KoreanReading.swift`, 카탈로그를 통과하는 호출은 제외합니다. 이렇게 하면 `String` 파라미터 때문에 빠진 문자열을 잡을 수 있습니다.

### 1-8. 작업 단위와 순서

1. 기반: `project.yml`, 카탈로그, `AppLanguage`, InfoPlist.strings, 테스트 스킴 언어
2. Core 순수 로직: Modes → Achievements → Level → Hints → WeeklyReport → ReminderPlanner → PushModels → World/Shop. Core 테스트를 먼저 고정한 뒤 진행합니다.
3. 읽기 계층: `EnglishReading`, `JapaneseReading`, `SpokenMath`, `VoiceLines` (5장과 함께)
4. DesignSystem 공용 컴포넌트: `ConfirmSheet`, `Feedback`, `Components`, `PhasePlaceholder`
5. 화면 단위(문자열 수가 많은 순): Settings/AccountSettingsCard(78) → LaneRunner(47) → Runner(39) → HomeView(34) → ResultView(30) → BattleView(29) → PaywallView(26) → BasketView(24) → ProfileView(23) → 나머지
6. 분할 문장 정리: `OnboardingView.swift:31-39`처럼 `Text + Text + Text`로 강조색을 넣은 문장은 어순이 언어마다 달라 조각 번역이 깨집니다. 문장 전체를 하나의 `AttributedString(localized:)`로 만들고 강조 구간만 표시하는 방식으로 바꿉니다. 같은 패턴은 `^\s*\+ Text\(`로 전수 검색합니다.

---

## 2. 언어 선택 동작

### 2-1. 동작 정의

- 기기 언어 목록을 따름 → 첫 번째로 맞는 ko/en/ja를 사용합니다.
- 앱별 언어 설정: 지원 언어가 2개 이상이면 iOS 설정 > 구구 어드벤처 > 언어 항목이 자동으로 생깁니다. 앱 설정 화면에 "언어" 행을 하나 두고 `UIApplication.openSettingsURLString`으로 연결합니다. 앱 안에 언어 전환 UI를 직접 만들지는 않습니다(시스템이 앱을 다시 시작해 주므로 런타임 전환 로직이 필요 없음).
- `AppLanguage` (Core, 신규 파일 1개):
  - `enum AppLanguage: String { case ko, en, ja }`
  - `static let current`는 `Bundle.main.preferredLocalizations.first`의 언어 코드에서 정합니다. 매칭이 안 되면 en.
  - 사용처: `SpokenMath`, `Speech` 음성 폴더, `Font.suite`, `PushApi.localeTag`, 법적 문서 URL, 공지 토픽.

### 2-2. `CFBundleDevelopmentRegion`: en으로 바꿀지, ko를 유지할지

| 안 | 장점 | 단점 |
|---|---|---|
| **A. 런타임 en / 원문 ko (추천)** | 기기 언어 목록에 ko/en/ja가 하나도 없는 이용자(fr, de, zh 등)에게 영어가 보임. 키·원문은 한국어 그대로라 diff가 최소 | Info.plist의 개발 지역과 프로젝트 developmentRegion이 서로 달라짐. 빌드 경고 여부와 실기기 폴백을 확인해야 함 |
| B. ko 유지 | 변경 없음 | 175개국 중 ko/en/ja를 쓰지 않는 국가에서 한국어 UI가 보임. 전 세계 출시 목적과 어긋남 |
| C. 원문까지 en으로 전환 | 표준적인 구성 | 키 494종을 영어로 다시 써야 해서 diff가 매우 큼. 한국어 품질이 회귀할 위험 |

- 검증: 시뮬레이터 언어를 fr만 남기면 en, [fr, ko]이면 ko, [ja]이면 ja가 나오는지 확인합니다.
- 주의: en 번역이 빠진 키는 원문(한국어)으로 폴백됩니다. 그래서 1-7의 완전성 검사를 출시 게이트로 둡니다.

### 2-3. `Locale.current` 관련 점검 (검증 필요)

- iOS의 `Locale.current` 언어는 앱이 지원하는 현지화 범위 안에서 정해지는 것으로 알고 있습니다. 그렇다면 지금(ko만 지원) 해외 기기도 `PushApi.localeTag()`(`PushApi.swift:95-98`)가 `ko-US` 같은 값을 보내고 있을 가능성이 있습니다. 즉 `push_devices.locale`의 기존 값으로는 실제 기기 언어를 알 수 없을 수 있습니다.
- 대응: `localeTag`를 `AppLanguage.current` + `Locale.current.region` 조합으로 명시합니다. 서버 템플릿은 UI 언어와 같은 언어를 써야 하기 때문입니다. 시뮬레이터 언어를 en으로 두고 기존 코드의 출력을 먼저 확인합니다.

---

## 3. 폰트

### 3-1. 현황

- 모든 텍스트가 `Font.suite(_:_:)` 한 곳을 지납니다(`Theme.swift:61-67`, 호출 322곳 / 29개 파일). `.custom(` 직접 호출은 이 두 곳뿐입니다.
- 3D 월드 빌보드는 시스템 폰트(`WorldEntities.swift:158`)라 일본어도 문제없습니다.
- SUITE에는 일본어 글리프가 없습니다. 그대로 두면 CoreText가 글자 단위로 Hiragino로 폴백하는데, 굵기가 맞지 않고(ExtraBold 옆에 W3/W6 혼재) 숫자는 SUITE, 가나는 Hiragino로 섞여 보입니다.

### 3-2. 선택지

| 안 | 용량 | 라이선스 | 평가 |
|---|---|---|---|
| **A. ja일 때 시스템 폰트 (추천, 1차)** | 0MB | 문제 없음 | `Font.suite`에서 `AppLanguage.current == .ja`이면 `.system(size:weight:design: .rounded)`로 분기하고 SUITE 굵기를 Font.Weight에 대응시킴. 수정은 1곳. 일본어는 Hiragino Sans, 라틴·숫자는 SF Rounded로 일관됨 |
| B. M PLUS Rounded 1c / Zen Maru Gothic 번들 | 굵기당 수 MB. 7굵기 전체면 수십 MB, 상용 한자 서브셋으로 줄이면 굵기당 약 1MB 내외(추정) | SIL OFL 1.1 (앱 내장·상용 가능) | 둥근 고딕이라 브랜드 톤이 더 잘 맞음. 굵기를 3~4개로 줄이고 서브셋을 만들면 현실적. 2차 개선 후보 |
| C. 그대로 폴백 | 0MB | - | 굵기 불일치로 품질 저하. 비추천 |

- 영어는 SUITE 라틴 글리프를 그대로 씁니다.
- 스토어 스크린샷 합성 스크립트도 SUITE를 씁니다(`store-assets/screenshots-2.3.0/compose.py:58`). ja 캡션에는 OFL 일본어 폰트(B안 후보)를 **마케팅 이미지용으로만** 쓰는 것을 추천합니다. 시스템 Hiragino를 이미지로 출력할 때의 라이선스 모호함을 피할 수 있습니다.

### 3-3. 영어 길이 차이 점검

- 영어는 한국어보다 글자 수가 대략 1.3~2배 길어 위험 구간이 생깁니다. 모드 카드 tagline, `Pill`, `GGButton`, 탭 라벨, `OxPad` 라벨(`OxPad.swift:10-11`, 높이 128 고정), 상점 칩, 결과 화면 버튼 줄, 레벨업 오버레이가 해당합니다.
- 방법
  1. 실행 인자 `-NSDoubleLocalizedStrings YES`로 의사 현지화(두 배 길이)를 한 뒤 주요 화면을 순회합니다.
  2. `-AppleLanguages (en) -AppleLocale en_US`, `(ja) ja_JP`로 실행해 XcodeBuildMCP 스크린샷을 찍고 화면 × 언어 매트릭스를 검수합니다(iPhone SE급 소형 + iPad).
  3. 넘치는 곳은 `lineLimit` + `minimumScaleFactor(0.8)`로 우선 처리합니다. 번역을 줄이는 쪽(카탈로그 comment에 최대 길이 명시)을 먼저 고려합니다.

---

## 4. 학습 콘텐츠 현지화

### 4-1. 언어별 표현 원칙

| 요소 | 한국어(현행) | 영어 | 일본어 |
|---|---|---|---|
| 개념 | 구구단, n단 | times tables, "the 7 times table" / "the 7s" | 九九, 7の段 |
| 외우기(낭송) | "칠 팔은 오십육" (`KoreanReading.swift:69-71`) | 전통 낭송이 없으므로 "Seven times eight is fifty-six." 문장형 | **九九 전통 읽기 72줄 고정표**: "しちは ごじゅうろく", "ににんが し", "さざんが く", "はっぱ ろくじゅうし", "くく はちじゅういち". 곱이 한 자리면 "が"가 붙는 등 불규칙해서 규칙 생성 대신 표로 하드코딩하고 원어민이 검수 |
| 문제 낭독 | "칠 곱하기 팔은?" | "What is seven times eight?" | "なな かける はちは？" (낭송과 문제의 수 읽기 차이, 예: しち/なな, く/きゅう는 검수자가 확정) |
| 빈칸 추리 | "오십육은 칠 곱하기 몇일까요?" | "Fifty-six is seven times what?" | "ごじゅうろくは なな かける なに？" |
| OX | "…맞을까요?" | 모드명 "True or False", 버튼 ✓/✗ (O/X 관습이 없음) | ○×クイズ가 자연스러움. "まるかな？ばつかな？" |
| 표 화면 읽기 열 (`TableSheet.swift:100`) | 한글 읽기 | 숨기는 안을 추천(`7 × 8 = 56`과 중복) | 히라가나 九九 읽기 표시. 문화적 강점 |
| 힌트 (`Hints.swift`) | 전략 이름 | "Double the 2s", "Times 10, take one away" | "2の段を2ばい", "10の段から1つ分ひく" |
| 업적·레벨 | 한국어 말맛 | 짧은 게임식 영어 | 일본 아동 앱 톤 |
| NPC 이름 | 의성어 이름 (끼룩, 부글) | 언어별로 다시 짓는 안 추천 ("Lolo the Bunny") | "うさぎのロロ" |

- 일본어 표기 원칙: 九九는 **소학교 2학년** 과정입니다. 문장은 히라가나·가타카나 위주로 쓰고 한자는 1~2학년 배당 한자로 제한하는 안을 추천합니다(SwiftUI에서 후리가나를 붙이기 어렵기 때문). 보호자용 화면(설정·리포트·결제)만 일반 표기로 씁니다.
- 영어 원칙: 단어 하나의 철자를 en-US로 고정하고 math/maths처럼 미국·영국 차이가 나는 단어는 피합니다("times tables"는 공통). 정직한 범위 표기로 "Times tables 2–9"를 씁니다. 영국 MTC는 12단까지 요구하므로 설명에서 "2–9단 입문" 대상임을 분명히 밝힙니다.

### 4-2. 번역 품질 확보 절차

1. **용어집(Glossary)**을 먼저 확정합니다. 대상: 구구단/단, 별/별 조각, 스트릭, 마스터리, 콤보, 하트, XP/레벨, 이용권("평생 이용권" → "Lifetime Pass" / "買い切り" 계열), 보호자, 기록 보관, 학습 알림, 공지 알림, 각 모드명. `tools/i18n/glossary.csv` 정도로 둡니다.
2. 카탈로그 comment에 맥락을 적습니다(화면, 대상: 아이/보호자, 최대 길이).
3. XLIFF를 내보내 LLM으로 초벌 번역합니다(용어집 + 맥락 + 톤 지침 포함).
4. **원어민 검수**: en은 미국 초등 교사나 학부모 경험자, ja는 일본 초등 교사나 아동 콘텐츠 경험자. 특히 九九 72줄과 음성 대본은 반드시 검수받습니다.
5. 스크린샷 기반 문맥 검수(3-3의 매트릭스를 검수자에게 전달)
6. XLIFF를 다시 가져와 완전성 검사를 통과시킵니다.
- 분량: 사용자 노출 문자열은 로그를 빼면 고유 400종 안팎, 한국어 약 2만 자로 추정됩니다. 원어민 검수 비용은 언어당 수십만 원 수준으로 추정합니다(업체·단가에 따라 다름, 견적 필요).

---

## 5. 음성 팩

### 5-1. 현황

- `Speech.playNext`는 `Bundle.main.url(forResource: key, withExtension: "m4a")`로 번들 루트에서 찾습니다(`Speech.swift:58`).
- m4a 553개가 각각 PBXBuildFile로 번들 루트에 평평하게 복사됩니다(pbxproj 122행 등). **같은 파일 이름의 en/ja 파일을 하위 폴더에 그냥 두면 "Multiple commands produce" 빌드 에러**가 납니다.
- 문장 규칙은 Swift(`VoiceLines.swift`, `KoreanReading.swift`)와 Python(`tools/voice/phrases.py`) 양쪽에 있습니다. `VoiceLinesTests`가 manifest와 앱 문장을 대조합니다(`VoiceLinesTests.swift:45-55`).
- 키 구성은 q 72 + m 72 + c 72 + ox 336 = 552개입니다. 키는 언어와 무관해서 **세 언어 모두 같은 키 집합**을 씁니다.
- 생성 설정: 24kHz 모노, AAC 48kbps (`generate.py:56-57`). 목소리는 Mina, 모델은 `eleven_v4`입니다.

### 5-2. 구조 (추천: 폴더 참조 + 명시적 선택)

```
Gugu/Resources/Voice/
  ko/  q-2x1.m4a … voice-manifest.json   (기존 파일을 git mv)
  en/  …
  ja/  …
```
- `project.yml`: `sources`의 `Gugu` 항목 excludes에 `Resources/Voice`를 넣고, `- path: Gugu/Resources/Voice, type: folder, buildPhase: resources`를 추가합니다. pbxproj에서 m4a 항목 553개가 폴더 참조 1개로 줄어드는 부수 효과도 있습니다.
- `Speech.swift:58`:
  `Bundle.main.url(forResource: line.key, withExtension: "m4a", subdirectory: "Voice/\(AppLanguage.current.rawValue)")`
  파일이 없으면 지금처럼 assertionFailure를 냅니다(fail-fast 유지).
- `VoiceLinesTests`: `lang`을 하나씩 돌며 각 언어의 manifest와 `VoiceLines.*(lang:)` 결과를 대조합니다.
- 대안인 `ko.lproj/en.lproj` 지역화 리소스 방식은 코드 변경이 0이라는 장점이 있습니다. 하지만 552 × 3개의 variant group이 생기고, 파일이 빠졌을 때 다른 언어 음성으로 조용히 폴백되는 문제가 있어 추천하지 않습니다.
- **Android 주의**: `generate.py`는 iOS와 Android에 같은 파일을 동시에 씁니다(`generate.py:255-272`). iOS 경로만 `ko/`로 옮길 때 Android는 기존 경로(`assets/voice/` 루트)를 그대로 두어야 합니다. 그래서 `--lang` 옵션을 추가하고 Android 출력은 ko일 때만 기존 경로에 쓰도록 바꿉니다. Android 언어별 폴더 전환은 2차에서 합니다.

### 5-3. 앱 용량 판단

| 안 | 용량 | 평가 |
|---|---|---|
| **A. 세 언어 모두 내장 (추천)** | 약 11MB × 3 = 약 33MB (+22MB) | 셀룰러 다운로드 한도(200MB)에 한참 못 미침. "네트워크 없이 재생"(`Speech.swift:4-6`) 원칙 유지. App Thinning은 언어별로 잘라 주지 않음 |
| A'. A + 비트레이트를 32kbps로 낮춤 | 약 22MB (+11MB) | 아동용 음성은 32kbps로도 충분할 가능성이 큼. ABX 청취 비교 뒤 결정 |
| B. On-Demand Resources (언어별 태그) | 기본 언어만 내장 | 처음 들을 때 네트워크가 필요하고, 시스템이 지울 수 있으며, 오류 상태 UI가 필요함. 복잡도에 비해 이득이 작아 비추천 |

### 5-4. 대본 작성·생성·검수 절차

1. Swift: `EnglishReading`(0~90 영어 수사, 최대값은 OX 오답 후보 (9+1)×9=90), `JapaneseReading`(0~90 히라가나 수사 + 九九 72줄 표), `SpokenMath`(언어 switch). `VoiceLines`에 `lang` 인자를 추가합니다.
2. Python: `phrases.py`를 `phrases(lang)`으로 확장합니다. Swift와 같은 규칙과 같은 표를 둡니다(어긋나면 `VoiceLinesTests`가 실패하는 기존 안전장치를 그대로 활용).
3. 대본 검수: 원어민이 552줄 CSV를 검수합니다(ja는 九九 표기·읽기, en은 억양을 위한 구두점).
   - ja는 **히라가나로 입력**해 TTS의 한자 오독(七=なな/しち)을 막습니다.
   - ko에서 배운 교훈(숫자 뒤 '이'가 섞여 들림, 메모리 `elevenlabs-voice-pack.md:15`)처럼 언어별 오청 패턴을 샘플 단계에서 먼저 확인합니다.
4. 목소리: 언어별 원어민 목소리를 새로 고르는 안을 추천합니다(`generate.py voices --search`로 후보 샘플 3종 → 대표님 선택). Mina에 다국어 모델을 쓰면 억양이 어색할 위험이 있습니다.
5. 생성: `generate.py build --lang en --voice <ID>` (캐시는 언어·목소리별)
6. QA: `qa.py`에 언어 인자를 추가합니다(mlx-whisper language=en/ja, 수사 정규화). 의심 줄은 ElevenLabs Scribe로 교차 확인하고, 마지막으로 원어민이 100% 청취합니다(최소한 c-* 72줄과 무작위 표본).
7. 크레딧 추정: en 약 2.2만 자(ox 336줄이 대부분), ja 약 1.3만 자, 재녹음 20%를 더하면 약 4만 자. Starter 월 한도를 넘을 수 있으므로 **현재 플랜의 월 크레딧을 확인**하고 Creator로 1개월 올리거나 2개월로 나눠 생성합니다.
   - 선택 최적화: ox 줄을 "Seven times eight is forty-nine." + 공통 "True or false?"로 나누면 크레딧이 크게 줄어듭니다. 다만 억양 연결이 부자연스러울 수 있어 기본안은 아닙니다.

---

## 6. 서버·웹·법무

### 6-1. 푸시 문구

| 항목 | 현황 | 대응 |
|---|---|---|
| 템플릿 | 한국어 단일 (`api/push_templates.py:86-137`), 발신자 `"구구 어드벤처"`(`:20`) | `TEMPLATES[key][lang]` 구조로 바꾸고 `render(key, lang=…)`. ko/ja가 아니면 en |
| 광고 키워드 검사 | 한국어 위주 + 영어 일부 (`:41-64`) | ja 키워드 추가: セール, 割引, クーポン, キャンペーン, 限定, 特典, 購入. en은 offer, free trial, deal |
| 학습 수치 차단 정규식 | 한국어 단위 (`:67`) | en(`points?`, `questions?`, `days? in a row`, `%`)과 ja(`問`, `点`, `日連続`)를 추가 |
| 주간 리포트 대상 RPC | 기기 locale·time_zone을 반환하지 않음 (`migrations/20261019_push.sql:679-690`) | devices 객체에 `locale`, `timeZone`을 추가하는 새 migration. `push_jobs.run_weekly_report`(`push_jobs.py:118-136`)를 언어별 토큰 묶음으로 발송 |
| 발송 시각 | 전부 KST 기준 08~21시, 리포트는 일 19시~월 20시 KST (`push_schedule.py:3,14,24`) | KST 일 19시는 미국 서부 새벽입니다. **해외 기기는 `time_zone` 기준 현지 주간 창**(매시간 실행 + 현지 시각 판정)으로 바꿉니다. 현재 기기별 dedupe 키는 사용자 단위라 그대로 둡니다 |
| 광고 2년 재확인 | 한국 법정 통지 (`push_templates.py:117-128`) | 해외는 광고 동의 자체가 없으므로(6-3) ko 전용 유지 |
| 콘솔 공지 토픽 | `notice`, `notice-ios` 단일 언어 (`PushModels.swift:116-134`) | 기존 `notice`/`notice-ios`는 **한국어 전용으로 의미를 고정**합니다(구버전 기기가 모두 한국어라 마이그레이션이 필요 없음). en/ja 기기는 `notice-en(-ios)`, `notice-ja(-ios)`를 구독. `PushTopics.desired` 수정 + PushTests |
| locale 등록 | `localeTag()`가 `Locale.current` 기준 | 2-3과 같이 앱 언어 기준으로 수정 |

### 6-2. OTP 메일

- 앱은 Supabase `/auth/v1/user`(승격)와 `/auth/v1/otp`(로그인)를 호출하고, 메일 템플릿은 Supabase 대시보드의 한국어 단일 템플릿입니다(`SupabaseAuth.swift:57-80`).
- 선택지
  - **A (1차 추천)**: 한 통의 메일에 ko/en/ja를 함께 적는 템플릿. 제목도 3개 언어 병기. 코드 변경이 없습니다.
  - B: 승격 시 `user_metadata.locale`을 함께 보내고 템플릿에서 Go template 조건으로 분기. 제목 분기 가능 여부와 기존 계정 메타데이터 갱신을 **확인해야 함**.
  - C: Supabase Send Email Hook으로 서버가 직접 발송. 품질은 가장 좋지만 작업량이 큼.

### 6-3. 웹 문서·법무

- 문서: `public/privacy.html`, `public/terms.html`, `public/delete-account.html`은 한국어만 있습니다. 앱 링크는 `PremiumConfig.Legal`(`PremiumConfig.swift:24-28`) 한 곳에 있으므로 `AppLanguage`에 따라 `/en/privacy`, `/ja/privacy`처럼 고릅니다. 현재 `/privacy`를 `privacy.html`로 서빙하는 설정(next.config, 서버 설정)이 저장소에 보이지 않아 **서빙 경로 확인이 필요**합니다.
- 번역만으로는 부족하고 **지역별 조항 보강**이 필요합니다. 현재 방침은 한국 PIPA·정보통신망법 구조입니다(국외 이전 표 `privacy.html:286-333`, 만 14세 `:377`, "이메일 연결 = 법정대리인 동의로 봄" `:385-386`).

| 규제 | 현재 데이터 처리와의 접점 | 위험 | 대응안 |
|---|---|---|---|
| **미국 COPPA** (13세 미만, 아동 대상 서비스) | 구구단 앱은 사실상 아동 대상. 이메일(보호자 입력 전제), 학습 기록(이메일 + 구매 계정만, `privacy.html:380-381`), FCM 토큰·설치 ID(지속 식별자), 광고성 푸시 동의 | "이메일 연결 = 동의"는 COPPA의 검증 가능한 보호자 동의(VPC) 방식과 맞지 않을 수 있음. 지속 식별자를 내부 운영 목적 밖에서 쓰면 VPC 필요. 아동에게 광고성 푸시는 고위험 | ① **KOR 이외 스토어프런트에서 광고성 푸시 토글 숨김** (StoreKit `Storefront.current?.countryCode == "KOR"`일 때만 표시, 확인 불가면 숨김) ② 학습 기록 서버 보관은 "보호자 구매(App Store 결제) + 이메일" 조건을 유지하고 근거로 명시 ③ 영어 방침에 COPPA 고지(수집 항목, 보호자의 열람·삭제 권리, 연락처) ④ 제3자 광고·분석 SDK 없음을 명시 (`project.yml:10` "Analytics 없음") ⑤ **미국 법률 검토 권장**. FTC 2025 개정 규칙에서 참여 유도 푸시 관련 조항의 최종 반영 여부도 확인 |
| **EU GDPR 제8조 / UK Children's Code** | 동의를 근거로 처리할 때 아동 동의 연령은 국가별 13~16세 | 광고 동의는 동의 근거라 제8조가 직접 걸림. Children's Code는 기본값을 높은 프라이버시로, 유도 기법 금지 | ① 광고 토글 숨김(위와 같음) ② 공지·보호자 알림은 기본 꺼짐 유지 (`PushModels.swift:15-19`, 이미 충족) ③ 학습 기록 보관은 계약(구매) 근거로 정리 ④ EU/UK 대리인(제27조) 지정 필요 여부와 국외 이전(SCC) 기재는 법률 검토 |
| **일본 APPI** | 아동 연령 규정은 없음(지침상 대략 12~15세 미만은 법정대리인 동의 권장). 외국 제3자 제공 규정(제28조) | 서버(Supabase 등)가 일본 밖에 있으면 외국 제3자 제공 동의·정보 제공이 필요할 수 있음 | 일본어 방침에 이전 국가·제도 정보 기재. 클라우드 예외 해당 여부는 법률 검토. 광고 이메일(特定電子メール法)은 보내지 않으므로 해당 없음 |
| Apple | 4+, 키즈 카테고리 아님 | 키즈 카테고리 밖이어도 아동 대상이면 지침 1.3/5.1.4 준수 기대 | 4+와 일반 교육 카테고리 유지. 새 연령 등급 설문(2025년 개편)을 재확인 |
| EU DSA 거래자 정보 | 유료 IAP 판매 | 거래자 정보를 신고하지 않았으면 EU 노출이 제한될 수 있음 | ASC에서 이미 신고했는지 확인 |
| 일본 特定商取引法 표기 | 인앱 판매 | App Store 판매에서 표기 의무가 있는지 해석이 갈림 | 법률 검토. 필요하면 간단한 표기 페이지 추가 |

- 보호자 출생연도 게이트(만 19세, `PushModels.swift:196-210`)는 한국 기준입니다. 해외에서는 광고 토글을 숨기면 게이트 자체가 나타나지 않으므로 그대로 둡니다.

---

## 7. App Store 등록정보

| 항목 | 내용 |
|---|---|
| **기본 언어** | ko에서 **en-US로 변경 추천**. 현지화가 없는 국가는 기본 언어 등록정보가 보이기 때문. ko는 현지화로 남으므로 한국 노출은 영향 없음 |
| 현지화 | en-US, en-GB(maths 표기 등 철자만 다름), ja, ko(기존). en-AU/en-CA는 별도로 만들지 않고 폴백 동작을 확인 |
| 이름(30자)·부제(30자) 후보 | en: "Gugu Adventure: Times Tables" / "Learn times tables 2–9 by play". ja: "九九アドベンチャー" / "2〜9の段をゲームでおぼえよう". 이름 중복 여부는 ASC에서 확인 |
| 키워드(100자) 후보 | en: multiplication, times tables, math, kids, 2nd grade, 3rd grade, practice, quiz, learning, games. ja: 九九, かけ算, 掛け算, 算数, 小学生, 2年生, 知育, 勉強, ゲーム, 暗記. 경쟁사 상표는 넣지 않음 |
| 설명 | "2~9단" 범위를 정직하게 표기. 오프라인 음성, 광고 없음(해외는 광고성 알림도 없음), 1회 구매 |
| 홈 화면 이름 | `en.lproj` "Gugu Adventure", `ja.lproj` "九九アドベンチャー"(전각 9자는 홈 화면에서 잘릴 수 있으므로 실기기 확인 후 축약안 결정) |
| 스크린샷·미리보기 | 기존 파이프라인(`store-assets/screenshots-2.3.0/seed.py`, `compose.py`)에 언어 인자를 추가. 시뮬레이터를 `-AppleLanguages (en|ja)`로 실행해 캡처하고, 캡션 사전(`compose.py:20-34`)을 3개 언어로 확장(ja 폰트는 3-2 참고). iPhone 6.9"와 iPad 13" 세트 |
| 인앱 구매 표시명·설명 | ASC IAP 현지화(en/ja)와 `Gugu.storekit` 로컬 테스트 파일(현재 ko_KR만, `Gugu.storekit:19-23`)도 추가. "평생 이용권" → "Lifetime Pass" / 일본어는 "買い切り" 계열을 검수자와 확정 |
| 가격 | 원화 기준가 자동 환산을 유지하는 안 추천. 환산 결과(US, JP)만 확인하고 심리적 가격대($2.99, ¥500 등)와 다르면 수동 조정을 검토. "커피 한 잔 값"(`SettingsView.swift:336`) 문구는 en/ja로 바꿔 씀 |
| 심사 메모 | 영어로 작성. 언어 전환 방법, 오프라인 음성, 보호자 게이트(KR 전용), 이메일 OTP 테스트 방법 |
| What's New | en/ja 작성 |
| 자동화 | ASC API로 `appInfoLocalizations`(이름·부제·방침 URL), `appStoreVersionLocalizations`(설명·키워드·홍보 문구), IAP localizations, 스크린샷 세트 등록 |

---

## 8. 마일스톤·작업량·위험·미결정 사항

### 8-1. 단계별 마일스톤

| 단계 | 내용 | 검증 기준 | 작업량(실작업) |
|---|---|---|---|
| **M0 결정·준비** | 8-3 미결정 확정, 용어집 초안, 음성 후보 샘플 | 대표님 확정 기록 | 2일 |
| **M1 i18n 기반** | `project.yml` 설정, 카탈로그, `AppLanguage`, en/ja InfoPlist.strings, 테스트 스킴 language ko, `Font.suite` ja 분기 | 빌드 성공, 기존 GuguTests 전부 통과, ko 화면 회귀 없음(스크린샷 비교), fr 기기 → en 폴백 확인 | 3일 |
| **M2 Core 전환** | 1-4 표의 Core 문자열 + 날짜·시각 포맷 + 폴백 가격 제거 | ko 단언 테스트 그대로 통과, en/ja 단언 테스트 추가 후 통과 | 4일 |
| **M3 읽기·학습 콘텐츠** | `EnglishReading`, `JapaneseReading`(九九 표), `SpokenMath`, `VoiceLines(lang:)`, 접근성 라벨 교체 | 언어별 단위 테스트(九九 72줄 전수), VoiceOver로 수동 확인 | 3일 |
| **M4 화면 전환** | DesignSystem → 화면별(1-8 순서), 분할 문장 재구성 | 한국어 누출 린트 0건, 의사 현지화 매트릭스 검수 | 6~8일 |
| **M5 번역** (M2부터 병렬) | XLIFF 내보내기 → LLM 초벌 → 원어민 검수 → 가져오기 | 카탈로그 en/ja 100% translated, 검수자 승인 | 개발 2일 + 외부 1.5~2주 |
| **M6 음성 팩** (M3 뒤 병렬) | 폴더 구조 이동, `phrases.py`·`generate.py`·`qa.py` 언어화, 생성·검수 | 언어별 `VoiceLinesTests` 통과, QA 의심 줄 0건, 원어민 청취 승인, Android 빌드 영향 없음 | 개발 3일 + 생성·검수 1주 |
| **M7 서버·웹·법무** (병렬) | 템플릿 언어화, RPC migration, 현지 시간대 발송, 언어별 공지 토픽, 해외 광고 토글 숨김, 영어·일본어 문서 3종, 법률 검토 | api 테스트(언어별 render, 키워드, 시간대 창), dry-run 결과 확인, 문서 게시 | 개발 4~5일 + 법률 검토(외부) |
| **M8 스토어** | 기본 언어 변경, 등록정보, 스크린샷, IAP 현지화, 심사 메모 | ASC 미리보기 확인 | 3일 |
| **M9 QA·출시** | TestFlight 원어민 테스터(en/ja 각 1~2명), 단계적 출시(phased release) | 치명 결함 0건, 크래시 없음 | 1주 |

- 합계: 개발 실작업 약 4~5주. 외부 의존(번역 검수, 법률 검토, 음성 생성)을 병렬로 돌려 달력 기준 6~8주.
- Android 2차: xcstrings를 strings.xml로 바꾸는 스크립트, `assets/voice/{lang}` 전환, 같은 서버 그대로. iOS 확정 뒤 별도 계획.

### 8-2. 위험 요소

| 위험 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| `String` 파라미터 경로로 한국어가 영어 화면에 섞임 | 높음 | 중 | 한국어 누출 린트 + 의사 현지화 + 언어별 스크린샷 매트릭스 |
| 영어 길이로 레이아웃 넘침 | 높음 | 중 | `-NSDoubleLocalizedStrings`, 번역 길이 comment, `minimumScaleFactor` |
| 九九 읽기·일본어 TTS 오독 | 중 | 높음(학습 앱 신뢰도) | 고정표 + 히라가나 입력 + Whisper ja QA + 원어민 청취 |
| 일본어 폰트 굵기 불일치 | 높음(대응하지 않으면) | 중 | ja일 때 시스템 폰트 분기 |
| 테스트가 시뮬레이터 언어에 따라 깨짐 | 높음 | 낮음 | 스킴 language ko + 언어 명시 테스트 |
| COPPA 등 해외 아동 규제 | 중 | 높음 | 해외 광고 토글 숨김, 방침 보강, 법률 검토 전에는 해외 마케팅 집행 보류 |
| 해외 기기에 심야 푸시 | 높음(현행 KST 기준) | 중 | 기기 시간대 기준 발송 창 |
| 음성 폴더 이동으로 Android 깨짐 | 중 | 중 | `generate.py`에서 Android 출력을 기존 경로로 고정 |
| ElevenLabs 크레딧 부족·목소리 품질 | 중 | 중 | 플랜 확인, 샘플 단계에서 목소리 확정 |
| 앱 용량 +22MB | 확정 | 낮음 | 필요하면 32kbps 재인코딩 |
| `Locale.current` 해석 차이로 서버 locale이 잘못 들어감 | 중 | 낮음 | 앱 언어 기준 `localeTag` |
| xcodegen 버전이 xcstrings나 스킴 language를 지원하지 않음 | 낮음 | 낮음 | M1 첫날 확인, 필요하면 xcodegen 업그레이드 |

### 8-3. 대표님 결정이 더 필요한 사항 (추천안 포함)

| # | 사항 | 추천 |
|---|---|---|
| 1 | `CFBundleDevelopmentRegion` | **en** (원문 언어는 ko 유지). 미지원 국가에 영어로 폴백 |
| 2 | ASC 기본 언어 | **en-US로 변경** |
| 3 | 일본어 폰트 | 1차는 **시스템 폰트**, 2차에 OFL 둥근 고딕 번들 검토 |
| 4 | 영어 표 화면 읽기 열 | **숨김** (ja는 히라가나 九九 표시) |
| 5 | OX 퀴즈 영어 표기 | **"True or False" + ✓/✗** (ja는 ○× 유지) |
| 6 | 영어 변형 | 앱 UI는 **en 하나**(US 철자, 차이 나는 단어 회피), 스토어는 en-US + en-GB |
| 7 | 음성 목소리 | **언어별 원어민 목소리를 새로 선택** (샘플 3종 → 대표님 선택), 크레딧 플랜 확인 |
| 8 | 해외 광고성 푸시 | **KOR 스토어프런트 외 토글 숨김** |
| 9 | 해외 주간 리포트 발송 시각 | **기기 시간대 기준 현지 주간 창** (대안: 1차엔 해외 발송 제외) |
| 10 | OTP 메일 | 1차 **3개 언어 병기 1통**, 이후 언어 분기 검토 |
| 11 | 가격 | **자동 환산 유지**, US/JP 환산값만 확인. 폴백 원화 가격 제거 |
| 12 | NPC·지역 이름 | 언어별로 **다시 지음** (음역 대신) |
| 13 | 번역 검수 인력·예산 | 언어당 **유료 원어민 검수 1명 + TestFlight 테스터** |
| 14 | 일본어 앱 이름·홈 화면 이름 | "九九アドベンチャー" 기준으로 실기기 잘림 확인 뒤 확정 |
| 15 | 법률 검토 범위 | 최소한 미국(COPPA) 1회 검토 뒤 해외 마케팅 시작 |
| 16 | 음성 비트레이트 | 48kbps 유지 vs 32kbps (청취 비교 뒤 결정) |

---
**이 계획에 의견이 있으시면 말씀해 주세요. 수정이 필요한 부분은 함께 논의하겠습니다.**

---

### 참고: 분석에 쓴 주요 파일 (절대 경로)
- /Users/genie/SmapSource/gugu/native-ios/project.yml
- /Users/genie/SmapSource/gugu/native-ios/Gugu/Core/KoreanReading.swift, VoiceLines.swift, Hints.swift, Modes.swift, Achievements.swift, Level.swift, WeeklyReport.swift, ReminderPlanner.swift, PushModels.swift, PremiumConfig.swift, Adventure/World.swift, Adventure/Shop.swift
- /Users/genie/SmapSource/gugu/native-ios/Gugu/Services/Speech.swift, PushApi.swift, LocalNotifications.swift, SupabaseAuth.swift, LearningApi.swift
- /Users/genie/SmapSource/gugu/native-ios/Gugu/DesignSystem/Theme.swift, ConfirmSheet.swift
- /Users/genie/SmapSource/gugu/native-ios/Gugu/Features/Onboarding/OnboardingView.swift, Session/OxPad.swift, Learn/TableSheet.swift, Adventure/World/WorldEntities.swift, Settings/SettingsView.swift
- /Users/genie/SmapSource/gugu/native-ios/Gugu/ko.lproj/InfoPlist.strings, /Users/genie/SmapSource/gugu/native-ios/Gugu.storekit
- /Users/genie/SmapSource/gugu/native-ios/GuguTests/VoiceLinesTests.swift, UxCoreTests.swift, ReminderTests.swift, PushTests.swift, AuthFlowTests.swift
- /Users/genie/SmapSource/gugu/tools/voice/phrases.py, generate.py
- /Users/genie/SmapSource/gugu/api/push_templates.py, push.py, push_jobs.py, push_schedule.py, migrations/20261019_push.sql
- /Users/genie/SmapSource/gugu/public/privacy.html, terms.html, delete-account.html
- /Users/genie/SmapSource/gugu/store-assets/screenshots-2.3.0/compose.py
- /Users/genie/.claude/projects/-Users-genie-SmapSource-gugu/memory/elevenlabs-voice-pack.md

### 검증이 필요한 가정 (이번 분석에서 실행으로 확인하지 못한 것)
- xcodegen 설치 버전의 xcstrings 지원 여부, 스킴 TestAction의 `language`/`region` 지원 여부
- `SWIFT_EMIT_LOC_STRINGS`를 설정하지 않았을 때의 Xcode 기본값
- 현재 ko만 지원하는 앱에서 해외 기기의 `Locale.current` 언어 코드 (서버 `push_devices.locale` 기존 값의 신뢰도)
- Supabase 메일 템플릿의 제목·본문 조건 분기 가능 여부
- ElevenLabs 현재 플랜의 월 크레딧
- `/privacy` 같은 경로를 실제로 서빙하는 설정 위치
- 6-3의 법령 해석 전반 (법률 자문 필요)
