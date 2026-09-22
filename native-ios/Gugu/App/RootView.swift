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
            .preferredColorScheme(theme.colorScheme)
            .tint(.gg.accent)
            // 첫 실행 시 조용히 익명 계정을 만든다 — 화면을 막지 않고 실패해도 앱은 그대로 동작
            .task {
                await auth.start()
                // 이용권을 가진 영구 계정이면 보호자 권한을 켜고 쌓인 기록을 올린다.
                // 검증 전이라면 요청을 보내지 않고 큐에 쌓아 둔다.
                if premium.isPremium {
                    await sync.enableGuardianSync(auth: auth)
                } else {
                    sync.flush(auth: auth)
                }
            }
            // 구매/복원 직후에도 권한을 켠다
            .onChange(of: premium.isPremium) { _, isPremium in
                guard isPremium else { return }
                Task { await sync.enableGuardianSync(auth: auth) }
            }
            // 이메일 승격이 끝난 직후에도 시도한다.
            // 구매가 이미 있던 사용자는 isPremium 이 처음부터 true 라 위 onChange 가 발동하지 않고,
            // 시작 시점에는 아직 익명이라 enableGuardianSync 가 반환된다 — 그래서 이 트리거가 필요하다.
            .onChange(of: auth.isPermanent) { _, isPermanent in
                guard isPermanent, premium.isPremium else { return }
                Task { await sync.enableGuardianSync(auth: auth) }
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
                    SessionView(mode: active.mode, table: active.table) {
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
        @Bindable var router = router
        TabView(selection: $router.tab) {
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
}
