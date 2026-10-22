import SwiftUI

// 앱 셸 (AppShell.tsx 이식) — 온보딩 게이트 + TabView + 세션/어드벤처/페이월 오버레이

struct RootView: View {
    @State private var game = GameStore()
    @State private var session = SessionStore()
    @State private var adventure = AdventureStore()
    @State private var premium = PremiumStore()
    @State private var theme = ThemeStore()
    @State private var router = Router()
    @State private var auth = AuthStore()
    @State private var sync = SyncStore()
    @State private var reminder = ReminderStore()
    @State private var prefs = PrefsStore()
    @State private var push = PushStore()
    /// AppDelegate 가 남긴 알림 탭·토큰 갱신 (GuguApp 이 주입)
    @Environment(PushEvents.self) private var pushEvents
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        content
            .environment(game)
            .environment(session)
            .environment(adventure)
            .environment(premium)
            .environment(theme)
            .environment(router)
            .environment(auth)
            .environment(sync)
            .environment(reminder)
            .environment(prefs)
            .environment(push)
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

    /// 세션·미니게임·어드벤처 진행 중 — 알림 이동을 미루고, 포그라운드 공지는 목록에만 둔다
    private var isImmersive: Bool {
        session.active != nil || adventure.open || router.runnerOpen || router.laneOpen || router.basketOpen
    }

    /// 보관한 알림 이동을 지금 적용해도 되는지 — 온보딩 전·진행 중·페이월이 떠 있으면 기다린다
    private var canApplyRoute: Bool {
        router.pendingRoute != nil && game.state.onboarded && !isImmersive && !premium.paywallOpen
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
            router.tab = .learn
        case .profile, .profileReport:
            // 주간 리포트 카드는 프로필 상단에 있다
            router.settingsOpen = false
            router.tab = .profile
        case .settingsNotifications:
            router.tab = .profile
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
                // 어드벤처 오버레이
                .fullScreenCover(isPresented: Binding(
                    get: { adventure.open },
                    set: { adventure.open = $0 }
                )) {
                    AdventureView { adventure.closeAdventure() }
                }
                // 구구 점프 오버레이
                .fullScreenCover(isPresented: Binding(
                    get: { router.runnerOpen },
                    set: { router.runnerOpen = $0 }
                )) {
                    RunnerView { router.runnerOpen = false }
                }
                // 구구 레인 오버레이
                .fullScreenCover(isPresented: Binding(
                    get: { router.laneOpen },
                    set: { router.laneOpen = $0 }
                )) {
                    LaneRunnerView { router.laneOpen = false }
                }
                // 구구 바구니 오버레이
                .fullScreenCover(isPresented: Binding(
                    get: { router.basketOpen },
                    set: { router.basketOpen = $0 }
                )) {
                    BasketView { router.basketOpen = false }
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
                .tag(AppTab.learn)
            ProfileView()
                .tabItem { Label("프로필", systemImage: "person.fill") }
                .tag(AppTab.profile)
        }
    }
}

#Preview {
    RootView()
        .environment(PushEvents())
}
