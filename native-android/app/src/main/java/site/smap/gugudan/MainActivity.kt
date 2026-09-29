package site.smap.gugudan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.designsystem.GuguTheme
import site.smap.gugudan.designsystem.LocalGG
import site.smap.gugudan.designsystem.PressableCard
import site.smap.gugudan.designsystem.suite
import site.smap.gugudan.features.adventure.AdventureScreen
import site.smap.gugudan.features.home.HomeScreen
import site.smap.gugudan.features.learn.LearnScreen
import site.smap.gugudan.features.onboarding.OnboardingScreen
import site.smap.gugudan.features.paywall.PaywallScreen
import site.smap.gugudan.features.profile.ProfileScreen
import site.smap.gugudan.features.basket.BasketScreen
import site.smap.gugudan.features.lanerunner.LaneRunnerScreen
import site.smap.gugudan.features.runner.RunnerScreen
import site.smap.gugudan.features.session.SessionScreen
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.LearningIdentityStore
import site.smap.gugudan.services.LearningOutboxStore
import site.smap.gugudan.services.LocalNotifications
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.*
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import site.smap.gugudan.designsystem.resolvedDark
import site.smap.gugudan.features.settings.SettingsScreen
import site.smap.gugudan.services.Speech

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // DEBUG 테스트 훅 — adb 인스트루먼트 없이 시스템 프로퍼티로 주입
        // 예) adb shell setprop debug.gugu.premium 1
        fun devFlag(name: String): Boolean =
            BuildConfig.DEBUG && getSystemProperty("debug.gugu.$name") == "1"

        val persistence = Persistence(applicationContext)
        Haptics.init(applicationContext, persistence)
        Sound.init(persistence)
        Speech.init(applicationContext)
        val game = GameStore(persistence)
        val adventure = AdventureStore(persistence, devClearR2 = devFlag("clear_r2"))
        val session = SessionStore()
        val premium = PremiumStore(this, persistence, devForcePremium = devFlag("premium"))
        val theme = ThemeStore(persistence)
        val prefs = PrefsStore(persistence)
        val router = Router()
        val auth = AuthStore(persistence, lifecycleScope)
        val sync = SyncStore(
            LearningIdentityStore(persistence), LearningOutboxStore(persistence), lifecycleScope,
        )

        reminder = ReminderStore(this, persistence).also { LocalNotifications.ensureChannel(this) }
        gameStore = game

        setContent {
            CompositionLocalProvider(
                LocalGame provides game,
                LocalAdventure provides adventure,
                LocalSession provides session,
                LocalPremium provides premium,
                LocalTheme provides theme,
                LocalPrefs provides prefs,
                LocalRouter provides router,
                LocalAuth provides auth,
                LocalSync provides sync,
                LocalReminder provides reminder,
            ) {
                // 상태바·내비게이션바 아이콘 색을 앱 테마에 맞춘다.
                // 기본값(auto)은 기기 설정을 따라, 기기가 라이트인데 앱이 다크면 검은 아이콘이 검은 배경에 묻혔다.
                val dark = resolvedDark(theme.theme)
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                GuguTheme(theme.theme) {
                    // 첫 실행 시 조용히 익명 계정을 만든다 — 화면을 막지 않고 실패해도 앱은 그대로 동작
                    LaunchedEffect(Unit) {
                        auth.start()
                        // 이용권을 가진 영구 계정이면 보호자 권한을 켜고 쌓인 기록을 올린다.
                        // 검증 전이라면 요청을 보내지 않고 큐에 쌓아 둔다.
                        if (premium.isPremium) sync.enableGuardianSync(auth, premium.lastPurchaseToken)
                        else sync.flush(auth)
                    }
                    // 구매/복원 직후에도 권한을 켠다
                    LaunchedEffect(premium.isPremium) {
                        if (premium.isPremium) sync.enableGuardianSync(auth, premium.lastPurchaseToken)
                    }
                    // 이메일 승격이 끝난 직후에도 시도한다.
                    // 구매가 이미 있던 사용자는 isPremium 이 처음부터 true 라 위 트리거가 발동하지 않고,
                    // 시작 시점에는 아직 익명이라 enableGuardianSync 가 반환된다 — 그래서 이 트리거가 필요하다.
                    LaunchedEffect(auth.isPermanent) {
                        if (auth.isPermanent && premium.isPremium) {
                            sync.enableGuardianSync(auth, premium.lastPurchaseToken)
                        }
                    }
                    // 학습 알림 재예약 — 학습해서 오늘 진척이 바뀔 때.
                    // 오늘 목표를 채웠다면 이때 오늘 알림이 빠진다. (앱 복귀는 onResume 에서)
                    LaunchedEffect(game.state.dailyCorrect) { reminder.reschedule(game.state) }
                    RootScreen()
                }
            }
        }
    }

    // 앱으로 돌아올 때마다 다시 계획한다 — 날짜가 바뀌었거나 시스템 알림 설정이 바뀌었을 수 있다
    override fun onResume() {
        super.onResume()
        if (::reminder.isInitialized) reminder.reschedule(gameStore.state)
    }

    private lateinit var reminder: ReminderStore
    private lateinit var gameStore: GameStore

    private fun getSystemProperty(key: String): String? = try {
        val cls = Class.forName("android.os.SystemProperties")
        cls.getMethod("get", String::class.java).invoke(null, key) as? String
    } catch (e: Exception) {
        null
    }
}

// 앱 셸 (iOS RootView 이식) — 온보딩 게이트 + 탭 + 오버레이(세션/어드벤처/게임/설정/페이월).
// 시스템 뒤로가기는 가장 위에 떠 있는 화면이 먼저 받는다 (각 화면이 자기 BackHandler 를 가진다).
@Composable
fun RootScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val session = LocalSession.current
    val adventure = LocalAdventure.current
    val premium = LocalPremium.current
    val router = LocalRouter.current

    Box(Modifier.fillMaxSize().background(gg.bg)) {
        if (!game.state.onboarded) {
            OnboardingScreen()
        } else {
            MainTabs()

            // 세션 오버레이 (fullScreenCover 대응)
            AnimatedVisibility(
                visible = session.active != null,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                session.active?.let { active ->
                    SessionScreen(
                        mode = active.mode, table = active.table, review = active.review,
                        onExit = { session.end() },
                    )
                }
            }

            // 어드벤처 오버레이
            AnimatedVisibility(
                visible = adventure.open,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                AdventureScreen(onExit = { adventure.closeAdventure() })
            }

            // 구구 점프 오버레이
            AnimatedVisibility(
                visible = router.runnerOpen,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                RunnerScreen(onExit = { router.runnerOpen = false })
            }

            // 구구 레인 오버레이
            AnimatedVisibility(
                visible = router.laneOpen,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                LaneRunnerScreen(onExit = { router.laneOpen = false })
            }

            // 구구 바구니 오버레이
            AnimatedVisibility(
                visible = router.basketOpen,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                BasketScreen(onExit = { router.basketOpen = false })
            }

            // 설정 오버레이 (프로필 톱니바퀴)
            AnimatedVisibility(
                visible = router.settingsOpen,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                SettingsScreen(onClose = { router.settingsOpen = false })
            }

            // 페이월 오버레이 (최상위 — 설정에서 열어도 그 위에 뜬다)
            AnimatedVisibility(
                visible = premium.paywallOpen,
                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
            ) {
                PaywallScreen(onDismiss = { premium.closePaywall() })
            }
        }
    }
}

@Composable
private fun MainTabs() {
    val router = LocalRouter.current
    val gg = LocalGG.current
    // 탭별 저장 상태(스크롤 위치 등)를 탭을 오가도 보존한다 — 예전에는 탭을 바꿀 때마다 맨 위로 돌아갔다
    val tabState = rememberSaveableStateHolder()

    // 홈이 아닌 탭에서 뒤로가기 → 홈으로 (안드로이드 관례). 홈에서는 앱을 나간다.
    BackHandler(enabled = router.tab != AppTab.HOME) { router.tab = AppTab.HOME }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            tabState.SaveableStateProvider(router.tab) {
                when (router.tab) {
                    AppTab.HOME -> HomeScreen()
                    AppTab.LEARN -> LearnScreen()
                    AppTab.PROFILE -> ProfileScreen()
                }
            }
        }
        // 하단 탭 바
        Column(Modifier.fillMaxWidth().background(gg.surface)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(gg.border))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TabItem(Icons.Filled.Home, "홈", router.tab == AppTab.HOME) { router.tab = AppTab.HOME }
                TabItem(Icons.Filled.School, "학습", router.tab == AppTab.LEARN) { router.tab = AppTab.LEARN }
                TabItem(Icons.Filled.Person, "프로필", router.tab == AppTab.PROFILE) { router.tab = AppTab.PROFILE }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val gg = LocalGG.current
    val tint = if (active) gg.accent else gg.textMuted
    // 선택된 탭은 아이콘 뒤에 알약 모양 표시를 깐다 (Material 3 내비게이션 바 관례)
    val pill by animateColorAsState(if (active) gg.accent.copy(alpha = 0.16f) else Color.Transparent, label = "tabPill")
    PressableCard(
        modifier = Modifier.weight(1f).semantics {
            role = Role.Tab
            selected = active
        },
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier.width(56.dp).height(28.dp).clip(CircleShape).background(pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(label, style = suite(if (active) FontWeight.ExtraBold else FontWeight.Bold, 11), color = tint)
        }
    }
}
