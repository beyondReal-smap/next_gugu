package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.games.DailyMissionPlan
import site.smap.gugudan.core.games.Mission
import site.smap.gugudan.core.games.MissionBoard
import site.smap.gugudan.core.games.MissionItem
import site.smap.gugudan.core.games.MissionKind
import site.smap.gugudan.core.games.MissionText
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.PlayRewards
import site.smap.gugudan.store.PlayRewardsDebug
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 오늘의 미션 + 보물상자 + 이번 주 도장 — iOS DailyMissionsTests.swift 와 같은 의미 (.claude/plans/daily-missions.md 성공 기준 1).
 * 계획(결정적·3개·풀 안·이틀 연속 다름), recordRound 누적·날짜 넘김·max·새로 완료·allDone 한 번, openChest, 구버전·잘못된 저장본.
 * 기대값은 계획서 공식(n = 1970-01-01 부터 일 수)으로 손으로 셈 — 2026-11-23(월) n=20780: 계단 20·몬스터 10·연속 5,
 * 2026-11-24(화) n=20781: 계단 10·몬스터 5·별 15, 2026-11-25(수) n=20782: 계단 15·몬스터 5·연속 7. 나눗셈은 내림(1970 년 전도 풀 안).
 */
class DailyMissionsTests {

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        val quarantined = mutableMapOf<String, MutableList<String>>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) {
            quarantined.getOrPut(key) { mutableListOf() }.add(raw)
        }
    }

    private val playKey = "gugu.test.play"
    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = LocalDate.of(2026, 11, 24)
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeStore(storage: MemoryRewardsStorage = MemoryRewardsStorage(), debug: PlayRewardsDebug? = null) =
        PlayRewardsStore(storage, key = playKey, adventureKey = "gugu.test.adventure", debug = debug)

    private fun saved(storage: MemoryRewardsStorage): PlayRewards =
        json.decodeFromString(PlayRewards.serializer(), assertNotNull(storage.values[playKey]))

    // MARK: 계획

    /** 날짜 문자열만으로 정해진다 — 계획서 공식의 값 그대로, 같은 날은 몇 번 불러도 같다 */
    @Test fun planMatchesFormula() {
        assertEquals(
            listOf(Mission(MissionKind.STAIRS_STEPS, 20), Mission(MissionKind.MONSTERS_DEFEATED, 10), Mission(MissionKind.BEST_STREAK, 5)),
            DailyMissionPlan.missions("2026-11-23"),
        )
        assertEquals(
            listOf(Mission(MissionKind.STAIRS_STEPS, 10), Mission(MissionKind.MONSTERS_DEFEATED, 5), Mission(MissionKind.STARS_EARNED, 15)),
            DailyMissionPlan.missions("2026-11-24"),
        )
        assertEquals(
            listOf(Mission(MissionKind.STAIRS_STEPS, 10), Mission(MissionKind.MONSTERS_DEFEATED, 5), Mission(MissionKind.BEST_STREAK, 5)),
            DailyMissionPlan.missions("1970-01-01"),
        )
        // n = -1 — 내림 나눗셈(iOS 와 같음): 계단 [-1 mod 3 = 2] 20, 몬스터 [floor(-1/3) mod 3 = 2] 10, 홀수 → 별 [floor(-1/2) mod 2 = 1] 25
        assertEquals(
            listOf(Mission(MissionKind.STAIRS_STEPS, 20), Mission(MissionKind.MONSTERS_DEFEATED, 10), Mission(MissionKind.STARS_EARNED, 25)),
            DailyMissionPlan.missions("1969-12-31"),
        )
        assertEquals(DailyMissionPlan.missions("2026-11-24"), DailyMissionPlan.missions("2026-11-24"))
    }

    /** 2년 치: 늘 3개, 계단·몬스터·(연속|별) 순서, 목표는 풀 안, 이틀 연속 같은 미션 묶음은 없다 */
    @Test fun planAlwaysThreeFromPoolsAndChangesDaily() {
        var day = LocalDate.of(2026, 1, 1)
        var previous: List<Mission>? = null
        repeat(730) {
            val key = "%04d-%02d-%02d".format(day.year, day.monthValue, day.dayOfMonth)
            val m = DailyMissionPlan.missions(key)
            assertEquals(3, m.size, key)
            assertEquals(MissionKind.STAIRS_STEPS, m[0].kind, key)
            assertTrue(m[0].target in setOf(10, 15, 20), key)
            assertEquals(MissionKind.MONSTERS_DEFEATED, m[1].kind, key)
            assertTrue(m[1].target in setOf(5, 8, 10), key)
            when (m[2].kind) {
                MissionKind.BEST_STREAK -> assertTrue(m[2].target in setOf(5, 7), key)
                MissionKind.STARS_EARNED -> assertTrue(m[2].target in setOf(15, 25), key)
                else -> error("셋째 미션은 연속·별: $key ${m[2]}")
            }
            if (previous != null) assertNotEquals(previous, m, key)
            previous = m
            day = day.plusDays(1)
        }
    }

    /** 날짜 형식이 틀리거나 없는 날은 던진다 */
    @Test fun planRejectsBadDays() {
        for (bad in listOf("2026-1-5", "2026/11/24", "2026-02-30", "", "+2026-11-24")) {
            assertFailsWith<IllegalArgumentException>(bad) { DailyMissionPlan.missions(bad) }
        }
    }

    // MARK: recordRound

    /** 계단·몬스터·별은 판마다 더하고, 연속은 오늘 판들 중 최고(더하지 않음) */
    @Test fun recordRoundAccumulatesAndKeepsMaxStreak() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        store.recordRound(PlayGame.STAIRS, score = 4, stars = 5, bestStreak = 3, now = tuesday)
        store.recordRound(PlayGame.STAIRS, score = 3, stars = 2, bestStreak = 2, now = tuesday)
        store.recordRound(PlayGame.MONSTER, score = 2, stars = 4, bestStreak = 1, now = tuesday)

        val log = assertNotNull(saved(storage).missionLog)
        assertEquals("2026-11-24", log.day)
        assertEquals(7, log.stairsSteps)
        assertEquals(2, log.monstersDefeated)
        assertEquals(11, log.starsEarned)
        assertEquals(3, log.bestStreak)
        // 화 = 계단 10·몬스터 5·별 15
        assertEquals(listOf(7, 2, 11), store.missionBoard(tuesday).items.map { it.progress })
        assertEquals(listOf(false, false, false), store.missionBoard(tuesday).items.map { it.done })
    }

    /** 새로 목표를 넘은 미션만 미션 순서대로 돌려주고, 3개가 처음 다 된 판에서만 allMissionsDone */
    @Test fun recordRoundReportsNewlyCompletedAndAllDoneOnce() {
        val store = makeStore()
        // 화: 계단 10·몬스터 5·별 15
        val first = store.recordRound(PlayGame.STAIRS, score = 12, stars = 16, bestStreak = 12, now = tuesday)
        assertEquals(listOf(Mission(MissionKind.STAIRS_STEPS, 10), Mission(MissionKind.STARS_EARNED, 15)), first.missionsCompleted)
        assertFalse(first.allMissionsDone)

        // 이미 넘은 미션은 다시 알리지 않는다
        val second = store.recordRound(PlayGame.STAIRS, score = 3, stars = 3, bestStreak = 3, now = tuesday)
        assertEquals(emptyList(), second.missionsCompleted)
        assertFalse(second.allMissionsDone)

        val third = store.recordRound(PlayGame.MONSTER, score = 5, stars = 5, bestStreak = 5, now = tuesday)
        assertEquals(listOf(Mission(MissionKind.MONSTERS_DEFEATED, 5)), third.missionsCompleted)
        assertTrue(third.allMissionsDone)

        val fourth = store.recordRound(PlayGame.MONSTER, score = 9, stars = 9, bestStreak = 9, now = tuesday)
        assertEquals(emptyList(), fourth.missionsCompleted)
        assertFalse(fourth.allMissionsDone)

        val board = store.missionBoard(tuesday)
        assertEquals(listOf(10, 5, 15), board.items.map { it.progress })   // 목표에서 자름
        assertTrue(board.allDone)
        assertTrue(board.chestReady)
        assertEquals(3, board.doneCount)
    }

    /** 날이 바뀌면 어제 진행도는 0 으로 보이고, 오늘 첫 판이 새 log 로 바꾼다(어제 값을 이어 더하지 않음) */
    @Test fun newDayStartsFromZero() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        store.recordRound(PlayGame.STAIRS, score = 18, stars = 20, bestStreak = 18, now = monday)
        assertEquals(listOf(18, 0, 5), store.missionBoard(monday).items.map { it.progress })   // 월: 계단 20·몬스터 10·연속 5

        assertEquals(listOf(0, 0, 0), store.missionBoard(tuesday).items.map { it.progress })
        assertFalse(store.missionBoard(tuesday).chestOpened)

        val record = store.recordRound(PlayGame.STAIRS, score = 9, stars = 1, bestStreak = 2, now = tuesday)
        assertEquals(emptyList(), record.missionsCompleted)   // 18+9 가 아니라 9 → 계단 10 미달
        val log = assertNotNull(saved(storage).missionLog)
        assertEquals("2026-11-24", log.day)
        assertEquals(9, log.stairsSteps)
        assertEquals(2, log.bestStreak)
    }

    // MARK: 보물상자

    /** 준비 전 열기는 던지고 아무것도 바꾸지 않는다. 다 하면 별 +10·연 상태·오늘 도장, 두 번째는 던진다 */
    @Test fun openChestOnlyWhenReadyOnce() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        store.recordRound(PlayGame.STAIRS, score = 10, stars = 15, bestStreak = 10, now = tuesday)
        val before = store.rewards
        assertFailsWith<IllegalStateException> { store.openChest(tuesday) }   // 몬스터 미션이 남음
        assertEquals(before, store.rewards)

        store.recordRound(PlayGame.MONSTER, score = 5, stars = 5, bestStreak = 5, now = tuesday)
        val starsBefore = store.stars
        assertEquals(10, store.openChest(tuesday))
        assertEquals(starsBefore + 10, store.stars)
        val board = store.missionBoard(tuesday)
        assertTrue(board.chestOpened)
        assertFalse(board.chestReady)
        assertEquals(listOf("2026-11-24"), saved(storage).chestDays)
        assertEquals(starsBefore + 10, saved(storage).stars)

        assertFailsWith<IllegalStateException> { store.openChest(tuesday) }
        assertEquals(starsBefore + 10, store.stars)

        // 다시 열어도(앱 재실행) 연 상태·도장이 남는다
        val reopened = makeStore(storage)
        assertTrue(reopened.missionBoard(tuesday).chestOpened)
        assertFailsWith<IllegalStateException> { reopened.openChest(tuesday) }
    }

    /** 이번 주 도장 = 월~일 7칸, 상자를 연 날이 찍히고 오늘 표시. 다음 날엔 새 상자(진행도 0) */
    @Test fun weekStampsMondayToSunday() {
        val store = makeStore()
        fun complete(day: LocalDate) {
            store.recordRound(PlayGame.STAIRS, score = 20, stars = 25, bestStreak = 7, now = day)
            store.recordRound(PlayGame.MONSTER, score = 10, stars = 0, bestStreak = 0, now = day)
            store.openChest(day)
        }
        complete(monday)
        complete(wednesday)

        val week = store.missionBoard(wednesday).week
        assertEquals((23..29).map { LocalDate.of(2026, 11, it) }, week.map { it.date })
        assertEquals(listOf(true, false, true, false, false, false, false), week.map { it.stamped })
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.isToday })

        // 일요일에 봐도 같은 주, 다음 월요일은 새 주(도장 없음)
        assertEquals(week.map { it.date }, store.missionBoard(LocalDate.of(2026, 11, 29)).week.map { it.date })
        val nextWeek = store.missionBoard(LocalDate.of(2026, 11, 30)).week
        assertEquals(LocalDate.of(2026, 11, 30), nextWeek.first().date)
        assertTrue(nextWeek.none { it.stamped })
        assertFalse(store.missionBoard(LocalDate.of(2026, 11, 26)).chestOpened)
    }

    /** 상자 연 날은 최근 14개만 남는다(오름차순) */
    @Test fun chestDaysKeepLatestFourteen() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        val start = LocalDate.of(2026, 11, 1)
        repeat(16) { i ->
            val day = start.plusDays(i.toLong())
            store.recordRound(PlayGame.STAIRS, score = 20, stars = 25, bestStreak = 7, now = day)
            store.recordRound(PlayGame.MONSTER, score = 10, stars = 0, bestStreak = 0, now = day)
            store.openChest(day)
        }
        val days = saved(storage).chestDays
        assertEquals(14, days.size)
        assertEquals("2026-11-03", days.first())
        assertEquals("2026-11-16", days.last())
    }

    // MARK: 저장본

    /** 2.9.0 전 저장본(미션 필드 없음)은 그대로 읽히고 오늘 진행도 0 — 격리하지 않음 */
    @Test fun legacySaveReadsWithoutMissions() {
        val storage = MemoryRewardsStorage()
        storage.values[playKey] = """{"version":1,"stars":42,"ownedMask":0,"stairsBest":7,"adventureMigrated":true}"""
        val store = makeStore(storage)

        assertEquals(42, store.stars)
        assertNull(store.rewards.missionLog)
        assertEquals(emptyList(), store.rewards.chestDays)
        assertEquals(listOf(0, 0, 0), store.missionBoard(tuesday).items.map { it.progress })
        assertTrue(storage.quarantined.isEmpty())
    }

    /** 날짜 형식이 틀리거나 음수 진행도·잘못된 도장 목록은 조용히 고치지 않고 저장본째 격리한다 */
    @Test fun badMissionFieldsAreQuarantined() {
        val cases = listOf(
            """{"stars":5,"adventureMigrated":true,"missionLog":{"day":"2026/11/24","stairsSteps":3}}""",
            """{"stars":5,"adventureMigrated":true,"missionLog":{"day":"2026-02-30"}}""",
            """{"stars":5,"adventureMigrated":true,"missionLog":{"day":"2026-11-24","stairsSteps":-1}}""",
            """{"stars":5,"adventureMigrated":true,"chestDays":["2026-11-24","24-11-2026"]}""",
            """{"stars":5,"adventureMigrated":true,"chestDays":["2026-11-24","2026-11-23"]}""",
            """{"stars":5,"adventureMigrated":true,"chestDays":["2026-11-24","2026-11-24"]}""",
        )
        for (raw in cases) {
            val storage = MemoryRewardsStorage()
            storage.values[playKey] = raw
            val store = makeStore(storage)

            assertEquals(0, store.stars, raw)
            assertEquals<List<String>?>(listOf(raw), storage.quarantined[playKey], raw)
        }
    }

    // MARK: 문구

    /** 결과 창 한 줄 — 다 됨 > 새로 완료(첫 미션) > 없음 */
    @Test fun resultNoteText() {
        val strings = TestStrings(AppLanguage.KO)
        val stairs = Mission(MissionKind.STAIRS_STEPS, 15)
        assertEquals("미션 완료! 계단 15칸 오르기", MissionText.resultNote(strings, listOf(stairs, Mission(MissionKind.BEST_STREAK, 7)), false))
        assertEquals("미션 완료! 한 판에 7번 연속 맞히기", MissionText.resultNote(strings, listOf(Mission(MissionKind.BEST_STREAK, 7)), false))
        assertEquals("오늘의 미션 완료! 홈에서 보물상자를 열어 봐요", MissionText.resultNote(strings, listOf(stairs), true))
        assertEquals("", MissionText.resultNote(strings, emptyList(), false))
        assertEquals("몬스터 8마리 물리치기", MissionText.title(strings, Mission(MissionKind.MONSTERS_DEFEATED, 8)))
        assertEquals("게임에서 별 25개 모으기", MissionText.title(strings, Mission(MissionKind.STARS_EARNED, 25)))
    }

    // MARK: DEBUG 스크린숏 훅

    /** debug.gugu.play_missions=half|ready|opened — 오늘 미션을 채워 심는다. 잘못된 값은 멈춘다 */
    @Test fun debugMissionsHook() {
        fun store(mode: String) = makeStore(debug = PlayRewardsDebug.from { if (it == "debug.gugu.play_missions") mode else null })

        val half = store("half").missionBoard()
        assertEquals(listOf(true, false, false), half.items.map { it.done })
        assertEquals(half.items[1].mission.target / 2, half.items[1].progress)
        assertEquals(0, half.items[2].progress)

        val ready = store("ready").missionBoard()
        assertTrue(ready.chestReady)
        assertFalse(ready.week.single { it.isToday }.stamped)

        val opened = store("opened").missionBoard()
        assertTrue(opened.allDone)
        assertTrue(opened.chestOpened)
        assertTrue(opened.week.single { it.isToday }.stamped)

        // opened 로 심은 저장본 위에 ready 를 덮으면 오늘 도장도 빠진다(안 연 상태와 맞춤)
        val storage = MemoryRewardsStorage()
        makeStore(storage, PlayRewardsDebug.from { if (it == "debug.gugu.play_missions") "opened" else null })
        val over = makeStore(storage, PlayRewardsDebug.from { if (it == "debug.gugu.play_missions") "ready" else null }).missionBoard()
        assertTrue(over.chestReady)
        assertFalse(over.week.single { it.isToday }.stamped)

        assertFailsWith<IllegalArgumentException> { PlayRewardsDebug.from { if (it == "debug.gugu.play_missions") "full" else null } }
    }

    // MARK: 미션 구슬 → 열 게임 (계획서 2-7)

    /** 계단 → 계단, 몬스터 → 몬스터, 세 번째(연속·코인) → 아직 안 끝낸 첫 게임(계단→몬스터), 둘 다 끝났으면 계단 */
    @Test fun missionBoardGameSelection() {
        fun board(stairsDone: Boolean, monsterDone: Boolean, thirdKind: MissionKind) = MissionBoard(
            items = listOf(
                MissionItem(Mission(MissionKind.STAIRS_STEPS, 10), if (stairsDone) 10 else 0, stairsDone),
                MissionItem(Mission(MissionKind.MONSTERS_DEFEATED, 5), if (monsterDone) 5 else 0, monsterDone),
                MissionItem(Mission(thirdKind, 5), 0, false),
            ),
            chestOpened = false,
            week = emptyList(),
        )

        for (third in listOf(MissionKind.BEST_STREAK, MissionKind.STARS_EARNED)) {
            val none = board(stairsDone = false, monsterDone = false, thirdKind = third)
            assertEquals(PlayGame.STAIRS, none.game(MissionKind.STAIRS_STEPS))
            assertEquals(PlayGame.MONSTER, none.game(MissionKind.MONSTERS_DEFEATED))
            assertEquals(PlayGame.STAIRS, none.game(third))
            assertEquals(PlayGame.STAIRS, none.game(none.items[2]))

            val stairsOnly = board(stairsDone = true, monsterDone = false, thirdKind = third)
            assertEquals(PlayGame.STAIRS, stairsOnly.game(MissionKind.STAIRS_STEPS))
            assertEquals(PlayGame.MONSTER, stairsOnly.game(third))

            val monsterOnly = board(stairsDone = false, monsterDone = true, thirdKind = third)
            assertEquals(PlayGame.MONSTER, monsterOnly.game(MissionKind.MONSTERS_DEFEATED))
            assertEquals(PlayGame.STAIRS, monsterOnly.game(third))

            val both = board(stairsDone = true, monsterDone = true, thirdKind = third)
            assertEquals(PlayGame.STAIRS, both.game(third))
        }
    }
}
