import Foundation

// 오늘의 미션 + 보물상자 + 이번 주 도장 (2.9.0, 계획서 .claude/plans/daily-missions.md — Android 와 같은 의미)
// 날마다 바뀌는 미션 3개를 날짜 문자열("yyyy-MM-dd")의 연·월·일 숫자만으로 정한다 — 같은 날이면 기기·시간대가 달라도 같은 미션.
// 진행도는 게임 결과를 띄울 때(PlayRewardsStore.recordRound) 오늘 기록(DailyMissionLog)에 쌓이고, 판 도중 나가면 안 쌓인다(별과 같은 규칙).
// 3개를 다 하면 홈에서 보물상자(별 +10)를 한 번 열 수 있고, 연 날은 이번 주 도장(월~일)에 찍힌다. 무료·이용권 모두 같다.

enum MissionKind: Equatable {
    case stairsSteps       // 구구 계단에서 오른 계단(오늘 누적)
    case monstersDefeated  // 구구 몬스터에서 물리친 몬스터(오늘 누적)
    case bestStreak        // 한 판의 연속 정답(오늘 판들 중 최고)
    case starsEarned       // 게임에서 받은 별(오늘 누적, 보물상자 별은 넣지 않음)
}

struct Mission: Equatable {
    let kind: MissionKind
    let target: Int

    /// 미션 문구 — 홈 카드 한 줄, 결과 창 "미션 완료! …"
    var title: String {
        switch kind {
        case .stairsSteps: return String(localized: "계단 \(target)칸 오르기")
        case .monstersDefeated: return String(localized: "몬스터 \(target)마리 물리치기")
        case .bestStreak: return String(localized: "한 판에 \(target)번 연속 맞히기")
        case .starsEarned: return String(localized: "게임에서 별 \(target)개 모으기")
        }
    }
}

enum DailyMissionPlan {
    /// 보물상자에서 받는 별
    static let chestStars = 10
    /// 도장(보물상자 연 날)을 남기는 개수 — 이번 주 7칸을 그리기에 넉넉하게
    static let chestDayCap = 14

    /// 미션 목표 — 체험 10문제 판으로도 여러 판이면 다 할 수 있는 수치(수치는 아이 관찰 전 초안)
    static let stairsTargets = [10, 15, 20]
    static let monsterTargets = [5, 8, 10]
    static let streakTargets = [5, 7]
    static let starTargets = [15, 25]

    /// 그날의 미션 3개 — [계단, 몬스터, 짝수 날 연속 / 홀수 날 별]. 이틀 연달아 같은 날은 없다(계단 목표가 날마다 바뀜)
    static func missions(day: String) -> [Mission] {
        guard let n = dayNumber(day) else { preconditionFailure("날짜 형식이 아님: \(day)") }
        let third = mod(n, 2) == 0
            ? Mission(kind: .bestStreak, target: streakTargets[mod(floorDiv(n, 2), 2)])
            : Mission(kind: .starsEarned, target: starTargets[mod(floorDiv(n, 2), 2)])
        return [
            Mission(kind: .stairsSteps, target: stairsTargets[mod(n, 3)]),
            Mission(kind: .monstersDefeated, target: monsterTargets[mod(floorDiv(n, 3), 3)]),
            third,
        ]
    }

    // MARK: - 날짜 문자열 ↔ 일 수

    /// "yyyy-MM-dd" → 1970-01-01 부터 센 일 수. 형식이 틀리거나 없는 날(2월 30일 등)이면 nil.
    /// 달력·시간대를 쓰지 않고 연·월·일 숫자로만 계산한다(Howard Hinnant days_from_civil) — 1970 년 전은 음수
    static func dayNumber(_ day: String) -> Int? {
        let parts = day.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy { ("0"..."9").contains($0) } }),
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              (1...12).contains(m), (1...daysIn(month: m, year: y)).contains(d)
        else { return nil }
        let yy = m <= 2 ? y - 1 : y          // 3월을 한 해의 첫 달로 센다(윤일이 해 끝에 오게)
        let era = floorDiv(yy, 400)
        let yoe = yy - era * 400
        let doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /// dayNumber 의 역 — 일 수 → "yyyy-MM-dd" (civil_from_days)
    static func dayString(_ n: Int) -> String {
        let z = n + 719_468
        let era = floorDiv(z, 146_097)
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        let y = yoe + era * 400 + (m <= 2 ? 1 : 0)
        return String(format: "%04d-%02d-%02d", y, m, d)
    }

    /// 그 날이 든 주의 월~일 7일
    static func week(of day: String) -> [String] {
        guard let n = dayNumber(day) else { preconditionFailure("날짜 형식이 아님: \(day)") }
        let monday = n - mod(n + 3, 7)   // 1970-01-01 은 목요일(월요일부터 3일째)
        return (0..<7).map { dayString(monday + $0) }
    }

    /// 도장 목록에 오늘을 넣는다 — 오름차순·중복 없음·최근 chestDayCap 개(기기 시계를 되돌린 뒤에도 순서가 깨지지 않게 다시 정렬)
    static func stamping(_ days: [String], _ day: String) -> [String] {
        Array(Set(days + [day]).sorted().suffix(chestDayCap))
    }

    private static func daysIn(month m: Int, year y: Int) -> Int {
        switch m {
        case 2: return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    /// 내림 나눗셈·나머지(Kotlin Math.floorDiv / floorMod 와 같음) — 1970 년 전 날짜도 0 이상 칸을 고른다
    private static func floorDiv(_ a: Int, _ b: Int) -> Int {
        let q = a / b
        return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q
    }

    private static func mod(_ a: Int, _ b: Int) -> Int { a - floorDiv(a, b) * b }
}

/// 하루치 미션 기록 (PlayRewards.missionLog). 날짜가 오늘이 아닌 기록은 지난 날 것이라 진행도 0 으로 본다(forDay)
struct DailyMissionLog: Codable, Equatable {
    let day: String
    /// 오늘 오른 계단·물리친 몬스터·받은 별 — 판마다 더한다
    var stairsSteps = 0
    var monstersDefeated = 0
    var starsEarned = 0
    /// 오늘 게임 판들의 최고 연속 정답
    var bestStreak = 0
    /// 오늘 보물상자를 열었는지
    var chestOpened = false

    init(day: String) {
        precondition(DailyMissionPlan.dayNumber(day) != nil, "날짜 형식이 아님: \(day)")
        self.day = day
    }

    /// 저장된 기록이 그날 것이면 그대로, 아니면(지난 날·처음) 빈 기록
    static func forDay(_ stored: DailyMissionLog?, day: String) -> DailyMissionLog {
        if let stored, stored.day == day { return stored }
        return DailyMissionLog(day: day)
    }

    var missions: [Mission] { DailyMissionPlan.missions(day: day) }

    func progress(_ kind: MissionKind) -> Int {
        switch kind {
        case .stairsSteps: return stairsSteps
        case .monstersDefeated: return monstersDefeated
        case .bestStreak: return bestStreak
        case .starsEarned: return starsEarned
        }
    }

    func isDone(_ mission: Mission) -> Bool { progress(mission.kind) >= mission.target }

    var allDone: Bool { missions.allSatisfy(isDone) }

    /// 게임 한 판(결과 화면)을 넣고, 이번 판으로 새로 목표를 넘은 미션을 미션 순서대로 돌려준다
    @discardableResult
    mutating func record(_ game: PlayGame, score: Int, stars: Int, bestStreak streak: Int) -> [Mission] {
        precondition(score >= 0 && stars >= 0 && streak >= 0, "판 결과는 0 이상이어야 함: \(score), \(stars), \(streak)")
        let before = missions.map(isDone)
        switch game {
        case .stairs: stairsSteps += score
        case .monster: monstersDefeated += score
        }
        starsEarned += stars
        bestStreak = max(bestStreak, streak)
        return zip(missions, before).filter { mission, wasDone in !wasDone && isDone(mission) }.map(\.0)
    }
}

extension DailyMissionLog {
    /// 누락 키는 기본값(앞으로 필드를 늘려도 읽히게). 날짜 형식이 틀리거나 음수면 throw — Persistence 가 원본을 격리한다
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let day = try c.decode(String.self, forKey: .day)
        guard DailyMissionPlan.dayNumber(day) != nil else {
            throw DecodingError.dataCorrupted(.init(codingPath: [CodingKeys.day], debugDescription: "날짜 형식이 아닌 미션 기록: \(day)"))  // i18n:skip — 개발용 디코딩 오류
        }
        self.init(day: day)
        stairsSteps = try c.decodeIfPresent(Int.self, forKey: .stairsSteps) ?? stairsSteps
        monstersDefeated = try c.decodeIfPresent(Int.self, forKey: .monstersDefeated) ?? monstersDefeated
        starsEarned = try c.decodeIfPresent(Int.self, forKey: .starsEarned) ?? starsEarned
        bestStreak = try c.decodeIfPresent(Int.self, forKey: .bestStreak) ?? bestStreak
        chestOpened = try c.decodeIfPresent(Bool.self, forKey: .chestOpened) ?? chestOpened
        guard stairsSteps >= 0, monstersDefeated >= 0, starsEarned >= 0, bestStreak >= 0 else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "음수 값이 있는 미션 기록"))  // i18n:skip — 개발용 디코딩 오류
        }
    }
}

/// 보물상자를 열 수 없는 이유 — 화면이 먼저 막아야 하는 상황(연타 등)이라 조용히 넘기지 않고 던진다
enum ChestError: Error, Equatable {
    case missionsLeft
    case alreadyOpened
}

/// 이번 주 도장 한 칸
struct WeekStamp: Equatable {
    let date: String
    /// 그날 보물상자를 열었는지
    let stamped: Bool
    let isToday: Bool
}

/// 홈 「오늘의 미션」 카드가 그리는 값 — 저장본을 읽기만 해서 만든다(PlayRewardsStore.missionBoard)
struct MissionBoard: Equatable {
    struct Item: Equatable {
        let mission: Mission
        /// 목표에서 자른 진행도 — 막대·"7/15"
        let progress: Int
        var done: Bool { progress >= mission.target }
    }

    let items: [Item]
    let chestOpened: Bool
    /// 이번 주 월~일 7칸
    let week: [WeekStamp]

    var doneCount: Int { items.filter(\.done).count }
    var allDone: Bool { items.allSatisfy(\.done) }
    var chestReady: Bool { allDone && !chestOpened }

    /// 홈 미션 구슬을 누르면 열 게임 — 계단·몬스터 미션은 그 게임. 연속 정답·별 모으기 미션은 어느 게임으로도 할 수 있어서
    /// 아직 자기 미션을 못 끝낸 첫 게임(계단 → 몬스터 순)으로 보낸다. 둘 다 끝났으면 구구 계단
    func game(for mission: Mission) -> PlayGame {
        switch mission.kind {
        case .stairsSteps: return .stairs
        case .monstersDefeated: return .monster
        case .bestStreak, .starsEarned:
            let pending = items.filter { !$0.done }.map(\.mission.kind)
            if pending.contains(.stairsSteps) { return .stairs }
            if pending.contains(.monstersDefeated) { return .monster }
            return .stairs
        }
    }

    /// log 는 오늘 기록(DailyMissionLog.forDay), chestDays 는 보물상자 연 날
    init(log: DailyMissionLog, chestDays: [String]) {
        items = log.missions.map { Item(mission: $0, progress: min(log.progress($0.kind), $0.target)) }
        chestOpened = log.chestOpened
        let stamped = Set(chestDays)
        week = DailyMissionPlan.week(of: log.day).map {
            WeekStamp(date: $0, stamped: stamped.contains($0), isToday: $0 == log.day)
        }
    }
}
