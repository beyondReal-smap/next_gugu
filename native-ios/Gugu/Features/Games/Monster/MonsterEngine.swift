import Foundation
import QuartzCore
import Observation

// 구구 몬스터 진행 (Unity 시험판 MonsterGame.cs 이식) — 규칙(MonsterSession)·카드 탭·몬스터 그림 상태·도감·보상 저장을 잇는다.
// 몬스터 체력(곱)과 같아지는 카드 2장을 고르면 처치 — 처치마다 별 코인이 배지로 날아가고, 연속 처치는 불꽃 "×N".
// 어떤 몬스터가 나올지는 MonsterDex(8종, 누적 처치로 해금), 처음 물리친 종은 위 도감 띠 칸이 채워진다.
// 한 프레임 순서: ① 입력 → 카드 고르기·채점  ② 시간 진행(60초)  ③ 그림 상태
// 쉬는 창이 열린 동안에는 프레임 루프를 멈춰 입력·시계·그림이 모두 멈춘다.
// 저장 시점(시험판 그대로): 도감은 처치하는 순간, 별·최고 기록은 결과(또는 체험 끝)를 띄울 때 한 번.
// 무료 체험(trial)은 처치 수 — 그만큼 물리치면 판을 끝내고 체험 종료 패널로(받은 별·기록은 저장).
// 그 전에 60초가 다 되면 체험을 다 쓴 게 아니라서 일반 결과 창([다시 하기])을 띄운다(2026-11-24 대표님 결정).
// 학습 기록(XP·오늘 목표·틀린 문제·서버 원장): 결과(또는 체험 끝)를 띄울 때 완료로, 판 중간에 나가면(abandon) 부분으로 한 번.
// 기록의 단: 순서대로 + 단 지정 판만 그 단, 그 밖(단 없는 순서대로·무작위)은 nil.
// 문제 순서(순서대로·무작위)는 시작 전 패널에서 바꾸며, 바꾸면 뒤에 비치는 첫 몬스터도 새 순서로 다시 만든다. [다시 하기]는 같은 순서.
// 무작위는 단 시트에서 고른 단을 쓰지 않는다.

@Observable
final class MonsterEngine {
    enum Phase: Equatable {
        case ready     // 시작 전 패널
        case playing   // 판 진행(끝난 뒤 결과 창까지 포함)
    }

    /// 카드 색 — 평소 카드 색 / 공개 중 정답 두 장 초록 / 공개 중 고른 두 장 빨강
    enum CardLook: Equatable {
        case normal
        case correct
        case wrong
    }

    /// 판이 끝나고 결과(또는 체험 종료 패널)를 띄우기까지(초) — "타임업!" 을 보여 주는 시간
    static let resultDelaySec = 1.0
    /// 프레임 하나에 흘릴 최대 시간(초) — 복귀 직후·긴 프레임에 시계가 한 번에 튀지 않게
    static let maxFrameSec = 0.1
    /// 방금 푼 식을 문제 아래에 잠깐 남겨 "왜 이겼는지"를 보여 줌(초)
    static let solvedEchoSec = 0.8
    /// 남은 시간이 이만큼 이하면 시계 배지가 빨갛게 두근거림(초)
    static let dangerSec = 10.0
    /// 불꽃 배지 자리(월드) — 몬스터 왼쪽 위
    static let comboWorld = (x: -2.7, y: MonsterStage.monsterY + 0.9)

    /// 위쪽 배지 줄(설계 px, 가운데 x·폭): [⏸](-471) · 몬스터 · 남은 시간 · 별.
    /// 몬스터 아이콘(1.2배)이 왼쪽으로 20px 나오므로 [⏸]와 26px 띄움
    enum Hud {
        static let monster = (x: CGFloat(-250), width: CGFloat(280))
        static let time = (x: CGFloat(68), width: CGFloat(280))
        static let coin = (x: CGFloat(386), width: CGFloat(280))
        /// 배지 줄(-80) 아래 도감 띠 — 띠 위 끝 -144 가 배지 아래 끝(-135) 바로 밑
        static let dexY: CGFloat = -190
    }

    /// 젤리 카드 6장(3 × 2) — 안전 영역 아래 끝 기준(엄지 영역)
    enum Cards {
        static let size = CGSize(width: 300, height: 230)
        static let gapX: CGFloat = 330
        /// 윗줄(0~2)·아랫줄(3~5) 가운데 높이(안전 영역 아래 끝에서 위로)
        static let rowY: [CGFloat] = [600, 330]

        static func center(_ index: Int, _ viewport: GameViewport) -> CGPoint {
            precondition((0..<MonsterSession.cardCount).contains(index), "없는 카드: \(index)")
            return viewport.safeBottom(x: CGFloat(index % 3 - 1) * gapX, y: rowY[index / 3])
        }

        static func frame(_ index: Int, _ viewport: GameViewport) -> CGRect {
            viewport.rect(center: center(index, viewport), designSize: size)
        }

        /// 카드 칸 안을 눌렀을 때만 카드(nil = 카드 밖). 카드 사이 틈도 가까운 카드로 보지 않는다
        static func index(at point: CGPoint, _ viewport: GameViewport) -> Int? {
            (0..<MonsterSession.cardCount).first { frame($0, viewport).contains(point) }
        }
    }

    // MARK: - 상태

    private(set) var phase: Phase = .ready
    /// 문제 순서 — 시작 전 패널에서만 바뀐다
    private(set) var order: ProblemOrder
    /// 쉬는 창이 열려 있는지
    private(set) var paused = false
    private(set) var session: MonsterSession
    private(set) var round = 0
    /// 이번 판의 체험 처치 수(nil = 제한 없음)
    private(set) var trial: Int?
    /// 체험 처치 수만큼 물리쳐 판이 끝났는지
    private(set) var trialFinished = false
    private(set) var stage = MonsterStage()
    private(set) var sparkles = SparklesState()
    private(set) var reward = RewardHudState()
    private(set) var monsterBadge = HudBadgeState()
    private(set) var timeBadge = HudBadgeState()
    private(set) var dex = DexStripState()
    private(set) var banner = BannerState()
    /// 흐른 시간(초) — 통통·반딧불·안내 깜빡임용, 쉬는 동안 멈춤
    private(set) var time: Double = 0
    /// 정답 순간 문제 글자가 통통(1 → 0)
    private(set) var problemPulse: Double = 0
    private(set) var lastWrong: AttemptRecord?
    /// 방금 푼 식("3 × 4 = 12")과 남은 표시 시간
    private(set) var solvedText = ""
    private(set) var solvedLeft: Double = 0
    /// 판이 끝난 뒤 흐른 시간
    private(set) var endedFor: Double = 0
    /// 결과 창 내용 — 결과(또는 체험 종료 패널)를 띄우는 순간 한 번 만들고 그때 별·기록을 저장한다
    private(set) var result: GameResultContent?
    private(set) var resultShownFor: Double = 0
    private(set) var viewport: GameViewport?
    /// 이번 판의 학습 기록 — 결과 창의 "+N XP"·레벨 업·업적 알림이 읽는다
    private(set) var learning: RoundLearningLog

    @ObservationIgnored private let rewards: PlayRewardsStore
    @ObservationIgnored private let commitSession: GameCommit
    /// 단 시트에서 고른 단(nil = 고르지 않음) — 순서대로일 때만 쓴다
    @ObservationIgnored private let table: Int?
    @ObservationIgnored private let random: () -> Double
    @ObservationIgnored private let runsLoop: Bool
    @ObservationIgnored private var pendingTaps: [CGPoint] = []
    /// 공개 중 빨강으로 보일 고른 두 장
    @ObservationIgnored private var wrongFirst: Int?
    @ObservationIgnored private var wrongSecond: Int?
    @ObservationIgnored private var currentKind: MonsterKind?
    @ObservationIgnored private var shownQuestionId = -1
    @ObservationIgnored private var shownMonster = -1
    @ObservationIgnored private var frameTimer: Timer?
    @ObservationIgnored private var lastTick: CFTimeInterval?
    #if DEBUG
    /// SIMCTL_CHILD_GUGU_MONSTER_AUTOPLAY=N — N마리를 정답 카드 탭으로 자동으로 물리친다(스크린숏용, 시작도 자동)
    @ObservationIgnored private var autoplayLeft = MonsterEngine.debugAutoplayCount()
    @ObservationIgnored private var autoplayWait = 0.6
    #endif

    /// start: 홈(.standard)·섞어서(.table(nil)) = 단 없음, .table(n) = n단(n 은 2~9 — 10단 이상은 진입 화면이 막는다).
    /// order: 처음 보여 줄 문제 순서(화면은 GameOrderStore 의 마지막 선택) — 순서대로는 n단 × 1 → × 9(단 없으면 단을 바꿔 가며),
    /// 무작위는 고른 단과 상관없이 2~9단 섞어서.
    /// commit: 학습 기록(화면은 GameStore.commitSession + 서버 적재). runsLoop: 테스트는 false 로 두고 advance(_:) 를 직접 부른다
    init(start: MinigameStart, order: ProblemOrder, rewards: PlayRewardsStore, commit: @escaping GameCommit,
         random: @escaping () -> Double = { Double.random(in: 0..<1) }, runsLoop: Bool = true) {
        let table = start.table(defaultTable: nil)
        if let table {
            precondition(MonsterSession.tables.contains(table), "구구 몬스터 단 지정은 \(MonsterSession.tables) 만: \(table)")
        }
        let played = Self.playedTable(order: order, chosen: table)
        self.rewards = rewards
        self.commitSession = commit
        self.order = order
        learning = RoundLearningLog(mode: .monster, table: played)
        self.table = table
        self.random = random
        self.runsLoop = runsLoop
        session = MonsterSession(order: order, table: played, random: random)
        resetRound()
    }

    /// 이 순서로 실제로 쓰는 단 — 순서대로일 때만 고른 단, 무작위는 nil
    private static func playedTable(order: ProblemOrder, chosen: Int?) -> Int? {
        order == .sequential ? chosen : nil
    }

    // MARK: - 화면에서 읽는 값

    /// 판이 끝났는지(60초·체험 끝)
    var roundOver: Bool { session.clock.phase == .over || trialFinished }
    var pauseButtonVisible: Bool { phase == .playing && !roundOver }
    /// "타임업!" — 시간이 다 된 뒤 결과가 뜨기 전까지
    var timeUpVisible: Bool { session.clock.phase == .over && result == nil }
    /// [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초)
    var restartArmed: Bool { result != nil && resultShownFor >= GameResultPanel.armDelaySec }
    /// 첫 카드 전 안내 — 첫 탭 전에는 시계도 멈춰 있음
    var hintVisible: Bool { phase == .playing && session.clock.phase == .waiting }
    /// 남은 체험 처치 수(지금 몬스터 포함, 체험이 아니면 nil)
    var trialLeft: Int? { trial.map { max(0, $0 - session.defeated) } }
    /// 단 시트에서 고른 단(2~9, nil = 고르지 않음) — 순서와 상관없이 그대로
    var chosenTable: Int? { table }
    /// 이번 판에 쓰는 단 — 순서대로 + 단 지정일 때만(시작 창 제목 "· N단"·학습 기록 단), 그 밖은 nil
    var playedTable: Int? { Self.playedTable(order: order, chosen: table) }

    var problemRevealing: Bool { session.clock.isRevealing && lastWrong != nil }

    /// 문제 판 글자 — 공개 중이면 정답 식, 아니면 "고른 카드 × ? = 체력"
    var problemText: String {
        if problemRevealing, let wrong = lastWrong {
            return "\(wrong.problem.a) × \(wrong.problem.b) = \(wrong.problem.a * wrong.problem.b)"
        }
        let q = session.current
        let first = session.selected.map { String(q.cards[$0]) } ?? "?"
        return "\(first) × ? = \(q.target.a * q.target.b)"
    }

    /// 문제 아래 줄 — 공개 중이면 고른 식(빨강), 방금 처치했으면 푼 식(초록) 0.8초
    var subText: String {
        if problemRevealing, let wrong = lastWrong, let a = wrong.pickA, let b = wrong.pickB {
            return "\(a) × \(b) = \(wrong.given)"
        }
        return solvedLeft > 0 ? solvedText : ""
    }

    func cardValue(_ index: Int) -> Int { session.current.cards[index] }

    func cardLook(_ index: Int) -> CardLook {
        guard problemRevealing else { return .normal }
        let q = session.current
        if index == q.pairA || index == q.pairB { return .correct }
        if index == wrongFirst || index == wrongSecond { return .wrong }
        return .normal
    }

    /// 먼저 고른 카드 — 흰 테두리 + 살짝 커짐(공개 중에는 없음)
    func cardSelected(_ index: Int) -> Bool { !problemRevealing && session.selected == index }

    /// 학습 기록(commitSession) 연결용 — 채점 기록(문제 = 몬스터 목표 식, given = 고른 두 카드의 곱)
    var answerRecords: [AnswerRecord] { session.answerRecords }
    /// 판 길이(ms) — 첫 카드부터 시간이 다 될(또는 체험이 끝날) 때까지
    var roundDurationMs: Int { Int((session.clock.elapsed * 1000).rounded()) }

    // MARK: - 조작

    /// 화면 크기·안전 영역 — 카드 자리·코인 출발점·배지 위치 계산에 쓴다
    func setViewport(_ viewport: GameViewport) {
        guard self.viewport != viewport else { return }
        self.viewport = viewport
    }

    /// 동작 줄이기(손쉬운 사용) — 켜면 몬스터 깡총·처치 화면 흔들림을 끈다. 화면이 뜰 때와 설정이 바뀔 때 알려 준다
    func setReduceMotion(_ on: Bool) { stage.reduceMotion = on }

    /// 시작 전 패널의 [시작]. trial: 무료 체험 처치 수(이용권이 있으면 nil)
    func start(trial: Int?) {
        precondition(phase == .ready, "이미 시작한 판: \(phase)")
        precondition(viewport != nil, "화면 크기를 먼저 알려야 함(setViewport)")
        self.trial = trial
        phase = .playing
        Sound.shared.tap()
        startLoop()
    }

    /// 시작 전 패널의 [순서대로]·[무작위] — 뒤에 비치는 첫 몬스터도 새 순서로 다시 만든다(새 세션·새 학습 기록 단). 같은 순서면 그대로
    func setOrder(_ next: ProblemOrder) {
        precondition(phase == .ready, "문제 순서는 시작 전에만 바꿈: \(phase)")
        guard next != order else { return }
        order = next
        learning = RoundLearningLog(mode: .monster, table: playedTable)
        session = MonsterSession(order: next, table: playedTable, random: random)
        resetRound()
    }

    /// 결과 창 [다시 하기] — 같은 순서·같은 단 지정으로 새 판. 쌓인 탭은 버린다(다시 하기를 누른 탭이 새 판 첫 카드로 새지 않게)
    func restart(trial: Int?) {
        // 결과 창이 없으면(이미 새 판을 연 뒤 연타가 한 번 더 온 것) 무시
        guard restartArmed else { return }
        session = MonsterSession(order: order, table: playedTable, random: random)
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

    /// [계속 하기] — 쌓인 탭은 버린다([계속 하기]를 누른 탭이 같은 프레임에 카드로 새지 않게)
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
    /// 판이 끝났으면(타임업·체험 끝 뒤 결과를 띄우기 전) 완료로, 아니면 부분으로. 결과를 띄웠으면 이미 기록함
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

        #if DEBUG
        autoplay(dt, viewport: viewport)
        #endif

        // ① 입력 — [⏸] 위 탭은 빼기(카드 칸 밖이라 지금도 무시되지만 A 와 같은 규칙으로).
        // 60초를 넘는 그 프레임에 누른 탭도 시간보다 먼저 채점한다
        let taps = pendingTaps
        pendingTaps.removeAll()
        if !roundOver {
            let pauseFrame = PauseLayout.buttonFrame(viewport)
            for point in taps where !pauseFrame.contains(point) && !roundOver {
                if let card = Cards.index(at: point, viewport) { tap(card: card, viewport: viewport) }
            }
        }

        // ② 시간
        if !roundOver {
            session.tick(dt)
            syncMonster(drop: true)   // 오답 공개가 끝나 카드만 다시 냈으면 고른 카드 표시를 지움
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

    // MARK: - 카드

    private func tap(card index: Int, viewport: GameViewport) {
        let first = session.selected
        let target = session.current.target
        let starsBefore = session.stars
        switch session.tap(card: index) {
        case .rejected(let reason):
            if reason == .revealing { Sound.shared.deny() }   // 정답 공개 중 — "아직이야"
        case .selected, .deselected:
            Sound.shared.tap()
            Haptics.selection()
        case .judged(let attempt):
            if attempt.correct {
                // 몬스터 자리에서 별이 터지고, 받은 별 코인이 위쪽 별 배지로 날아감
                let camera = MonsterCamera(size: viewport.size)
                sparkles.burst(x: stage.x, y: stage.y, big: StarRules.isComboMilestone(session.streak))
                reward.earn(from: camera.point(stage.x, stage.y), to: coinBadgeIcon(viewport),
                            earned: session.stars - starsBefore, total: session.stars, unit: viewport.unit)
                recordDefeat()
                stage.defeat()
                guard let a = attempt.pickA, let b = attempt.pickB else { preconditionFailure("B 채점에는 고른 카드가 있어야 함") }
                solvedText = "\(a) × \(b) = \(target.a * target.b)"
                solvedLeft = Self.solvedEchoSec
                Sound.shared.correct()
                Haptics.impact(.light)
                problemPulse = 1
                if let trial, session.defeated >= trial { trialFinished = true }
            } else {
                lastWrong = attempt
                wrongFirst = first
                wrongSecond = index
                stage.hit()
                Sound.shared.wrong()
                Haptics.error()
            }
            reward.setStreak(session.streak)
        }
        syncMonster(drop: true)
    }

    /// 새 문제가 나왔으면 고른 카드 표시를 지우고, 새 몬스터면 몬스터를 바꾼다.
    /// 오답 뒤 같은 몬스터 재출제(카드만 섞임)는 몬스터를 그대로 둔다
    private func syncMonster(drop: Bool) {
        let q = session.current
        guard q.id != shownQuestionId else { return }
        shownQuestionId = q.id
        wrongFirst = nil
        wrongSecond = nil
        guard session.monstersIssued != shownMonster else { return }
        shownMonster = session.monstersIssued
        // 순환 위치도 누적 처치 수로: 판마다 0번부터 다시 돌면 한 판에 3~5마리 잡는 아이는 뒤 종(4~7)을 거의 다시 못 만남
        let seen = rewards.dexSeenMask
        let total = rewards.dexTotal
        let kind = MonsterDex.nextKind(totalDefeated: total, seenMask: seen, spawnIndex: total, previous: currentKind)
        currentKind = kind
        stage.spawn(hp: q.target.a * q.target.b, kind: kind, drop: drop)
        // 뒤 4종(누적 처치로 해금)을 처음 만나면 알림 — 처음 4종은 도감 띠의 실루엣으로 충분
        if MonsterDex.unlockAt(kind) > 0 && !MonsterDex.isSeen(kind, seenMask: seen) {
            banner.show(String(localized: "새 몬스터 등장!"))
        }
    }

    /// 방금 물리친 몬스터를 도감에 기록(바로 저장). 처음 물리친 종이면 띠 칸이 채워지며 통통 튐
    private func recordDefeat() {
        guard let kind = currentKind else { preconditionFailure("처치한 몬스터 종이 없음") }
        let record = rewards.recordDefeat(kind)
        guard record.newlySeen || record.unlocked != nil else { return }
        dex.show(seenMask: rewards.dexSeenMask, total: rewards.dexTotal)   // 해금되면 자물쇠도 풀림
        guard record.newlySeen else { return }
        dex.reveal(kind)
        banner.show(String(localized: "도감에 등록!"))
    }

    // MARK: - 판 시작·끝

    private func resetRound() {
        round += 1
        pendingTaps.removeAll()
        stage.reset()
        sparkles.reset()
        reward.reset()
        monsterBadge.reset(0)
        timeBadge.reset(Int(RoundClock.durationSec))
        dex.resetPop()
        dex.show(seenMask: rewards.dexSeenMask, total: rewards.dexTotal)
        banner.clear()
        lastWrong = nil
        wrongFirst = nil
        wrongSecond = nil
        solvedText = ""
        solvedLeft = 0
        problemPulse = 0
        endedFor = 0
        result = nil
        resultShownFor = 0
        trialFinished = false
        learning.reset()
        currentKind = nil
        shownQuestionId = -1
        shownMonster = -1
        syncMonster(drop: false)
    }

    /// 결과를 띄우는 순간 한 번: 받은 별을 쌓고 최고 기록을 갱신(체험 끝도 같음)
    private func showResult() {
        let record = rewards.recordRound(.monster, score: session.defeated, stars: session.stars, bestStreak: session.bestStreak)
        let commit = closeLearning(partial: false)
        let miss = session.missToShow
        result = GameResultContent(
            note: GameResultContent.note(record),
            mainLabel: String(localized: "물리친 몬스터"),
            mainValue: session.defeated,
            subLabel: String(localized: "최고 연속"),
            subValue: session.bestStreak,
            miss: miss?.problem,
            missGiven: miss.flatMap { m in
                guard let a = m.pickA, let b = m.pickB else { return nil }
                return String(localized: "고른 카드 \(a) × \(b) = \(m.given)")
            },
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
        let clock = session.clock
        if clock.phase == .over { banner.clear() }   // 타임업 뒤에는 알림을 지움("타임업!" 만)

        stage.update(dt, time: time)
        sparkles.update(dt)
        banner.update(dt)
        dex.update(dt)
        problemPulse = max(0, problemPulse - dt * 5)
        if !problemRevealing { solvedLeft = max(0, solvedLeft - dt) }

        // 배지 줄: 물리친 몬스터 · 남은 초(10초 이하 빨강·두근) · 별(코인이 닿을 때마다)
        monsterBadge.setValue(session.defeated)
        timeBadge.setValue(Int(clock.remaining.rounded(.up)))
        timeBadge.setDanger(clock.remaining <= Self.dangerSec && clock.phase != .over)
        monsterBadge.update(dt)
        timeBadge.update(dt)
        for index in reward.update(dt, badgeIcon: coinBadgeIcon(viewport)) {
            Sound.shared.coin(step: index)
        }
        let camera = MonsterCamera(size: viewport.size)
        reward.followCombo(camera.point(Self.comboWorld.x, Self.comboWorld.y))
    }

    /// 별 배지 아이콘 가운데(화면 pt) — 코인이 날아가 닿는 곳
    private func coinBadgeIcon(_ viewport: GameViewport) -> CGPoint {
        viewport.safeTop(x: Hud.coin.x - Hud.coin.width / 2 + HudBadgeState.height / 2, y: PauseLayout.hudRowY)
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
    /// 자동 처치가 켜져 있으면 시작 패널을 건너뛴다 — 화면이 이용권 여부에 맞춘 체험 수를 넘긴다(무료면 MinigameTrial.questions 마리에서 체험 끝)
    func debugAutoStart(trial: Int?) {
        guard autoplayLeft > 0, phase == .ready, viewport != nil else { return }
        start(trial: trial)
    }

    /// GUGU_MONSTER_AUTOPLAY 값(없으면 0). 잘못된 값은 조용히 넘기지 않고 멈춘다
    private static func debugAutoplayCount() -> Int {
        guard let raw = ProcessInfo.processInfo.environment["GUGU_MONSTER_AUTOPLAY"] else { return 0 }
        guard let n = Int(raw), n >= 0 else { preconditionFailure("GUGU_MONSTER_AUTOPLAY 는 0 이상 정수: \(raw)") }
        return n
    }

    /// 자동 정답 — 정답 두 장(pairA → pairB)을 실제 탭 경로(큐)로 누른다. 고른 상태가 잠깐 보이게 사이를 둔다
    private func autoplay(_ dt: Double, viewport: GameViewport) {
        guard autoplayLeft > 0, !roundOver, !session.clock.isRevealing else { return }
        autoplayWait -= dt
        guard autoplayWait <= 0 else { return }
        let q = session.current
        if session.selected == nil {
            pendingTaps.append(Cards.center(q.pairA, viewport))
            autoplayWait = 0.35
        } else {
            pendingTaps.append(Cards.center(q.pairB, viewport))
            autoplayWait = 0.6
            autoplayLeft -= 1
        }
    }
    #endif
}
