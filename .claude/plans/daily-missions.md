# 오늘의 미션 + 보물상자 + 이번 주 도장 (2.9.0 추가)

## 목표
초등생이 **매일** 앱을 열 이유를 만든다. 지금 홈은 매일 똑같다(오늘 목표 = 정답 N개, 작은 연속일 배지).
날마다 바뀌는 미션 3개 → 다 하면 보물상자(별 +10) → 이번 주 도장 1칸. 무료·이용권 모두 같음(체험 10문제 판으로도 여러 판이면 다 할 수 있는 수치).

## 데이터 모양 (양 플랫폼 같은 의미)

```
MissionKind  = stairsSteps | monstersDefeated | bestStreak | starsEarned
Mission      = (kind, target)
DailyMissionPlan.missions(day: "yyyy-MM-dd") -> [Mission] (항상 3개, 같은 날 = 같은 미션, 기기·시간대 무관)
  n = 1970-01-01 부터 그 날짜까지 일 수(날짜 문자열의 연·월·일로만 계산 — 시간대 영향 없게)
  [0] stairsSteps       target = [10, 15, 20][n % 3]
  [1] monstersDefeated  target = [5, 8, 10][(n / 3) % 3]
  [2] n 짝수 → bestStreak  target = [5, 7][(n / 2) % 2]
      n 홀수 → starsEarned target = [15, 25][(n / 2) % 2]
DailyMissionPlan.chestStars = 10

DailyMissionLog (PlayRewards 안, 저장 키 그대로 gugu.play.v1)
  day: "yyyy-MM-dd"
  stairsSteps, monstersDefeated, starsEarned: 누적(+=)
  bestStreak: 오늘 게임 판들의 최고 연속(max)
  chestOpened: Bool
PlayRewards += missionLog: DailyMissionLog? (없으면 nil), chestDays: [String] (보물상자 연 날, 오름차순·중복 없음, 최근 14개만)
  구버전 저장본(필드 없음) → nil / []. 날짜 형식이 틀리거나 음수 → 디코딩 실패(기존 관례대로 격리)
```

"오늘"은 기존 `Commit.todayStr(now)`(기기 현지 달력)를 쓴다. 저장된 log.day 가 오늘이 아니면 진행도 0 으로 본다(읽기에서), 쓰기 때 새 log 로 바꾼다.

## 저장소 API (PlayRewardsStore)

- `recordRound(game, score, stars, bestStreak, now = 지금)` — 기존 별·최고 기록 + 오늘 log 갱신
  (stairs → stairsSteps += score, monster → monstersDefeated += score, starsEarned += stars, bestStreak = max).
  반환 `PlayRoundRecord` 에 `missionsCompleted: [Mission]`(이번 판으로 **새로** 목표를 넘은 미션, 미션 순서대로), `allMissionsDone: Bool`(이번 판으로 3개가 처음 다 됨) 추가.
- `missionBoard(now = 지금) -> MissionBoard` — 화면용 읽기 전용 값
  `items: [(mission, progress(목표에서 자름), done)]`, `allDone`, `chestOpened`, `chestReady = allDone && !chestOpened`,
  `week: [WeekStamp(date, stamped, isToday)] × 7` (이번 주 월~일, stamped = chestDays 에 있음)
- `openChest(now = 지금) throws -> Int` — chestReady 가 아니면 오류(화면이 먼저 막아야 하는 상황), 별 +10, chestOpened = true, 오늘을 chestDays 에. 받은 별 수 반환.
- 판 도중 나가기(abandon)는 recordRound 를 안 부르므로 미션에도 안 쌓인다(별과 같은 규칙).

## 화면

### 홈 (헤더 바로 아래, 게임 카드 위)
`DailyMissionsCard`
- 제목 줄: "오늘의 미션" + 오른쪽 보물상자 그림(`chest_closed`/`chest_open`, 이미 에셋 있음). 준비되면 상자가 통통 튄다(반복 애니메이션, 동작 줄이기 설정이면 멈춤).
- 미션 3줄: 아이콘(계단 = 구구 계단 썸네일, 몬스터 = 구구 몬스터 썸네일, 연속 = flame, 별 = icon_coin) + 문구 + "7/15" + 진행 막대. 다 하면 초록 체크 도장.
- 아래:
  - chestReady → 큰 버튼 "보물상자 열기!" → 상자 열림(그림 교체 + 커지는 스프링) + 꽃가루(ConfettiView) + "별 10개를 받았어요!" + Sound.complete + 햅틱 success. 옷장 카드의 모은 별이 바로 늘어난다.
  - 열었음 → "내일 새 미션이 와요!"
  - 그 외 → "미션 3개를 다 하면 보물상자가 열려요"
- 이번 주 도장: "이번 주 도장" + 월~일 동그라미 7개(요일 글자는 OS 현지 요일 약자 — 새 문구 아님), 도장 찍힌 날은 별, 오늘은 테두리 강조.
- 화면 읽기: 카드 한 덩어리 "오늘의 미션, 3개 중 N개 완료"

### 게임 결과 창 (구구 계단·구구 몬스터 공통 GameResultPanel)
기존 note(새 기록/최고 기록) 아래 한 줄:
- allMissionsDone → "오늘의 미션 완료! 홈에서 보물상자를 열어 봐요"
- missionsCompleted 비어 있지 않음 → "미션 완료! " + 첫 미션 문구
- 그 외 없음. 체험 끝 창(TrialEndPanel)에도 같은 한 줄(가능하면).

## 문구 (한국어 원문 = 키, 6개 언어 ko·en·ja·hi·id·vi — 기존 항목처럼 en 외는 needs_review)
- "오늘의 미션"
- "계단 %lld칸 오르기"
- "몬스터 %lld마리 물리치기"
- "한 판에 %lld번 연속 맞히기"
- "게임에서 별 %lld개 모으기"
- "미션 3개를 다 하면 보물상자가 열려요"
- "보물상자 열기!"
- "별 %lld개를 받았어요!"
- "내일 새 미션이 와요!"
- "이번 주 도장"
- "미션 완료! %@"
- "오늘의 미션 완료! 홈에서 보물상자를 열어 봐요"
- "오늘의 미션, %lld개 중 %lld개 완료" (화면 읽기)
아이 문구: 쉬운 말, 반말 아님(앱 기존 톤 "~해요").

## 디버그 스크린숏 (DEBUG 전용, 기존 GUGU_PLAY_* 와 같은 방식)
`GUGU_PLAY_MISSIONS=ready` → 오늘 미션 3개 다 채움·상자 안 엶, `=opened` → 다 채움·연 상태 + 이번 주 앞 날짜 몇 개 도장, `=half` → 첫 미션만 완료·둘째 절반.

## 성공 기준
1. 단위 테스트: 계획(결정적·3개·풀 안·이틀 연속 다름), recordRound 누적·날짜 넘김 초기화·max·새로 완료 목록·allDone 한 번만, openChest(준비 전 오류·+10·두 번째 오류·도장), 구버전 저장본 읽기·잘못된 날짜 격리, 엔진 판 끝 → 미션 반영(계단·몬스터 각각). 기존 테스트 전부 통과.
2. 실제 화면: 시뮬레이터/에뮬레이터 홈 캡처 3장(half·ready·opened) + 상자 열기 실제 탭 후 별 증가 확인 + 게임 한 판 후 결과 창 미션 줄 확인.
3. i18n 점검 통과(iOS 카탈로그 6개 언어 빈 값 없음, Android `android.py check`).

## 작업 순서
- iOS(native-ios) 먼저 문구를 카탈로그에 넣는다 → Android 는 같은 한국어 키로 `tools/i18n/android.py name && gen && check`.
- 커밋·푸시 금지(대표님 규칙). 파일 삭제 금지.
