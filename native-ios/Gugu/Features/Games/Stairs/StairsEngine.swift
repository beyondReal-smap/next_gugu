import Foundation
import QuartzCore
import Observation

// 구구 계단 진행 (Unity 시험판 StairsGame.cs 이식) — 규칙(StairsSession)·탭 입력·그림 상태·보상 저장을 잇는다.
// 시간 제한 대신 아래에서 먹구름이 차오르고, 잡히면 판이 끝난다(위 배지 줄의 구름 막대 = 구름까지 거리).
// 한 프레임 순서: ① 입력 → 점프·채점  ② 시간 진행  ③ 그림 상태 (잡히기 직전 프레임에 누른 정답은 한 칸을 벌어 살아남음)
// 쉬는 창이 열린 동안에는 프레임 루프를 멈춰 입력·먹구름·그림이 모두 멈춘다. 별·최고 기록은 결과(또는 체험 끝)를 띄울 때 한 번 저장.
// 무료 체험(trial)은 맞힌 문제 수 — 그만큼 오르면 판을 끝내고 체험 종료 패널로(받은 별·기록은 저장).
// 그 전에 먹구름에 잡히면 체험을 다 쓴 게 아니라서 일반 결과 창([다시 하기])을 띄운다(2026-11-24 대표님 결정).
// 학습 기록(XP·오늘 목표·틀린 문제·서버 원장): 결과(또는 체험 끝)를 띄울 때 완료로, 판 중간에 나가면(abandon) 부분으로 한 번.
// 단이 판 중에 바뀌므로(보물 → 다음 단, 무작위는 문제마다) 기록의 단은 nil.
// 문제 순서(순서대로·무작위)는 시작 전 패널에서 바꾸며, 바꾸면 뒤에 비치는 첫 문제도 새 순서로 다시 만든다. [다시 하기]는 같은 순서.

@Observable
final class StairsEngine {
    enum Phase: Equatable {
        case ready     // 시작 전 패널
        case playing   // 판 진행(끝난 뒤 결과 창까지 포함)
    }

    /// 판이 끝나고 결과(또는 체험 종료 패널)를 띄우기까지(초) — "잡혔어요!" 를 보여 주는 시간
    static let resultDelaySec = 1.0
    /// 캐릭터 발에서 불꽃 배지 중심까지(유닛) — 머리 옆 높이, 몸·장신구에 닿지 않게.
    /// 배지 위 끝이 다음 줄 발판(답 숫자) 아래에 오게: 머리 바로 위는 다음 줄 발판이라 그 위에 두면 답을 가린다
    static let comboOffset = (x: 1.0, y: 1.05)
    /// 불꽃 배지 크기 배율(공용 배지 대비) — 구구(1.3유닛)보다 작게. 몬스터는 원래 크기
    static let comboScale: CGFloat = 0.6
    /// 프레임 하나에 흘릴 최대 시간(초) — 복귀 직후·긴 프레임에 먹구름이 한 번에 튀어 오르지 않게
    static let maxFrameSec = 0.1

    /// 위쪽 배지 줄(설계 px, 가운데 x·폭): [⏸](-471) · 계단 · 먹구름 거리 · 보물 · 별
    enum Hud {
        static let stairs = (x: CGFloat(-292), width: CGFloat(220))
        static let cloud = (x: CGFloat(-56), width: CGFloat(224))
        static let chest = (x: CGFloat(182), width: CGFloat(220))
        static let coin = (x: CGFloat(416), width: CGFloat(220))
    }

    /// 단마다 발판 색을 바꿔 "새 단에 올라왔다"를 눈으로 알게 한다(2단부터 8색 반복)
    static let tableColors: [GameRGB] = [
        GameRGB(0.27, 0.47, 0.95), GameRGB(0.20, 0.66, 0.62), GameRGB(0.56, 0.40, 0.90), GameRGB(0.90, 0.45, 0.30),
        GameRGB(0.30, 0.62, 0.30), GameRGB(0.85, 0.35, 0.60), GameRGB(0.25, 0.55, 0.80), GameRGB(0.75, 0.55, 0.20),
    ]

    static func color(table: Int) -> GameRGB { tableColors[(table - Problems.minTable) % tableColors.count] }

    // MARK: - 상태

    private(set) var phase: Phase = .ready
    /// 문제 순서 — 시작 전 패널에서만 바뀐다
    private(set) var order: ProblemOrder
    /// 쉬는 창이 열려 있는지
    private(set) var paused = false
    private(set) var session: StairsSession
    private(set) var round = 0
    /// 이번 판의 체험 문제 수(nil = 제한 없음)
    private(set) var trial: Int?
    /// 체험 문제 수만큼 올라 판이 끝났는지
    private(set) var trialFinished = false
    private(set) var stage = StairsStage()
    private(set) var sparkles = SparklesState()
    private(set) var reward = RewardHudState()
    private(set) var stairsBadge = HudBadgeState()
    private(set) var cloudBadge = HudBadgeState()
    private(set) var chestBadge = HudBadgeState()
    private(set) var altimeter = AltimeterState()
    private(set) var banner = BannerState()
    /// 이번 판을 시작할 때의 최고 기록(깃발 높이) — 판 중에는 바뀌지 않음
    private(set) var bestAtStart = 0
    /// 흐른 시간(초) — 반짝임·일렁임용, 쉬는 동안 멈춤
    private(set) var time: Double = 0
    /// 정답 순간 문제 글자가 통통(1 → 0)
    private(set) var problemPulse: Double = 0
    private(set) var lastWrong: AttemptRecord?
    /// 판이 끝난 뒤 흐른 시간
    private(set) var endedFor: Double = 0
    /// 결과 창 내용 — 결과(또는 체험 종료 패널)를 띄우는 순간 한 번 만들고 그때 별·기록을 저장한다
    private(set) var result: GameResultContent?
    private(set) var resultShownFor: Double = 0
    private(set) var viewport: GameViewport?
    /// 이번 판의 학습 기록 — 결과 창의 "+N XP"·레벨 업·업적 알림이 읽는다
    private(set) var learning = RoundLearningLog(mode: .stairs, table: nil)

    @ObservationIgnored private let rewards: PlayRewardsStore
    @ObservationIgnored private let commitSession: GameCommit
    @ObservationIgnored private let topStep: Int
    /// 단 시트에서 고른 시작 단(nil = 2~9 무작위) — 무작위 순서에서는 쓰지 않는다
    @ObservationIgnored private let firstTable: Int?
    @ObservationIgnored private let random: () -> Double
    @ObservationIgnored private let runsLoop: Bool
    @ObservationIgnored private var pendingTaps: [CGPoint] = []
    @ObservationIgnored private var zone: SkyZone = .meadow
    @ObservationIgnored private var recordShown = false
    @ObservationIgnored private var caughtShown = false
    @ObservationIgnored private var shownQuestionId = -1
    @ObservationIgnored private var frameTimer: Timer?
    @ObservationIgnored private var lastTick: CFTimeInterval?
    #if DEBUG
    /// SIMCTL_CHILD_GUGU_STAIRS_AUTOPLAY=N — N칸을 정답 칸 탭으로 자동으로 오른다(스크린숏용, 시작도 자동)
    @ObservationIgnored private var autoplayLeft = StairsEngine.debugAutoplayCount()
    @ObservationIgnored private var autoplayWait = 0.6
    #endif

    /// start: 홈(.standard)·섞어서(.table(nil)) = 2~9단 무작위, .table(n) = n단부터(순서대로일 때만). topStep = 설정 「곱하는 수」.
    /// order: 처음 보여 줄 문제 순서(화면은 GameOrderStore 의 마지막 선택).
    /// commit: 학습 기록(화면은 GameStore.commitSession + 서버 적재). runsLoop: 테스트는 false 로 두고 advance(_:) 를 직접 부른다
    init(start: MinigameStart, order: ProblemOrder, topStep: Int = Problems.maxB, rewards: PlayRewardsStore,
         commit: @escaping GameCommit, random: @escaping () -> Double = { Double.random(in: 0..<1) }, runsLoop: Bool = true) {
        self.rewards = rewards
        self.commitSession = commit
        self.order = order
        self.topStep = topStep
        self.firstTable = start.table(defaultTable: nil)
        self.random = random
        self.runsLoop = runsLoop
        session = Self.makeSession(order: order, topStep: topStep, firstTable: firstTable, random: random)
        resetRound()
    }

    /// 무작위 순서는 고른 시작 단을 쓰지 않는다(단·곱하는 수 둘 다 무작위)
    private static func makeSession(order: ProblemOrder, topStep: Int, firstTable: Int?,
                                    random: @escaping () -> Double) -> StairsSession {
        StairsSession(topStep: topStep, order: order, firstTable: order == .sequential ? firstTable : nil, random: random)
    }

    // MARK: - 화면에서 읽는 값

    /// 판이 끝났는지(먹구름에 잡힘·체험 끝)
    var roundOver: Bool { session.caught || trialFinished }
    var pauseButtonVisible: Bool { phase == .playing && !roundOver }
    /// "잡혔어요!" — 잡힌 뒤 결과가 뜨기 전까지
    var caughtTextVisible: Bool { session.caught && result == nil }
    /// [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초)
    var restartArmed: Bool { result != nil && resultShownFor >= GameResultPanel.armDelaySec }
    /// 첫 점프 전 안내("답이 적힌 발판을 눌러 점프!") — 시작 후 첫 탭 전에는 먹구름도 멈춰 있음
    var hintVisible: Bool { phase == .playing && session.clock.phase == .waiting }
    /// 남은 체험 문제 수(지금 문제 포함, 체험이 아니면 nil)
    var trialLeft: Int? { trial.map { max(0, $0 - session.climbed) } }
    var accessory: Accessory? { stage.accessory }

    /// 문제 판 윗줄 — 순서대로 "7단 · 3번째 계단", 무작위는 단이 문제마다 바뀌어 "3번째 계단"(보물까지 몇 번째 칸인지)만
    var tableLabel: String {
        switch order {
        case .sequential:
            let p = session.current.problem
            return String(localized: "\(p.a)단 · \(p.b)번째 계단")
        case .random:
            return String(localized: "\(session.step)번째 계단")
        }
    }

    /// 문제 판 글자 — 오답 공개 중이면 정답 식
    var problemText: String {
        if session.clock.isRevealing, let wrong = lastWrong {
            return "\(wrong.problem.a) × \(wrong.problem.b) = \(wrong.problem.a * wrong.problem.b)"
        }
        let p = session.current.problem
        return "\(p.a) × \(p.b)"
    }

    var problemRevealing: Bool { session.clock.isRevealing && lastWrong != nil }

    /// 학습 기록(commitSession) 연결용 — 채점 기록(문제 = 단 × 칸, given = 고른 발판 숫자)
    var answerRecords: [AnswerRecord] { session.answerRecords }
    /// 판 길이(ms) — 첫 점프부터 잡힐(또는 체험이 끝날) 때까지
    var roundDurationMs: Int { Int((session.clock.elapsed * 1000).rounded()) }

    // MARK: - 조작

    /// 화면 크기·안전 영역 — 레인 경계·코인 출발점·배지 위치 계산에 쓴다
    func setViewport(_ viewport: GameViewport) {
        guard self.viewport != viewport else { return }
        self.viewport = viewport
        #if DEBUG
        if autoplayLeft > 0 && phase == .ready { start(trial: nil) }
        #endif
    }

    /// 동작 줄이기(손쉬운 사용) — 켜면 구구 숨쉬기·점프 늘어남·기울기를 끈다. 화면이 뜰 때와 설정이 바뀔 때 알려 준다
    func setReduceMotion(_ on: Bool) { stage.reduceMotion = on }

    /// 시작 전 패널의 [시작]. trial: 무료 체험 문제 수(이용권이 있으면 nil)
    func start(trial: Int?) {
        precondition(phase == .ready, "이미 시작한 판: \(phase)")
        precondition(viewport != nil, "화면 크기를 먼저 알려야 함(setViewport)")
        self.trial = trial
        phase = .playing
        Sound.shared.tap()
        startLoop()
    }

    /// 시작 전 패널의 [순서대로]·[무작위] — 뒤에 비치는 첫 문제도 새 순서로 다시 만든다(새 세션). 같은 순서면 그대로
    func setOrder(_ next: ProblemOrder) {
        precondition(phase == .ready, "문제 순서는 시작 전에만 바꿈: \(phase)")
        guard next != order else { return }
        order = next
        session = Self.makeSession(order: next, topStep: topStep, firstTable: firstTable, random: random)
        resetRound()
    }

    /// 결과 창 [다시 하기] — 같은 순서·같은 시작 단으로 새 판. 쌓인 탭은 버린다(다시 하기를 누른 탭이 새 판 첫 입력으로 새지 않게)
    func restart(trial: Int?) {
        // 결과 창이 없으면(이미 새 판을 연 뒤 연타가 한 번 더 온 것) 무시
        guard restartArmed else { return }
        session = Self.makeSession(order: order, topStep: topStep, firstTable: firstTable, random: random)
        resetRound()
        self.trial = trial
        Sound.shared.tap()
        startLoop()
    }

    /// 손가락이 닿은 곳(화면 pt). 다음 프레임에 처리한다
    func enqueueTap(_ point: CGPoint) {
        guard phase == .playing, !paused, result == nil else { return }
        pendingTaps.append(point)
    }

    /// [⏸] → 쉬는 창. 판이 끝난 뒤에는 열지 않음(결과로 넘어가는 1초를 멈추지 않게)
    func openPause() {
        guard pauseButtonVisible, !paused else { return }
        paused = true
        pendingTaps.removeAll()
        stopLoop()
    }

    /// [계속 하기] — 쌓인 탭은 버린다([계속 하기]를 누른 탭이 같은 프레임에 점프로 새지 않게)
    func resume() {
        guard paused else { return }
        paused = false
        pendingTaps.removeAll()
        startLoop()
    }

    /// 앱이 백그라운드로 가면: 진행 중이면 쉬는 창, 그 밖에는 루프만 멈춤
    func sceneBecameInactive() {
        if pauseButtonVisible && !paused {
            openPause()
        } else {
            stopLoop()
        }
    }

    /// 앱으로 돌아오면: 쉬는 창이 아니면(끝난 뒤 결과 대기·결과 창) 루프를 다시 돌린다
    func sceneBecameActive() {
        guard phase == .playing, !paused else { return }
        startLoop()
    }

    /// 판 중간에 나감([메뉴로 나가기] 등) — 아직 기록하지 않은 판이면 푼 답만 기록한다.
    /// 판이 끝났으면(잡힘·체험 끝 뒤 결과를 띄우기 전) 완료로, 아니면 부분으로. 결과를 띄웠으면 이미 기록함
    func abandon() {
        guard phase == .playing else { return }
        closeLearning(partial: !roundOver)
    }

    func teardown() { stopLoop() }

    // MARK: - 프레임

    /// 한 프레임. 루프가 부르고, 테스트는 직접 부른다. 쉬는 동안·시작 전에는 아무것도 하지 않는다
    func advance(_ rawDt: Double) {
        guard phase == .playing, !paused, let viewport else { return }
        let dt = min(rawDt, Self.maxFrameSec)
        time += dt
        let camera = StairsCamera(size: viewport.size, centerY: stage.cameraY)

        #if DEBUG
        autoplay(dt, camera: camera, viewport: viewport)
        #endif

        // ① 입력 — [⏸] 위 탭은 빼기(화면 x 로 칸을 고르므로 왼쪽 칸 점프로 샘)
        let taps = pendingTaps
        pendingTaps.removeAll()
        if !roundOver {
            let pauseFrame = PauseLayout.buttonFrame(viewport)
            for point in taps where !pauseFrame.contains(point) && !roundOver {
                tap(lane: camera.lane(atScreenX: point.x), camera: camera, viewport: viewport)
            }
        }

        // ② 시간
        if !roundOver {
            session.tick(dt)
            syncRow()   // 오답 공개가 끝나 같은 칸을 다시 냈으면 발판을 다시 깐다
        }
        if roundOver {
            if result == nil {
                endedFor += dt
                if endedFor >= Self.resultDelaySec { showResult() }
            } else {
                resultShownFor += dt
            }
        }

        // ③ 그림 상태
        render(dt, viewport: viewport)
    }

    // MARK: - 점프

    private func tap(lane: Int, camera: StairsCamera, viewport: GameViewport) {
        let table = session.table
        let starsBefore = session.stars
        let q = session.current
        let jump = session.jump(lane: lane)
        guard let attempt = jump.attempt else {
            if jump.reject == .revealing { Sound.shared.tap() }   // 정답 공개 중 — "아직이야" 짧은 소리
            return
        }

        if attempt.correct {
            stage.land(lane: lane)
            Sound.shared.correct()
            Haptics.impact(.light)
            problemPulse = 1
            // 밟은 발판 윗면에서 별이 터지고, 받은 별 코인이 위쪽 별 배지로 날아감
            let top = (x: StairsStage.laneX(lane), y: StairsStage.platformY(stage.standingHeight) + StairsStage.platformHeight * 0.5)
            let big = StarRules.isComboMilestone(session.streak) || jump.clearedTable
            sparkles.burst(x: top.x, y: top.y, big: big)
            reward.earn(from: camera.point(top.x, top.y), to: coinBadgeIcon(viewport),
                        earned: session.stars - starsBefore, total: session.stars, unit: viewport.unit)
            if jump.clearedTable {
                stage.placeTreasure()
                switch order {
                case .sequential: banner.show(String(localized: "\(table)단 완료! 보물 +1"))
                case .random: banner.show(String(localized: "\(topStep)칸 돌파! 보물 +1"))   // 단이 문제마다 바뀌어 칸 기준
                }
            }
            let reached = SkyZone.at(height: session.climbed)
            if reached != zone {
                zone = reached
                banner.show(String(localized: "\(Self.zoneName(reached))에 왔어요!"))
            }
            // 지난 최고 기록을 처음 넘는 순간: 깃발이 튀고 큰 반짝이 + 알림(판마다 한 번)
            if !recordShown && bestAtStart > 0 && session.climbed > bestAtStart {
                recordShown = true
                stage.celebrateFlag()
                let flag = flagWorld(camera)
                sparkles.burst(x: flag.x, y: flag.y, big: true)
                banner.show(String(localized: "신기록!"))
            }
            if let trial, session.climbed >= trial { trialFinished = true }
        } else {
            lastWrong = attempt
            stage.miss(lane: lane, correctLane: q.correctLane)
            Sound.shared.wrong()
            Haptics.error()
        }
        reward.setStreak(session.streak)
        syncRow()
    }

    private func syncRow() {
        let q = session.current
        guard q.id != shownQuestionId else { return }
        stage.showNextRow(q, color: Self.color(table: q.problem.a))
        shownQuestionId = q.id
    }

    // MARK: - 판 시작·끝

    private func resetRound() {
        round += 1
        pendingTaps.removeAll()
        bestAtStart = rewards.best(.stairs)
        stage.reset(startLabel: String(localized: "출발"), accessory: rewards.equipped, best: bestAtStart)
        sparkles.reset()
        reward.reset()
        stairsBadge.reset(0)
        chestBadge.reset(0)
        cloudBadge.reset(nil)
        cloudBadge.setBar(fill: session.cloud.gap(standing: 0) / RisingCloud.maxGap, danger: false)
        altimeter.reset(best: bestAtStart)
        banner.clear()
        zone = .meadow
        recordShown = false
        caughtShown = false
        lastWrong = nil
        shownQuestionId = -1
        problemPulse = 0
        endedFor = 0
        result = nil
        resultShownFor = 0
        trialFinished = false
        learning.reset()
        syncRow()
    }

    /// 결과를 띄우는 순간 한 번: 받은 별을 쌓고 최고 기록을 갱신(체험 끝도 같음)
    private func showResult() {
        let record = rewards.recordRound(.stairs, score: session.climbed, stars: session.stars, bestStreak: session.bestStreak)
        let commit = closeLearning(partial: false)
        let miss = session.missToShow
        result = GameResultContent(
            note: GameResultContent.note(record),
            mainLabel: String(localized: "오른 계단"),
            mainValue: session.climbed,
            subLabel: String(localized: "보물"),
            subValue: session.treasures,
            miss: miss?.problem,
            missGiven: miss.map { String(localized: "고른 답 \($0.given)") },
            stars: session.stars,
            starTotal: record.starTotal,
            xpEarned: commit?.xpEarned,
            missionNote: GameResultContent.missionNote(record)
        )
        resultShownFor = 0
        Sound.shared.complete()
    }

    /// 학습 기록을 한 번 넘긴다(판마다 한 번 — RoundLearningLog 가 막음). 답이 없으면 nil
    @discardableResult
    private func closeLearning(partial: Bool) -> CommitResult? {
        learning.close(answers: session.answerRecords, maxCombo: session.bestStreak,
                       durationMs: roundDurationMs, partial: partial, using: commitSession)
    }

    // MARK: - 그림 상태

    private func render(_ dt: Double, viewport: GameViewport) {
        if session.caught && !caughtShown {
            caughtShown = true
            stage.caught()
            Sound.shared.wrong()
            Haptics.error()
        }
        if session.caught { banner.clear() }   // 잡힌 뒤에는 알림을 지움("잡혔어요!" 만)

        stage.update(dt, caught: session.caught)
        sparkles.update(dt)
        banner.update(dt)
        problemPulse = max(0, problemPulse - dt * 5)

        // 배지 줄: 오른 계단 · 먹구름까지 거리(가득 = maxGap 칸, 1칸 미만이면 빨강) · 보물 · 별(코인이 닿을 때마다)
        let gap = session.cloud.gap(standing: session.climbed)
        stairsBadge.setValue(session.climbed)
        cloudBadge.setBar(fill: gap / RisingCloud.maxGap, danger: gap < 1)
        chestBadge.setValue(session.treasures)
        stairsBadge.update(dt)
        cloudBadge.update(dt)
        chestBadge.update(dt)
        for index in reward.update(dt, badgeIcon: coinBadgeIcon(viewport)) {
            Sound.shared.coin(step: index)
        }
        // 불꽃은 캐릭터 옆을 따라다님(오른쪽 발판에서는 화면 밖으로 나가지 않게 왼쪽에)
        let camera = StairsCamera(size: viewport.size, centerY: stage.cameraY)
        let side = stage.standingLane == StairsSession.lanes - 1 ? -1.0 : 1.0
        reward.followCombo(camera.point(stage.characterX + Self.comboOffset.x * side, stage.characterY + Self.comboOffset.y))
        altimeter.update(dt, climbed: session.climbed, best: bestAtStart)
    }

    /// 별 배지 아이콘 가운데(화면 pt) — 코인이 날아가 닿는 곳
    private func coinBadgeIcon(_ viewport: GameViewport) -> CGPoint {
        viewport.safeTop(x: Hud.coin.x - Hud.coin.width / 2 + HudBadgeState.height / 2, y: PauseLayout.hudRowY)
    }

    /// 최고 기록 깃발 가운데(월드)
    private func flagWorld(_ camera: StairsCamera) -> (x: Double, y: Double) {
        let height = 1.1
        let width = height * Double(GameArt.flagPixels.width / GameArt.flagPixels.height)
        return (camera.halfWidth - 0.3 - width / 2, StairsStage.feetY(Double(bestAtStart)) + height / 2)
    }

    /// 구간 이름(알림·고도계)
    static func zoneName(_ zone: SkyZone) -> String {
        switch zone {
        case .meadow: return String(localized: "풀밭")
        case .skyVillage: return String(localized: "하늘 마을")
        case .rainbow: return String(localized: "무지개 언덕")
        case .space: return String(localized: "달나라")
        }
    }

    // MARK: - 프레임 루프

    private func startLoop() {
        guard runsLoop else { return }
        stopLoop()
        lastTick = nil
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.tickFromTimer()
        }
        RunLoop.main.add(timer, forMode: .common)
        frameTimer = timer
    }

    private func stopLoop() {
        frameTimer?.invalidate()
        frameTimer = nil
        lastTick = nil
    }

    private func tickFromTimer() {
        let now = CACurrentMediaTime()
        defer { lastTick = now }
        guard let last = lastTick else { return }
        advance(now - last)
    }

    #if DEBUG
    /// GUGU_STAIRS_AUTOPLAY 값(없으면 0). 잘못된 값은 조용히 넘기지 않고 멈춘다
    private static func debugAutoplayCount() -> Int {
        guard let raw = ProcessInfo.processInfo.environment["GUGU_STAIRS_AUTOPLAY"] else { return 0 }
        guard let n = Int(raw), n >= 0 else { preconditionFailure("GUGU_STAIRS_AUTOPLAY 는 0 이상 정수: \(raw)") }
        return n
    }

    /// 자동 정답 — 정답 칸 가운데를 실제 탭 경로(큐)로 누른다
    private func autoplay(_ dt: Double, camera: StairsCamera, viewport: GameViewport) {
        guard autoplayLeft > 0, !roundOver, !session.clock.isRevealing else { return }
        autoplayWait -= dt
        guard autoplayWait <= 0 else { return }
        autoplayWait = 0.45
        autoplayLeft -= 1
        pendingTaps.append(CGPoint(x: camera.laneCenterX(session.current.correctLane), y: viewport.size.height * 0.6))
    }
    #endif
}
