import XCTest
@testable import Gugu

/// B 구구 몬스터(곱셈 몬스터) 순수 규칙.
/// Unity 시험판 PrototypeSessionTests.MonsterSessionTests·ProblemRulesTests(출제) 이식
/// + PlayMode 규칙성(1,000회 채점·쉬는 동안 정지) + 학습 기록 + 골든 벡터(Android MonsterTests 와 같은 값)
/// + 2.9.0 문제 순서(무작위 = 원본 출제, 순서대로 = 단 × 1 → × 9).
/// 출제는 2~9단 × 1~9 고정 — 설정 「곱하는 수」(Problems.maxB)와 무관하다.
final class MonsterTests: XCTestCase {

    // MARK: - 도우미

    private func rng(_ seed: UInt32) -> () -> Double { SeededRandom.mulberry32(seed) }

    private func session(_ seed: UInt32) -> MonsterSession { MonsterSession(order: .random, random: rng(seed)) }

    private func product(_ p: Problem) -> Int { p.a * p.b }

    /// pairA 와 짝지었을 때 곱이 목표가 아닌 카드 중 가장 앞 자리
    private func wrongCard(_ q: MonsterQuestion, file: StaticString = #filePath, line: UInt = #line) -> Int {
        guard let i = (0..<MonsterSession.cardCount).first(where: {
            $0 != q.pairA && q.cards[q.pairA] * q.cards[$0] != product(q.target)
        }) else {
            XCTFail("오답 카드가 없음 \(q.cards)", file: file, line: line)
            return q.pairA
        }
        return i
    }

    private func defeat(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) {
        let q = s.current
        XCTAssertEqual(s.tap(card: q.pairA), .selected, file: file, line: line)
        XCTAssertEqual(s.tap(card: q.pairB).attempt?.correct, true, file: file, line: line)
    }

    /// 틀린 짝 → 공개가 끝날 때까지 흘림(같은 몬스터, 카드만 다시)
    private func miss(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) -> AttemptRecord? {
        let q = s.current
        _ = s.tap(card: q.pairA)
        let r = s.tap(card: wrongCard(q, file: file, line: line))
        XCTAssertEqual(r.attempt?.correct, false, file: file, line: line)
        s.tick(RoundClock.revealSec + 0.01)
        return r.attempt
    }

    private func assertIntegrity(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) {
        let ids = s.attempts.map(\.questionId)
        XCTAssertEqual(Set(ids).count, ids.count, "같은 문제 두 번 채점", file: file, line: line)
        XCTAssertEqual(s.defeated + s.wrongCount, s.attempts.count, file: file, line: line)
        let unjudged = Set(0..<s.issuedCount).subtracting(ids)
        XCTAssertLessThanOrEqual(unjudged.count, 1, "채점 없이 사라진 문제 \(unjudged.sorted())", file: file, line: line)
        if let only = unjudged.first { XCTAssertEqual(only, s.current.id, file: file, line: line) }
        for (i, a) in s.attempts.enumerated() {
            XCTAssertEqual(a.id, i, file: file, line: line)
            guard let pickA = a.pickA, let pickB = a.pickB else {
                XCTFail("B 기록에는 고른 카드가 있어야 함", file: file, line: line)
                continue
            }
            XCTAssertEqual(a.given, pickA * pickB, file: file, line: line)
            XCTAssertEqual(a.correct, a.given == product(a.problem), file: file, line: line)
            XCTAssertTrue(MonsterSession.targets.contains(a.problem), file: file, line: line)
        }
        XCTAssertEqual(s.answerRecords, s.attempts.map(\.answerRecord), file: file, line: line)
    }

    // MARK: - 출제 (원본 ProblemRulesTests)

    func testTargetsAre72FactsTwoToNineByOneToNine() {
        XCTAssertEqual(MonsterSession.targets.count, 72)
        XCTAssertTrue(MonsterSession.targets.allSatisfy { (2...9).contains($0.a) && (1...9).contains($0.b) })
        XCTAssertEqual(Set(MonsterSession.targets.map { Problems.key($0.a, $0.b) }).count, 72)
        XCTAssertEqual(MonsterSession.targets.first, Problem(a: 2, b: 1))
        XCTAssertEqual(MonsterSession.targets.last, Problem(a: 9, b: 9))
    }

    func testPickStaysInRangeAndCoversEveryFact() {
        let random = rng(1)
        var recent: [Problem] = []
        var seen = Set<String>()
        for _ in 0..<20_000 {
            let p = MonsterSession.pickTarget(recent: recent, random: random)
            XCTAssertTrue((2...9).contains(p.a) && (1...9).contains(p.b))
            seen.insert(Problems.key(p.a, p.b))
            recent.append(p)
            if recent.count > 3 { recent.removeFirst() }
        }
        XCTAssertEqual(seen.count, 72)
    }

    /// 직전 3문제 가중치 ×0.15 → 이론값: 3문제 안 반복 0.648%
    func testPickRecentAvoidanceMatchesSoftWeight() {
        let n = 200_000
        let random = rng(7)
        var history: [Problem] = []
        var immediate = 0, withinThree = 0
        for _ in 0..<n {
            let p = MonsterSession.pickTarget(recent: history, random: random)
            if history.last == p { immediate += 1 }
            if history.contains(p) { withinThree += 1 }
            history.append(p)
            if history.count > 3 { history.removeFirst() }
        }
        let expectedWithin = 3 * 0.15 / (69 + 3 * 0.15)
        XCTAssertEqual(Double(withinThree) / Double(n), expectedWithin, accuracy: 0.0015)
        XCTAssertLessThan(Double(immediate) / Double(n), 0.005)
    }

    /// 설정 「곱하는 수」를 19로 바꿔도 B 는 2~9단 × 1~9, 카드 1~9 그대로
    func testMultiplierSettingDoesNotChangeRange() {
        Problems.maxB = 19
        defer { Problems.maxB = Problems.defaultMaxB }
        let s = session(3)
        for _ in 0..<300 {
            XCTAssertTrue(MonsterSession.targets.contains(s.current.target))
            XCTAssertTrue(s.current.cards.allSatisfy { (1...9).contains($0) })
            defeat(s)
        }
    }

    // MARK: - 카드·채점 (원본 MonsterSessionTests)

    func testCardsAllFactsContainPairAndAreValid() {
        for seed: UInt32 in 0..<40 {
            let random = rng(seed)
            for p in MonsterSession.targets {
                let (cards, pairA, pairB) = MonsterSession.makeCards(p, random: random)
                XCTAssertEqual(cards.count, MonsterSession.cardCount)
                XCTAssertNotEqual(pairA, pairB)
                XCTAssertEqual(cards[pairA] * cards[pairB], product(p), "\(p)")
                XCTAssertTrue(cards.allSatisfy { MonsterSession.cardValues.contains($0) })
                let expectedDistinct = p.a == p.b ? MonsterSession.cardCount - 1 : MonsterSession.cardCount
                XCTAssertEqual(Set(cards).count, expectedDistinct, "\(p): \(cards)")
            }
        }
    }

    func testSelectAndDeselectDoNotJudge() {
        let s = session(2)
        let q = s.current
        XCTAssertEqual(s.tap(card: q.pairA), .selected)
        XCTAssertEqual(s.selected, q.pairA)
        XCTAssertEqual(s.clock.phase, .running, "첫 카드 고르기부터 시간 시작")
        XCTAssertEqual(s.tap(card: q.pairA), .deselected)
        XCTAssertNil(s.selected)
        XCTAssertTrue(s.attempts.isEmpty)
    }

    func testCorrectPairDefeatsOnceAndNextMonsterIsImmediate() {
        let s = session(4)
        let q = s.current
        _ = s.tap(card: q.pairB)
        let r = s.tap(card: q.pairA)
        guard case let .judged(attempt) = r else { return XCTFail("채점돼야 함: \(r)") }
        XCTAssertTrue(attempt.correct)
        XCTAssertEqual(attempt.questionId, q.id)
        XCTAssertEqual(s.defeated, 1)
        XCTAssertEqual(s.current.id, q.id + 1)
        XCTAssertEqual(s.monstersIssued, 2)
        XCTAssertNil(s.selected)
    }

    /// 예: 12 = 3×4 = 2×6. 그런 카드 배치가 나오는 시드를 찾아 확인한다. 기록은 몬스터의 목표 식으로 남는다
    func testOtherPairWithSameProductIsAcceptedAndRecordedAsTarget() {
        for seed: UInt32 in 0..<2000 {
            let s = session(seed)
            let q = s.current
            for i in 0..<MonsterSession.cardCount {
                for j in (i + 1)..<MonsterSession.cardCount {
                    let isShownPair = (i == q.pairA && j == q.pairB) || (i == q.pairB && j == q.pairA)
                    let isSamePair = Set([q.cards[i], q.cards[j]]) == Set([q.target.a, q.target.b])
                    if isShownPair || isSamePair || q.cards[i] * q.cards[j] != product(q.target) { continue }
                    _ = s.tap(card: i)
                    let attempt = s.tap(card: j).attempt
                    XCTAssertEqual(attempt?.correct, true, "\(q.target): \(q.cards[i])×\(q.cards[j])")
                    XCTAssertEqual(s.defeated, 1)
                    XCTAssertEqual(attempt?.pickA, q.cards[i])
                    XCTAssertEqual(attempt?.pickB, q.cards[j])
                    XCTAssertEqual(attempt?.answerRecord.a, q.target.a, "학습 기록은 목표 식")
                    XCTAssertEqual(attempt?.answerRecord.b, q.target.b)
                    XCTAssertEqual(attempt?.answerRecord.given, .number(product(q.target)))
                    return
                }
            }
        }
        XCTFail("다른 정답 조합이 나오는 시드를 찾지 못함")
    }

    func testWrongPairRevealsThenSameMonsterWithReshuffledCards() {
        let s = session(6)
        let q = s.current
        _ = s.tap(card: q.pairA)
        let r = s.tap(card: wrongCard(q))
        XCTAssertEqual(r.attempt?.correct, false)
        XCTAssertEqual(s.wrongCount, 1)
        XCTAssertEqual(s.tap(card: 0).reject, .revealing)
        XCTAssertNil(s.selected, "공개 중 탭은 선택도 하지 않음")
        s.tick(RoundClock.revealSec + 0.01)
        XCTAssertNotEqual(s.current.id, q.id)
        XCTAssertEqual(s.current.target, q.target, "같은 몬스터 재도전")
        XCTAssertEqual(s.monstersIssued, 1)
    }

    func testTapsAfterTimeUpAreRejectedAndSelectionDoesNotLeak() {
        let s = session(8)
        _ = s.tap(card: s.current.pairA)
        s.tick(RoundClock.durationSec - 0.0001)
        let q = s.current
        XCTAssertEqual(s.tap(card: q.pairB).attempt?.correct, true, "종료 직전 두 번째 카드는 채점")
        s.tick(0.001)
        XCTAssertEqual(s.tap(card: s.current.pairA).reject, .over)
        XCTAssertEqual(s.defeated, 1)
    }

    func testInvalidCardIsRejectedAndDoesNotStartClock() {
        let s = session(9)
        XCTAssertEqual(s.tap(card: -1).reject, .invalid)
        XCTAssertEqual(s.tap(card: MonsterSession.cardCount).reject, .invalid)
        XCTAssertEqual(s.clock.phase, .waiting)
    }

    // MARK: - 학습 기록 연결

    func testAnswerRecordsForLearning() {
        let s = session(10)
        s.tick(1.0)  // 첫 동작 전 1초
        let first = s.current.target
        defeat(s)
        s.tick(0.25)
        let second = s.current.target
        let wrong = miss(s)  // 탭 뒤 0.61초 공개
        s.tick(0.5)
        defeat(s)
        guard let wrong else { return XCTFail("오답 기록이 있어야 함") }
        XCTAssertEqual(s.answerRecords, [
            AnswerRecord(a: first.a, b: first.b, correct: true, ms: 1000, given: .number(product(first))),
            AnswerRecord(a: second.a, b: second.b, correct: false, ms: 250, given: .number(wrong.given)),
            AnswerRecord(a: second.a, b: second.b, correct: true, ms: 500, given: .number(product(second))),
        ])
        XCTAssertNotEqual(wrong.given, product(second))
    }

    /// 쉬는 창 = 화면이 tick 을 부르지 않음. 그동안 60초 시계·응답 시간이 멈춘다(원본 PausePlayTests)
    func testPauseFreezesClockAndResponseTime() {
        let s = session(11)
        _ = s.tap(card: s.current.pairA)  // 카드 고르기 → 60초 시작
        s.tick(0.2)
        XCTAssertEqual(s.clock.phase, .running)
        let remaining = s.clock.remaining
        s.tick(0)  // (쉬는 중: tick 없음 — 첫 프레임 dt 0 도 아무것도 바꾸지 않음)
        XCTAssertEqual(s.clock.remaining, remaining, "쉬는 동안 시계 멈춤")
        s.tick(0.5)  // 계속 하기
        XCTAssertLessThan(s.clock.remaining, remaining)
        XCTAssertEqual(s.tap(card: s.current.pairB).attempt?.ms, 700)
    }

    // MARK: - 무작위 채점 무결성

    /// 원본 RandomPlay: 판마다 60초 끝까지, 정답 쪽을 조금 더 자주 눌러 처치·오답이 둘 다 충분히 나오게
    func testRandomPlayNoDuplicateOrMissingGrades() {
        for seed: UInt32 in 1...5 {
            let driver = rng(seed &* 7919)
            var judged = 0
            for round: UInt32 in 0..<15 {
                let s = session(seed * 100 + round)
                var roundJudged = 0
                while s.clock.phase != .over {
                    let card: Int
                    if driver() < 0.5 {
                        card = GameRandom.index(2, random: driver) == 0 ? s.current.pairA : s.current.pairB
                    } else {
                        card = -1 + GameRandom.index(8, random: driver)  // -1…6 (없는 카드 포함)
                    }
                    if case .judged = s.tap(card: card) { judged += 1; roundJudged += 1 }
                    s.tick(driver() * 0.2)
                }
                XCTAssertEqual(s.attempts.count, roundJudged)
                assertIntegrity(s)
            }
            XCTAssertGreaterThan(judged, 1000, "seed \(seed)")
        }
    }

    /// 고정 프레임(1/15초)에 한 프레임 0~3번 탭, 1,000회 이상 채점(원본 PrototypePlayTests.ThousandTaps)
    func testFramePlayThousandJudgementsNoDuplicateOrMissing() {
        for seed: UInt32 in [11, 7, 2026] {
            let driver = rng(seed)
            var judged = 0, rounds = 0, rejected = 0
            var s = session(seed &+ 900)
            while judged < 1000 {
                let before = s.attempts.count
                var counted = 0
                for _ in 0..<GameRandom.index(4, random: driver) {
                    let q = s.current
                    let card = driver() < 0.6
                        ? (s.selected == q.pairA ? q.pairB : q.pairA)
                        : GameRandom.index(MonsterSession.cardCount, random: driver)
                    let r = s.tap(card: card)
                    if case .judged = r { counted += 1 }
                    if let reject = r.reject {
                        XCTAssertEqual(reject, .revealing, "거절은 오답 공개 중일 때만")
                        rejected += 1
                    }
                }
                XCTAssertEqual(s.attempts.count - before, counted)
                judged += counted
                s.tick(1.0 / 15.0)
                if s.clock.phase == .over {
                    assertIntegrity(s)
                    rounds += 1
                    s = session(seed &+ 900 &+ UInt32(rounds))
                }
            }
            XCTAssertGreaterThanOrEqual(rounds, 1, "seed \(seed)")
            XCTAssertGreaterThan(rejected, 0, "seed \(seed)")
        }
    }

    // MARK: - 골든 벡터 (Android MonsterTests.kt 와 같은 시드·같은 기대값)
    //
    // 스크립트: "c" = pairA → pairB 순서로 누름(처치), "w" = pairA → wrongCard(pairA 와 곱이 목표가 아닌 가장 앞 자리)
    // → tick(0.61) 으로 공개 끝. 기록: 생성 직후 + 글자마다 그 뒤의 지금 몬스터를 "단x곱하는수 [카드 6장] pairA,pairB" 로.
    // 기대값은 C# 원본을 그대로 옮긴 참조 구현과 대조해 확인했다.

    private func trace(seed: UInt32, order: ProblemOrder = .random, table: Int? = nil,
                       script: String) -> (questions: [String], s: MonsterSession) {
        let s = MonsterSession(order: order, table: table, random: rng(seed))
        func line() -> String {
            let q = s.current
            return "\(q.target.a)x\(q.target.b) \(q.cards) \(q.pairA),\(q.pairB)"
        }
        var lines = [line()]
        for ch in script {
            if ch == "c" {
                defeat(s)
            } else {
                _ = miss(s)
            }
            lines.append(line())
        }
        return (lines, s)
    }

    func testGoldenSeed42() {
        let (questions, s) = trace(seed: 42, script: "ccccwcc")
        XCTAssertEqual(questions, [
            "6x8 [8, 4, 6, 7, 9, 5] 2,0", "9x1 [6, 2, 8, 5, 1, 9] 5,4", "6x6 [9, 7, 6, 5, 6, 4] 4,2",
            "4x5 [3, 7, 4, 8, 6, 5] 2,5", "8x8 [7, 8, 8, 3, 4, 9] 2,1",
            "8x8 [9, 8, 3, 2, 7, 8] 5,1",  // 오답 뒤 같은 몬스터, 카드만 다시
            "9x5 [7, 9, 4, 6, 8, 5] 1,5", "6x6 [6, 6, 7, 8, 5, 9] 1,0",
        ])
        XCTAssertEqual(s.attempts.map(\.given), [48, 9, 36, 20, 56, 64, 45])
        XCTAssertEqual([s.defeated, s.wrongCount, s.stars, s.streak, s.bestStreak, s.monstersIssued], [6, 1, 6, 2, 4, 7])
    }

    func testGoldenSeed2026() {
        let (questions, s) = trace(seed: 2026, script: "cccccc")
        XCTAssertEqual(questions, [
            "5x6 [7, 4, 6, 5, 9, 2] 3,2", "6x9 [5, 6, 7, 8, 9, 2] 1,4", "8x3 [3, 2, 4, 7, 8, 9] 4,0",
            "9x4 [4, 3, 1, 9, 8, 5] 3,0", "2x3 [6, 1, 9, 4, 2, 3] 4,5", "8x1 [1, 2, 8, 3, 7, 9] 2,0",
            "3x5 [3, 4, 7, 5, 6, 2] 0,3",
        ])
        XCTAssertEqual([s.defeated, s.wrongCount, s.stars, s.streak, s.bestStreak, s.monstersIssued], [6, 0, 8, 6, 6, 7])
    }

    // MARK: - 순서대로 (2.9.0 시작 창 — 단 × 1 → × 9)
    //
    // 기존 "단 지정 + 가중 무작위"(testGoldenTable7Seed42·testTableLimitsTargetsToThatTable)는 화면이 더 만들지 않는 조합이라
    // 세션에서 지웠다(무작위는 단 지정을 받지 않음). "단을 지정하면 목표가 그 단 안에서만"은 아래 순서대로 + 단 지정 테스트가 맡는다.

    /// 단 지정이면 목표가 그 단 × 1 → × 9 차례이고 × 9 다음은 그 단 × 1 로 다시 — 몇 바퀴를 돌아도 그 단뿐, 1~9 모두 나온다
    func testSequentialFixedTableGoesOneToNineThenWraps() {
        for table in MonsterSession.tables {
            let s = MonsterSession(order: .sequential, table: table, random: rng(UInt32(100 + table)))
            XCTAssertEqual(s.table, table)
            XCTAssertEqual(s.order, .sequential)
            var seen = Set<Int>()
            for i in 0..<30 {
                XCTAssertEqual(s.current.target, Problem(a: table, b: i % 9 + 1), "\(table)단 \(i)번째")
                seen.insert(s.current.target.b)
                defeat(s)
            }
            XCTAssertEqual(seen, Set(1...9), "\(table)단")
            XCTAssertEqual(s.monstersIssued, 31)
            assertIntegrity(s)
        }
    }

    /// 단 없음: 시작 단은 2~9 중 무작위(× 1 부터), × 9 다음은 직전과 다른 2~9단 × 1. 시드마다 시작 단이 고루 나온다
    func testSequentialWithoutTableSwitchesToDifferentTableAfterNine() {
        var starts = Set<Int>()
        for seed: UInt32 in 0..<40 {
            let s = MonsterSession(order: .sequential, random: rng(seed))
            XCTAssertNil(s.table)
            XCTAssertEqual(s.current.target.b, 1)
            starts.insert(s.current.target.a)
            var previousTable = s.current.target.a
            for round in 0..<4 {
                for b in 1...9 {
                    XCTAssertEqual(s.current.target, Problem(a: previousTable, b: b), "seed \(seed) 바퀴 \(round)")
                    defeat(s)
                }
                let next = s.current.target
                XCTAssertEqual(next.b, 1)
                XCTAssertTrue(MonsterSession.tables.contains(next.a))
                XCTAssertNotEqual(next.a, previousTable, "× 9 다음은 다른 단 (seed \(seed))")
                previousTable = next.a
            }
            assertIntegrity(s)
        }
        XCTAssertEqual(starts, Set(MonsterSession.tables), "시작 단 2~9 가 모두 나옴")
    }

    /// 순서대로에서도 틀리면 같은 목표(같은 몬스터)를 카드만 섞어 다시 — 다음 곱하는 수로 넘어가지 않는다
    func testSequentialWrongKeepsSameTarget() {
        let s = MonsterSession(order: .sequential, table: 4, random: rng(5))
        defeat(s)   // 4×1 → 4×2
        let before = s.current
        let issued = s.monstersIssued
        let attempt = miss(s)
        XCTAssertEqual(attempt?.problem, Problem(a: 4, b: 2))
        XCTAssertEqual(s.current.target, Problem(a: 4, b: 2))
        XCTAssertNotEqual(s.current.id, before.id)
        XCTAssertEqual(s.monstersIssued, issued, "같은 몬스터 재출제는 새 몬스터로 세지 않음")
        defeat(s)
        XCTAssertEqual(s.current.target, Problem(a: 4, b: 3))
    }

    /// 다음 목표 규칙(난수 소비 포함): × 9 가 아니면 난수 없이 다음 곱하는 수, × 9 다음 단 지정은 그 단 × 1(난수 없음),
    /// 단 없음은 index(8) 하나 — 직전 단이 나오면 다시 뽑는다
    func testNextInOrderRuleAndRandomUse() {
        var used = 0
        func scripted(_ values: [Double]) -> () -> Double {
            var rest = values
            return {
                used += 1
                return rest.removeFirst()
            }
        }
        used = 0
        XCTAssertEqual(MonsterSession.nextInOrder(after: Problem(a: 7, b: 3), table: nil, random: scripted([])), Problem(a: 7, b: 4))
        XCTAssertEqual(MonsterSession.nextInOrder(after: Problem(a: 7, b: 9), table: 7, random: scripted([])), Problem(a: 7, b: 1))
        XCTAssertEqual(used, 0)
        // 0.6 → 2 + 4 = 6단(직전 단과 같음 → 다시), 0.1 → 2 + 0 = 2단
        XCTAssertEqual(MonsterSession.nextInOrder(after: Problem(a: 6, b: 9), table: nil, random: scripted([0.6, 0.1])),
                       Problem(a: 2, b: 1))
        XCTAssertEqual(used, 2)
    }

    /// 순서대로 판(단 지정·없음)도 무작위로 누르면 채점 중복·누락 없음, × 1 목표의 카드도 규칙대로
    func testSequentialRandomPlayNoDuplicateOrMissingGrades() {
        for seed: UInt32 in 1...4 {
            let driver = rng(seed &* 104_729)
            var judged = 0
            for round: UInt32 in 0..<10 {
                let tables: [Int?] = [nil, 2, 7, 9, nil]
                let s = MonsterSession(order: .sequential, table: tables[Int(round) % tables.count],
                                       random: rng(seed * 1000 + round))
                var roundJudged = 0
                while s.clock.phase != .over {
                    let card: Int
                    if driver() < 0.5 {
                        card = GameRandom.index(2, random: driver) == 0 ? s.current.pairA : s.current.pairB
                    } else {
                        card = -1 + GameRandom.index(8, random: driver)  // -1…6 (없는 카드 포함)
                    }
                    if case .judged = s.tap(card: card) { judged += 1; roundJudged += 1 }
                    s.tick(driver() * 0.2)
                }
                XCTAssertEqual(s.attempts.count, roundJudged)
                assertIntegrity(s)
                // 맞힌 목표만 차례로 보면 곱하는 수가 1씩 늘고(× 9 다음 × 1), 단 지정이면 그 단뿐
                let defeatedTargets = s.attempts.filter(\.correct).map(\.problem)
                for (prev, next) in zip(defeatedTargets, defeatedTargets.dropFirst()) {
                    XCTAssertEqual(next.b, prev.b == 9 ? 1 : prev.b + 1, "\(prev) → \(next)")
                    if let table = s.table { XCTAssertEqual(next.a, table) }
                    if prev.b != 9 { XCTAssertEqual(next.a, prev.a) }
                }
            }
            XCTAssertGreaterThan(judged, 500, "seed \(seed)")
        }
    }

    /// 순서대로 · 단 없음 골든: 시작 단 무작위 → × 1 … × 9(× 9 에서 한 번 틀림) → 다른 단 × 1 (참조 구현과 대조)
    func testGoldenSequentialSeed42() {
        let (questions, s) = trace(seed: 42, order: .sequential, script: "ccccccccwccc")
        XCTAssertEqual(questions, [
            "6x1 [1, 9, 6, 7, 2, 5] 2,0", "6x2 [5, 7, 2, 1, 3, 6] 5,2", "6x3 [2, 5, 4, 7, 3, 6] 5,4",
            "6x4 [2, 7, 5, 6, 4, 3] 3,4", "6x5 [7, 1, 5, 8, 4, 6] 5,2", "6x6 [7, 6, 2, 5, 3, 6] 1,5",
            "6x7 [7, 5, 8, 6, 1, 2] 3,0", "6x8 [6, 5, 7, 8, 9, 4] 0,3", "6x9 [6, 8, 9, 7, 1, 5] 0,2",
            "6x9 [5, 8, 7, 6, 9, 4] 3,4",  // 오답 뒤 같은 몬스터, 카드만 다시
            "9x1 [7, 8, 9, 4, 1, 2] 2,4", "9x2 [8, 3, 1, 9, 2, 6] 3,4", "9x3 [4, 6, 2, 8, 3, 9] 5,4",
        ])
        XCTAssertEqual(s.attempts.map(\.given), [6, 12, 18, 24, 30, 36, 42, 48, 48, 54, 9, 18])
        XCTAssertEqual([s.defeated, s.wrongCount, s.stars, s.streak, s.bestStreak, s.monstersIssued], [11, 1, 13, 3, 8, 12])
        XCTAssertNil(s.table)
    }

    /// 순서대로 · 7단 골든: 7×1 … 7×9 → 7×1 로 다시 (참조 구현과 대조)
    func testGoldenSequentialTable7Seed42() {
        let (questions, s) = trace(seed: 42, order: .sequential, table: 7, script: "ccccccccccc")
        XCTAssertEqual(questions, [
            "7x1 [1, 8, 6, 4, 7, 2] 4,0", "7x2 [3, 7, 8, 6, 2, 1] 1,4", "7x3 [4, 2, 7, 8, 3, 6] 2,4",
            "7x4 [3, 8, 6, 7, 5, 4] 3,5", "7x5 [8, 7, 6, 4, 5, 3] 1,4", "7x6 [2, 8, 5, 7, 1, 6] 3,5",
            "7x7 [3, 7, 7, 9, 6, 8] 2,1", "7x8 [8, 6, 7, 2, 5, 9] 2,0", "7x9 [9, 7, 6, 5, 8, 4] 1,0",
            "7x1 [6, 2, 1, 7, 4, 8] 3,2", "7x2 [3, 6, 1, 2, 7, 8] 4,3", "7x3 [8, 4, 6, 2, 3, 7] 5,4",
        ])
        XCTAssertEqual(s.attempts.map(\.given), [7, 14, 21, 28, 35, 42, 49, 56, 63, 7, 14])
        XCTAssertEqual([s.defeated, s.wrongCount, s.stars, s.streak, s.bestStreak, s.monstersIssued], [11, 0, 15, 11, 11, 12])
        XCTAssertEqual(s.table, 7)
    }

    /// 무작위 순서는 원본 출제 그대로 — 단 지정이 없고 목표가 2~9단에 고루 퍼진다
    func testRandomOrderHasNoTable() {
        let s = session(1)
        XCTAssertEqual(s.order, .random)
        XCTAssertNil(s.table)
        var tables = Set<Int>()
        for _ in 0..<60 {
            tables.insert(s.current.target.a)
            defeat(s)
        }
        XCTAssertEqual(tables, Set(MonsterSession.tables))
    }
}
