import Foundation
import QuartzCore
import Observation

// 세션 상태 머신 (SessionScreen.tsx 로직 이식) — @Observable 명령형 엔진.
// 뷰는 이 엔진을 관찰하고, 타이밍/제출/전환은 엔진이 직접 관리한다.

enum Feedback { case correct, wrong }

struct SessionDone {
    let result: SessionResult
    let commit: CommitResult
    let wrongCount: Int
}

@Observable
final class SessionEngine {
    let mode: GameMode
    let table: Int?
    /// 취약 문제 복습 세션 — 오답 풀의 문제를 먼저 낸다
    let review: Bool
    private let def: ModeDef
    private let game: GameStore
    /// 세션 결과를 밖으로 넘긴다 (학습 원장 적재).
    /// 엔진이 SyncStore/AuthStore 를 직접 알면 의존이 역류하므로 뷰 경계에서 잇는다.
    private let onCommit: (SessionResult) -> Void

    /// 코치 힌트를 띄우는 기준 — 이번 판에서 같은 단을 이만큼 틀리면 그 단 문제에 풀이 힌트를 붙인다
    static let coachAfterMisses = 2
    /// 복습 세션 문제 수 — 오답 풀 크기를 따르되 너무 짧거나 길지 않게
    static let reviewMin = 5
    static let reviewMax = 10

    // 표시 상태
    var problem: Problem
    var statement: Statement?
    var input: String = ""
    var combo: Int = 0
    var score: Int = 0
    var lives: Int
    var feedback: Feedback?
    var idx: Int = 0
    var done: SessionDone?
    /// 오답일 때 아이가 낸 수 — "35가 아니라 36이에요" 설명에 쓴다
    private(set) var lastGiven: Int?
    /// 새 문제가 나올 때마다 1씩 오른다 (문제 낭독 트리거)
    private(set) var problemSerial = 0
    /// 확인 시트가 떠 있는 동안 시간을 멈춘 시각
    private(set) var pausedAt: Double?
    /// 이번 판에서 풀어 낸 문제 수 — 0이면 그만둘 때 확인 없이 닫는다
    var answeredCount: Int { answers.count }

    // 진행 상태 (비표시)
    private var wrongPool: [String: Int]
    private var lock = false
    private var answers: [AnswerRecord] = []
    private var wrongList: [Problem] = []
    private var queue: [Problem] = []
    private var recent: [String] = []
    private var maxCombo = 0
    private var qStart: Double = 0
    private var sessionStart: Double = 0
    /// 대기 중인 문제 전환 콜백을 무효화하는 세대 번호 (재시작·이탈)
    private var sessionId = 0
    /// 60초 챌린지 만료 예약을 무효화하는 세대 번호 (일시정지·재시작·이탈)
    private var timerToken = 0
    /// 마지막 답을 내서 판의 결과가 정해졌다 — 전환을 기다리는 사이에 그만둬도 다 푼 판으로 기록한다
    private var outcomeDecided = false
    /// 그만두기로 이미 기록했다 — 두 번 불려도 한 번만 기록한다
    private var closed = false
    private var missesByTable: [Int: Int] = [:]
    private let sessionTotal: Int

    private func now() -> Double { CACurrentMediaTime() * 1000 }

    init(mode: GameMode, table: Int?, review: Bool = false, game: GameStore,
         onCommit: @escaping (SessionResult) -> Void = { _ in }) {
        self.mode = mode
        self.table = table
        self.review = review
        self.onCommit = onCommit
        self.def = Modes.def(mode)
        self.game = game
        self.wrongPool = game.state.wrongPool
        self.lives = def.lives ?? 0

        var q: [Problem] = []
        if review {
            q = Problems.reviewQueue(game.state.wrongPool, count: Self.reviewMax)
            sessionTotal = max(Self.reviewMin, min(Self.reviewMax, q.count))
        } else {
            sessionTotal = def.total
        }
        // 첫 문제/문장 (짝 보장)
        let p = q.isEmpty
            ? Problems.pick(table: table, wrongPool: game.state.wrongPool, recentKeys: [])
            : q.removeFirst()
        self.queue = q
        self.problem = p
        self.statement = mode == .truefalse ? Problems.makeStatement(p) : nil
    }

    func start() {
        sessionStart = now()
        qStart = now()
        problemSerial += 1
        scheduleExpiry(after: Double(def.timeLimitMs ?? 0))
    }

    /// 60초 챌린지 만료 예약 — 일시정지 후에는 남은 시간만큼 다시 건다
    private func scheduleExpiry(after ms: Double) {
        guard def.kind == .timed, def.timeLimitMs != nil else { return }
        timerToken += 1
        let token = timerToken
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0, ms) / 1000) { [weak self] in
            guard let self, token == self.timerToken, self.done == nil, self.pausedAt == nil else { return }
            self.finish()
        }
    }

    // 기대 정답: 빈칸 추리는 곱하는 수(b), 그 외는 곱셈 결과
    private func expected(_ p: Problem) -> Int { mode == .missing ? p.b : p.a * p.b }

    // MARK: - 입력 핸들러

    private var acceptsInput: Bool { !lock && done == nil && pausedAt == nil }

    func handleInput(_ n: Int) {
        guard acceptsInput else { return }
        if input.count >= 3 { return }
        Sound.shared.tap()
        input += String(n)
        let ans = mode == .missing ? problem.b : problem.a * problem.b
        if input.count >= String(ans).count {
            lock = true
            let captured = input
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) { [weak self] in
                self?.submit(captured)
            }
        }
    }

    func handleDelete() {
        guard acceptsInput else { return }
        Sound.shared.tap()
        if !input.isEmpty { input.removeLast() }
    }

    func handleManualSubmit() {
        guard acceptsInput, !input.isEmpty else { return }
        Sound.shared.tap()
        lock = true
        submit(input)
    }

    func handleOX(_ choice: Bool) {
        guard acceptsInput, let st = statement else { return }
        Sound.shared.tap()
        lock = true
        resolve(choice == st.isTrue, given: .boolean(choice))
    }

    // MARK: - 일시정지 / 중도 이탈

    /// 그만둘지 묻는 동안 시간을 멈춘다 — 제한 시간·응답 시간에서 확인 시간을 뺀다
    func pause() {
        guard done == nil, pausedAt == nil else { return }
        pausedAt = now()
        timerToken += 1   // 만료 예약 무효화
    }

    func resume() {
        guard let pausedAt else { return }
        let paused = now() - pausedAt
        sessionStart += paused
        qStart += paused
        self.pausedAt = nil
        if let limit = def.timeLimitMs, def.kind == .timed {
            scheduleExpiry(after: Double(limit) - (now() - sessionStart))
        }
    }

    /// 표시용 경과 시간 — 일시정지 중에는 멈춘 시각에서 고정된다
    func elapsedMs(at time: Double) -> Double {
        max(0, min(time, pausedAt ?? time) - sessionStart)
    }

    /// 중도 이탈 — 푼 문제만 부분 커밋한다(XP·오답 풀·오늘 정답). 별·신기록·추이 같은
    /// 한 판 완료 통계는 건드리지 않는다. 푼 문제가 없으면 기록할 것이 없다.
    func abandon() {
        guard done == nil, !closed else { return }
        closed = true
        sessionId += 1    // 대기 중인 문제 전환 무효화
        timerToken += 1   // 만료 예약 무효화
        lock = true
        Speech.shared.stop()
        guard !answers.isEmpty else { return }
        // 시간은 확인 창을 띄운 시각까지만 잰다. 마지막 답까지 냈으면(전환만 남았으면) 완료로 기록한다
        let result = SessionResult(
            mode: mode, table: table, answers: answers, maxCombo: maxCombo,
            durationMs: Int(elapsedMs(at: now()).rounded()), partial: !outcomeDecided
        )
        game.commitSession(result)
        onCommit(result)
    }

    // MARK: - 코어 로직

    private func submit(_ value: String) {
        guard let parsed = Int(value) else { lock = false; return }
        resolve(parsed == expected(problem), given: .number(parsed))
    }

    /// given 은 아이가 실제로 제출한 답 — 학습 원장에 그대로 남는다.
    /// 여기서 빠뜨리면 원장의 submittedAnswer 가 전부 꾸며진 값이 된다.
    private func resolve(_ correct: Bool, given: GivenAnswer) {
        let prob = problem
        let ms = Int((now() - qStart).rounded())
        answers.append(AnswerRecord(a: prob.a, b: prob.b, correct: correct, ms: ms, given: given))
        if case let .number(n) = given { lastGiven = n } else { lastGiven = nil }

        if correct {
            combo += 1
            if combo > maxCombo { maxCombo = combo }
            score += 1
            feedback = .correct
            Sound.shared.correct()
            if combo >= 3 { Sound.shared.combo(combo) }
            Haptics.success()
        } else {
            combo = 0
            wrongList.append(Problem(a: prob.a, b: prob.b))
            missesByTable[prob.a, default: 0] += 1
            feedback = .wrong
            Sound.shared.wrong()
            Haptics.error()
            if def.kind == .lives { lives -= 1 }
        }

        let last = def.kind == .fixed && idx + 1 >= sessionTotal
        let outOfLives = def.kind == .lives && lives <= 0
        if last || outOfLives { outcomeDecided = true }
        let sid = sessionId
        let delay = correct ? 0.38 : 1.05
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self, self.done == nil, sid == self.sessionId else { return }
            if last || outOfLives {
                self.finish()
            } else {
                self.idx += 1
                self.goNext()
            }
        }
    }

    private func goNext() {
        recent = Array((recent + [Problems.key(problem.a, problem.b)]).suffix(4))
        let p = queue.isEmpty
            ? Problems.pick(table: table, wrongPool: wrongPool, recentKeys: recent)
            : queue.removeFirst()
        problem = p
        statement = mode == .truefalse ? Problems.makeStatement(p) : nil
        input = ""
        feedback = nil
        lastGiven = nil
        // 확인 시트가 떠 있는 동안 넘어온 문제는 재개 시각부터 잰다 (resume 이 멈춘 시간만큼 민다)
        qStart = pausedAt ?? now()
        lock = false
        problemSerial += 1
    }

    private func finish() {
        guard done == nil else { return }
        timerToken += 1
        let result = SessionResult(
            mode: mode, table: table, answers: answers,
            // 확인 창이 떠 있는 동안 끝나면 멈춘 시각까지만 잰다
            maxCombo: maxCombo, durationMs: Int(elapsedMs(at: now()).rounded())
        )
        let commit = game.commitSession(result)
        onCommit(result)
        Sound.shared.complete()
        if commit.leveledUp {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { Sound.shared.levelUp() }
        }
        done = SessionDone(result: result, commit: commit, wrongCount: wrongList.count)
    }

    // MARK: - 재시작

    func restart(retryWrong: Bool) {
        let retained = retryWrong ? wrongList : []
        answers = []
        wrongList = []
        recent = []
        maxCombo = 0
        combo = 0
        score = 0
        lives = def.lives ?? 0
        idx = 0
        lock = false
        pausedAt = nil
        lastGiven = nil
        missesByTable = [:]
        outcomeDecided = false
        closed = false
        sessionId += 1
        wrongPool = game.state.wrongPool
        // 복습 세션의 "한 판 더"는 갱신된 오답 풀로 새 복습 큐를 만든다
        var next = retained
        if next.isEmpty && review {
            next = Problems.reviewQueue(wrongPool, count: Self.reviewMax)
        }
        let first = next.first ?? Problems.pick(table: table, wrongPool: wrongPool, recentKeys: [])
        queue = Array(next.dropFirst())
        problem = first
        statement = mode == .truefalse ? Problems.makeStatement(first) : nil
        input = ""
        feedback = nil
        done = nil
        start()
    }

    // MARK: - 뷰 편의

    var modeName: String { review ? "취약 문제 복습" : def.name }
    var modeKind: ModeKind { def.kind }
    var total: Int { sessionTotal }
    var maxLives: Int { def.lives ?? 3 }
    var timeLimitMs: Int? { def.timeLimitMs }
    var answerValue: Int { problem.a * problem.b }

    /// 지금 문제에 붙일 코치 힌트 — 학습 모드에서 같은 단을 여러 번 틀렸을 때만
    var coachHint: CoachHint? {
        guard mode == .practice, (missesByTable[problem.a] ?? 0) >= Self.coachAfterMisses else { return nil }
        return Hints.coach(a: problem.a, b: problem.b)
    }
}
