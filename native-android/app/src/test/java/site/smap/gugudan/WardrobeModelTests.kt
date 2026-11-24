package site.smap.gugudan

import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.features.games.wardrobe.WardrobeModel
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구구 꾸미기(옷장) 화면 상태 — iOS WardrobeModelTests.swift 와 같은 의미. Unity 시험판 WardrobeScreen 에서 고친 규칙 회귀:
 * 사기·쓰기·벗기 직후 0.4초 잠금(연타로 사자마자 벗겨짐 방지), 벗기 → 큰 구구 맨머리(대표님 지적),
 * 써 보기는 별을 안 씀, 별 부족은 거절(흔들림), 다시 열면 쓰고 있는 것이 골라져 있음.
 */
class WardrobeModelTests {

    private val frame = 1.0 / 60
    private val strings = TestStrings(AppLanguage.KO)

    @Before fun quiet() {
        Sound.setEnabled(false)   // JVM 테스트에는 AudioTrack 이 없다
    }

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) = error("격리될 저장본이 없어야 함: $key")
    }

    private fun makeStore(stars: Int): PlayRewardsStore {
        val store = PlayRewardsStore(MemoryRewardsStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")
        if (stars > 0) store.recordRound(PlayGame.STAIRS, score = 1, stars = stars, bestStreak = 1)
        return store
    }

    private fun run(model: WardrobeModel, sec: Double) {
        repeat(ceil(sec / frame).toInt()) { model.advance(frame) }
    }

    /** 처음 열면 아무것도 안 고름 → 버튼은 안내, 눌러도 아무 일 없음 */
    @Test fun nothingSelectedAtFirst() {
        val store = makeStore(62)
        val model = WardrobeModel(store, strings)
        assertNull(model.selected)
        assertNull(model.previewWearing)
        assertEquals(WardrobeModel.Action.None, model.action)
        assertEquals("장신구를 골라 보세요", model.actionTitle)
        model.pressAction()
        assertEquals(62, store.stars)
        assertEquals(0.0, model.actionLock)
        assertFalse(model.running)
    }

    /** 써 보기: 카드를 누르면 큰 구구가 쓰지만 별은 그대로, 저장도 그대로 */
    @Test fun tryOnSpendsNoStars() {
        val store = makeStore(62)
        val model = WardrobeModel(store, strings)
        model.select(Accessory.PARTY_HAT)
        assertEquals(Accessory.PARTY_HAT, model.previewWearing)
        assertEquals(WardrobeModel.Action.Buy(50), model.action)
        assertEquals("별 50개로 사기", model.actionTitle)
        assertEquals(62, store.stars)
        assertNull(store.equipped)
        assertFalse(store.owns(Accessory.PARTY_HAT))
        assertTrue(model.running, "통통 — 프레임 루프를 돌림")
    }

    /** 별 부족: 왕관을 써 볼 수는 있지만 버튼은 "별 N개 더 모아요" — 누르면 흔들리고 아무것도 안 바뀜 */
    @Test fun notEnoughStarsIsRefused() {
        val store = makeStore(62)
        val model = WardrobeModel(store, strings)
        model.select(Accessory.CROWN)
        assertEquals(Accessory.CROWN, model.previewWearing)
        assertEquals(WardrobeModel.Action.NeedMore(138), model.action)
        assertEquals("별 138개 더 모아요", model.actionTitle)
        assertTrue(model.cannotAfford(Accessory.CROWN))

        model.pressAction()
        assertEquals(62, store.stars)
        assertFalse(store.owns(Accessory.CROWN))
        assertNull(store.equipped)
        assertTrue(model.shake > 0)
        assertEquals(0.0, model.actionLock, "거절은 잠그지 않음")
        assertEquals(Accessory.CROWN, model.previewWearing, "거절 뒤에도 계속 써 보는 중")
        run(model, WardrobeModel.SHAKE_SEC + 0.02)
        assertEquals(0.0, model.shake)
        assertEquals(0.0, model.actionShakeX)
        assertFalse(model.running, "움직임이 끝나면 루프를 멈춤")
    }

    /** 사면 바로 씀 + 0.4초 잠금: 사자마자 연타해도 벗겨지지 않고, 잠금이 풀린 뒤에야 [벗기]가 먹힘 → 큰 구구 맨머리 */
    @Test fun buyLocksButtonThenTakeOffShowsBareHead() {
        val store = makeStore(62)
        val model = WardrobeModel(store, strings)
        model.select(Accessory.PARTY_HAT)
        model.pressAction()

        assertEquals(12, store.stars)
        assertTrue(store.owns(Accessory.PARTY_HAT))
        assertEquals(Accessory.PARTY_HAT, store.equipped)
        assertEquals(WardrobeModel.Action.TakeOff, model.action)
        assertEquals("벗기", model.actionTitle)
        assertEquals(Accessory.PARTY_HAT, model.previewWearing)
        assertEquals(12, model.starsBadge.value)

        // 연타 — 잠금 중이라 무시
        model.pressAction()
        assertEquals(Accessory.PARTY_HAT, store.equipped)
        run(model, WardrobeModel.ACTION_LOCK_SEC - 0.05)
        model.pressAction()
        assertEquals(Accessory.PARTY_HAT, store.equipped, "0.4초 전에는 그대로")

        run(model, 0.07)
        assertEquals(0.0, model.actionLock)
        model.pressAction()
        assertNull(store.equipped)
        assertTrue(store.owns(Accessory.PARTY_HAT))
        assertEquals(Accessory.PARTY_HAT, model.selected, "카드는 골라진 채 — [쓰기]로 바로 되돌릴 수 있음")
        assertEquals(WardrobeModel.Action.Wear, model.action)
        assertNull(model.previewWearing, "벗기 → 큰 구구는 맨머리")

        // 잠금이 풀린 뒤 다시 쓰기 → 씀
        run(model, WardrobeModel.ACTION_LOCK_SEC + 0.02)
        model.pressAction()
        assertEquals(Accessory.PARTY_HAT, store.equipped)
        assertEquals(Accessory.PARTY_HAT, model.previewWearing)
        assertEquals(12, store.stars, "가진 것을 다시 쓰는 데 별은 안 씀")
    }

    /** 다른 장신구를 써 보는 동안은 그것, 쓰기를 누르면 저장된 모습으로 돌아옴 */
    @Test fun tryOnOtherWhileWearingThenWear() {
        val store = makeStore(200)
        val model = WardrobeModel(store, strings)
        model.select(Accessory.RIBBON)
        model.pressAction()   // 리본 사서 씀
        run(model, WardrobeModel.ACTION_LOCK_SEC + 0.02)
        model.select(Accessory.FLOWER_CROWN)
        model.pressAction()   // 화관 사서 씀
        run(model, WardrobeModel.ACTION_LOCK_SEC + 0.02)
        assertEquals(Accessory.FLOWER_CROWN, store.equipped)

        model.select(Accessory.RIBBON)
        assertEquals(Accessory.RIBBON, model.previewWearing, "써 보는 중")
        assertEquals(WardrobeModel.Action.Wear, model.action)
        assertEquals(Accessory.FLOWER_CROWN, store.equipped)
        model.pressAction()
        assertEquals(Accessory.RIBBON, store.equipped)
        assertEquals(Accessory.RIBBON, model.previewWearing)
        assertEquals(200 - 15 - 30, store.stars)
    }

    /** 다시 열면(새 모델) 쓰고 있는 것이 골라진 채, 큰 구구는 저장된 모습 */
    @Test fun reopenSelectsEquipped() {
        val store = makeStore(62)
        val first = WardrobeModel(store, strings)
        first.select(Accessory.RIBBON)
        first.pressAction()

        val reopened = WardrobeModel(store, strings)
        assertEquals(Accessory.RIBBON, reopened.selected)
        assertFalse(reopened.tryingOn)
        assertEquals(Accessory.RIBBON, reopened.previewWearing)
        assertEquals(WardrobeModel.Action.TakeOff, reopened.action)
        assertEquals(0.0, reopened.actionLock, "새로 열면 잠금 없음")
        assertEquals(62 - 15, reopened.starsBadge.value)
    }

    /** 영어 버튼 문구 — 모자란 별이 1개면 단수 */
    @Test fun needMoreStarsUsesPluralForms() {
        val store = makeStore(14)
        val model = WardrobeModel(store, TestStrings(AppLanguage.EN))
        model.select(Accessory.RIBBON)
        assertEquals("Collect 1 more star", model.actionTitle)
        model.select(Accessory.FLOWER_CROWN)
        assertEquals("Collect 16 more stars", model.actionTitle)
    }
}
