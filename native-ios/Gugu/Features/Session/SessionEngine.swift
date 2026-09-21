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
    private let def: ModeDef
    private let game: GameStore

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
    private var sessionId = 0

    private func now() -> Double { CACurrentMediaTime() * 1000 }

    init(mode: GameMode, table: Int?, game: GameStore) {
        self.mode = mode
        self.table = table
        self.def = Modes.def(mode)
        self.game = game
        self.wrongPool = game.state.wrongPool
        self.lives = def.lives ?? 0
        // 첫 문제/문장 (짝 보장)
        let p = Problems.pick(table: table, wrongPool: game.state.wrongPool, recentKeys: [])
        self.problem = p
        self.statement = mode == .truefalse ? Problems.makeStatement(p) : nil
    }

    func start() {
        sessionStart = now()
        qStart = now()
        // 60초 챌린지 만료 타이머
        if def.kind == .timed, let limit = def.timeLimitMs {
            let sid = sessionId
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(limit) / 1000) { [weak self] in
                guard let self, sid == self.sessionId, self.done == nil else { return }
                self.finish()
            }
        }
    }

    // 기대 정답: 빈칸 추리는 곱하는 수(b), 그 외는 곱셈 결과
    private func expected(_ p: Problem) -> Int { mode == .missing ? p.b : p.a * p.b }

    // MARK: - 입력 핸들러

    func handleInput(_ n: Int) {
        guard !lock, done == nil else { return }
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
        guard !lock, done == nil else { return }
        Sound.shared.tap()
        if !input.isEmpty { input.removeLast() }
    }

    func handleManualSubmit() {
        guard !lock, done == nil, !input.isEmpty else { return }
        Sound.shared.tap()
        lock = true
        submit(input)
    }

    func handleOX(_ choice: Bool) {
        guard !lock, done == nil, let st = statement else { return }
        Sound.shared.tap()
        lock = true
        resolve(choice == st.isTrue)
    }

    func expire() {
        finish()
    }

    // MARK: - 코어 로직

    private func submit(_ value: String) {
        guard let parsed = Int(value) else { lock = false; return }
        resolve(parsed == expected(problem))
    }

    private func resolve(_ correct: Bool) {
        let prob = problem
        let ms = Int((now() - qStart).rounded())
        answers.append(AnswerRecord(a: prob.a, b: prob.b, correct: correct, ms: ms))

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
            feedback = .wrong
            Sound.shared.wrong()
            Haptics.error()
            if def.kind == .lives { lives -= 1 }
        }

        let last = def.kind == .fixed && idx + 1 >= def.total
        let outOfLives = def.kind == .lives && lives <= 0
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
        qStart = now()
        lock = false
    }

    private func finish() {
        guard done == nil else { return }
        let result = SessionResult(
            mode: mode, table: table, answers: answers,
            maxCombo: maxCombo, durationMs: Int((now() - sessionStart).rounded())
        )
        let commit = game.commitSession(result)
        Sound.shared.complete()
        if commit.leveledUp {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { Sound.shared.levelUp() }
        }
        done = SessionDone(result: result, commit: commit, wrongCount: wrongList.count)
    }

    // MARK: - 재시작

    func restart(retryWrong: Bool) {
        let retained = retryWrong ? wrongList : []
        queue = retained.isEmpty ? [] : Array(retained.dropFirst())
        answers = []
        wrongList = []
        recent = []
        maxCombo = 0
        combo = 0
        score = 0
        lives = def.lives ?? 0
        idx = 0
        lock = false
        sessionId += 1
        wrongPool = game.state.wrongPool
        sessionStart = now()
        qStart = now()
        let first = retained.first ?? Problems.pick(table: table, wrongPool: wrongPool, recentKeys: [])
        problem = first
        statement = mode == .truefalse ? Problems.makeStatement(first) : nil
        input = ""
        feedback = nil
        done = nil
        start()
    }

    // MARK: - 뷰 편의

    var modeName: String { def.name }
    var modeKind: ModeKind { def.kind }
    var total: Int { def.total }
    var maxLives: Int { def.lives ?? 3 }
    var timeLimitMs: Int? { def.timeLimitMs }
    var sessionStartClock: Double { sessionStart }
    var answerValue: Int { problem.a * problem.b }
}
