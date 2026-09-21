package site.smap.gugudan.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import site.smap.gugudan.core.*
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.AdvAchievements
import site.smap.gugudan.core.adventure.AdventureProgress
import site.smap.gugudan.core.adventure.Shop
import site.smap.gugudan.core.adventure.World
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

    fun setDailyGoal(goal: Int) { state = state.copy(dailyGoal = maxOf(5, goal)); persist() }
    fun setOnboarded(v: Boolean) { state = state.copy(onboarded = v); persist() }
    fun resetProgress() {
        state = Commit.applyVisit(Commit.defaultState.copy(onboarded = true))
        persist()
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

/** 활성 세션 (SessionStore.swift 이식) */
data class ActiveSession(val mode: GameMode, val table: Int?, val token: Int = 0)

class SessionStore {
    var active: ActiveSession? by mutableStateOf(null)
        private set

    fun start(mode: GameMode, table: Int?) { active = ActiveSession(mode, table) }
    fun end() { active = null }
}

/** 테마 (ThemeStore.swift 이식) — 기본 dark, 수동 토글 */
class ThemeStore(private val persistence: Persistence) {
    var theme: Theme by mutableStateOf(
        when (persistence.getString(Persistence.THEME_KEY)) {
            "light" -> Theme.LIGHT
            "dark" -> Theme.DARK
            else -> Theme.DARK
        }
    )
        private set

    fun toggle() {
        theme = if (theme == Theme.DARK) Theme.LIGHT else Theme.DARK
        persistence.putString(Persistence.THEME_KEY, if (theme == Theme.DARK) "dark" else "light")
    }
}

enum class AppTab { HOME, LEARN, PROFILE }

class Router {
    var tab: AppTab by mutableStateOf(AppTab.HOME)

    /** 구구 점프 전체화면 오버레이 (홈·학습 카드에서 진입) */
    var runnerOpen: Boolean by mutableStateOf(false)

    /** 구구 레인 전체화면 오버레이 */
    var laneOpen: Boolean by mutableStateOf(false)

    /** 구구 바구니 전체화면 오버레이 */
    var basketOpen: Boolean by mutableStateOf(false)
}

// CompositionLocal 제공 (루트에서 주입)
val LocalGame = staticCompositionLocalOf<GameStore> { error("GameStore 미제공") }
val LocalAdventure = staticCompositionLocalOf<AdventureStore> { error("AdventureStore 미제공") }
val LocalSession = staticCompositionLocalOf<SessionStore> { error("SessionStore 미제공") }
val LocalPremium = staticCompositionLocalOf<PremiumStore> { error("PremiumStore 미제공") }
val LocalTheme = staticCompositionLocalOf<ThemeStore> { error("ThemeStore 미제공") }
val LocalRouter = staticCompositionLocalOf<Router> { error("Router 미제공") }
val LocalAuth = staticCompositionLocalOf<AuthStore> { error("AuthStore 미제공") }
