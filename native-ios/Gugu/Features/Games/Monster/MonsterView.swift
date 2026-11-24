import SwiftUI

// 구구 몬스터 화면 (Unity 시험판 B 곱셈 몬스터 — MonsterGame.BuildHud·PrototypeShell 화면 구성 이식)
// 숲 무대(Canvas) 위에 위 배지 줄 [⏸]·몬스터·남은 시간·별, 도감 띠, 문제 판, 젤리 카드 6장, 알림·불꽃·코인을 얹고,
// 시작 전 패널·쉬는 창·결과 창(체험이면 체험 종료 패널)을 맨 위에 띄운다.
// 카드는 닿는 순간 반응 — 화면 전체 TouchDownCatcher 가 받고 엔진이 카드 칸 안인지 가린다(카드 뷰는 그리기만).

struct MonsterView: View {
    var onExit: () -> Void
    private let start: MinigameStart

    @State private var engine: MonsterEngine?
    @State private var paywallOpen = false
    @Environment(PremiumStore.self) private var premium
    @Environment(PlayRewardsStore.self) private var rewards
    @Environment(GameOrderStore.self) private var orders
    @Environment(GameStore.self) private var game
    @Environment(SyncStore.self) private var sync
    @Environment(AuthStore.self) private var auth
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// 숲 그림이 뜨기 전 바탕(숲 아래쪽 어두운 초록)
    static let backdrop = Color(hex: "#1E3A2C")

    /// start: 홈·섞어서 = 2~9단 섞어서, 단 시트에서 고른 단(.table(n), n 은 2~9) = 그 단만
    init(start: MinigameStart = .standard, onExit: @escaping () -> Void) {
        self.start = start
        self.onExit = onExit
    }

    var body: some View {
        // 바깥 GeometryReader 는 안전 영역을 지켜야 safeAreaInsets 가 0 이 아니다 —
        // 크기는 안전 영역 + 여백으로 전체 화면을 다시 만들고, 안쪽 화면만 안전 영역 밖까지 그린다(StairsView 와 같음)
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let viewport = GameViewport(size: full, safeArea: insets)
            ZStack {
                if let engine {
                    MonsterPlayfield(engine: engine, viewport: viewport, isPremium: premium.isPremium,
                                     onOrder: { order in
                                         // 두 손가락으로 [시작]과 같이 눌러 판이 이미 시작됐으면 무시(엔진은 시작 뒤 순서 변경을 버그로 보고 멈춘다)
                                         guard engine.phase == .ready else { return }
                                         // 엔진이 뒤의 첫 몬스터를 새 순서로 다시 만들고, 선택은 다음에 열 때를 위해 기억한다
                                         engine.setOrder(order)
                                         orders.setOrder(order, for: .monster)
                                     },
                                     onExit: exit, onUnlock: { paywallOpen = true })
                } else {
                    Self.backdrop
                }
            }
            .frame(width: full.width, height: full.height)
            .ignoresSafeArea()
            .onAppear {
                if let engine {
                    engine.setViewport(viewport)
                } else {
                    // 엔진은 환경의 저장소가 필요해 화면이 뜰 때 만든다
                    let created = MonsterEngine(start: start, order: orders.order(.monster), rewards: rewards,
                                                commit: commitSession)
                    created.setReduceMotion(reduceMotion)
                    created.setViewport(viewport)
                    #if DEBUG
                    created.debugAutoStart(trial: MinigameTrial.limit(isPremium: premium.isPremium))
                    #endif
                    engine = created
                }
            }
            .onChange(of: viewport) { _, next in engine?.setViewport(next) }
            .onChange(of: reduceMotion) { _, on in engine?.setReduceMotion(on) }
        }
        .background(Self.backdrop.ignoresSafeArea())
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                engine?.sceneBecameActive()
            } else {
                engine?.sceneBecameInactive()
            }
        }
        .onDisappear { engine?.teardown() }
        // 게임은 루트의 fullScreenCover 라 루트의 결제 시트가 위에 뜨지 못한다 — 여기서 직접 띄운다
        .sheet(isPresented: $paywallOpen) { PaywallView() }
    }

    /// 나가기 — 판 중간이면 푼 답을 부분 기록하고 닫는다(결과·체험 끝 뒤에는 이미 기록함)
    private func exit() {
        engine?.abandon()
        engine?.teardown()
        onExit()
    }

    /// 학습 기록 — 진행도(XP·오늘 목표·틀린 문제)에 반영하고, 학습 원장에 적재해 바로 올려 본다.
    /// 보호자 검증 전이면 flush 가 요청 없이 큐에 남긴다(세션과 같음)
    private func commitSession(_ result: SessionResult) -> CommitResult {
        let commit = game.commitSession(result)
        sync.record(result)
        sync.flush(auth: auth)
        return commit
    }
}

/// 엔진이 있는 동안의 화면 — 프레임마다 엔진 상태를 그린다
private struct MonsterPlayfield: View {
    let engine: MonsterEngine
    let viewport: GameViewport
    let isPremium: Bool
    /// 시작 전 패널의 [순서대로]·[무작위]
    let onOrder: (ProblemOrder) -> Void
    let onExit: () -> Void
    let onUnlock: () -> Void

    var body: some View {
        let u = viewport.unit
        ZStack {
            MonsterScene(stage: engine.stage, sparkles: engine.sparkles, time: engine.time)
            TouchDownCatcher { engine.enqueueTap($0) }
            hud(u)
            if engine.pauseButtonVisible {
                PauseButton(unit: u) { engine.openPause() }
                    .position(viewport.safeTop(x: PauseLayout.buttonX, y: PauseLayout.hudRowY))
            }
            overlays(u)
            // 학습 기록 알림(새 업적·레벨 업) — 결과·체험 종료 창 위
            GameCommitFeedback(commit: engine.learning.commit, round: engine.round, viewport: viewport)
        }
        .frame(width: viewport.size.width, height: viewport.size.height)
    }

    // MARK: - 문제 판·카드·배지 줄·도감·알림 (입력은 받지 않음 — 아래 TouchDownCatcher 로 통과)

    private func hud(_ u: CGFloat) -> some View {
        // 카드 모습은 body 에서 값으로 만들어 넘긴다 — ForEach 안에서 엔진을 읽으면 엔진(같은 참조)이 그대로라
        // SwiftUI 가 카드 내용을 다시 만들지 않아 고른 카드 표시가 안 바뀌었음(세션은 관찰 대상이 아님)
        let faces = (0..<MonsterSession.cardCount).map {
            MonsterCardFace(id: $0, value: engine.cardValue($0), look: engine.cardLook($0), selected: engine.cardSelected($0))
        }
        return ZStack {
            problemBoard(u)
            ForEach(faces) { face in
                MonsterCardView(face: face, unit: u)
                    .position(MonsterEngine.Cards.center(face.id, viewport))
            }
            badge(engine.monsterBadge, icon: GameArt.monsterFile(.green), slot: MonsterEngine.Hud.monster, iconScale: 1.2)
            badge(engine.timeBadge, icon: "icon_hourglass", slot: MonsterEngine.Hud.time)
            DexStripView(state: engine.dex, unit: u)
                .position(viewport.safeTop(x: 0, y: MonsterEngine.Hud.dexY))
            // 알림: 몬스터 배 숫자를 가리지 않게 몬스터 아래(그루터기 근처, 카드 줄 위)
            BannerView(state: engine.banner, fontSize: 76, unit: u)
                .position(viewport.safeCenter(x: 0, y: -165))
            if engine.timeUpVisible {
                OutlinedText(text: String(localized: "타임업!"), font: .suite(.extrabold, 170 * u),
                             color: GamePalette.gold, width: 7 * u)
                    .position(viewport.safeCenter(x: 0, y: 200))
            }
            if let left = engine.trialLeft, engine.phase == .playing, !engine.roundOver {
                // 카드 아랫줄 아래 빈 숲 — 문제 판·몬스터·안내 글자와 겹치지 않는 자리
                TrialProgressPill(left: left)
                    .position(viewport.safeBottom(x: 0, y: 130))
            }
            // 보상(별 배지·불꽃·코인)은 판·카드 위에
            badge(engine.reward.coins, icon: "icon_coin", slot: MonsterEngine.Hud.coin)
            ComboBadgeView(state: engine.reward.combo, unit: u)
            CoinFlightView(state: engine.reward.flight, unit: u)
        }
        .allowsHitTesting(false)
    }

    private func badge(_ state: HudBadgeState, icon: String, slot: (x: CGFloat, width: CGFloat),
                       iconScale: CGFloat = 1) -> some View {
        HudBadgeView(state: state, icon: icon, width: slot.width, iconScale: iconScale, unit: viewport.unit)
            .position(viewport.safeTop(x: slot.x, y: PauseLayout.hudRowY))
    }

    /// 문제 판(별 테두리 보드, 10:3): 안쪽 남색 면에 문제(-352)와 보조 식(-452)을 함께 담는다.
    /// 몬스터 귀 꼭대기(약 -540)와 겹치지 않는 높이. 첫 카드 전 안내는 카드 윗줄 위
    private func problemBoard(_ u: CGFloat) -> some View {
        ZStack {
            GameArt.image("board_star")
                .resizable()
                .scaledToFit()
                .placed(at: viewport.safeTop(x: 0, y: -375), size: viewport.px(CGSize(width: 1000, height: 262)))
            Text(verbatim: engine.problemText)
                .font(.suiteNum(.extrabold, 132 * u))
                .foregroundStyle(engine.problemRevealing ? GamePalette.correct : GamePalette.text)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .scaleEffect(1 + 0.12 * engine.problemPulse)
                .placed(at: viewport.safeTop(x: 0, y: -352), size: viewport.px(CGSize(width: 1060, height: 170)))
                .accessibilityLabel(Text(verbatim: engine.problemText))
            Text(verbatim: engine.subText)
                .font(.suiteNum(.extrabold, 54 * u))
                .foregroundStyle(engine.problemRevealing ? GamePalette.wrong : GamePalette.correct)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .placed(at: viewport.safeTop(x: 0, y: -452), size: viewport.px(CGSize(width: 1000, height: 76)))
            if engine.hintVisible {
                OutlinedText(text: String(localized: "곱해서 몬스터 숫자가 되는 카드 2장!"), font: .suite(.extrabold, 52 * u),
                             color: .white, width: 4 * u)
                    .opacity(0.55 + 0.45 * abs(sin(engine.time * 3)))
                    .position(viewport.safeBottom(x: 0, y: 800))
            }
        }
    }

    // MARK: - 창

    @ViewBuilder
    private func overlays(_ u: CGFloat) -> some View {
        if engine.phase == .ready {
            GameStartPanel(title: title,
                           summary: String(localized: "곱해서 몬스터 숫자가 되는 카드 2장을 골라 몬스터를 물리쳐요!"),
                           symbol: "pawprint.fill", isPremium: isPremium,
                           order: engine.order, onOrder: onOrder,
                           onStart: {
                               // 여러 손가락·연타로 [시작]이 두 번 와도 한 번만(엔진은 두 번째 시작을 버그로 보고 멈춘다)
                               guard engine.phase == .ready else { return }
                               engine.start(trial: MinigameTrial.limit(isPremium: isPremium))
                           },
                           onExit: onExit)
        } else if let result = engine.result {
            if engine.trialFinished && !isPremium {
                // 체험 처치 수를 다 채워 끝남 — 다시 하기 대신 결제 화면으로(구매하면 바로 일반 결과 창으로 바뀜).
                // 그 전에 시간이 다 된 판은 아래 일반 결과 창 → [다시 하기]도 같은 체험 수로 새 판
                GamePanelCard {
                    TrialEndPanel(correct: result.mainValue,
                                  lead: String(localized: "몬스터 \(result.mainValue)마리를 물리쳤어요! "),
                                  xpEarned: result.xpEarned, missionNote: result.missionNote,
                                  onUnlock: onUnlock, onExit: onExit)
                }
            } else {
                GameResultPanel(viewport: viewport, content: result, restartArmed: engine.restartArmed,
                                onRestart: { engine.restart(trial: MinigameTrial.limit(isPremium: isPremium)) },
                                onExit: onExit)
            }
        }
        if engine.paused {
            PausePanelView(viewport: viewport, starsThisRound: engine.session.stars,
                           onResume: { engine.resume() }, onQuit: onExit)
        }
    }

    /// 순서대로 + 단 시트에서 고른 단이면 이름 옆에 단을 붙인다(무작위는 고른 단을 쓰지 않음)
    private var title: String {
        guard let table = engine.playedTable else { return String(localized: "구구 몬스터") }
        return String(localized: "구구 몬스터 · \(table)단")
    }
}

/// 카드 한 장의 그릴 값(자리·숫자·색·골랐는지)
private struct MonsterCardFace: Identifiable, Equatable {
    /// 카드 자리(0~5)
    let id: Int
    let value: Int
    let look: MonsterEngine.CardLook
    let selected: Bool
}

/// 젤리 카드 한 장(원래 비율 그림을 카드 크기로 — 9분할 없이) + 숫자. 고르면 흰 테두리가 생기고 살짝 커진다(색은 그대로)
private struct MonsterCardView: View {
    let face: MonsterCardFace
    let unit: CGFloat

    /// 카드마다 다른 젤리 색(무채색 그림에 곱함) — 민트·하늘·보라·코랄·연두·살구. 어두운 숫자가 잘 읽히는 밝은 색만
    static let colors: [Color] = ["#7FE3A8", "#7CC6FF", "#C8A8FF", "#FF9D8F", "#A9E27B", "#FFC18A"].map { Color(hex: $0) }
    /// 젤리 카드 밝은 몸통 중심이 그림 중심보다 이만큼(높이 대비, 실측) 위
    private static let labelLift: CGFloat = 0.072
    private static let selectedScale: CGFloat = 1.08
    /// 고른 카드 둘레 흰 테두리 두께(양쪽 합)
    private static let glowPad: CGFloat = 34

    var body: some View {
        let size = MonsterEngine.Cards.size
        let w = size.width * unit
        let h = size.height * unit
        ZStack {
            if face.selected {
                GameArt.image("card_jelly")
                    .resizable()
                    .frame(width: w + Self.glowPad * unit, height: h + Self.glowPad * unit)
            }
            GameArt.image("card_jelly")
                .resizable()
                .colorMultiply(color)
                .frame(width: w, height: h)
            Text(verbatim: "\(face.value)")
                .font(.suiteNum(.extrabold, 130 * unit))
                .foregroundStyle(GamePalette.ink)
                .offset(y: -h * Self.labelLift)
        }
        .scaleEffect(face.selected ? Self.selectedScale : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("카드 \(face.value)"))
        .accessibilityIdentifier("monster.card.\(face.id)")
    }

    private var color: Color {
        switch face.look {
        case .normal: return Self.colors[face.id]
        case .correct: return GamePalette.correct
        case .wrong: return GamePalette.wrong
        }
    }
}
