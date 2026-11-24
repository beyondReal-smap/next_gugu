import SwiftUI

// 구구 계단 화면 (Unity 시험판 A 계단 점프 — StairsGame.BuildHud·PrototypeShell 화면 구성 이식)
// 무대(Canvas) 위에 위 배지 줄 [⏸]·계단·먹구름 거리·보물·별, 문제 판, 왼쪽 고도계, 알림·불꽃·코인을 얹고,
// 시작 전 패널·쉬는 창·결과 창(체험이면 체험 종료 패널)을 맨 위에 띄운다.
// 화면 아무 곳이나 닿는 순간 그 칸(왼쪽·가운데·오른쪽) 발판으로 점프 — [⏸] 위는 빼고(엔진), 창이 열리면 창이 입력을 막는다.

struct StairsView: View {
    var onExit: () -> Void
    private let start: MinigameStart

    @State private var engine: StairsEngine?
    @State private var paywallOpen = false
    @Environment(PremiumStore.self) private var premium
    @Environment(PlayRewardsStore.self) private var rewards
    @Environment(GameOrderStore.self) private var orders
    @Environment(GameStore.self) private var game
    @Environment(SyncStore.self) private var sync
    @Environment(AuthStore.self) private var auth
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// start: 홈·섞어서 = 2~9단 무작위, 단 시트에서 고른 단(.table(n)) = n단부터
    init(start: MinigameStart = .standard, onExit: @escaping () -> Void) {
        self.start = start
        self.onExit = onExit
    }

    var body: some View {
        // 바깥 GeometryReader 는 안전 영역을 지켜야 safeAreaInsets 가 0 이 아니다 —
        // 크기는 안전 영역 + 여백으로 전체 화면을 다시 만들고, 안쪽 화면만 안전 영역 밖까지 그린다
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let viewport = GameViewport(size: full, safeArea: insets)
            ZStack {
                if let engine {
                    StairsPlayfield(engine: engine, viewport: viewport, isPremium: premium.isPremium,
                                    onOrder: { order in
                                        // 두 손가락으로 [시작]과 같이 눌러 판이 이미 시작됐으면 무시(엔진은 시작 뒤 순서 변경을 버그로 보고 멈춘다)
                                        guard engine.phase == .ready else { return }
                                        // 엔진이 뒤의 첫 문제를 새 순서로 다시 만들고, 선택은 다음에 열 때를 위해 기억한다
                                        engine.setOrder(order)
                                        orders.setOrder(order, for: .stairs)
                                    },
                                    onExit: exit, onUnlock: { paywallOpen = true })
                } else {
                    StairsSky.skyTop[0].color
                }
            }
            .frame(width: full.width, height: full.height)
            .ignoresSafeArea()
            .onAppear {
                if let engine {
                    engine.setViewport(viewport)
                } else {
                    // 엔진은 환경의 저장소가 필요해 화면이 뜰 때 만든다
                    let created = StairsEngine(start: start, order: orders.order(.stairs), rewards: rewards,
                                                commit: commitSession)
                    created.setReduceMotion(reduceMotion)
                    created.setViewport(viewport)
                    engine = created
                }
            }
            .onChange(of: viewport) { _, next in engine?.setViewport(next) }
            .onChange(of: reduceMotion) { _, on in engine?.setReduceMotion(on) }
        }
        .background(StairsSky.skyTop[0].color.ignoresSafeArea())
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
private struct StairsPlayfield: View {
    let engine: StairsEngine
    let viewport: GameViewport
    let isPremium: Bool
    /// 시작 전 패널의 [순서대로]·[무작위]
    let onOrder: (ProblemOrder) -> Void
    let onExit: () -> Void
    let onUnlock: () -> Void

    var body: some View {
        let u = viewport.unit
        ZStack {
            StairsScene(stage: engine.stage, sparkles: engine.sparkles, cloudTop: engine.session.cloud.top, time: engine.time)
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

    // MARK: - 배지 줄·문제 판·고도계·알림 (입력은 받지 않음 — 아래 TouchDownCatcher 로 통과)

    private func hud(_ u: CGFloat) -> some View {
        ZStack {
            problemBoard(u)
            AltimeterView(state: engine.altimeter, cloudTop: engine.session.cloud.top, best: engine.bestAtStart,
                          accessory: engine.accessory, viewport: viewport)
            BannerView(state: engine.banner, unit: u)
                .position(viewport.safeCenter(x: 0, y: 300))
            badge(engine.stairsBadge, icon: "icon_stairs", slot: StairsEngine.Hud.stairs)
            badge(engine.cloudBadge, icon: "icon_cloud", slot: StairsEngine.Hud.cloud, content: .bar)
            badge(engine.chestBadge, icon: "chest_closed", slot: StairsEngine.Hud.chest, iconScale: 1.2)
            // 보상(별 배지·불꽃·코인)은 판·글자 위에
            badge(engine.reward.coins, icon: "icon_coin", slot: StairsEngine.Hud.coin)
            ComboBadgeView(state: engine.reward.combo, unit: u * StairsEngine.comboScale)
            CoinFlightView(state: engine.reward.flight, unit: u)
            if engine.caughtTextVisible {
                OutlinedText(text: String(localized: "잡혔어요!"), font: .suite(.extrabold, 170 * u),
                             color: GamePalette.gold, width: 7 * u)
                    .position(viewport.safeCenter(x: 0, y: 200))
            }
        }
        .allowsHitTesting(false)
    }

    private func badge(_ state: HudBadgeState, icon: String, slot: (x: CGFloat, width: CGFloat),
                       content: HudBadgeView.Content = .number, iconScale: CGFloat = 1) -> some View {
        HudBadgeView(state: state, icon: icon, width: slot.width, content: content, iconScale: iconScale, unit: viewport.unit)
            .position(viewport.safeTop(x: slot.x, y: PauseLayout.hudRowY))
    }

    /// 문제 판(별 테두리 보드): 안쪽 남색 면에 단 안내와 문제를 함께 담는다. 판 아래에 첫 점프 안내
    private func problemBoard(_ u: CGFloat) -> some View {
        ZStack {
            GameArt.image("board_star")
                .resizable()
                .scaledToFit()
                .placed(at: viewport.safeTop(x: 0, y: -320), size: viewport.px(CGSize(width: 1000, height: 300)))
            Text(verbatim: engine.tableLabel)
                .font(.suite(.extrabold, 48 * u))
                .foregroundStyle(GamePalette.textDim)
                .lineLimit(1)
                .placed(at: viewport.safeTop(x: 0, y: -255), size: viewport.px(CGSize(width: 1000, height: 70)))
            Text(verbatim: engine.problemText)
                .font(.suiteNum(.extrabold, 150 * u))
                .foregroundStyle(engine.problemRevealing ? GamePalette.correct : GamePalette.text)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .scaleEffect(1 + 0.12 * engine.problemPulse)
                .placed(at: viewport.safeTop(x: 0, y: -360), size: viewport.px(CGSize(width: 1060, height: 180)))
                .accessibilityLabel(Text(verbatim: engine.problemText))
            if engine.hintVisible {
                OutlinedText(text: String(localized: "답이 적힌 발판을 눌러 점프!"), font: .suite(.extrabold, 56 * u),
                             color: .white, width: 4 * u)
                    .opacity(0.55 + 0.45 * abs(sin(engine.time * 3)))
                    .position(viewport.safeTop(x: 0, y: -525))
            }
            if let left = engine.trialLeft, engine.phase == .playing, !engine.roundOver {
                TrialProgressPill(left: left)
                    .position(viewport.safeTop(x: 0, y: -610))
            }
        }
    }

    // MARK: - 창

    @ViewBuilder
    private func overlays(_ u: CGFloat) -> some View {
        if engine.phase == .ready {
            GameStartPanel(title: String(localized: "구구 계단"),
                           summary: String(localized: "답이 적힌 발판으로 점프해 먹구름보다 높이 올라가요!"),
                           symbol: "stairs", isPremium: isPremium,
                           order: engine.order, onOrder: onOrder,
                           onStart: {
                               // 여러 손가락·연타로 [시작]이 두 번 와도 한 번만(엔진은 두 번째 시작을 버그로 보고 멈춘다)
                               guard engine.phase == .ready else { return }
                               engine.start(trial: MinigameTrial.limit(isPremium: isPremium))
                           },
                           onExit: onExit)
        } else if let result = engine.result {
            if engine.trialFinished && !isPremium {
                // 체험 문제 수를 다 풀어 끝남 — 다시 하기 대신 결제 화면으로(구매하면 바로 일반 결과 창으로 바뀜).
                // 그 전에 먹구름에 잡힌 판은 아래 일반 결과 창 → [다시 하기]도 같은 체험 수로 새 판
                GamePanelCard {
                    TrialEndPanel(correct: result.mainValue, xpEarned: result.xpEarned, missionNote: result.missionNote,
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
}
