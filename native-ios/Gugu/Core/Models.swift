import Foundation

// 구구단 학습 앱 — 도메인 타입 (types.ts 이식)

/// 저장값(rawValue)은 기존 사용자 저장본 modesPlayed·bestScores 에 들어 있다 — 값을 지우거나 이름을 바꾸면
/// GameState 디코딩이 실패해 진행도가 통째로 격리·초기화된다. adventure 는 3D 어드벤처를 없앤 뒤에도 남겨 둔다.
/// stairs·monster 는 구구 계단·구구 몬스터 판의 학습 기록(2.9.0) — 학습 탭 모드 목록(Modes.list)에는 넣지 않는다.
enum GameMode: String, Codable, CaseIterable, Hashable {
    case practice, timeAttack, challenge, survival, missing, truefalse, adventure, stairs, monster

    /// 게임(구구 계단·구구 몬스터) 판 — 학습 기록(XP·오늘 목표·틀린 문제)에는 들어가지만 단 마스터리 별은 주지 않는다.
    /// 마스터리 별은 문제 수 하한 없이 정확도·속도로 매겨져서, 몇 마리짜리 체험 판만으로도 별 3개가 나오기 때문
    var isGame: Bool { self == .stairs || self == .monster }
}

/// 화면 테마 — system 은 기기 설정(라이트/다크)을 따른다
enum Theme: String, Codable, CaseIterable {
    case system, light, dark

    /// 저장값으로 정하는 시작 테마 — 고른 적 없으면(nil) 낮(light). 재디자인 V1 그림이 낮 하늘 기준이라 첫값을 다크에서 바꿨다.
    /// 다크를 직접 고른 사람(저장값 dark)은 그대로 밤이다. 모르는 값(손상)도 낮으로 시작하고 로그를 남긴다 — 저장값은 건드리지 않는다
    static func initial(saved: String?) -> Theme {
        guard let saved else { return .light }
        if let theme = Theme(rawValue: saved) { return theme }
        print("[Theme] 알 수 없는 저장값 — 낮으로 시작:", String(saved.prefix(32)))
        return .light
    }
}

struct Problem: Equatable {
    var a: Int   // 단 (2~9, 확장 가능)
    var b: Int   // 곱하는 수 (1~9)
}

/// 제출한 답 — OX 퀴즈는 참/거짓, 나머지는 수 (웹 `given?: number | boolean` 대응)
enum GivenAnswer: Codable, Equatable {
    case number(Int)
    case boolean(Bool)
    /// 수/참거짓으로 표현할 수 없는 경우 — 시간 초과로 아무 답도 내지 않은 문항 등.
    /// 서버 계약이 str 을 받으므로 "답이 없었다"를 0 으로 꾸미지 않고 그대로 남긴다.
    case text(String)

    /// 시간 초과 등으로 제출 자체가 없었음
    static let noAnswer = GivenAnswer.text("no_answer")

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(Int.self) {
            self = .number(value)
        } else if let value = try? container.decode(Bool.self) {
            self = .boolean(value)
        } else {
            self = .text(try container.decode(String.self))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .number(value): try container.encode(value)
        case let .boolean(value): try container.encode(value)
        case let .text(value): try container.encode(value)
        }
    }
}

struct AnswerRecord: Codable, Equatable {
    var a: Int
    var b: Int
    var correct: Bool
    var ms: Int   // 응답 시간(ms)
    /// 제출한 답 (서버 동기화용, 구기록은 없음)
    var given: GivenAnswer? = nil
}

struct SessionResult {
    var mode: GameMode
    var table: Int?          // 단일 단 집중이면 단 번호, 혼합이면 nil
    var answers: [AnswerRecord]
    var maxCombo: Int
    var durationMs: Int
    var partial: Bool = false   // 중도 이탈 부분 커밋 — 한 판 완료 통계와 구분
    var xpScale: Double? = nil  // 재대결 등 XP 체감 (nil/1 = 기본)
}

/// 하루 동안 한 단(AnswerRecord.a)에서 푼 문제의 합계 — 보호자 리포트 단별 실력(TableSkills)이 최근 14일을 더해 쓴다
struct TableTally: Codable, Equatable {
    var correct: Int
    var wrong: Int
    var msSum: Int

    static let zero = TableTally(correct: 0, wrong: 0, msSum: 0)

    var attempts: Int { correct + wrong }

    mutating func add(correct isCorrect: Bool, ms: Int) {
        if isCorrect { correct += 1 } else { wrong += 1 }
        msSum += ms
    }

    static func + (l: TableTally, r: TableTally) -> TableTally {
        TableTally(correct: l.correct + r.correct, wrong: l.wrong + r.wrong, msSum: l.msSum + r.msSum)
    }
}

// 날짜별 집계 — 보호자 주간 리포트용 링버퍼 항목 (최근 56일)
struct DayLogEntry: Codable, Equatable {
    var date: String            // YYYY-MM-DD
    var correct: Int
    var wrong: Int
    var msSum: Int
    var misses: [String: Int]
    /// 단별 합계(키 = 단). nil = 이 필드가 생기기 전 저장본의 날 — 합성 디코더가 Optional 을 decodeIfPresent 로 읽어
    /// 구버전 저장본도 실패하지 않는다. 그날 기록이 이 필드 전후에 걸쳐 있으면 뒤쪽 답만 들어 있다
    var tables: [Int: TableTally]? = nil

    /// 같은 날짜 기록을 합친다(손상된 저장본에 같은 날이 둘 이상 있을 때). 한쪽만 tables 가 있으면 그쪽을 쓴다
    func merged(with other: DayLogEntry) -> DayLogEntry {
        precondition(date == other.date, "다른 날짜는 합칠 수 없음: \(date), \(other.date)")
        var next = self
        next.correct += other.correct
        next.wrong += other.wrong
        next.msSum += other.msSum
        next.misses.merge(other.misses, uniquingKeysWith: +)
        switch (tables, other.tables) {
        case (nil, let t), (let t, nil): next.tables = t
        case let (l?, r?): next.tables = l.merging(r, uniquingKeysWith: +)
        }
        return next
    }
}

struct TableMastery: Codable, Equatable {
    var stars: Int          // 0~3
    var bestAccuracy: Double // 0~1
    var bestAvgMs: Int      // 최고(최저) 평균 응답시간
    var plays: Int

    static let empty = TableMastery(stars: 0, bestAccuracy: 0, bestAvgMs: 0, plays: 0)
}

struct GameState: Codable, Equatable {
    var version: Int = 1
    var totalXp: Int = 0
    // 리텐션
    var streak: Int = 0
    var lastPlayedDate: String = ""   // YYYY-MM-DD
    var dailyGoal: Int = 20           // 하루 목표 정답 수
    var dailyDate: String = ""        // 오늘 날짜
    var dailyCorrect: Int = 0         // 오늘 누적 정답
    // 진행
    var tableMastery: [Int: TableMastery] = [:]
    var unlockedAchievements: [String] = []
    // 통계
    var totalCorrect: Int = 0
    var totalWrong: Int = 0
    var maxCombo: Int = 0
    var recentAccuracy: [Int] = []    // 최근 세션 정확도(스파크라인용, 최대 20)
    var recentAvgMs: [Int] = []       // 최근 세션 평균속도
    // 출제 보조: 오답 가중 풀 (key=`a x b`)
    var wrongPool: [String: Int] = [:]
    // 모드 기록
    var bestScores: [GameMode: Int] = [:]  // 점수형 모드(챌린지/서바이벌) 최고 기록
    var modesPlayed: [GameMode] = []       // 플레이해 본 모드 (업적용)
    // 설정
    var onboarded: Bool = false
    // 주간 리포트용 날짜별 링버퍼. 구버전 저장본에는 키가 없어 nil 로 디코딩된다(= 기록 없음).
    var dayLog: [DayLogEntry]? = nil
}

extension GameState {
    /// 구버전 저장본(신규 필드 없던 시절) 호환 — 누락된 키는 선언부의 기본값으로 채운다.
    /// 합성 디코더는 기본값이 있어도 키가 없으면 실패해서, 필드를 추가할 때마다 구버전 사용자의
    /// 진행도가 통째로 초기화될 수 있었다.
    /// 타입이 틀린 값은 일부러 그대로 throw 한다 — 조용히 기본값으로 덮으면 손상을 알아채지 못하므로
    /// Persistence 가 원본을 격리하게 둔다.
    /// 주의: 필드를 추가하면 여기에도 한 줄 추가할 것 (빠지면 저장은 되는데 읽히지 않는다 —
    /// CoreTests.testGameStateRoundTripPreservesEveryField 가 잡는다).
    /// 합성 CodingKeys 가 private 이라 이 extension 은 선언과 같은 파일에 있어야 한다.
    init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? version
        totalXp = try c.decodeIfPresent(Int.self, forKey: .totalXp) ?? totalXp
        streak = try c.decodeIfPresent(Int.self, forKey: .streak) ?? streak
        lastPlayedDate = try c.decodeIfPresent(String.self, forKey: .lastPlayedDate) ?? lastPlayedDate
        dailyGoal = try c.decodeIfPresent(Int.self, forKey: .dailyGoal) ?? dailyGoal
        dailyDate = try c.decodeIfPresent(String.self, forKey: .dailyDate) ?? dailyDate
        dailyCorrect = try c.decodeIfPresent(Int.self, forKey: .dailyCorrect) ?? dailyCorrect
        tableMastery = try c.decodeIfPresent([Int: TableMastery].self, forKey: .tableMastery) ?? tableMastery
        unlockedAchievements = try c.decodeIfPresent([String].self, forKey: .unlockedAchievements) ?? unlockedAchievements
        totalCorrect = try c.decodeIfPresent(Int.self, forKey: .totalCorrect) ?? totalCorrect
        totalWrong = try c.decodeIfPresent(Int.self, forKey: .totalWrong) ?? totalWrong
        maxCombo = try c.decodeIfPresent(Int.self, forKey: .maxCombo) ?? maxCombo
        recentAccuracy = try c.decodeIfPresent([Int].self, forKey: .recentAccuracy) ?? recentAccuracy
        recentAvgMs = try c.decodeIfPresent([Int].self, forKey: .recentAvgMs) ?? recentAvgMs
        wrongPool = try c.decodeIfPresent([String: Int].self, forKey: .wrongPool) ?? wrongPool
        bestScores = try c.decodeIfPresent([GameMode: Int].self, forKey: .bestScores) ?? bestScores
        modesPlayed = try c.decodeIfPresent([GameMode].self, forKey: .modesPlayed) ?? modesPlayed
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? onboarded
        dayLog = try c.decodeIfPresent([DayLogEntry].self, forKey: .dayLog)
    }
}

struct CommitResult {
    var xpEarned: Int
    var leveledUp: Bool
    var newLevel: Int
    var unlocked: [String]     // 새로 해금된 업적 id
    var table: Int?
    var newStars: Int          // 이번에 갱신된 별점(단 집중 세션일 때)
    var improvedStars: Bool
    var goalReached: Bool      // 이번 세션으로 데일리 골 달성
    var score: Int?            // 점수형 모드(챌린지/서바이벌)의 이번 점수 = 정답 수
    var isNewBest: Bool        // 최고 기록 갱신 여부
    var partial: Bool          // 중도 이탈 부분 커밋
}
