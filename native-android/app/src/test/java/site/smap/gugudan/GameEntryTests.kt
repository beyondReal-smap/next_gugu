package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.PushRoute
import site.smap.gugudan.features.games.Minigames
import site.smap.gugudan.store.Minigame
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.Router
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게임 진입점 — 라우터(게임·옷장), 단 시트에서 고를 수 있는 단(구구 몬스터 2~9단), 카드 배지, 알림 딥링크
 * (iOS GameEntryTests.swift 와 같은 의미).
 */
class GameEntryTests {

    private val strings = TestStrings(AppLanguage.KO)

    // MARK: 라우터

    @Test fun gamesAreStairsThenMonster() {
        assertEquals(listOf(Minigame.STAIRS, Minigame.MONSTER), Minigame.entries)
        assertEquals(listOf("구구 계단", "구구 몬스터"), Minigame.entries.map { strings.get(Minigames.nameRes(it)) })
    }

    /** 홈은 단을 정하지 않고(Default), 단 시트는 고른 단을 넘긴다. 같은 게임을 다시 열어도 새 판(새 id) */
    @Test fun routerOpensGameWithStart() {
        val router = Router()
        assertNull(router.minigame)

        router.openMinigame(Minigame.STAIRS)
        val home = assertNotNull(router.minigame)
        assertEquals(Minigame.STAIRS, home.game)
        assertEquals(MinigameStart.Default, home.start)

        router.openMinigame(Minigame.MONSTER, MinigameStart.Table(7))
        assertEquals(Minigame.MONSTER, router.minigame?.game)
        assertEquals(MinigameStart.Table(7), router.minigame?.start)

        router.openMinigame(Minigame.MONSTER, MinigameStart.Table(null))
        assertEquals(MinigameStart.Table(null), router.minigame?.start)

        val first = assertNotNull(router.minigame).id
        router.openMinigame(Minigame.MONSTER, MinigameStart.Table(null))
        assertNotEquals(first, router.minigame?.id)

        router.closeMinigame()
        assertNull(router.minigame)
    }

    /** 옷장은 게임과 따로 띄운다 */
    @Test fun routerOpensWardrobe() {
        val router = Router()
        assertFalse(router.wardrobeOpen)
        router.openWardrobe()
        assertTrue(router.wardrobeOpen)
        assertNull(router.minigame)
        router.closeWardrobe()
        assertFalse(router.wardrobeOpen)
    }

    // MARK: 단 시트 잠금

    /** 구구 계단은 2~19단 어디서나, 구구 몬스터는 2~9단만(카드가 1~9). 섞어서는 둘 다 */
    @Test fun monsterOnlySupportsTablesUpToNine() {
        for (table in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            assertTrue(Minigame.STAIRS.supports(table), "계단 ${table}단")
            assertEquals(table <= 9, Minigame.MONSTER.supports(table), "몬스터 ${table}단")
        }
        assertTrue(Minigame.STAIRS.supports(null))
        assertTrue(Minigame.MONSTER.supports(null))
        assertTrue(Minigame.MONSTER.supports(7))
        assertFalse(Minigame.MONSTER.supports(13))
    }

    /** 단 시트가 막아야 하는 조합(몬스터 13단)이 라우터까지 오면 화면 쪽 버그 — 조용히 열지 않고 실패로 알린다 */
    @Test fun routerRejectsLockedMonsterTable() {
        val router = Router()
        assertFailsWith<IllegalArgumentException> { router.openMinigame(Minigame.MONSTER, MinigameStart.Table(13)) }
        assertNull(router.minigame)
        router.openMinigame(Minigame.STAIRS, MinigameStart.Table(13))
        assertEquals(MinigameStart.Table(13), router.minigame?.start)
    }

    // MARK: 카드 배지

    /** 무료는 "10문제 체험", 이용권은 "새 게임" — 두 게임이 같은 배지를 쓴다 */
    @Test fun cardBadges() {
        assertEquals(10, PremiumConfig.MINIGAME_TRIAL_QUESTIONS)
        assertEquals("${PremiumConfig.MINIGAME_TRIAL_QUESTIONS}문제 체험", Minigames.badge(strings, isPremium = false))
        assertEquals("새 게임", Minigames.badge(strings, isPremium = true))
    }

    // MARK: 알림 딥링크

    /** 서버 허용 목록에는 게임·어드벤처 경로가 없다 — 옛 이름(adventure·play 등)이 와도 홈으로 */
    @Test fun retiredGameLinksGoHome() {
        for (link in listOf("adventure", "play", "minigame", "pop", "stairs", "monster", "wardrobe")) {
            assertEquals(PushRoute.HOME, PushRoute.parse(link), link)
            assertFalse(PushRoute.isKnown(link), link)
        }
        assertEquals(
            setOf("home", "learn", "profile", "profile/report", "paywall", "settings/notifications"),
            PushRoute.entries.map { it.link }.toSet(),
        )
    }
}
