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
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.*
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Haptics.init(applicationContext)

        // DEBUG 테스트 훅 — adb 인스트루먼트 없이 시스템 프로퍼티로 주입
        // 예) adb shell setprop debug.gugu.premium 1
        fun devFlag(name: String): Boolean =
            BuildConfig.DEBUG && getSystemProperty("debug.gugu.$name") == "1"

        val persistence = Persistence(applicationContext)
        Sound.init(persistence)
        val game = GameStore(persistence)
        val adventure = AdventureStore(persistence, devClearR2 = devFlag("clear_r2"))
        val session = SessionStore()
        val premium = PremiumStore(this, persistence, devForcePremium = devFlag("premium"))
        val theme = ThemeStore(persistence)
        val router = Router()
        val auth = AuthStore(persistence, lifecycleScope)

        setContent {
            CompositionLocalProvider(
                LocalGame provides game,
                LocalAdventure provides adventure,
                LocalSession provides session,
                LocalPremium provides premium,
                LocalTheme provides theme,
                LocalRouter provides router,
                LocalAuth provides auth,
            ) {
                GuguTheme(theme.theme) {
                    // 첫 실행 시 조용히 익명 계정을 만든다 — 화면을 막지 않고 실패해도 앱은 그대로 동작
                    LaunchedEffect(Unit) { auth.start() }
                    RootScreen()
                }
            }
        }
    }

    private fun getSystemProperty(key: String): String? = try {
        val cls = Class.forName("android.os.SystemProperties")
        cls.getMethod("get", String::class.java).invoke(null, key) as? String
    } catch (e: Exception) {
        null
    }
}

// 앱 셸 (iOS RootView 이식) — 온보딩 게이트 + 탭 + 오버레이(세션/어드벤처/페이월)
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
                        mode = active.mode, table = active.table,
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

            // 페이월 오버레이 (최상위)
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

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (router.tab) {
                AppTab.HOME -> HomeScreen()
                AppTab.LEARN -> LearnScreen()
                AppTab.PROFILE -> ProfileScreen()
            }
        }
        // 하단 탭 바
        Row(
            Modifier.fillMaxWidth().background(gg.surface).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TabItem(Icons.Filled.Home, "홈", router.tab == AppTab.HOME) { router.tab = AppTab.HOME }
            TabItem(Icons.Filled.School, "학습", router.tab == AppTab.LEARN) { router.tab = AppTab.LEARN }
            TabItem(Icons.Filled.Person, "프로필", router.tab == AppTab.PROFILE) { router.tab = AppTab.PROFILE }
        }
    }
}

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val gg = LocalGG.current
    val tint = if (active) gg.accent else gg.textMuted
    PressableCard(modifier = Modifier.weight(1f), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, style = suite(FontWeight.Bold, 11), color = tint)
        }
    }
}
