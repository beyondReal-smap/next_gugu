import XCTest
@testable import Gugu

/// A 구구 계단(계단 점프) 순수 규칙.
/// Unity 시험판 PrototypeSessionTests.StairsSessionTests 이식 + PlayMode 규칙성(1,000회 채점·쉬는 동안 정지)
/// + 2.9.0 달라진 규칙(시작 단 2~19·topStep·보기 최댓값·보물 뒤 2~9) + 골든 벡터(Android StairsTests 와 같은 값)
/// + 2.9.0 문제 순서(순서대로 = 기존 규칙, 무작위 = 문제마다 2~9단 × 1~topStep).
/// 이 규칙은 설정 「곱하는 수」(Problems.maxB)를 읽지 않는다 — topStep 을 생성 때 받는다.
final class StairsTests: XCTestCase {

    // MARK: - 도우미

    private func rng(_ seed: UInt32) -> () -> Double { SeededRandom.mulberry32(seed) }

    private func session(_ seed: UInt32, topStep: Int = 9, firstTable: Int? = nil) -> StairsSession {
        StairsSession(topStep: topStep, order: .sequential, firstTable: firstTable, random: rng(seed))
    }

    private func randomSession(_ seed: UInt32, topStep: Int = 9) -> StairsSession {
        StairsSession(topStep: topStep, order: .random, random: rng(seed))
    }

    private func wrongLane(_ q: StairsQuestion) -> Int { (q.correctLane + 1) % StairsSession.lanes }

    @discardableResult
    private func correct(_ s: StairsSession, file: StaticString = #filePath, line: UInt = #line) -> StairsJumpResult {
        let r = s.jump(lane: s.current.correctLane)
        XCTAssertEqual(r.attempt?.correct, true, file: file, line: line)
        return r
    }

    /// 오답 → 공개가 끝날 때까지 흘림(같은 칸이 다시 나옴)
    private func wrong(_ s: StairsSession, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(s.jump(lane: wrongLane(s.current)).attempt?.correct, false, file: file, line: line)
        s.tick(RoundClock.revealSec + 0.01)
    }

    /// 구름이 서 있는 칸에 닿을 때까지 시간을 흘린다(무한 반복 방지 상한 포함). 흘린 시간을 돌려준다
    @discardableResult
    private func tickUntilCaught(_ s: StairsSession, step: Double = 0.05,
                                 file: StaticString = #filePath, line: UInt = #line) -> Double {
        var t = 0.0
        var i = 0
        while i < 100_000 && !s.caught {
            s.tick(step)
            t += step
            i += 1
        }
        XCTAssertTrue(s.caught, "먹구름에 잡혀야 함", file: file, line: line)
        return t
    }

    /// 끝난 판의 채점 무결성: 문제마다 최대 1번 채점, 마지막 하나 말고는 빠짐없이 채점, 채점 값이 규칙과 일치
    private func assertIntegrity(_ s: StairsSession, file: StaticString = #filePath, line: UInt = #line) {
        let ids = s.attempts.map(\.questionId)
        XCTAssertEqual(Set(ids).count, ids.count, "같은 문제 두 번 채점", file: file, line: line)
        XCTAssertEqual(s.climbed + s.wrongCount, s.attempts.count, file: file, line: line)
        let unjudged = Set(0..<s.issuedCount).subtracting(ids)
        XCTAssertLessThanOrEqual(unjudged.count, 1, "채점 없이 사라진 문제 \(unjudged.sorted())", file: file, line: line)
        if let only = unjudged.first { XCTAssertEqual(only, s.current.id, file: file, line: line) }
        for (i, a) in s.attempts.enumerated() {
            XCTAssertEqual(a.id, i, file: file, line: line)
            XCTAssertEqual(a.correct, a.given == a.problem.a * a.problem.b, file: file, line: line)
            XCTAssertGreaterThanOrEqual(a.responseSec, 0, file: file, line: line)
            XCTAssertEqual(a.answerRecord, AnswerRecord(a: a.problem.a, b: a.problem.b, correct: a.correct,
                                                        ms: a.ms, given: .number(a.given)), file: file, line: line)
        }
        XCTAssertEqual(s.answerRecords.count, s.attempts.count, file: file, line: line)
    }

    // MARK: - 보기

    func testChoicesAllFactsAllTopStepsAreValid() {
        for seed: UInt32 in 0..<40 {
            let random = rng(seed)
            for topStep in Problems.maxBOptions {
                for a in StairsSession.firstTables {
                    for b in 1...topStep {
                        let p = Problem(a: a, b: b)
                        let standing = a * (b - 1)
                        let c = StairsSession.makeChoices(p, standing: standing, topStep: topStep, random: random)
                        XCTAssertEqual(c.count, 3)
                        XCTAssertEqual(Set(c).count, 3, "\(p) 중복 \(c)")
                        XCTAssertTrue(c.contains(a * b))
                        XCTAssertFalse(c.contains(standing), "\(p) 서 있는 숫자 \(standing) 가 보기에 있음")
                        let maxValue = StairsSession.maxChoice(table: a, topStep: topStep)
                        XCTAssertTrue(c.allSatisfy { (1...maxValue).contains($0) }, "\(p) top \(topStep): \(c)")
                    }
                }
            }
        }
    }

    /// 보기 최댓값 = 단 × topStep + 단 (원본 99 고정 대체). 2 × 9 = 18 의 후보 27(+곱하는 수)은 최댓값에 따라 들어오고 빠진다
    func testMaxChoiceFollowsTableAndTopStep() {
        XCTAssertEqual(StairsSession.maxChoice(table: 7, topStep: 9), 70)
        XCTAssertEqual(StairsSession.maxChoice(table: 2, topStep: 12), 26)
        XCTAssertEqual(StairsSession.maxChoice(table: 19, topStep: 19), 380)

        let p = Problem(a: 2, b: 9)
        func everSeen(_ value: Int, topStep: Int) -> Bool {
            (0..<200).contains { seed in
                StairsSession.makeChoices(p, standing: 16, topStep: topStep, random: rng(UInt32(seed))).contains(value)
            }
        }
        XCTAssertFalse(everSeen(27, topStep: 9), "최댓값 20")
        XCTAssertFalse(everSeen(27, topStep: 12), "최댓값 26")
        XCTAssertTrue(everSeen(27, topStep: 15), "최댓값 32 — 후보로 들어옴")
        XCTAssertTrue(everSeen(20, topStep: 9), "다음 배수(단 × 10)는 최댓값과 같아 들어옴")
    }

    // MARK: - 오르기·보물 (원본)

    func testCorrectClimbsOneStepAndNextQuestionIsImmediate() {
        let s = session(3, firstTable: 7)
        XCTAssertEqual(s.current.problem, Problem(a: 7, b: 1))
        XCTAssertEqual(s.current.standing, 0)
        let r = s.jump(lane: s.current.correctLane)
        XCTAssertTrue(r.accepted)
        XCTAssertEqual(r.attempt?.correct, true)
        XCTAssertEqual(s.climbed, 1)
        XCTAssertEqual(s.current.problem, Problem(a: 7, b: 2))
        XCTAssertEqual(s.current.standing, 7)
        XCTAssertFalse(s.currentJudged)
        XCTAssertEqual(s.clock.phase, .running)
    }

    func testClimbingToTopGivesTreasureAndSwitchesTable() {
        let s = session(5, firstTable: 4)
        for step in 1...9 {
            XCTAssertEqual(correct(s).clearedTable, step == 9, "×\(step)")
        }
        XCTAssertEqual(s.treasures, 1)
        XCTAssertNotEqual(s.table, 4)
        XCTAssertTrue(StairsSession.randomTables.contains(s.table))
        XCTAssertEqual(s.step, 1)
        XCTAssertEqual(s.current.standing, 0)
        XCTAssertEqual(s.climbed, 9)
    }

    func testWrongRevealsThenRetriesSameStepWithNewQuestionId() {
        let s = session(9, firstTable: 6)
        correct(s)  // 6×1 → 6×2
        let q = s.current
        let r = s.jump(lane: wrongLane(q))
        XCTAssertTrue(r.accepted)
        XCTAssertEqual(r.attempt?.correct, false)
        XCTAssertEqual(s.wrongCount, 1)
        XCTAssertEqual(s.jump(lane: q.correctLane).reject, .revealing, "공개 중 입력은 채점하지 않음")
        XCTAssertEqual(s.attempts.count, 2)
        s.tick(RoundClock.revealSec + 0.01)
        XCTAssertNotEqual(s.current.id, q.id)
        XCTAssertEqual(s.current.problem, q.problem, "같은 칸 재도전")
        XCTAssertEqual(s.step, 2)
        XCTAssertEqual(s.climbed, 1)
    }

    // MARK: - 먹구름 (원본)

    func testInvalidLaneAndAfterCaughtAreRejected() {
        let s = session(1)
        XCTAssertEqual(s.jump(lane: 3).reject, .invalid)
        XCTAssertEqual(s.jump(lane: -1).reject, .invalid)
        XCTAssertEqual(s.clock.phase, .waiting, "잘못된 입력은 시계를 시작하지 않음")
        correct(s)  // 1칸, 구름은 3칸 아래
        s.tick(RisingCloud.secPerStep(standing: 1) * 3 - 0.01)
        XCTAssertEqual(s.clock.phase, .running)
        XCTAssertTrue(s.jump(lane: s.current.correctLane).accepted, "잡히기 직전 입력은 채점")
        tickUntilCaught(s)
        XCTAssertEqual(s.clock.phase, .over)
        XCTAssertEqual(s.jump(lane: s.current.correctLane).reject, .over)
        XCTAssertEqual(s.climbed, 2)
    }

    func testCloudStaysStillUntilFirstAction() {
        let s = session(2)
        s.tick(30)
        XCTAssertEqual(s.cloud.top, -RisingCloud.startGap)
        XCTAssertEqual(s.clock.phase, .waiting)
    }

    func testNoAnswerCaughtWhenCloudReachesStandingStep() {
        let s = session(4)
        correct(s)  // 서 있는 칸 1, 구름 -2 → 거리 3칸(따라잡기 없음)
        let expected = 3 * RisingCloud.secPerStep(standing: 1)
        s.tick(expected - 0.05)
        XCTAssertFalse(s.caught)
        s.tick(0.1)
        XCTAssertTrue(s.caught)
        XCTAssertEqual(s.clock.phase, .over)
        XCTAssertEqual(s.cloud.top, 1, "잡히면 구름은 서 있는 칸에서 멈춤")
        let issued = s.issuedCount
        s.tick(10)
        XCTAssertEqual(s.cloud.top, 1, "끝난 뒤에는 움직이지 않음")
        XCTAssertEqual(s.issuedCount, issued)
    }

    /// 입력 → 시간 순서: 잡히기 직전 누른 정답은 한 칸을 벌어 살아남는다
    func testCorrectAnswerJustBeforeCatchSurvives() {
        let s = session(6)
        correct(s)
        s.tick(3 * RisingCloud.secPerStep(standing: 1) - 0.001)
        XCTAssertTrue(s.jump(lane: s.current.correctLane).accepted)
        s.tick(0.1)
        XCTAssertFalse(s.caught)
        XCTAssertGreaterThan(s.cloud.gap(standing: s.climbed), 0.9)
    }

    func testWrongAnswerRevealDoesNotPauseCloud() {
        let s = session(8)
        correct(s)
        _ = s.jump(lane: wrongLane(s.current))
        let before = s.cloud.top
        s.tick(RoundClock.revealSec * 0.5)
        XCTAssertTrue(s.clock.isRevealing)
        XCTAssertGreaterThan(s.cloud.top, before, "오답 공개 중에도 구름은 차오름 = 오답의 대가")
    }

    func testFastClimberCloudCatchesUpToMaxGap() {
        let s = session(10, firstTable: 3)
        for _ in 0..<10 { correct(s) }  // 10칸, 구름 -2 → 거리 12칸
        var t = 0.0
        while s.cloud.gap(standing: s.climbed) > RisingCloud.maxGap {
            s.tick(0.05)
            t += 0.05
        }
        let expected = (12 - RisingCloud.maxGap) * RisingCloud.secPerStep(standing: 10) / RisingCloud.catchUpFactor
        XCTAssertEqual(t, expected, accuracy: 0.1, "멀어진 만큼은 3배 속도로 따라옴")
        XCTAssertFalse(s.caught)
    }

    func testSteadyClimberKeepsGoingPastSixtySeconds() {
        let s = session(12)
        for i in 0..<60 {
            XCTAssertTrue(s.jump(lane: s.current.correctLane).accepted, "\(i)번째")
            s.tick(1.5)
        }
        XCTAssertEqual(s.clock.phase, .running, "시간 제한 없음 — 꾸준히 맞히면 계속")
        XCTAssertGreaterThan(s.clock.elapsed, RoundClock.durationSec)
        XCTAssertEqual(s.treasures, 6)
    }

    func testCloudSpeedsUpWithHeightDownToFloor() {
        XCTAssertEqual(RisingCloud.secPerStep(standing: 0), RisingCloud.startSecPerStep)
        XCTAssertLessThan(RisingCloud.secPerStep(standing: 10), RisingCloud.secPerStep(standing: 9))
        XCTAssertEqual(RisingCloud.secPerStep(standing: 1000), RisingCloud.minSecPerStep)
    }

    // MARK: - 2.9.0 달라진 규칙

    func testRandomStartIsTwoToNine() {
        var tables = Set<Int>()
        for seed: UInt32 in 0..<300 {
            let s = session(seed)
            XCTAssertTrue(StairsSession.randomTables.contains(s.table), "시작 단 \(s.table)")
            XCTAssertEqual(s.current.problem, Problem(a: s.table, b: 1))
            tables.insert(s.table)
        }
        XCTAssertEqual(tables, Set(2...9))
    }

    func testStartAtNineteenWithTopStepTwelve() {
        let s = session(31, topStep: 12, firstTable: 19)
        for step in 1...12 {
            XCTAssertEqual(s.current.problem, Problem(a: 19, b: step))
            XCTAssertTrue(s.current.choices.allSatisfy { $0 <= 19 * 12 + 19 })
            XCTAssertEqual(correct(s).clearedTable, step == 12, "×\(step)")
        }
        XCTAssertEqual(s.treasures, 1)
        XCTAssertTrue(StairsSession.randomTables.contains(s.table), "보물 뒤 다음 단은 2~9: \(s.table)")
        XCTAssertEqual(s.current.problem, Problem(a: s.table, b: 1))
    }

    func testTopStepNineteenClimbsToTimesNineteen() {
        let s = session(32, topStep: 19, firstTable: 2)
        for step in 1...19 {
            XCTAssertEqual(s.current.problem, Problem(a: 2, b: step))
            XCTAssertEqual(correct(s).clearedTable, step == 19, "×\(step)")
        }
        XCTAssertEqual(s.treasures, 1)
        XCTAssertEqual(s.climbed, 19)
        XCTAssertNotEqual(s.table, 2)
    }

    /// 보물 뒤 다음 단: 2~9 무작위, 방금 끝낸 단은 빼고(원본 PickTable). 10단 이상에서 시작해도 다음은 2~9
    func testAfterTreasureNextTableIsTwoToNineAndDifferent() {
        for first in StairsSession.firstTables {
            for topStep in Problems.maxBOptions {
                let s = session(UInt32(first * 100 + topStep), topStep: topStep, firstTable: first)
                for _ in 0..<4 {
                    let finished = s.table
                    for _ in 0..<topStep { correct(s) }
                    XCTAssertTrue(StairsSession.randomTables.contains(s.table), "\(first)단 시작: 다음 \(s.table)")
                    XCTAssertNotEqual(s.table, finished)
                    XCTAssertEqual(s.step, 1)
                }
                XCTAssertEqual(s.treasures, 4)
            }
        }
    }

    // MARK: - 학습 기록 연결

    /// 응답 시간 = 문제가 나온 뒤 tick 으로 흘린 시간(첫 동작 전 대기 포함). 오답 뒤 재도전은 다시 나온 때부터 잰다
    func testAnswerRecordsForLearning() {
        let s = session(40, firstTable: 7)
        s.tick(2.0)  // 첫 동작 전 2초 생각
        correct(s)
        s.tick(0.3)
        let shown = s.current
        let given = shown.choices[wrongLane(shown)]
        _ = s.jump(lane: wrongLane(shown))
        s.tick(RoundClock.revealSec + 0.01)  // 공개 끝 → 같은 칸 다시
        s.tick(0.4)
        correct(s)
        XCTAssertEqual(s.answerRecords, [
            AnswerRecord(a: 7, b: 1, correct: true, ms: 2000, given: .number(7)),
            AnswerRecord(a: 7, b: 2, correct: false, ms: 300, given: .number(given)),
            AnswerRecord(a: 7, b: 2, correct: true, ms: 400, given: .number(14)),
        ])
        XCTAssertEqual(s.missToShow?.problem, Problem(a: 7, b: 2))
    }

    /// 쉬는 창 = 화면이 tick 을 부르지 않음. 그동안 먹구름·시계·응답 시간이 모두 멈춘다(원본 PausePlayTests)
    func testPauseFreezesCloudClockAndResponseTime() {
        let s = session(41)
        correct(s)  // 첫 점프 → 먹구름 출발
        s.tick(1.0)
        let top = s.cloud.top
        let elapsed = s.clock.elapsed
        // (쉬는 중: tick 없음 — 화면 첫 프레임의 dt 0 도 아무것도 바꾸지 않음)
        s.tick(0)
        XCTAssertEqual(s.cloud.top, top, "쉬는 동안 먹구름 멈춤")
        XCTAssertEqual(s.clock.elapsed, elapsed)
        s.tick(0.5)  // 계속 하기
        XCTAssertGreaterThan(s.cloud.top, top, "계속 하면 다시 차오름")
        let r = correct(s)
        XCTAssertEqual(r.attempt?.ms, 1500, "쉬는 동안은 응답 시간에 들어가지 않음")
        XCTAssertEqual(s.stars, 2)
    }

    // MARK: - 무작위 채점 무결성

    /// 무작위 탭·시간 진행 수천 번(원본 RandomPlay): 받아들인 탭마다 채점 정확히 1번, 한 문제는 최대 1번 채점, 빠진 문제 없음.
    /// 판마다 topStep·시작 단을 바꿔 달라진 규칙도 함께 돌린다
    func testRandomPlayNoDuplicateOrMissingGrades() {
        for seed: UInt32 in 1...5 {
            let driver = rng(seed &* 7919)
            var accepted = 0, rejected = 0
            for round: UInt32 in 0..<10 {
                let firsts: [Int?] = [nil, 19, 2, 13, nil]
                let s = session(seed * 100 + round, topStep: Problems.maxBOptions[Int(round) % 4],
                                firstTable: firsts[Int(round) % firsts.count])
                var roundAccepted = 0
                var played = 0.0
                // 판에 시간 제한이 없으므로 120초 동안 무작위로 누른 뒤 손을 떼고 구름에 잡힐 때까지 흘린다
                var guardCount = 0
                while s.clock.phase != .over && guardCount < 200_000 {
                    guardCount += 1
                    if played < 120 {
                        let lane = -1 + GameRandom.index(5, random: driver)  // -1…3 (없는 칸 포함)
                        if s.jump(lane: lane).accepted { accepted += 1; roundAccepted += 1 } else { rejected += 1 }
                    }
                    let dt = driver() * 0.25
                    s.tick(dt)
                    played += dt
                }
                XCTAssertTrue(s.caught)
                XCTAssertEqual(s.attempts.count, roundAccepted)
                assertIntegrity(s)
            }
            XCTAssertGreaterThan(accepted, 1000, "충분히 많은 채점 (seed \(seed))")
            XCTAssertGreaterThan(rejected, 0)
        }
    }

    /// 고정 프레임(1/15초)에 한 프레임 0~2번 탭, 1,000회 이상 채점(원본 PrototypePlayTests.ThousandTaps):
    /// 탭마다 채점 0 또는 1번, 거절은 오답 공개 중일 때만, 문제당 최대 1번
    func testFramePlayThousandJudgementsNoDuplicateOrMissing() {
        let configs: [(seed: UInt32, topStep: Int, first: Int?)] = [(7, 9, nil), (11, 12, 19), (2026, 19, 5)]
        for config in configs {
            let driver = rng(config.seed)
            var judged = 0, rounds = 0, rejected = 0
            var s = session(config.seed &+ 500, topStep: config.topStep, firstTable: config.first)
            while judged < 1000 {
                let before = s.attempts.count
                var acceptedThisFrame = 0
                for _ in 0..<GameRandom.index(3, random: driver) {
                    let lane = driver() < 0.7 ? s.current.correctLane : GameRandom.index(3, random: driver)
                    let r = s.jump(lane: lane)
                    if r.accepted {
                        acceptedThisFrame += 1
                    } else {
                        XCTAssertEqual(r.reject, .revealing, "거절은 오답 공개 중일 때만")
                        rejected += 1
                    }
                }
                XCTAssertEqual(s.attempts.count - before, acceptedThisFrame)
                judged += acceptedThisFrame
                s.tick(1.0 / 15.0)
                // 시간 제한이 없으니 한 판에 120번 채점하면 손을 떼고 구름에 잡히게 해 판을 넘긴다
                if s.attempts.count >= 120 && s.clock.phase != .over { tickUntilCaught(s) }
                if s.clock.phase == .over {
                    assertIntegrity(s)
                    rounds += 1
                    s = session(config.seed &+ 500 &+ UInt32(rounds), topStep: config.topStep, firstTable: config.first)
                }
            }
            XCTAssertGreaterThanOrEqual(rounds, 1, "seed \(config.seed)")
            XCTAssertGreaterThan(rejected, 0, "seed \(config.seed)")
        }
    }

    // MARK: - 골든 벡터 (Android StairsTests.kt 와 같은 시드·같은 기대값)
    //
    // 스크립트: "c" = 정답 칸으로 점프, "w" = 정답 오른쪽 칸((correctLane + 1) % 3)으로 점프 → tick(0.61) 으로 공개 끝.
    // 기록: 생성 직후 + 글자마다 그 뒤의 지금 문제를 "단x칸 [왼쪽, 가운데, 오른쪽]" 으로.
    // 기대값은 C# 원본을 그대로 옮긴 참조 구현(2.9.0 규칙 반영)과 대조해 확인했다.

    private func trace(seed: UInt32, topStep: Int, firstTable: Int?, script: String) -> (questions: [String], s: StairsSession) {
        trace(session(seed, topStep: topStep, firstTable: firstTable), script: script) {
            "\($0.problem.a)x\($0.problem.b) \($0.choices)"
        }
    }

    /// 무작위 순서 — 서 있는 발판(st)이 문제마다 직전 정답이라 줄에 함께 적는다: "단x곱하는수 st=발판 [보기]"
    private func randomTrace(seed: UInt32, topStep: Int, script: String) -> (questions: [String], s: StairsSession) {
        trace(randomSession(seed, topStep: topStep), script: script) {
            "\($0.problem.a)x\($0.problem.b) st=\($0.standing) \($0.choices)"
        }
    }

    private func trace(_ s: StairsSession, script: String,
                       format: (StairsQuestion) -> String) -> (questions: [String], s: StairsSession) {
        var lines = [format(s.current)]
        for ch in script {
            if ch == "c" {
                correct(s)
            } else {
                wrong(s)
            }
            lines.append(format(s.current))
        }
        return (lines, s)
    }

    /// 홈 시작: 2~9단 무작위, 곱하는 수 9. 8칸 맞힘 → ×9 에서 한 번 틀림 → 보물 → 다음 단
    func testGoldenRandomStartTopStep9() {
        let (questions, s) = trace(seed: 42, topStep: 9, firstTable: nil, script: "ccccccccwccc")
        XCTAssertEqual(questions, [
            "6x1 [12, 6, 7]", "6x2 [14, 12, 18]", "6x3 [18, 15, 17]", "6x4 [25, 28, 24]", "6x5 [30, 25, 31]",
            "6x6 [35, 36, 37]", "6x7 [48, 43, 42]", "6x8 [40, 56, 48]", "6x9 [55, 60, 54]",
            "6x9 [54, 45, 55]",  // 오답 뒤 같은 칸, 보기만 다시
            "2x1 [1, 2, 4]", "2x2 [5, 4, 3]", "2x3 [7, 6, 5]",
        ])
        XCTAssertEqual([s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak], [11, 1, 1, 18, 3, 8])
    }

    /// 단 시트 시작: 19단, 곱하는 수 12. 꼭대기 ×12 → 보물 → 2~9단
    func testGoldenStart19TopStep12() {
        let (questions, s) = trace(seed: 2026, topStep: 12, firstTable: 19, script: "cccccccccccccc")
        XCTAssertEqual(questions, [
            "19x1 [19, 38, 20]", "19x2 [36, 37, 38]", "19x3 [54, 60, 57]", "19x4 [80, 95, 76]",
            "19x5 [114, 94, 95]", "19x6 [120, 133, 114]", "19x7 [126, 133, 134]", "19x8 [171, 144, 152]",
            "19x9 [180, 171, 170]", "19x10 [189, 190, 209]", "19x11 [209, 228, 208]", "19x12 [228, 240, 229]",
            "3x1 [4, 3, 2]", "3x2 [6, 5, 7]", "3x3 [8, 9, 10]",
        ])
        XCTAssertEqual([s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak], [14, 1, 0, 23, 14, 14])
    }

    /// 단 시트 시작: 13단, 곱하는 수 19
    func testGoldenStart13TopStep19() {
        let (questions, s) = trace(seed: 7, topStep: 19, firstTable: 13, script: "cwcc")
        XCTAssertEqual(questions, [
            "13x1 [13, 12, 14]", "13x2 [26, 39, 28]", "13x2 [24, 28, 26]", "13x3 [42, 36, 39]", "13x4 [52, 53, 51]",
        ])
        XCTAssertEqual([s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak], [3, 0, 1, 3, 2, 2])
    }

    // MARK: - 무작위 순서 (2.9.0 시작 창)

    /// 무작위 골든: 문제마다 2~9단 × 1~9, 서 있는 발판 = 직전 정답(첫 칸 0), 오답 뒤 같은 식·같은 발판에 보기만 다시 (참조 구현과 대조)
    func testGoldenRandomOrderSeed42TopStep9() {
        let (questions, s) = randomTrace(seed: 42, topStep: 9, script: "cccwccc")
        XCTAssertEqual(questions, [
            "6x5 st=0 [29, 25, 30]", "8x5 st=30 [48, 45, 40]", "7x6 st=40 [48, 42, 36]", "4x1 st=42 [3, 4, 5]",
            "4x1 st=42 [4, 3, 5]",  // 오답 뒤 같은 식, 보기만 다시
            "4x5 st=4 [21, 15, 20]", "3x3 st=20 [12, 8, 9]", "4x1 st=9 [4, 8, 5]",
        ])
        XCTAssertEqual([s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak], [6, 0, 1, 6, 3, 3])
        XCTAssertEqual(s.step, 7)
    }

    /// 무작위 골든: 곱하는 수 12 — 12칸째에 보물(+5), 단은 그대로 문제마다 무작위 (참조 구현과 대조)
    func testGoldenRandomOrderSeed2026TopStep12() {
        let (questions, s) = randomTrace(seed: 2026, topStep: 12, script: "cccccccccccc")
        XCTAssertEqual(questions, [
            "5x4 st=0 [20, 19, 24]", "4x1 st=20 [3, 4, 5]", "3x4 st=4 [16, 15, 12]", "8x12 st=12 [95, 104, 96]",
            "4x8 st=96 [24, 36, 32]", "8x9 st=32 [81, 72, 80]", "9x3 st=72 [28, 26, 27]", "6x11 st=27 [66, 72, 77]",
            "6x9 st=66 [55, 45, 54]", "9x3 st=54 [27, 28, 30]", "2x12 st=27 [24, 26, 23]", "2x11 st=24 [21, 22, 23]",
            "9x12 st=22 [117, 109, 108]",
        ])
        XCTAssertEqual([s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak], [12, 1, 0, 21, 12, 12])
        XCTAssertEqual(s.step, 1, "보물 뒤 1번째 칸부터 다시")
    }

    /// 무작위 식 고르기: 단 index(8) → 곱하는 수 index(topStep) 순서로 난수 2개, 직전 식과 같으면 둘 다 다시
    func testRandomProblemDrawOrderAndRedrawOfSameProblem() {
        var used = 0
        var values = [0.26, 0.23, 0.26, 0.23, 0.39, 0.23]   // 4×3 → 4×3(직전과 같음) → 5×3
        let random: () -> Double = {
            used += 1
            return values.removeFirst()
        }
        XCTAssertEqual(StairsSession.randomProblem(topStep: 9, previous: nil, random: random), Problem(a: 4, b: 3))
        XCTAssertEqual(used, 2)
        XCTAssertEqual(StairsSession.randomProblem(topStep: 9, previous: Problem(a: 4, b: 3), random: random), Problem(a: 5, b: 3))
        XCTAssertEqual(used, 6)
    }

    /// 무작위: 단 2~9·곱하는 수 1~topStep 안에서만, 직전과 같은 식은 바로 이어서 안 나오고, 범위가 모두 나온다
    func testRandomOrderRangeNoImmediateRepeatAndCoverage() {
        for topStep in Problems.maxBOptions {
            let s = randomSession(UInt32(300 + topStep), topStep: topStep)
            var tables = Set<Int>(), multipliers = Set<Int>()
            var previous: Problem?
            for _ in 0..<(topStep * 40) {
                let p = s.current.problem
                XCTAssertTrue(StairsSession.randomTables.contains(p.a), "top \(topStep): \(p)")
                XCTAssertTrue((1...topStep).contains(p.b), "top \(topStep): \(p)")
                XCTAssertNotEqual(p, previous, "직전과 같은 식")
                tables.insert(p.a)
                multipliers.insert(p.b)
                previous = p
                correct(s)
            }
            XCTAssertEqual(tables, Set(StairsSession.randomTables), "top \(topStep)")
            XCTAssertEqual(multipliers, Set(1...topStep), "top \(topStep)")
            XCTAssertEqual(s.table, s.current.problem.a, "table 은 지금 문제의 단")
        }
    }

    /// 무작위: 서 있는 발판 = 직전 정답(첫 칸 0), 보기에 서 있는 숫자는 없음(정답과 같을 때 말고), 오답이면 같은 식·같은 발판
    func testRandomOrderStandingIsPreviousAnswer() {
        let s = randomSession(17)
        XCTAssertEqual(s.current.standing, 0, "첫 칸은 출발(0)")
        for i in 0..<200 {
            let q = s.current
            let answer = q.problem.a * q.problem.b
            if q.standing != answer { XCTAssertFalse(q.choices.contains(q.standing), "\(q.problem) 발판 \(q.standing) \(q.choices)") }
            XCTAssertTrue(q.choices.allSatisfy { (1...StairsSession.maxChoice(table: q.problem.a, topStep: 9)).contains($0) })
            if i % 7 == 3 {
                wrong(s)
                XCTAssertEqual(s.current.problem, q.problem)
                XCTAssertEqual(s.current.standing, q.standing)
                XCTAssertNotEqual(s.current.id, q.id)
            } else {
                correct(s)
                XCTAssertEqual(s.current.standing, answer, "서 있는 발판 = 방금 맞힌 답")
            }
        }
    }

    /// 무작위: topStep 칸을 맞힐 때마다 보물 +1(별 +5) — 틀린 칸은 세지 않고, step 은 1~topStep 을 돈다
    func testRandomOrderTreasureEveryTopStepClimbs() {
        for topStep in Problems.maxBOptions {
            let s = randomSession(UInt32(topStep), topStep: topStep)
            for i in 1...(topStep * 3) {
                if i % 4 == 0 { wrong(s) }
                XCTAssertEqual(s.step, (i - 1) % topStep + 1)
                let r = correct(s)
                XCTAssertEqual(r.clearedTable, i % topStep == 0, "top \(topStep) \(i)칸째")
                XCTAssertEqual(s.treasures, i / topStep)
            }
            XCTAssertEqual(s.climbed, topStep * 3)
            XCTAssertEqual(s.step, 1)
            assertIntegrity(s)
        }
    }

    /// 무작위 판도 무작위로 누르면 채점 중복·누락 없음(먹구름에 잡힐 때까지)
    func testRandomOrderRandomPlayNoDuplicateOrMissingGrades() {
        for seed: UInt32 in 1...4 {
            let driver = rng(seed &* 104_729)
            var accepted = 0
            for round: UInt32 in 0..<8 {
                let s = randomSession(seed * 1000 + round, topStep: Problems.maxBOptions[Int(round) % 4])
                var roundAccepted = 0
                var played = 0.0
                var guardCount = 0
                while s.clock.phase != .over && guardCount < 200_000 {
                    guardCount += 1
                    if played < 90 {
                        let lane = -1 + GameRandom.index(5, random: driver)  // -1…3 (없는 칸 포함)
                        if s.jump(lane: lane).accepted { accepted += 1; roundAccepted += 1 }
                    }
                    let dt = driver() * 0.25
                    s.tick(dt)
                    played += dt
                }
                XCTAssertTrue(s.caught)
                XCTAssertEqual(s.attempts.count, roundAccepted)
                assertIntegrity(s)
            }
            XCTAssertGreaterThan(accepted, 500, "seed \(seed)")
        }
    }
}
