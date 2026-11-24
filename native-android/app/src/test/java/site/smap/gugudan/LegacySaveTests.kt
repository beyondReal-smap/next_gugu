package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.Test
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.TableTally
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 어드벤처를 해 본 사용자의 학습 저장본(gugu.progress.v1) — iOS LegacySaveTests.swift 와 같은 의미.
 * modesPlayed·bestScores 에 "adventure" 가 들어 있다 — 2.9.0 에서 3D 어드벤처를 지운 뒤에도 이 저장본이 읽혀야
 * 진행도가 통째로 격리·초기화되지 않는다. 그래서 GameMode.ADVENTURE 는 남겨 두었고,
 * 누가 그 값을 지우면(GameState 디코더가 모르는 모드를 걸러 내지 않으므로) 이 테스트가 깨져 알려 준다.
 * 2.9.0 의 게임 판 기록(stairs·monster)도 같은 저장본에 함께 쌓여 다시 읽혀야 한다.
 * Persistence 는 Context 가 필요해, 같은 설정의 Json(Persistence.json 과 같음)으로 같은 디코더를 지난다.
 */
class LegacySaveTests {

    /** Persistence 의 Json 설정 그대로 */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 2.8.x Android 앱이 실제로 쓰던 모양: Map<GameMode, Int>·Map<Int, TableMastery> 는 문자열 키 객체 */
    private val legacyJSON = """
        {"version":1,"totalXp":840,"streak":3,"lastPlayedDate":"2026-11-20","dailyGoal":20,"dailyDate":"2026-11-20",
         "dailyCorrect":12,"tableMastery":{"2":{"stars":3,"bestAccuracy":1.0,"bestAvgMs":1800,"plays":4},
         "7":{"stars":1,"bestAccuracy":0.6,"bestAvgMs":3200,"plays":2}},
         "unlockedAchievements":["first_win","adv_first_boss"],"totalCorrect":210,"totalWrong":31,"maxCombo":14,
         "recentAccuracy":[80,90],"recentAvgMs":[2100,1900],"wrongPool":{"7x8":2},
         "bestScores":{"challenge":15,"adventure":4},"modesPlayed":["practice","adventure","challenge"],
         "onboarded":true,"dayLog":[]}
    """.trimIndent()

    /** 어드벤처 기록이 든 저장본이 읽히고, 다른 진행도가 그대로 남는다 */
    @Test fun gameStateWithAdventureDecodes() {
        val state = json.decodeFromString(GameState.serializer(), legacyJSON)

        assertEquals(840, state.totalXp)
        assertEquals(3, state.streak)
        assertEquals(3, state.tableMastery[2]?.stars)
        assertEquals(2, state.tableMastery[7]?.plays)
        assertEquals(210, state.totalCorrect)
        assertEquals(2, state.wrongPool["7x8"])
        assertEquals(15, state.bestScores[GameMode.CHALLENGE])
        assertTrue(GameMode.CHALLENGE in state.modesPlayed)
        assertTrue(state.onboarded)
        assertTrue(state.dayLog.isEmpty())
    }

    /** 지금 앱이 쓰는 저장 경로(같은 Json 으로 인코딩)로 만든 저장본도 다시 읽힌다 */
    @Test fun gameStateWithAdventureRoundTrips() {
        val state = json.decodeFromString(GameState.serializer(), legacyJSON)
        val written = json.encodeToString(GameState.serializer(), state)

        assertEquals(state, json.decodeFromString(GameState.serializer(), written))
        assertTrue("\"adventure\"" in written)
    }

    /** 2.9.0 에서 GameMode 에 stairs·monster 를 더해도 원래 값(adventure 포함)의 저장 이름은 그대로다 */
    @Test fun gameModeSerialNamesAreStable() {
        val names = GameMode.entries.map { json.encodeToString(GameMode.serializer(), it).trim('"') }
        assertEquals(
            listOf("practice", "timeAttack", "challenge", "survival", "missing", "truefalse", "adventure", "stairs", "monster"),
            names,
        )
    }

    /** 어드벤처 기록이 든 저장본에 게임 판을 기록해 저장하고 다시 읽어도 둘 다 남는다(새 값도 읽히고, 7단 별은 그대로) */
    @Test fun legacyStateKeepsAdventureAfterGameCommit() {
        val legacy = json.decodeFromString(GameState.serializer(), legacyJSON)
        val stairs = SessionResult(
            GameMode.STAIRS, null,
            listOf(
                AnswerRecord(4, 3, true, 1400, GivenAnswer.Number(12)),
                AnswerRecord(4, 4, false, 2600, GivenAnswer.Number(20)),
            ),
            maxCombo = 1, durationMs = 8000,
        )
        val monster = SessionResult(
            GameMode.MONSTER, 7, listOf(AnswerRecord(7, 6, true, 1800, GivenAnswer.Number(42))), maxCombo = 1, durationMs = 60_000,
        )
        var state = Commit.applySession(legacy, stairs).first
        state = Commit.applySession(state, monster).first
        val written = json.encodeToString(GameState.serializer(), state)

        val loaded = json.decodeFromString(GameState.serializer(), written)
        assertEquals(state, loaded)
        assertEquals(
            listOf(GameMode.PRACTICE, GameMode.ADVENTURE, GameMode.CHALLENGE, GameMode.STAIRS, GameMode.MONSTER),
            loaded.modesPlayed,
        )
        assertEquals(15, loaded.bestScores[GameMode.CHALLENGE])
        assertEquals(212, loaded.totalCorrect)
        assertNotNull(loaded.wrongPool[Problems.key(4, 4)])
        assertEquals(legacy.tableMastery[7], loaded.tableMastery[7], "단 지정 몬스터 판도 7단 별·플레이 수는 건드리지 않는다")
        assertTrue("\"stairs\"" in written && "\"monster\"" in written)
    }

    /** 2.9.0 이전 dayLog 항목(tables 키 없음)은 tables=null 로 읽히고, 새 판을 치면 그날 단별 합계가 쌓여 왕복 보존된다 */
    @Test fun legacyDayLogWithoutTablesDecodesAsNullAndRoundTrips() {
        val payload = """
            {"version":1,"totalXp":400,"streak":2,"lastPlayedDate":"2026-11-23","dailyGoal":20,"dailyDate":"2026-11-23",
             "dailyCorrect":10,"tableMastery":{},"unlockedAchievements":[],"totalCorrect":40,"totalWrong":5,"maxCombo":8,
             "recentAccuracy":[90],"recentAvgMs":[2000],"wrongPool":{"7x8":2},"bestScores":{},"modesPlayed":["practice"],
             "onboarded":true,"dayLog":[{"date":"2026-11-23","correct":10,"wrong":2,"msSum":24000,"misses":{"7x8":2}}]}
        """.trimIndent()
        val decoded = json.decodeFromString(GameState.serializer(), payload)
        assertEquals(1, decoded.dayLog.size)
        assertEquals("2026-11-23", decoded.dayLog[0].date)
        assertEquals(10, decoded.dayLog[0].correct)
        assertNull(decoded.dayLog[0].tables)

        // 같은 날 새 판을 치면 새로 푼 답이 tables 에 쌓인다
        val (updated, _) = Commit.applySession(
            decoded,
            SessionResult(
                GameMode.PRACTICE, 7,
                listOf(AnswerRecord(7, 8, correct = true, ms = 1800)),
                maxCombo = 1, durationMs = 1800,
            ),
            LocalDate.of(2026, 11, 23),
        )
        val roundTripped = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), updated))
        assertEquals(updated, roundTripped)
        assertEquals(mapOf(7 to TableTally(correct = 1, wrong = 0, msSum = 1800)), roundTripped.dayLog[0].tables)
    }
}
