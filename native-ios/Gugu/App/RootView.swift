import SwiftUI

// 앱 셸 (AppShell.tsx 이식) — 온보딩 게이트 + TabView + 세션/게임/옷장/페이월 오버레이

struct RootView: View {
    @State private var game = GameStore()
    @State private var session = SessionStore()
    @State private var premium = PremiumStore()
    @State private var theme = ThemeStore()
    @State private var router = Router()
    @State private var auth = AuthStore()
    @State private var sync = SyncStore()
    @State private var reminder = ReminderStore()
    @State private var prefs = PrefsStore()
    @State private var push = PushStore()
    @State private var playRewards = PlayRewardsStore()
    @State private var gameOrders = GameOrderStore()
    #if DEBUG
    /// DEBUG 전용 — SIMCTL_CHILD_GUGU_GAME=stairs|monster|wardrobe 이면 앱 시작 때 구구 계단·구구 몬스터·옷장을
    /// 전체 화면으로 바로 띄운다(온보딩 전이어도)
    @State private var debugGame: String? = RootView.debugGameFromEnvironment()

    private static func debugGameFromEnvironment() -> String? {
        guard let name = ProcessInfo.processInfo.environment["GUGU_GAME"] else { return nil }
        precondition(["stairs", "monster", "wardrobe"].contains(name), "GUGU_GAME 은 stairs·monster·wardrobe 만 지원: \(name)")
        return name
    }
    #endif
    /// AppDelegate 가 남긴 알림 탭·토큰 갱신 (GuguApp 이 주입)
    @Environment(PushEvents.self) private var pushEvents
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        content
            .environment(game)
            .environment(session)
            .environment(premium)
            .environment(theme)
            .environment(router)
            .environment(auth)
            .environment(sync)
            .environment(reminder)
            .environment(prefs)
            .environment(push)
            .environment(playRewards)
            .environment(gameOrders)
            .preferredColorScheme(theme.colorScheme)
            .tint(.gg.accent)
            // 첫 실행 시 조용히 익명 계정을 만든다 — 화면을 막지 않고 실패해도 앱은 그대로 동작
            .task {
                // 로그인한 계정의 서버 기록(문제별 통계)은 약한 문제로 반영한다
                sync.onServerRestore = { facts in game.mergeRestoredWrongPool(facts) }
                await auth.start()
                await syncAccount()
                // 원격 알림을 켜 둔 기기만 토큰·토픽·서버 등록을 맞춘다 (모두 꺼져 있으면 Firebase 를 건드리지 않는다)
                await push.sync(pushContext)
            }
            // 구매/복원 직후에도 권한을 켠다
            .onChange(of: premium.isPremium) { _, isPremium in
                guard isPremium else { return }
                Task { await sync.enableGuardianSync(auth: auth) }
            }
            // 계정이 바뀌면(이메일 연결·로그인·로그아웃·계정 삭제 후 새 익명 계정) 그 계정 기준으로 다시 맞춘다.
            // 구매가 이미 있던 사용자는 isPremium 이 처음부터 true 라 위 onChange 가 발동하지 않으므로 여기서도 권한을 켠다.
            .onChange(of: accountKey) { _, _ in
                Task { await syncAccount() }
            }
            // 학습 알림 재예약 — 앱 시작, 앱으로 돌아올 때(날짜가 바뀌었을 수 있다),
            // 학습해서 오늘 진척이 바뀔 때. 오늘 목표를 채웠다면 이때 오늘 알림이 빠진다.
            .task { await reminder.reschedule(state: game.state) }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .active else { return }
                Task { await reminder.reschedule(state: game.state) }
                // 앱으로 돌아올 때마다 서버 부여 프리미엄을 다시 확인한다 — DB 변경이 재설치 없이 반영된다
                Task { await premium.refreshServerEntitlement(auth: auth) }
                // 복귀 — 토픽 차이·하루 1회 재등록·대기 중인 광고 거부를 다시 맞춘다
                Task { await push.sync(pushContext) }
            }
            // 계정(로그인·로그아웃·삭제 후 새 익명)·기록 보관 자격·학습 알림·FCM 토큰이 바뀌면 새 값으로 다시 등록한다
            .onChange(of: pushSyncKey) { _, _ in
                Task { await push.sync(pushContext) }
            }
            // 알림 표시 정책용 — 진행 중이면 공지를 배너 없이 목록에만 둔다
            .onChange(of: isImmersive, initial: true) { _, immersive in
                pushEvents.isImmersive = immersive
            }
            // 알림 탭 → 보관 → 온보딩·진행 중 화면이 끝나면 적용
            .onChange(of: pushEvents.tappedRoute, initial: true) { _, route in
                guard let route else { return }
                router.pendingRoute = route
                pushEvents.tappedRoute = nil
            }
            .onChange(of: canApplyRoute, initial: true) { _, ready in
                if ready { applyPendingRoute() }
            }
            .onChange(of: game.state.dailyCorrect) { _, _ in
                Task { await reminder.reschedule(state: game.state) }
            }
    }

    /// 계정 식별 — 사용자가 바뀌거나 익명이 이메일 계정이 되면 달라진다
    private var accountKey: String {
        "\(auth.userId ?? "-")|\(auth.isPermanent)"
    }

    private var pushContext: PushStore.Context {
        .current(auth: auth, premium: premium, reminder: reminder)
    }

    private var pushSyncKey: String {
        "\(accountKey)|\(PushStore.guardianEligible(auth: auth, premium: premium))|\(reminder.settings.enabled)|\(pushEvents.tokenRevision)"
    }

    /// 세션·게임 진행 중 — 알림 이동을 미루고, 포그라운드 공지는 목록에만 둔다
    private var isImmersive: Bool {
        session.active != nil || router.minigame != nil
    }

    /// 보관한 알림 이동을 지금 적용해도 되는지 — 온보딩 전·진행 중·옷장·페이월이 떠 있으면 기다린다
    /// (옷장은 놀이 중이 아니라 알림 배너는 그대로 보이지만, 전체화면 뒤에서 탭이 바뀌거나 결제 시트가 무시되지 않게 닫힐 때까지 미룬다)
    private var canApplyRoute: Bool {
        router.pendingRoute != nil && game.state.onboarded && !isImmersive && !router.wardrobeOpen && !premium.paywallOpen
    }

    /// 허용 목록 딥링크를 화면 이동으로 바꾼다. 설정 시트가 떠 있으면 닫고 이동한다
    private func applyPendingRoute() {
        guard let route = router.pendingRoute else { return }
        router.pendingRoute = nil
        let settingsWasOpen = router.settingsOpen
        print("[Push] 알림 이동:", route.rawValue)
        switch route {
        case .home:
            router.settingsOpen = false
            router.tab = .home
        case .learn:
            router.settingsOpen = false
            router.tab = .map
        case .profile, .profileReport:
            // 주간 리포트 카드는 프로필 상단에 있다
            router.settingsOpen = false
            router.tab = .treasure
        case .settingsNotifications:
            router.tab = .treasure
            router.settingsFocusNotifications = true
            router.settingsOpen = true
        case .paywall:
            router.settingsOpen = false
            guard !premium.isPremium else {
                // 이미 이용권이 있다 — 결제 화면 대신 홈으로
                print("[Push] 이미 프리미엄 — paywall 대신 홈으로")
                router.tab = .home
                return
            }
            Task {
                // 설정 시트가 닫히는 중에 다른 시트를 띄우면 무시된다 — 닫힘을 기다린다
                if settingsWasOpen { try? await Task.sleep(for: .milliseconds(600)) }
                premium.paywallOpen = true
            }
        }
    }

    /// 현재 계정 기준으로 이용권·기록 보관을 맞춘다. 순서가 중요하다 —
    /// 서버 부여 이용권을 먼저 알아야 보호자 권한(구매 근거)을 켤 수 있고,
    /// 권한이 켜진 뒤에야 귀속 → 서버 기록 복원 → 업로드가 통과한다.
    private func syncAccount() async {
        // 운영자가 서버 DB 에서 부여한 프리미엄을 반영한다 (실패하면 마지막 값 유지)
        await premium.refreshServerEntitlement(auth: auth)
        // 이용권을 가진 영구 계정이면 보호자 권한을 켜고 쌓인 기록을 올린다.
        // 검증 전이라면 요청을 보내지 않고 큐에 쌓아 둔다.
        if premium.isPremium {
            await sync.enableGuardianSync(auth: auth)
        } else {
            sync.flush(auth: auth)
        }
    }

    @ViewBuilder
    private var content: some View {
        #if DEBUG
        if let name = debugGame {
            // 디버그 훅 게임 — 나가면 평소 화면(온보딩 또는 탭)으로
            switch name {
            case "monster": MonsterView(start: .standard) { debugGame = nil }
            case "wardrobe": WardrobeView { debugGame = nil }
            default: StairsView(start: .standard) { debugGame = nil }
            }
        } else {
            appContent
        }
        #else
        appContent
        #endif
    }

    @ViewBuilder
    private var appContent: some View {
        if !game.state.onboarded {
            OnboardingView()
                .transition(.opacity)
        } else {
            MainTabView()
                // 세션 오버레이
                .fullScreenCover(item: Binding(
                    get: { session.active },
                    set: { session.active = $0 }
                )) { active in
                    SessionView(mode: active.mode, table: active.table, review: active.review) {
                        session.end()
                    }
                    .id(active.id)
                }
                // 게임 오버레이 (구구 계단·구구 몬스터) — 고른 단을 엔진 시작 단으로 넘긴다
                .fullScreenCover(item: Binding(
                    get: { router.minigame },
                    set: { router.minigame = $0 }
                )) { launch in
                    minigameView(launch)
                }
                // 구구 꾸미기(옷장) 오버레이 — 홈 "게임" 묶음에서 연다
                .fullScreenCover(isPresented: Binding(
                    get: { router.wardrobeOpen },
                    set: { router.wardrobeOpen = $0 }
                )) {
                    WardrobeView { router.wardrobeOpen = false }
                }
                // 페이월 오버레이
                .sheet(isPresented: Binding(
                    get: { premium.paywallOpen },
                    set: { premium.paywallOpen = $0 }
                )) {
                    PaywallView()
                }
        }
    }
}

// MARK: - 게임

extension RootView {
    @ViewBuilder
    fileprivate func minigameView(_ launch: MinigameLaunch) -> some View {
        let exit = { router.minigame = nil }
        switch launch.game {
        case .stairs: StairsView(start: launch.start, onExit: exit)
        case .monster: MonsterView(start: launch.start, onExit: exit)
        }
    }
}

// MARK: - 하단 탭 (TabBar.tsx → 네이티브 TabView)

struct MainTabView: View {
    @Environment(Router.self) private var router

    var body: some View {
        // 시스템 탭 바는 진동이 없어 다른 버튼과 느낌이 달랐다 — 탭 바를 누를 때마다 가볍게 울린다
        // (이미 선택된 탭을 다시 눌러도 setter 가 불려 울리는데, Android 의 PressableCard 도 같다).
        // 코드로 탭을 바꾸는 경로(HomeView 의 "학습" 카드)는 이 setter 를 거치지 않아 중복으로 울리지 않는다 — 시뮬레이터에서 확인
        TabView(selection: Binding(
            get: { router.tab },
            set: { newTab in
                router.tab = newTab
                Haptics.impact(.light)
            }
        )) {
            HomeView()
                .tabItem { Label("홈", systemImage: "house.fill") }
                .tag(AppTab.home)
            LearnView()
                .tabItem { Label("학습", systemImage: "graduationcap.fill") }
                .tag(AppTab.map)
            ProfileView()
                .tabItem { Label("프로필", systemImage: "person.fill") }
                .tag(AppTab.treasure)
        }
    }
}

#Preview {
    RootView()
        .environment(PushEvents())
}
