import Foundation
import QuartzCore
import Observation

// 배틀 상태 머신 (BattleScreen.tsx 이식) — hp/speed/counter 3종 + 보스 분노.
// 종료 시 기존 commitSession 파이프라인으로 XP/별점/업적 반영.

enum BattlePhase { case intro, play, end }

struct Floater: Equatable {
    let id: Int
    let text: String
}

struct BattleEnd {
    let won: Bool
    let commit: CommitResult
    let advUnlocked: [String]
}

@Observable
final class BattleEngine {
    let npc: NpcDef
    private let game: GameStore
    private let adventure: AdventureStore
    /// 전투 결과를 밖으로 넘긴다 (학습 원장 적재).
    /// 엔진이 SyncStore/AuthStore 를 직접 알면 의존이 역류하므로 뷰 경계에서 잇는다.
    private let onCommit: (SessionResult) -> Void

    private let isBoss: Bool
    private let style: BattleStyle
    let counterLimit: Int
    /// 이미 격파한 상대와의 재대결 — 진입 시점에 확정한다 (이번 전투 기록 반영 전)
    private let rematch: Bool

    // 표시 상태
    var phase: BattlePhase = .intro
    var problem: Problem
    var input: String = ""
    var combo: Int = 0
    var qIdx: Int = 0
    var npcHp: Int
    var playerHp: Int = Battle.playerMaxHp
    var npcScore: Int = 0
    var playerScore: Int = 0
    var enraged: Bool = false
    var timedOut: Bool = false
    var feedback: Feedback?
    var npcFloat: Floater?
    var playerFloat: Floater?
    var npcHit: Int = 0
    var playerHit: Int = 0
    var end: BattleEnd?
    var qStartClock: Double = 0   // 반격전 타이머 표시용

    // 내부
    private var lock = false
    private var done = false
    private var decided = false
    private var answers: [AnswerRecord] = []
    private var recent: [String] = []
    private var maxCombo = 0
    private var floatId = 0
    private var wrongPool: [String: Int]
    private var qStart: Double = 0
    private var battleStart: Double = 0
    private var raceTimer: Timer?
    private var counterWork: DispatchWorkItem?

    private func now() -> Double { CACurrentMediaTime() * 1000 }

    // 뷰 편의
    var isBossBattle: Bool { isBoss }
    var battleStyle: BattleStyle { style }
    var maxComboReached: Int { maxCombo }
    var npcMaxHp: Int { npc.hp }

    init(npc: NpcDef, game: GameStore, adventure: AdventureStore,
         onCommit: @escaping (SessionResult) -> Void = { _ in }) {
        self.npc = npc
        self.game = game
        self.adventure = adventure
        self.onCommit = onCommit
        self.isBoss = npc.kind == .boss
        self.style = npc.battle
        self.counterLimit = Battle.counterLimitMs(npc.table)
        self.rematch = AdvProgress.isNpcDefeated(adventure.progress, npc.id)
        self.npcHp = npc.hp
        self.wrongPool = game.state.wrongPool
        self.problem = Problems.pick(table: npc.table, wrongPool: game.state.wrongPool, recentKeys: [])
    }

    func start() {
        // 인트로 1.5초 후 전투 시작
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self, !self.done else { return }
            self.battleStart = self.now()
            self.qStart = self.now()
            self.qStartClock = self.qStart
            self.phase = .play
            self.startRacePaceIfNeeded()
            self.scheduleCounterIfNeeded()
        }
    }

    func teardown() {
        done = true
        raceTimer?.invalidate()
        counterWork?.cancel()
    }

    // MARK: - 입력

    func handleInput(_ n: Int) {
        guard phase == .play, !lock, !done else { return }
        if input.count >= 3 { return }
        Sound.shared.tap()
        input += String(n)
        let ans = problem.a * problem.b
        if input.count >= String(ans).count {
            lock = true
            let captured = input
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) { [weak self] in self?.submit(captured) }
        }
    }
    func handleDelete() {
        guard phase == .play, !lock, !done else { return }
        Sound.shared.tap()
        if !input.isEmpty { input.removeLast() }
    }
    func handleManualSubmit() {
        guard phase == .play, !lock, !done, !input.isEmpty else { return }
        Sound.shared.tap()
        lock = true
        submit(input)
    }
    func timeout() {
        guard phase == .play, !lock, !done else { return }
        lock = true
        timedOut = true
        resolve(false, ms: counterLimit, given: .noAnswer)
    }

    // MARK: - 코어

    private func submit(_ value: String) {
        guard let parsed = Int(value) else { lock = false; return }
        let ms = Int((now() - qStart).rounded())
        resolve(parsed == problem.a * problem.b, ms: ms, given: .number(parsed))
    }

    /// given 은 아이가 실제로 제출한 답 — 시간 초과처럼 제출이 없으면 .noAnswer
    private func resolve(_ correct: Bool, ms: Int, given: GivenAnswer) {
        answers.append(AnswerRecord(a: problem.a, b: problem.b, correct: correct, ms: ms, given: given))
        floatId += 1

        if correct {
            combo += 1
            if combo > maxCombo { maxCombo = combo }
            feedback = .correct
            Sound.shared.correct()
            if combo >= 3 { Sound.shared.combo(combo) }
            Haptics.success()

            if style == .speed {
                playerScore += 1
                npcHit += 1
            } else {
                let dmg = Battle.damage(ms: ms, combo: combo)
                npcHp = max(0, npcHp - dmg)
                npcFloat = Floater(id: floatId, text: "-\(dmg)")
                npcHit += 1
                // 보스 분노
                if isBoss && !enraged && npcHp > 0 && Double(npcHp) <= Double(npc.hp) * Battle.bossEnrageRatio {
                    enraged = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { [weak self] in
                        guard let self, !self.done else { return }
                        Sound.shared.wrong()
                        Haptics.error()
                    }
                }
            }
        } else {
            combo = 0
            feedback = .wrong
            Sound.shared.wrong()
            Haptics.error()
            if style != .speed {
                let atk = (isBoss && enraged) ? Battle.enragedAttack(npc.attack) : npc.attack
                playerHp = max(0, playerHp - atk)
                playerFloat = Floater(id: floatId, text: "-\(atk)")
                playerHit += 1
            }
        }

        let playerWon = style == .speed ? playerScore >= Battle.raceTarget : npcHp <= 0
        let playerLost = style == .speed ? false : playerHp <= 0
        if playerWon || playerLost { decided = true }

        let delay = correct ? 0.55 : 1.25
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self, !self.done else { return }
            if playerWon { self.finish(won: true) }
            else if playerLost { self.finish(won: false) }
            else { self.goNext() }
        }
    }

    private func goNext() {
        recent = Array((recent + [Problems.key(problem.a, problem.b)]).suffix(4))
        problem = Problems.pick(table: npc.table, wrongPool: wrongPool, recentKeys: recent)
        input = ""
        feedback = nil
        timedOut = false
        qIdx += 1
        qStart = now()
        qStartClock = qStart
        lock = false
        scheduleCounterIfNeeded()
    }

    private func finish(won: Bool) {
        guard !done else { return }
        done = true
        raceTimer?.invalidate()
        counterWork?.cancel()
        let result = Battle.toSessionResult(npc, answers: answers, maxCombo: maxCombo,
                                            durationMs: Int((now() - battleStart).rounded()),
                                            rematch: rematch)
        let commit = game.commitSession(result)
        onCommit(result)
        let advUnlocked = adventure.recordBattle(npcId: npc.id, won: won)
        end = BattleEnd(won: won, commit: commit, advUnlocked: advUnlocked)
        phase = .end
        if won {
            if isBoss { Sound.shared.levelUp() } else { Sound.shared.complete() }
            Haptics.success()
        }
    }

    // MARK: - 방식별 타이머

    private func startRacePaceIfNeeded() {
        guard style == .speed else { return }
        let interval = Double(Battle.racePaceMs(npc.table)) / 1000
        raceTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            guard let self, !self.done, !self.decided else { return }
            self.npcScore += 1
            if self.npcScore >= Battle.raceTarget {
                self.decided = true
                self.finish(won: false)
            }
        }
    }

    private func scheduleCounterIfNeeded() {
        guard style == .counter else { return }
        counterWork?.cancel()
        let idx = qIdx
        let work = DispatchWorkItem { [weak self] in
            guard let self, !self.done, self.phase == .play, self.qIdx == idx, !self.lock else { return }
            self.timeout()
        }
        counterWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Double(counterLimit) / 1000, execute: work)
    }
}
