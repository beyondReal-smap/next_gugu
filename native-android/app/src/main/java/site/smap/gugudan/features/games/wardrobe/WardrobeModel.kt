package site.smap.gugudan.features.games.wardrobe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.R
import site.smap.gugudan.core.Strings
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.Wardrobe
import site.smap.gugudan.core.games.WardrobeError
import site.smap.gugudan.features.games.shared.HudBadgeState
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

// 구구 꾸미기(옷장) 화면 상태 (Unity 시험판 WardrobeScreen.cs 의 규칙 부분 이식, iOS WardrobeModel.swift 대응)
// 장신구 카드를 누르면 먼저 큰 구구에게 씌워 보여 주고(써 보기 — 별은 안 씀), 아래 큰 버튼으로 사기·쓰기·벗기.
// 한 번 잘못 눌러 별이 빠지지 않게 두 단계로 나눴다. 저장은 PlayRewardsStore(사면 바로 씀).
// 시험판에서 고친 규칙(2026-11-23):
//  ① 사기·쓰기·벗기 직후 0.4초는 버튼 잠금 — 같은 버튼이 곧바로 [벗기]·[쓰기]로 바뀌어, 연타하면 사자마자 벗겨졌음
//  ② 큰 구구는 "카드를 눌러 써 보는 중"이면 고른 것, 사기·쓰기·벗기 뒤에는 저장된 모습 — 벗기면 맨머리(대표님 지적)
//  ③ 별이 모자라면 버튼이 흔들리고 거절음 ④ 다시 열면 쓰고 있는 것이 골라져 있음
// 프레임 루프는 움직이는 것(통통·흔들림·잠금·배지)이 있을 때만 화면이 돌린다(running). 테스트는 advance 를 직접 부른다.

class WardrobeModel(
    private val rewards: PlayRewardsStore,
    private val strings: Strings,
) {
    /** 아래 큰 버튼이 할 일 */
    sealed class Action {
        /** 아직 아무 카드도 안 고름 */
        data object None : Action()
        data class Buy(val price: Int) : Action()
        /** 별이 모자람 — 누르면 흔들림 */
        data class NeedMore(val lack: Int) : Action()
        data object Wear : Action()
        data object TakeOff : Action()
    }

    /** 고른 카드(처음엔 쓰고 있는 것) */
    var selected by mutableStateOf(rewards.equipped)
        private set
    /** 큰 구구가 고른 카드를 써 보는 중인지 — 카드를 누르면 켜지고, 사기·쓰기·벗기를 하면 꺼져 저장된 모습을 보여 줌 */
    var tryingOn by mutableStateOf(false)
        private set
    /** 움직이는 것이 있어 화면이 프레임 루프를 돌려야 하는지 */
    var running by mutableStateOf(false)
        private set
    /** 프레임 번호 — 통통·흔들림을 그리는 곳이 이 값을 읽어 다시 그린다 */
    var frame by mutableLongStateOf(0L)
        private set
    var pop = 0.0
        private set
    var shake = 0.0
        private set
    var actionLock = 0.0
        private set
    /** 위 모은 별 배지 */
    val starsBadge = HudBadgeState().also { it.reset(rewards.stars) }
    /** 배지 통통이 남은 시간 — 그동안만 배지를 움직인다(가만히 있을 때 프레임마다 다시 그리지 않게) */
    private var badgeLeft = 0.0

    // MARK: 화면에서 읽는 값

    val stars: Int get() = rewards.stars

    /** 큰 구구가 쓰고 있을 것 — 써 보는 중이면 고른 것, 아니면 저장된 것(없으면 맨머리 null) */
    val previewWearing: Accessory? get() = if (tryingOn) selected else rewards.equipped

    val action: Action
        get() {
            val item = selected ?: return Action.None
            if (rewards.owns(item)) return if (rewards.equipped == item) Action.TakeOff else Action.Wear
            val lack = Wardrobe.shortfall(item, rewards.stars)
            return if (lack > 0) Action.NeedMore(lack) else Action.Buy(item.price)
        }

    val actionTitle: String
        get() = when (val a = action) {
            Action.None -> strings.get(R.string.wardrobe_pick_item)
            is Action.Buy -> strings.plural(R.plurals.wardrobe_buy_for_stars, a.price, a.price)   // iOS 카탈로그가 복수형으로 바꿨다(영어 "1 star")
            is Action.NeedMore -> strings.plural(R.plurals.wardrobe_need_more_stars, a.lack, a.lack)
            Action.Wear -> strings.get(R.string.wardrobe_wear)
            Action.TakeOff -> strings.get(R.string.wardrobe_take_off)
        }

    fun owns(item: Accessory): Boolean = rewards.owns(item)
    fun isEquipped(item: Accessory): Boolean = rewards.equipped == item
    /** 별이 모자라 아직 못 사는 것(값을 빨갛게) */
    fun cannotAfford(item: Accessory): Boolean = !owns(item) && Wardrobe.shortfall(item, rewards.stars) > 0

    /** 큰 구구 통통 배율 */
    val previewScale: Double get() = 1 + 0.12 * sin(pop * PI)
    /** 별 부족 흔들림(설계 px) */
    val actionShakeX: Double get() = sin(shake * 60) * 18 * (shake / SHAKE_SEC)

    // MARK: 조작

    /** 카드 누름: 써 보기(별은 아직 안 씀) */
    fun select(item: Accessory) {
        selected = item
        tryingOn = true
        pop = 1.0
        Sound.tap()
        Haptics.selection()
        startLoop()
    }

    /** 아래 큰 버튼. 방금 바뀐 버튼을 연타로 또 누른 것(잠금 중)은 무시 */
    fun pressAction() {
        if (actionLock > 0) return
        when (action) {
            Action.None -> return
            is Action.Buy -> {
                val item = checkNotNull(selected) { "사기 버튼인데 고른 장신구가 없음" }
                try {
                    rewards.buy(item)   // 사면 바로 씀
                } catch (e: WardrobeError) {
                    // action 이 가진 것·별 부족을 이미 걸렀으므로 여기 오면 화면 규칙 버그 — 삼키지 않고 멈춘다
                    throw IllegalStateException("화면이 막았어야 하는 사기: ${e.message}", e)
                }
                Sound.correct()
                Haptics.success()
                pop = 1.0
            }
            Action.Wear -> {
                val item = checkNotNull(selected) { "쓰기 버튼인데 고른 장신구가 없음" }
                rewards.equip(item)
                Sound.correct()
                Haptics.impactLight()
                pop = 1.0
            }
            Action.TakeOff -> {
                rewards.equip(null)
                Sound.tap()
                Haptics.impactLight()
            }
            is Action.NeedMore -> {
                Sound.deny()
                Haptics.warning()
                shake = SHAKE_SEC
                startLoop()
                return
            }
        }
        actionLock = ACTION_LOCK_SEC
        tryingOn = false   // 저장이 바뀌었으니 큰 구구는 저장된 모습(벗기 → 맨머리)
        if (starsBadge.value != rewards.stars) {
            starsBadge.setValue(rewards.stars)   // 값이 바뀌면 통통
            badgeLeft = HudBadgeState.POP_SEC
        }
        startLoop()
    }

    /** 한 프레임 — 통통·흔들림·잠금 시간을 줄인다. 화면 루프가 부르고, 테스트는 직접 부른다 */
    fun advance(dt: Double) {
        require(dt >= 0) { "dt 는 0 이상이어야 함: $dt" }
        if (pop > 0) pop = max(0.0, pop - dt / POP_SEC)
        if (shake > 0) shake = max(0.0, shake - dt)
        if (actionLock > 0) actionLock = max(0.0, actionLock - dt)
        if (badgeLeft > 0) {
            badgeLeft = max(0.0, badgeLeft - dt)
            starsBadge.update(dt)
        }
        frame += 1
        if (!animating) running = false
    }

    // MARK: 프레임 루프 (움직이는 것이 있을 때만)

    private val animating: Boolean get() = pop > 0 || shake > 0 || actionLock > 0 || badgeLeft > 0

    private fun startLoop() {
        frame += 1
        running = true
    }

    companion object {
        /** 사기·쓰기·벗기 직후 버튼 잠금(초) */
        const val ACTION_LOCK_SEC = 0.4
        /** 큰 구구 통통(초) */
        const val POP_SEC = 0.35
        /** 별 부족 흔들림(초) */
        const val SHAKE_SEC = 0.35
    }
}
