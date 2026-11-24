package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.Altimeter
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.SkyZone
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.core.games.StarRules
import site.smap.gugudan.core.games.Wardrobe
import site.smap.gugudan.core.games.WardrobeError
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A·B 보상·수집 규칙 — 별 코인·연속 정답·높이 구간(1차), 몬스터 도감·옷장·고도계(2차).
 * Unity 시험판 RewardRulesTests·Round2RulesTests 이식 + Round2PlayTests 의 도감 순환(누적 처치 기준) 검사.
 * iOS GameRewardRulesTests.swift 와 같은 기대값 — 한쪽을 바꾸면 양쪽을 함께 바꿀 것.
 * 원본에서 예외를 단언하던 잘못된 인자(연속 0·음수 높이)는 Swift 에서 precondition 이라 빠졌지만,
 * Kotlin 은 require 라 예외로 확인할 수 있어 되살린다(없는 종은 enum 이라 만들 수 없음).
 */
class GameRewardRulesTests {

    private fun rng(seed: Int): () -> Double = SeededRandom.mulberry32(seed.toUInt())

    // 별 코인·높이 구간 (원본 RewardRulesTests)

    @Test fun starRulesOnePerCorrectPlusBonusEveryFifthInARow() {
        assertEquals(1, StarRules.forCorrect(1))
        assertEquals(1, StarRules.forCorrect(4))
        assertEquals(1 + StarRules.COMBO_BONUS, StarRules.forCorrect(5))
        assertEquals(1, StarRules.forCorrect(6))
        assertEquals(1 + StarRules.COMBO_BONUS, StarRules.forCorrect(10))
        assertFalse(StarRules.isComboMilestone(0))
        assertEquals(
            listOf(1, 5, 2, 5),
            listOf(StarRules.PER_CORRECT, StarRules.COMBO_EVERY, StarRules.COMBO_BONUS, StarRules.TREASURE_BONUS),
        )
        assertFailsWith<IllegalArgumentException>("정답 직후 연속 0") { StarRules.forCorrect(0) }
    }

    @Test fun skyZonesChangeAtTenTwentyFiveForty() {
        assertEquals(SkyZone.MEADOW, SkyZone.at(0))
        assertEquals(SkyZone.MEADOW, SkyZone.at(9))
        assertEquals(SkyZone.SKY_VILLAGE, SkyZone.at(10))
        assertEquals(SkyZone.SKY_VILLAGE, SkyZone.at(24))
        assertEquals(SkyZone.RAINBOW, SkyZone.at(25))
        assertEquals(SkyZone.RAINBOW, SkyZone.at(39))
        assertEquals(SkyZone.SPACE, SkyZone.at(40))
        assertEquals(SkyZone.SPACE, SkyZone.at(500))
        assertEquals(SkyZone.MEADOW, SkyZone.at(-3), "출발 아래(카메라가 아래를 비출 때)")
        // 구간 순서 = 화면 현지화·그림 순서 (원본 Name() 대신 UI 층에서 이름을 붙인다)
        assertEquals(listOf(SkyZone.MEADOW, SkyZone.SKY_VILLAGE, SkyZone.RAINBOW, SkyZone.SPACE), SkyZone.entries.toList())
        assertEquals(listOf(0, 10, 25, 40), SkyZone.entries.map { it.startHeight })
    }

    private fun stairsCorrect(s: StairsSession) {
        assertEquals(true, s.jump(s.current.correctLane).attempt?.correct)
    }

    private fun stairsWrong(s: StairsSession) {
        val lane = (s.current.correctLane + 1) % StairsSession.LANES
        assertEquals(false, s.jump(lane).attempt?.correct)
        s.tick(RoundClock.REVEAL_SEC + 0.01)  // 공개가 끝나야 다시 풀 수 있음
    }

    @Test fun stairsStreakAndStarsComboBonusAndResetOnWrong() {
        val s = StairsSession(topStep = 9, order = ProblemOrder.SEQUENTIAL, firstTable = 3, random = rng(21))
        repeat(5) { stairsCorrect(s) }
        assertEquals(5, s.streak)
        assertEquals(5 + StarRules.COMBO_BONUS, s.stars, "5연속에서 보너스")
        stairsWrong(s)
        assertEquals(0, s.streak)
        assertEquals(5, s.bestStreak)
        assertEquals(7, s.stars, "오답은 별을 빼지 않음")
        stairsCorrect(s)
        assertEquals(1, s.streak)
        assertEquals(8, s.stars)
    }

    /** 꼭대기(×topStep) 보물 +5 — topStep 마다: 정답 수 + 5연속 보너스들 + 보물 */
    @Test fun stairsTreasureGivesFiveStars() {
        for (topStep in Problems.MAX_B_OPTIONS) {
            val s = StairsSession(topStep = topStep, order = ProblemOrder.SEQUENTIAL, firstTable = 4, random = rng(22))
            repeat(topStep) { stairsCorrect(s) }
            assertEquals(1, s.treasures)
            assertEquals(
                topStep + (topStep / 5) * StarRules.COMBO_BONUS + StarRules.TREASURE_BONUS, s.stars, "topStep $topStep",
            )
        }
    }

    private fun monsterCorrect(s: MonsterSession) {
        val q = s.current
        s.tap(q.pairA)
        assertEquals(true, s.tap(q.pairB).attempt?.correct)
    }

    private fun monsterWrong(s: MonsterSession) {
        val q = s.current
        s.tap(q.pairA)
        for (i in 0 until MonsterSession.CARD_COUNT) {
            if (i == q.pairA || q.cards[q.pairA] * q.cards[i] == q.target.a * q.target.b) continue
            assertEquals(false, s.tap(i).attempt?.correct)
            s.tick(RoundClock.REVEAL_SEC + 0.01)
            return
        }
        fail("오답 카드가 없음")
    }

    @Test fun monsterStreakAndStarsComboBonusAndResetOnWrong() {
        val s = MonsterSession(ProblemOrder.RANDOM, random = rng(23))
        repeat(6) { monsterCorrect(s) }
        assertEquals(6, s.streak)
        assertEquals(6 + StarRules.COMBO_BONUS, s.stars)
        monsterWrong(s)
        assertEquals(0, s.streak)
        assertEquals(6, s.bestStreak)
        assertEquals(8, s.stars)
    }

    @Test fun selectAndDeselectDoNotTouchStreakOrStars() {
        val s = MonsterSession(ProblemOrder.RANDOM, random = rng(24))
        monsterCorrect(s)
        val q = s.current
        s.tap(q.pairA)
        s.tap(q.pairA)  // 고르기 취소
        assertEquals(1, s.streak)
        assertEquals(1, s.stars)
    }

    // 몬스터 도감 (원본 Round2RulesTests)

    @Test fun dexFirstFourUnlockedRestUnlockAt10_25_45_70() {
        assertEquals(4, MonsterDex.unlockedCount(0))
        assertEquals(4, MonsterDex.unlockedCount(9))
        assertEquals(5, MonsterDex.unlockedCount(10))
        assertEquals(6, MonsterDex.unlockedCount(25))
        assertEquals(7, MonsterDex.unlockedCount(45))
        assertEquals(7, MonsterDex.unlockedCount(69))
        assertEquals(MonsterDex.KINDS, MonsterDex.unlockedCount(70))
        assertEquals(MonsterKind.PINK, MonsterDex.newlyUnlocked(before = 9, after = 10))
        assertNull(MonsterDex.newlyUnlocked(before = 10, after = 11))
        assertEquals(MonsterKind.GOLD, MonsterDex.newlyUnlocked(before = 69, after = 70))
        assertEquals(listOf(0, 0, 0, 0, 10, 25, 45, 70), MonsterKind.entries.map { MonsterDex.unlockAt(it) })
        for (k in 1 until MonsterDex.KINDS) {
            assertTrue(
                MonsterDex.unlockAt(MonsterKind.entries[k]) >= MonsterDex.unlockAt(MonsterKind.entries[k - 1]),
                "해금 기준은 앞 종보다 작지 않음",
            )
        }
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64, 128), MonsterKind.entries.map { it.bit }, "저장 비트 위치 고정")
        assertFailsWith<IllegalArgumentException>("음수 누적 처치") { MonsterDex.unlockedCount(-1) }
    }

    @Test fun dexNewPlayerMeetsFirstFourInOrderThenCycles() {
        var total = 0
        var seen = 0
        var prev: MonsterKind? = null
        val order = mutableListOf<MonsterKind>()
        for (i in 0 until 8) {
            val kind = MonsterDex.nextKind(totalDefeated = total, seenMask = seen, spawnIndex = i, previous = prev)
            order.add(kind)
            seen = MonsterDex.markSeen(kind, seen)  // 모두 물리쳤다고 가정
            total += 1
            prev = kind
        }
        val firstFour = listOf(MonsterKind.GREEN, MonsterKind.ORANGE, MonsterKind.PURPLE, MonsterKind.SKY)
        assertEquals(firstFour, order.subList(0, 4), "처음 4종을 차례로 만남")
        assertEquals(firstFour, order.subList(4, 8), "다 만난 뒤에는 해금된 4종을 돌림")
        assertEquals(4, MonsterDex.seenCount(seen))
    }

    @Test fun dexNewlyUnlockedKindAppearsNextAndNeverTwiceInARow() {
        // 9마리 물리쳐 4종을 다 만난 상태 → 10번째 처치로 분홍 해금 → 다음 몬스터는 분홍
        var seen = 0b1111
        assertEquals(
            MonsterKind.PINK,
            MonsterDex.nextKind(totalDefeated = 10, seenMask = seen, spawnIndex = 3, previous = MonsterKind.SKY),
        )
        // 분홍을 물리친 뒤: 5종 순환. spawnIndex 9 → 9 % 5 = 4(분홍)인데 바로 앞이 분홍이면 다음 종
        seen = MonsterDex.markSeen(MonsterKind.PINK, seen)
        assertEquals(
            MonsterKind.GREEN,
            MonsterDex.nextKind(totalDefeated = 11, seenMask = seen, spawnIndex = 9, previous = MonsterKind.PINK),
        )
        // 오래 돌려도 같은 종이 연달아 나오지 않음(해금이 바뀌는 순간 포함)
        var prev: MonsterKind? = null
        seen = 0
        for (i in 0 until 200) {
            val total = i
            val kind = MonsterDex.nextKind(totalDefeated = total, seenMask = seen, spawnIndex = i, previous = prev)
            assertTrue(MonsterDex.isUnlocked(kind, total), "${i}번째: 해금 안 된 종 $kind")
            if (prev != null) assertNotEquals(prev, kind, "${i}번째: 같은 종 연속")
            seen = MonsterDex.markSeen(kind, seen)
            prev = kind
        }
        assertEquals(MonsterDex.KINDS, MonsterDex.seenCount(seen), "200마리면 8종을 모두 만남")
    }

    // 도감 순환 — 게임 흐름 (원본 Round2PlayTests)

    /**
     * 게임 층이 할 일을 그대로 흉내: 새 몬스터(monstersIssued 가 바뀜)마다 누적 처치 수로 종을 고르고,
     * 처치하는 순간 누적 처치·만난 종을 기록한다(도감은 처치 순간 저장 — 시험판 그대로)
     */
    private class DexPlay(var total: Int, var seen: Int) {
        var kind: MonsterKind? = null
        var unlockedNow: MonsterKind? = null

        /** 판 시작 — 바로 앞 몬스터 없음 */
        fun startRound(): MonsterKind {
            kind = null
            return spawn()
        }

        fun spawn(): MonsterKind {
            val next = MonsterDex.nextKind(totalDefeated = total, seenMask = seen, spawnIndex = total, previous = kind)
            kind = next
            return next
        }

        /** 지금 몬스터를 물리침 → 다음 몬스터 종 */
        fun defeat(s: MonsterSession): MonsterKind {
            val current = kind ?: fail("몬스터가 없음")
            val issued = s.monstersIssued
            val q = s.current
            s.tap(q.pairA)
            assertEquals(true, s.tap(q.pairB).attempt?.correct)
            assertEquals(issued + 1, s.monstersIssued, "새 몬스터")
            unlockedNow = MonsterDex.newlyUnlocked(before = total, after = total + 1)
            total += 1
            seen = MonsterDex.markSeen(current, seen)
            return spawn()
        }
    }

    @Test fun tenthDefeatUnlocksPinkItAppearsNextThenJoinsDex() {
        val dex = DexPlay(total = 9, seen = 0b1111)
        val s = MonsterSession(ProblemOrder.RANDOM, random = rng(77))
        assertEquals(MonsterKind.entries[9 % 4], dex.startRound(), "4종을 다 만났으면 누적 처치 수 차례로(9마리 → 1번)")
        assertFalse(MonsterDex.isUnlocked(MonsterKind.PINK, dex.total))
        assertEquals(MonsterKind.PINK, dex.defeat(s), "10마리째 → 분홍 해금, 새 몬스터가 바로 등장")
        assertEquals(MonsterKind.PINK, dex.unlockedNow)
        assertFalse(MonsterDex.isSeen(MonsterKind.PINK, dex.seen), "아직 못 물리침 = 실루엣")
        dex.defeat(s)
        assertTrue(MonsterDex.isSeen(MonsterKind.PINK, dex.seen), "도감에 등록")
        assertEquals(11, dex.total)
    }

    @Test fun allEightSeenMonstersCycleNoSameKindTwiceInARow() {
        val dex = DexPlay(total = 100, seen = 0xFF)
        val s = MonsterSession(ProblemOrder.RANDOM, random = rng(77))
        val kinds = mutableListOf(dex.startRound())
        repeat(9) { kinds.add(dex.defeat(s)) }
        for (i in 1 until kinds.size) assertNotEquals(kinds[i - 1], kinds[i])
        assertEquals(MonsterDex.KINDS, kinds.toSet().size, "10마리 안에 8종 모두")
    }

    /** 한 판에 3마리씩만 잡는 아이: 판마다 0번부터 다시 돌면 0~3번만 보게 됨 → 누적 처치 수로 이어 돈다 */
    @Test fun shortRoundsCycleContinuesAcrossRoundsAllEightAppear() {
        val dex = DexPlay(total = 100, seen = 0xFF)
        val kinds = mutableSetOf<MonsterKind>()
        for (round in 0 until 3) {
            val s = MonsterSession(ProblemOrder.RANDOM, random = rng(77 + round))
            kinds.add(dex.startRound())
            repeat(3) { kinds.add(dex.defeat(s)) }
        }
        assertEquals(MonsterDex.KINDS, kinds.size, "짧은 판 3번에 8종 모두 만남")
    }

    // 옷장·고도계 (원본 Round2RulesTests)

    @Test fun wardrobePricesRiseBuyNeedsEnoughStarsNoDoubleBuy() {
        assertEquals(listOf(15, 30, 50, 80, 120, 200), Accessory.entries.map { it.price }, "뒤로 갈수록 비쌈")
        assertEquals(listOf(1, 2, 4, 8, 16, 32), Accessory.entries.map { it.bit }, "저장 비트 위치 고정")
        assertEquals(6, Wardrobe.COUNT)
        assertEquals(5, Wardrobe.shortfall(Accessory.RIBBON, balance = 10))
        assertEquals(0, Wardrobe.shortfall(Accessory.RIBBON, balance = 15))
        val lack = assertFailsWith<WardrobeError.NotEnoughStars> {
            Wardrobe.buy(Accessory.RIBBON, balance = 14, ownedMask = 0)
        }
        assertEquals(WardrobeError.NotEnoughStars(Accessory.RIBBON, lack = 1), lack)
        assertEquals(0, Wardrobe.buy(Accessory.RIBBON, balance = 15, ownedMask = 0).balance, "딱 맞으면 잔고 0 으로 살 수 있음")

        val (balance, owned) = Wardrobe.buy(Accessory.RIBBON, balance = 40, ownedMask = 0)
        assertEquals(25, balance)
        assertTrue(Wardrobe.owns(Accessory.RIBBON, owned))
        assertFalse(Wardrobe.owns(Accessory.CROWN, owned))
        val again = assertFailsWith<WardrobeError.AlreadyOwned>("이미 가진 것") {
            Wardrobe.buy(Accessory.RIBBON, balance = balance, ownedMask = owned)
        }
        assertEquals(WardrobeError.AlreadyOwned(Accessory.RIBBON), again)
        // 하나 더 사면 가진 목록에 비트가 더해짐
        val (left, both) = Wardrobe.buy(Accessory.CROWN, balance = 225, ownedMask = owned)
        assertEquals(25, left)
        assertEquals(Accessory.RIBBON.bit or Accessory.CROWN.bit, both)
    }

    @Test fun altimeterShowsMoonFromStartGrowsInStepsOfFive() {
        assertEquals(45, Altimeter.top(climbed = 0, best = 0), "처음부터 달나라(40)까지 보임")
        assertEquals(45, Altimeter.top(climbed = 37, best = 0))
        assertEquals(50, Altimeter.top(climbed = 38, best = 0), "38 + 여유 8 = 46 → 5칸 단위로 50")
        assertEquals(70, Altimeter.top(climbed = 0, best = 60), "최고 기록이 높으면 처음부터 깃발이 보이게")
        assertEquals(0.0, Altimeter.fraction(height = 0.0, top = 45.0))
        assertEquals(0.0, Altimeter.fraction(height = -2.0, top = 45.0), "출발 아래 먹구름은 막대 아래 끝")
        assertEquals(1.0, Altimeter.fraction(height = 45.0, top = 45.0))
        assertEquals(0.2, Altimeter.fraction(height = 9.0, top = 45.0), 1e-9)
        assertFailsWith<IllegalArgumentException>("음수 높이") { Altimeter.top(climbed = -1, best = 0) }
    }
}
