package site.smap.gugudan

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.res.stringResource
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
import site.smap.gugudan.services.AndroidStrings
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.LearningIdentityStore
import site.smap.gugudan.services.LearningOutboxStore
import site.smap.gugudan.services.LocalNotifications
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.RemotePush
import site.smap.gugudan.core.PushRoute
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
        // 앱 언어 — 앱별 언어를 바꾸면 Activity 가 다시 만들어지며 여기서 다시 정한다
        AndroidStrings.updateLanguage(this)

        // DEBUG 테스트 훅 — adb 인스트루먼트 없이 시스템 프로퍼티로 주입
        // 예) adb shell setprop debug.gugu.premium 1
        fun devFlag(name: String): Boolean =
            BuildConfig.DEBUG && getSystemProperty("debug.gugu.$name") == "1"

        val persistence = Persistence(applicationContext)
        persistence.purgeExpiredQuarantine()   // 보존 기한(30일)이 지난 손상 저장본 사본 정리
        Haptics.init(applicationContext, persistence)
        Sound.init(persistence)
        Speech.init(applicationContext)
        val game = GameStore(persistence)
        val adventure = AdventureStore(persistence, devClearR2 = devFlag("clear_r2"))
        val session = SessionStore()
        val premium = PremiumStore(this, persistence, devForcePremium = devFlag("premium"))
        val theme = ThemeStore(persistence)
        val prefs = PrefsStore(persistence)
        val router = Router().also { this.router = it }
        val auth = AuthStore(persistence, lifecycleScope, AndroidStrings(this))
        val sync = SyncStore(
            LearningIdentityStore(persistence), LearningOutboxStore(persistence), lifecycleScope,
        )

        // 로그인한 계정의 서버 기록(문제별 통계)은 약한 문제로 반영한다
        sync.onServerRestore = { facts -> game.mergeRestoredWrongPool(facts) }

        // 알림 채널 4종(학습·공지·보호자 소식·광고)을 앱 시작 때 한 번에 만든다
        reminder = ReminderStore(this, persistence).also { LocalNotifications.ensureChannel(this) }
        gameStore = game
        premiumStore = premium
        authStore = auth
        val push = PushStore(
            PersistencePushStorage(persistence), FirebasePushMessaging, ApiPushServer,
            SystemPushPermission(applicationContext), AuthPushAccount(auth), lifecycleScope,
            learningEligible = { auth.isPermanent && premium.isPremium },
            localReminderEnabled = { reminder.settings.enabled },
        )
        pushStore = push

        // 알림을 눌러 앱을 처음 연 경우 — 재생성(복원)일 때는 이미 처리했으므로 건너뛴다
        if (savedInstanceState == null) handlePushIntent(intent)

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
                LocalPush provides push,
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
                        syncAccount(auth, premium, sync)
                        push.sync()
                    }
                    // 구매/복원 직후에도 권한을 켠다
                    LaunchedEffect(premium.isPremium) {
                        if (premium.isPremium) sync.enableGuardianSync(auth, premium.lastPurchaseToken)
                    }
                    // 계정이 바뀌면(이메일 연결·로그인·로그아웃·계정 삭제 후 새 익명 계정) 그 계정 기준으로 다시 맞춘다.
                    // 구매가 이미 있던 사용자는 isPremium 이 처음부터 true 라 위 트리거가 발동하지 않으므로 여기서도 권한을 켠다.
                    // 첫 진입(저장된 계정 그대로)은 위 시작 흐름이 맡으므로 건너뛴다.
                    val accountKey = "${auth.userId ?: "-"}|${auth.isPermanent}"
                    var lastAccountKey by remember { mutableStateOf(accountKey) }
                    LaunchedEffect(accountKey) {
                        if (accountKey != lastAccountKey) {
                            lastAccountKey = accountKey
                            syncAccount(auth, premium, sync)
                            // 새 계정(새 JWT)으로 알림 기기를 다시 등록한다
                            push.sync()
                        }
                    }
                    // 보호자 학습 소식 자격(이용권)·로컬 학습 알림이 바뀌면 서버 등록 내용도 맞춘다 (켜진 원격 항목이 없으면 아무것도 안 함).
                    // 첫 진입은 시작 흐름(위)·onResume 이 이미 동기화하므로 건너뛴다.
                    val pushKey = premium.isPremium to reminder.settings.enabled
                    var lastPushKey by remember { mutableStateOf(pushKey) }
                    LaunchedEffect(pushKey) {
                        if (pushKey != lastPushKey) {
                            lastPushKey = pushKey
                            push.requestSync()
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
        // 원격 푸시 — 토큰·토픽·하루 1회 재등록·광고 거부 대기열 (시스템 알림 설정이 바뀌었을 수도 있다)
        if (::pushStore.isInitialized) pushStore.requestSync()
        // 앱으로 돌아올 때마다 서버 부여 프리미엄을 다시 확인한다 — DB 변경이 재설치 없이 반영된다
        if (::premiumStore.isInitialized) {
            lifecycleScope.launch { premiumStore.refreshServerEntitlement(authStore) }
        }
    }

    /**
     * 현재 계정 기준으로 이용권·기록 보관을 맞춘다. 순서가 중요하다 —
     * 서버 부여 이용권을 먼저 알아야 보호자 권한(구매 근거)을 켤 수 있고,
     * 권한이 켜진 뒤에야 귀속 → 서버 기록 복원 → 업로드가 통과한다.
     */
    private suspend fun syncAccount(auth: AuthStore, premium: PremiumStore, sync: SyncStore) {
        // 운영자가 서버 DB 에서 부여한 프리미엄을 반영한다 (실패하면 마지막 값 유지)
        premium.refreshServerEntitlement(auth)
        // 이용권을 가진 영구 계정이면 보호자 권한을 켜고 쌓인 기록을 올린다.
        // 검증 전이라면 요청을 보내지 않고 큐에 쌓아 둔다.
        if (premium.isPremium) sync.enableGuardianSync(auth, premium.lastPurchaseToken)
        else sync.flush(auth)
    }

    // 앱 화면이 보이는지 — 메시징 서비스가 광고를 조용히 둘지 정할 때 읽는다
    override fun onStart() {
        super.onStart()
        RemotePush.appInForeground = true
    }

    override fun onStop() {
        RemotePush.appInForeground = false
        super.onStop()
    }

    // singleTop — 앱이 떠 있을 때 알림을 누르면 새 화면 대신 여기로 온다
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    /** 알림 data `link` → 이동 대상. 허용 목록 밖이면 홈. 온보딩 전·진행 중이면 RootScreen 이 끝난 뒤 적용한다 */
    private fun handlePushIntent(intent: Intent?) {
        if (!RemotePush.isPushIntent(intent)) return
        val link = intent?.getStringExtra(RemotePush.EXTRA_LINK)
        if (!PushRoute.isKnown(link)) Log.w("Push", "허용 목록 밖 링크 — 홈으로 이동: ${link?.take(40)}")
        router.pendingRoute = PushRoute.parse(link)
        // 같은 인텐트가 다시 전달돼도 반복 이동하지 않게 표시를 지운다
        intent?.removeExtra(RemotePush.EXTRA_LINK)
        intent?.removeExtra(RemotePush.EXTRA_FROM_PUSH)
        intent?.removeExtra("google.message_id")
    }

    private lateinit var reminder: ReminderStore
    private lateinit var router: Router
    private lateinit var pushStore: PushStore
    private lateinit var gameStore: GameStore
    private lateinit var premiumStore: PremiumStore
    private lateinit var authStore: AuthStore

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

    // 세션·미니게임·어드벤처 진행 중에는 원격 알림을 소리·배너 없이 목록에만 둔다
    val playing = session.active != null || adventure.open || router.runnerOpen || router.laneOpen || router.basketOpen
    LaunchedEffect(playing) { RemotePush.playing = playing }
    // 알림으로 들어온 이동 — 온보딩을 마치고 진행 중인 세션·미니게임·어드벤처가 끝난 뒤에 적용한다 (그 전에는 보관)
    val pendingRoute = router.pendingRoute
    LaunchedEffect(pendingRoute, game.state.onboarded, playing) {
        router.applyPendingRoute(
            onboarded = game.state.onboarded, playing = playing, isPremium = premium.isPremium,
        ) { premium.openPaywall() }
    }

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
                TabItem(Icons.Filled.Home, stringResource(R.string.home), router.tab == AppTab.HOME) { router.tab = AppTab.HOME }
                TabItem(Icons.Filled.School, stringResource(R.string.learn), router.tab == AppTab.LEARN) { router.tab = AppTab.LEARN }
                TabItem(Icons.Filled.Person, stringResource(R.string.profile), router.tab == AppTab.PROFILE) { router.tab = AppTab.PROFILE }
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
