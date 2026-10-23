package site.smap.gugudan.store

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import site.smap.gugudan.R
import site.smap.gugudan.core.*
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.AdvAchievements
import site.smap.gugudan.core.adventure.AdventureProgress
import site.smap.gugudan.core.adventure.Shop
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.core.learning.LearningFactStats
import site.smap.gugudan.core.learning.LearningRestore
import site.smap.gugudan.services.Persistence

// 상태 스토어 (iOS Store/ @Observable 이식) — Compose mutableStateOf 기반

class GameStore(private val persistence: Persistence) {
    var state: GameState by mutableStateOf(
        Commit.applyVisit(persistence.loadGameState() ?: Commit.defaultState)
    )
        private set

    val levelInfo: LevelInfo get() = Level.info(state.totalXp)

    init { persist() }

    private fun persist() = persistence.saveGameState(state)

    fun commitSession(result: SessionResult): CommitResult {
        val (next, commit) = Commit.applySession(state, result)
        state = next
        persist()
        return commit
    }

    /** 세션 외 소량 XP 지급 (별 조각 등) — 통계 미오염. 반환: 레벨업 여부 */
    fun grantXp(amount: Int): Boolean {
        val prev = Level.info(state.totalXp).level
        state = state.copy(totalXp = state.totalXp + amount)
        persist()
        return Level.info(state.totalXp).level > prev
    }

    /** 로그인한 계정의 서버 기록으로 약한 문제를 채운다 (기기 값과 문제별 max 병합) */
    fun mergeRestoredWrongPool(facts: Map<String, LearningFactStats>) {
        state = state.copy(wrongPool = LearningRestore.merge(state.wrongPool, facts))
        persist()
    }

    fun setDailyGoal(goal: Int) { state = state.copy(dailyGoal = maxOf(5, goal)); persist() }
    fun setOnboarded(v: Boolean) { state = state.copy(onboarded = v); persist() }
    fun resetProgress() {
        state = Commit.applyVisit(Commit.defaultState.copy(onboarded = true))
        persist()
        // 사용자가 기록을 지웠다면 손상 저장본의 격리 사본도 남기지 않는다
        persistence.clearQuarantine(Persistence.PROGRESS_KEY)
    }
}

class AdventureStore(private val persistence: Persistence, devClearR2: Boolean = false) {
    var progress: AdventureProgress by mutableStateOf(run {
        var p = persistence.loadAdventure() ?: AdvProgress.defaultProgress
        if (devClearR2) {
            val ids = World.regions.first().npcs.map { it.id }.filter { it !in p.defeatedNpcs }
            p = p.copy(defeatedNpcs = p.defeatedNpcs + ids)
        }
        // 업적 소급 반영
        val backfill = AdvAchievements.newlyUnlocked(p)
        if (backfill.isNotEmpty()) p = p.copy(achievements = p.achievements + backfill)
        p
    })
        private set

    var open: Boolean by mutableStateOf(false)

    init { persist() }
    private fun persist() = persistence.saveAdventure(progress)

    fun openAdventure() { open = true }
    fun closeAdventure() { open = false }

    fun recordBattle(npcId: String, won: Boolean): List<String> {
        val (next, unlocked) = AdvProgress.applyBattle(progress, npcId, won)
        progress = next
        persist()
        return unlocked
    }

    fun collectShard() {
        progress = progress.copy(starShards = progress.starShards + 1)
        persist()
    }

    // MARK: 꾸미기 상점

    val equippedColorHex: String get() = Shop.color(progress.equippedColor)?.hex ?: "#6366f1"

    fun ownsColor(id: String): Boolean = id == Shop.DEFAULT_COLOR_ID || id in progress.ownedColors
    fun ownsHat(id: String): Boolean = id in progress.ownedHats

    fun buyColor(id: String): Boolean {
        val item = Shop.color(id) ?: return false
        if (ownsColor(id) || progress.starShards < item.price) return false
        progress = progress.copy(
            starShards = progress.starShards - item.price,
            ownedColors = progress.ownedColors + id,
            equippedColor = id,
        )
        persist(); return true
    }

    fun buyHat(id: String): Boolean {
        val item = Shop.hat(id) ?: return false
        if (ownsHat(id) || progress.starShards < item.price) return false
        progress = progress.copy(
            starShards = progress.starShards - item.price,
            ownedHats = progress.ownedHats + id,
            equippedHat = id,
        )
        persist(); return true
    }

    fun equipColor(id: String) {
        if (!ownsColor(id)) return
        progress = progress.copy(equippedColor = id); persist()
    }

    fun equipHat(id: String?) {
        if (id != null && !ownsHat(id)) return
        progress = progress.copy(equippedHat = id); persist()
    }

    fun resetAdventure() { progress = AdvProgress.defaultProgress; persist() }
}

/** 활성 세션 (SessionStore.swift 이식). review = 취약 문제 복습 (오답 풀에서 가중치 큰 문제부터 출제) */
data class ActiveSession(val mode: GameMode, val table: Int?, val review: Boolean = false, val token: Int = 0)

class SessionStore {
    var active: ActiveSession? by mutableStateOf(null)
        private set

    fun start(mode: GameMode, table: Int?) { active = ActiveSession(mode, table) }
    /** 취약 문제 복습 — 학습(practice) 규칙으로 헷갈렸던 문제를 모아 푼다 */
    fun startReview() { active = ActiveSession(GameMode.PRACTICE, null, review = true) }
    fun end() { active = null }
}

/** 테마 (ThemeStore.swift 이식) — 기기 설정/라이트/다크 선택. 저장값이 없는 첫 실행은 웹과 같은 다크 */
class ThemeStore(private val persistence: Persistence) {
    var theme: Theme by mutableStateOf(
        when (persistence.getString(Persistence.THEME_KEY)) {
            "system" -> Theme.SYSTEM
            "light" -> Theme.LIGHT
            else -> Theme.DARK
        }
    )
        private set

    fun set(t: Theme) {
        theme = t
        persistence.putString(
            Persistence.THEME_KEY,
            when (t) { Theme.SYSTEM -> "system"; Theme.LIGHT -> "light"; Theme.DARK -> "dark" },
        )
    }

    companion object {
        /** 테마 이름 — 화면에서 stringResource 로 읽는다 */
        @StringRes
        fun labelRes(t: Theme): Int = when (t) {
            Theme.SYSTEM -> R.string.system
            Theme.LIGHT -> R.string.light
            Theme.DARK -> R.string.dark
        }
    }
}

enum class AppTab { HOME, LEARN, PROFILE }

/** 움직이며 놀기 게임 — 선언 순서가 홈·단 시트·'섞어서' 시트의 표시 순서다 */
enum class Minigame { POP, BASKET, RUNNER, LANE }

/**
 * 미니게임 시작 단. "단을 안 정함(게임 기본값)"과 "섞어서(null)"를 구분해야 해서 Int? 하나로 두지 않는다.
 * Table(null) = 섞어서, Table(7) = 7단.
 */
sealed class MinigameStart {
    data object Default : MinigameStart()
    data class Table(val table: Int?) : MinigameStart()
}

class Router {
    var tab: AppTab by mutableStateOf(AppTab.HOME)

    /** 구구 점프 전체화면 오버레이 (홈·학습 카드에서 진입) */
    var runnerOpen: Boolean by mutableStateOf(false)

    /** 구구 레인 전체화면 오버레이 */
    var laneOpen: Boolean by mutableStateOf(false)

    /** 구구 바구니 전체화면 오버레이 */
    var basketOpen: Boolean by mutableStateOf(false)

    /** 구구 팡팡 전체화면 오버레이 */
    var popOpen: Boolean by mutableStateOf(false)

    /** 미니게임을 열 때 미리 골라 둘 단 — 홈 진입은 게임 기본값, 단 시트·'섞어서' 시트 진입은 그 단 */
    var minigameStart: MinigameStart by mutableStateOf(MinigameStart.Default)
        private set

    /** 미니게임 오버레이를 연다. 시작 단을 함께 바꿔 이전 진입의 단이 남지 않게 한다 */
    fun openMinigame(game: Minigame, start: MinigameStart = MinigameStart.Default) {
        minigameStart = start
        when (game) {
            Minigame.POP -> popOpen = true
            Minigame.BASKET -> basketOpen = true
            Minigame.RUNNER -> runnerOpen = true
            Minigame.LANE -> laneOpen = true
        }
    }

    /** 설정 화면 (프로필 톱니바퀴) */
    var settingsOpen: Boolean by mutableStateOf(false)

    /** 설정 화면을 열면서 알림 섹션으로 스크롤할지 — 알림 딥링크 settings/notifications. 스크롤한 뒤 설정 화면이 끈다 */
    var settingsFocusNotifications: Boolean by mutableStateOf(false)

    /** 알림을 눌러 들어온 이동 대상 — 온보딩 전·세션·미니게임·어드벤처 진행 중이면 보관했다가 끝난 뒤 적용한다 */
    var pendingRoute: PushRoute? by mutableStateOf(null)

    /**
     * 보관한 이동 대상을 지금 적용할 수 있으면 적용하고 비운다. 온보딩 전이거나 진행 중(playing)이면 그대로 둔다 —
     * 아이의 진행을 끊지 않고, 끝나면(playing 이 false 로 바뀌면) 다시 불려 적용된다. 적용했으면 true.
     */
    fun applyPendingRoute(onboarded: Boolean, playing: Boolean, isPremium: Boolean, openPaywall: () -> Unit): Boolean {
        val route = pendingRoute ?: return false
        if (!onboarded || playing) return false
        pendingRoute = null
        open(route, isPremium, openPaywall)
        return true
    }

    /** 알림 이동 대상을 연다. 이미 이용권이 있으면 페이월 대신 홈으로 */
    fun open(route: PushRoute, isPremium: Boolean, openPaywall: () -> Unit) {
        when (route) {
            PushRoute.HOME -> { settingsOpen = false; tab = AppTab.HOME }
            PushRoute.LEARN -> { settingsOpen = false; tab = AppTab.LEARN }
            // 주간 리포트 카드는 프로필 탭에 있다
            PushRoute.PROFILE, PushRoute.PROFILE_REPORT -> { settingsOpen = false; tab = AppTab.PROFILE }
            PushRoute.PAYWALL ->
                if (isPremium) { settingsOpen = false; tab = AppTab.HOME } else openPaywall()
            PushRoute.SETTINGS_NOTIFICATIONS -> { settingsFocusNotifications = true; settingsOpen = true }
        }
    }
}

// CompositionLocal 제공 (루트에서 주입)
val LocalGame = staticCompositionLocalOf<GameStore> { error("GameStore 미제공") }
val LocalAdventure = staticCompositionLocalOf<AdventureStore> { error("AdventureStore 미제공") }
val LocalSession = staticCompositionLocalOf<SessionStore> { error("SessionStore 미제공") }
val LocalPremium = staticCompositionLocalOf<PremiumStore> { error("PremiumStore 미제공") }
val LocalTheme = staticCompositionLocalOf<ThemeStore> { error("ThemeStore 미제공") }
val LocalRouter = staticCompositionLocalOf<Router> { error("Router 미제공") }
val LocalAuth = staticCompositionLocalOf<AuthStore> { error("AuthStore 미제공") }
val LocalSync = staticCompositionLocalOf<SyncStore> { error("SyncStore 미제공") }
val LocalReminder = staticCompositionLocalOf<ReminderStore> { error("ReminderStore 미제공") }
val LocalPrefs = staticCompositionLocalOf<PrefsStore> { error("PrefsStore 미제공") }
val LocalPush = staticCompositionLocalOf<PushStore> { error("PushStore 미제공") }
