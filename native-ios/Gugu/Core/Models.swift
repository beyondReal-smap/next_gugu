import Foundation

// 구구단 학습 앱 — 도메인 타입 (types.ts 이식)

enum GameMode: String, Codable, CaseIterable, Hashable {
    case practice, timeAttack, challenge, survival, missing, truefalse, adventure
}

enum Theme: String, Codable {
    case light, dark
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

// 날짜별 집계 — 보호자 주간 리포트용 링버퍼 항목 (최근 56일)
struct DayLogEntry: Codable, Equatable {
    var date: String            // YYYY-MM-DD
    var correct: Int
    var wrong: Int
    var msSum: Int
    var misses: [String: Int]
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
