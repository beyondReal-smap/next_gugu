import Foundation
import QuartzCore
import Observation
import UIKit

// 구구 팡팡 진행 상태 관리 — BasketEngine 과 같은 구조.
// 게임 규칙은 Core/Pop.swift 순수 함수가 담당하고, 여기서는 프레임 루프·기록 저장·소리/햅틱·화면 효과만 맡는다.

/// 화면 효과 한 개 — Core 좌표(x 0~1, y 0 등장선 ~ 1 지붕선)와 연출 시계 기준 태어난 시각.
/// 보스에 붙는 효과(pair, big burst)의 y 는 보스 y — 화면은 PopLayout.bossPoint 로 바꾼다
struct PopEffect: Identifiable {
    enum Kind: Equatable {
        /// 물총 물방울 — 물총에서 (x, y)까지 날아간다
        case shot
        /// 풍선이 터짐 (golden = 황금 풍선, big = 보스 격파)
        case burst(golden: Bool, big: Bool)
        /// 얻은 점수 "+20"
        case points(Int)
        /// 오답 탭 표시 ✕
        case wrong
        /// 오답 탭한 풍선 흔들기 (그 풍선 id)
        case shake(balloonId: Int)
        /// 정답 풍선·보스가 지붕에 닿음 — 하트 -1
        case splash
        /// 짝꿍 완성 "4 × 9"
        case pair(a: Int, b: Int)
    }

    let id: Int
    let kind: Kind
    let x: Double
    let y: Double
    let born: Double

    /// 효과가 보이는 시간(ms)
    var life: Double {
        switch kind {
        case .shot: return 160
        case .burst(_, let big): return big ? 900 : 450
        case .points: return 700
        case .wrong: return 500
        case .shake: return 320
        case .splash: return 600
        case .pair: return 1100
        }
    }
}

@Observable
final class PopEngine {
    private(set) var game: PopState
    private(set) var bests: [String: Int]
    private(set) var effects: [PopEffect] = []
    /// 연출 시계(ms) — 흔들림·효과 나이 계산용. 일시정지 동안 멈춘다
    private(set) var clockMs: Double = 0
    /// 물총이 마지막으로 겨눈 곳 (Core 좌표) — 처음에는 위쪽 가운데
    private(set) var aim: (x: Double, y: Double) = (0.5, 0)
    /// 선택한 단 (nil = 섞어서)
    var table: Int?

    private var frameTimer: Timer?
    private var lastTick: Double?
    private var nextEffectId = 1

    #if DEBUG
    /// DEBUG 전용 — SIMCTL_CHILD_GUGU_POP_AUTOPLAY=1 이면 정답 풍선을 자동으로 눌러 웨이브·보스·체험 종료를 시뮬레이터에서 확인한다
    private static let autoplay = ProcessInfo.processInfo.environment["GUGU_POP_AUTOPLAY"] == "1"
    private var autoplayMs: Double = 0
    #endif

    /// 표시용 — 단 선택 목록
    static let tables = Array(Problems.minTable...Problems.maxTable)
    /// 시작 단 기본값 — 단을 정하지 않고 열면(홈) 섞어서. 섞어서는 로드맵 순서라 첫 웨이브가 2단이다
    static let defaultTable: Int? = nil
    /// 한 번에 그리는 효과 수 상한 (입자 60개 이내 — 계획의 저사양 기준)
    private static let maxEffects = 24

    init(table: Int? = PopEngine.defaultTable) {
        precondition(table.map(PopEngine.tables.contains) ?? true, "단 범위 밖: \(table!)")
        self.table = table
        self.game = Pop.create(table: table, phase: .ready)
        self.bests = Pop.sanitizeBests(Persistence.load([String: Int].self, key: Persistence.popBestKey) ?? [:])
    }

    // MARK: - 기록

    /// 시작 화면은 고른 단, 진행 중에는 그 판의 단 기준 최고 기록
    var displayBest: Int {
        let playing = game.phase != .ready && game.phase != .over
        return bests[Pop.bestKey(playing ? game.table : table)] ?? 0
    }
    var runBest: Int { bests[Pop.bestKey(game.table)] ?? 0 }

    private func recordBestIfNeeded() {
        let key = Pop.bestKey(game.table)
        guard game.score > (bests[key] ?? 0) else { return }
        bests[key] = game.score
        Persistence.save(bests, key: Persistence.popBestKey)
    }

    // MARK: - 조작

    /// trial: 무료 체험 웨이브 수 (nil = 제한 없음)
    func start(trial: Int?) {
        effects = []
        aim = (0.5, 0)
        game = Pop.create(table: table, trial: trial)
        Sound.shared.tap()
        startLoop()
    }

    /// 놀이 영역을 누름 — 가장 가까운 풍선을 쏜다. 맞힐 풍선이 없으면 물만 쏜다
    func tap(x: Double, y: Double, aspect: Double) {
        guard game.phase == .running else { return }
        let before = game
        game = Pop.tap(game, x: x, y: y, aspect: aspect)
        if game.events.isEmpty {
            if before.introMs == 0 && before.reloadMs == 0 { shoot(x: x, y: y) }
            return
        }
        handle(game.events, before: before)
        // 마지막 정답 탭으로 체험이 끝나거나 웨이브가 바뀔 수 있다
        if game.phase == .over { finish() }
    }

    func togglePause() {
        switch game.phase {
        case .running:
            game.phase = .paused
            stopLoop()
        case .paused:
            game.phase = .running
            startLoop()
        default:
            break
        }
    }

    /// 백그라운드 전환·화면 이탈 시 자동 일시정지
    func pause() {
        guard game.phase == .running else { return }
        game.phase = .paused
        stopLoop()
    }

    func teardown() { stopLoop() }

    // MARK: - 프레임 루프

    private func startLoop() {
        stopLoop()
        lastTick = nil
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.tick()
        }
        RunLoop.main.add(timer, forMode: .common)
        frameTimer = timer
    }

    private func stopLoop() {
        frameTimer?.invalidate()
        frameTimer = nil
        lastTick = nil
    }

    private func tick() {
        guard game.phase == .running else {
            stopLoop()
            return
        }
        let now = CACurrentMediaTime() * 1000
        let dt = lastTick.map { min(now - $0, 80) } ?? 0
        lastTick = now
        guard dt > 0 else { return }

        clockMs += dt
        let before = game
        game = Pop.advance(game, dt: dt)
        handle(game.events, before: before)
        effects.removeAll { clockMs - $0.born > $0.life }
        if game.phase == .over {
            finish()
            return
        }
        #if DEBUG
        if Self.autoplay { autoplayStep(dt) }
        #endif
    }

    #if DEBUG
    private func autoplayStep(_ dt: Double) {
        autoplayMs += dt
        guard autoplayMs >= 450, game.introMs == 0, game.reloadMs == 0 else { return }
        autoplayMs = 0
        let target: PopBalloon?
        if let boss = game.boss {
            if let first = game.pending {
                target = game.balloons.first { $0.value * first == boss.value }
            } else {
                target = game.balloons.first { b in
                    b.correct && game.balloons.contains { $0.id != b.id && $0.value * b.value == boss.value }
                }
            }
        } else {
            target = game.balloons.filter { $0.correct && $0.y > 0.2 }.max { $0.y < $1.y }
        }
        guard let target else { return }
        let before = game
        game = Pop.tap(game, id: target.id)
        handle(game.events, before: before)
        if game.phase == .over { finish() }
    }
    #endif

    private func finish() {
        recordBestIfNeeded()
        stopLoop()
    }

    // MARK: - 이벤트 → 소리·햅틱·효과

    private func handle(_ events: [PopEvent], before: PopState) {
        func position(_ id: Int) -> (x: Double, y: Double)? {
            before.balloons.first { $0.id == id }.map { ($0.x, $0.y) }
        }
        let bossPos = before.boss.map { (x: Pop.bossX, y: $0.y) }

        for event in events {
            switch event {
            case .waveStarted:
                // 화면 낭독기 사용자에게 새 미션을 읽어 준다 (화면의 미션 막대와 같은 문구)
                if UIAccessibility.isVoiceOverRunning {
                    UIAccessibility.post(notification: .announcement, argument: game.mission.text)
                }
            case .popped(let id, _, let points, let golden):
                if let p = position(id) {
                    shoot(x: p.x, y: p.y, sound: false)
                    add(.burst(golden: golden, big: false), p)
                    add(.points(points), p)
                }
                Sound.shared.pop()
                Haptics.impact(.light)
                // 배율이 오르는 순간(콤보 5·10…)에만 콤보 소리 — 매번 울리면 시끄럽다
                if game.combo >= 5 && game.combo % 5 == 0 { Sound.shared.combo(game.combo) }
                recordBestIfNeeded()
            case .slowMoStarted:
                Sound.shared.collect()
            case .wrongTap(let id, _):
                if let p = position(id) {
                    shoot(x: p.x, y: p.y, sound: false)
                    add(.wrong, p)
                    add(.shake(balloonId: id), p)
                }
                Sound.shared.wrong()
                Haptics.error()
            case .reloading:
                Sound.shared.tap()
            case .missed(let id, _):
                if let p = position(id) { add(.splash, (p.x, 1)) }
                Sound.shared.wrong()
                Haptics.warning()
            case .passed:
                break
            case .bossPick(let id, _, let points):
                if let p = position(id) {
                    shoot(x: p.x, y: p.y, sound: false)
                    add(.burst(golden: false, big: false), p)
                    add(.points(points), p)
                }
                Sound.shared.pop()
                Haptics.impact(.light)
                recordBestIfNeeded()
            case .bossHit(let a, let b, let points, _):
                // 두 번째 풍선 자리는 이벤트에 없다 — before 에서 짝 값으로 찾는다
                if let second = before.balloons.first(where: { $0.value == b && $0.correct }) {
                    shoot(x: second.x, y: second.y, sound: false)
                    add(.burst(golden: false, big: false), (second.x, second.y))
                }
                if let p = bossPos { add(.pair(a: a, b: b), p) }
                if let second = before.balloons.first(where: { $0.value == b && $0.correct }) {
                    add(.points(points), (second.x, second.y))
                }
                Sound.shared.correct()
                Haptics.impact(.medium)
                recordBestIfNeeded()
            case .bossDefeated:
                if let p = bossPos { add(.burst(golden: true, big: true), p) }
                Sound.shared.levelUp()
                Haptics.success()
            case .bossEscaped:
                add(.splash, (Pop.bossX, 1))
                Sound.shared.wrong()
                Haptics.warning()
            case .waveCleared:
                // 보스 격파(levelUp)·체험 종료(complete)와 같은 순간이면 그쪽 소리 하나만 — 겹치면 뭉개진다
                if !events.contains(.bossDefeated) && !events.contains(.trialEnded) { Sound.shared.collect() }
            case .gameOver, .trialEnded:
                Sound.shared.complete()
            }
        }
    }

    private func shoot(x: Double, y: Double, sound: Bool = true) {
        aim = (x, y)
        add(.shot, (x, y))
        if sound { Sound.shared.tap() }
    }

    private func add(_ kind: PopEffect.Kind, _ p: (x: Double, y: Double)) {
        // 짝꿍 식은 한 번에 하나만 — 앞 식이 사라지기 전에 다음 식이 겹쳐 읽기 어려웠다(녹화 프레임 확인)
        if case .pair = kind { effects.removeAll { if case .pair = $0.kind { return true }; return false } }
        effects.append(PopEffect(id: nextEffectId, kind: kind, x: p.x, y: p.y, born: clockMs))
        nextEffectId += 1
        if effects.count > Self.maxEffects { effects.removeFirst(effects.count - Self.maxEffects) }
    }
}

extension PopMission {
    /// 미션 문구 — 조사가 붙지 않게 쓴다 ("7단 정답만 팡!", "곱해서 36 만들기")
    var text: String {
        switch self {
        case .multiples(let table): return String(localized: "\(table)단 정답만 팡!")
        case .pairBoss(let target): return String(localized: "곱해서 \(target) 만들기")
        }
    }
}
