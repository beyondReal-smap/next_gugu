package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.DayLogEntry
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.Hints
import site.smap.gugudan.core.KoreanReading
import site.smap.gugudan.core.MissCount
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.PushRoute
import site.smap.gugudan.core.TableMastery
import site.smap.gugudan.core.TableTally
import site.smap.gugudan.core.Theme
import site.smap.gugudan.core.TodayIsland
import site.smap.gugudan.core.guardian.GuardianDestination
import site.smap.gugudan.core.guardian.GuardianEvent
import site.smap.gugudan.core.guardian.GuardianFlow
import site.smap.gugudan.core.guardian.GuardianReport
import site.smap.gugudan.core.guardian.GuardianStep
import site.smap.gugudan.core.guardian.NavTab
import site.smap.gugudan.core.guardian.NavTarget
import site.smap.gugudan.core.guardian.ReportHeadline
import site.smap.gugudan.core.guardian.SettingsFocus
import site.smap.gugudan.core.guardian.SkillStatus
import site.smap.gugudan.core.guardian.TableSkill
import site.smap.gugudan.core.guardian.TableSkills
import site.smap.gugudan.designsystem.DayWorldColors
import site.smap.gugudan.designsystem.NightWorldColors
import site.smap.gugudan.designsystem.WorldArt
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 보호자 흐름·단별 실력·리포트·알림 이동·테마 첫값·잠김 조사 검증 (계획서 6-1, iOS GuardianTests.swift 대응)

class GuardianTests {

    private val today = "2026-11-24"

    // MARK: TableSkills

    /** 14일 창 경계(오늘 포함 14일째 포함, 15일째·미래 제외) + 구버전(tables=null) 무시 + 중복 날짜 병합 */
    @Test fun tableSkillsWindowAndLegacyHandling() {
        val log = listOf(
            // 15일째(today-14 = 2026-11-10) — 제외
            DayLogEntry("2026-11-10", correct = 10, msSum = 10_000, tables = mapOf(2 to TableTally(10, 0, 10_000))),
            // 14일째(today-13 = 2026-11-11) — 포함
            DayLogEntry("2026-11-11", correct = 6, wrong = 4, msSum = 20_000, tables = mapOf(7 to TableTally(6, 4, 20_000))),
            // 구버전 날(tables == null) — 무시
            DayLogEntry("2026-11-20", correct = 20, wrong = 0, msSum = 20_000, tables = null),
            // 오늘 중복 날짜 두 항목 — 병합
            DayLogEntry("2026-11-24", correct = 5, wrong = 0, msSum = 7_500, tables = mapOf(3 to TableTally(5, 0, 7_500))),
            DayLogEntry("2026-11-24", correct = 4, wrong = 1, msSum = 7_500, tables = mapOf(3 to TableTally(4, 1, 7_500))),
            // 미래 날 — 제외
            DayLogEntry("2026-11-25", correct = 10, msSum = 10_000, tables = mapOf(4 to TableTally(10, 0, 10_000))),
        )

        val skills = TableSkills.build(log, today)
        assertEquals((Problems.MIN_TABLE..Problems.MAX_TABLE).toSet(), skills.keys)

        // 15일째·미래·기록 없음 → 0문제 NO_RECORD
        assertEquals(0, skills.getValue(2).attempts)
        assertNull(skills.getValue(2).accuracy)
        assertNull(skills.getValue(2).avgMs)
        assertEquals(SkillStatus.NO_RECORD, skills.getValue(2).status)
        assertEquals(SkillStatus.NO_RECORD, skills.getValue(4).status)

        // 14일째 7단: 6/10 = 60%, avg 2000ms → WEAK
        val s7 = skills.getValue(7)
        assertEquals(10, s7.attempts)
        assertEquals(6, s7.correct)
        assertEquals(0.6, s7.accuracy)
        assertEquals(2000, s7.avgMs)
        assertEquals(SkillStatus.WEAK, s7.status)

        // 오늘 중복 병합 3단: 9/10 = 90%, avg 1500ms → STRONG
        val s3 = skills.getValue(3)
        assertEquals(10, s3.attempts)
        assertEquals(9, s3.correct)
        assertEquals(0.9, s3.accuracy)
        assertEquals(1500, s3.avgMs)
        assertEquals(SkillStatus.STRONG, s3.status)

        // dayLog == null 도 2..19 전부 NO_RECORD
        val empty = TableSkills.build(null, today)
        assertEquals(18, empty.size)
        assertEquals(SkillStatus.NO_RECORD, empty.getValue(9).status)
    }

    /** 상태 판정 경계값: 0 → 기록 없음, 9 → 연습 중, 10·90% → 잘해요, 89% → 연습 중, 70% → 연습 중, 69% → 약해요 */
    @Test fun tableSkillStatusBoundariesAndWeakOrdering() {
        assertEquals(SkillStatus.NO_RECORD, TableSkill(2, attempts = 0, correct = 0, msSum = 0).status)
        assertEquals(SkillStatus.PRACTICING, TableSkill(2, attempts = 9, correct = 0, msSum = 9000).status)
        assertEquals(SkillStatus.PRACTICING, TableSkill(2, attempts = 9, correct = 9, msSum = 9000).status)
        assertEquals(SkillStatus.STRONG, TableSkill(2, attempts = 10, correct = 9, msSum = 10_000).status)
        assertEquals(SkillStatus.PRACTICING, TableSkill(2, attempts = 100, correct = 89, msSum = 100_000).status)
        assertEquals(SkillStatus.PRACTICING, TableSkill(2, attempts = 10, correct = 7, msSum = 10_000).status)
        assertEquals(SkillStatus.WEAK, TableSkill(2, attempts = 100, correct = 69, msSum = 100_000).status)

        // 약한 순 정렬: 정확도 오름차순, 같으면 단 오름차순
        val skills = mapOf(
            2 to TableSkill(2, 10, 9, 10_000),     // STRONG
            5 to TableSkill(5, 10, 7, 10_000),     // PRACTICING
            6 to TableSkill(6, 10, 6, 10_000),     // WEAK 60%
            7 to TableSkill(7, 10, 5, 10_000),     // WEAK 50%
            8 to TableSkill(8, 10, 6, 10_000),     // WEAK 60% (6단과 동률 → 6 먼저)
            9 to TableSkill(9, 0, 0, 0),           // NO_RECORD
            12 to TableSkill(12, 10, 4, 10_000),   // WEAK 40%
        )
        assertEquals(listOf(12, 7, 6, 8), TableSkills.weak(skills))
    }

    // MARK: GuardianReport

    /** 2~9단 항상 + 10~19단은 기록 있을 때만, 자주 틀린 문제 14일·최대 5개·정렬 */
    @Test fun guardianReportRowsAndTopMisses() {
        val log = listOf(
            // 15일째(제외)
            DayLogEntry(
                "2026-11-10", correct = 1, wrong = 10, msSum = 1000,
                misses = mapOf("2x2" to 10),
                tables = mapOf(15 to TableTally(1, 10, 1000)),
            ),
            // 14일째 구버전(tables == null 이어도 misses 는 집계)
            DayLogEntry(
                "2026-11-11", correct = 5, wrong = 4, msSum = 5000,
                misses = mapOf("7x8" to 3, "8x7" to 1),
                tables = null,
            ),
            // 오늘 — 12단만 10~19단 중 기록 있음, 오답 6종(+깨진 키) 중 상위 5개만(동률은 키 오름차순)
            DayLogEntry(
                "2026-11-24", correct = 10, wrong = 11, msSum = 30_000,
                misses = mapOf("8x7" to 2, "6x7" to 2, "7x6" to 2, "9x8" to 2, "4x8" to 1, "3x9" to 1, "bad" to 99),
                tables = mapOf(
                    7 to TableTally(5, 5, 15_000),
                    12 to TableTally(5, 6, 15_000),
                ),
            ),
        )
        val report = GuardianReport.build(GameState(dayLog = log), today)

        // rows: 2..9 (8개) + 12 (1개) = 9개. 15단은 15일째라 제외
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 12), report.rows.map { it.table })
        // weak: 12단(45.5%) < 7단(50%)
        assertEquals(listOf(12, 7), report.weak)
        // topMisses: 7x8(3), 8x7(3), 6x7(2), 7x6(2), 9x8(2) — 5개 상한, 동률은 문자열 키 오름차순
        assertEquals(
            listOf(
                MissCount("7x8", 3),
                MissCount("8x7", 3),
                MissCount("6x7", 2),
                MissCount("7x6", 2),
                MissCount("9x8", 2),
            ),
            report.topMisses,
        )
        // 이번 주는 월(23)~일(29)
        assertEquals("2026-11-23", report.week.days.first().date)
        assertEquals("2026-11-29", report.week.days.last().date)
    }

    /** 한마디 5경우: NoRecord / Weak(1개) / Weak(2개+) / KeepGoing / AllStrong */
    @Test fun guardianReportAllFiveHeadlines() {
        fun m(s: Int) = TableMastery(stars = s, bestAccuracy = 1.0, bestAvgMs = 1000, plays = 1)

        // 1. NoRecord
        assertEquals(ReportHeadline.NoRecord, GuardianReport.build(GameState(), today).headline)

        // 2. Weak(first, null) — 약한 단 1개
        val oneWeak = GameState(
            dayLog = listOf(DayLogEntry(today, tables = mapOf(7 to TableTally(5, 5, 20_000)))),
        )
        assertEquals(ReportHeadline.Weak(7, null), GuardianReport.build(oneWeak, today).headline)

        // 3. Weak(first, second) — 약한 단 3개면 가장 약한 2개, first 는 TodayIsland 와 같다
        val threeWeak = GameState(
            dayLog = listOf(
                DayLogEntry(
                    today,
                    tables = mapOf(
                        6 to TableTally(6, 4, 20_000),
                        7 to TableTally(4, 6, 20_000),
                        8 to TableTally(5, 5, 20_000),
                    ),
                ),
            ),
        )
        val r3 = GuardianReport.build(threeWeak, today)
        assertEquals(ReportHeadline.Weak(7, 8), r3.headline)
        assertEquals(
            TodayIsland.Table(7, TodayIsland.Reason.WEAK),
            TodayIsland.pick(threeWeak.tableMastery, TableSkills.build(threeWeak.dayLog, today)),
        )

        // 4. KeepGoing(next) — 약한 단 없고 2단이 잘해요 → 다음 섬 5단
        val keepGoing = GameState(
            dayLog = listOf(DayLogEntry(today, tables = mapOf(2 to TableTally(10, 0, 15_000)))),
        )
        assertEquals(ReportHeadline.KeepGoing(5), GuardianReport.build(keepGoing, today).headline)

        // 5. AllStrong — 약한 단 없고 모든 단 별 3개 → 섞어서
        val allStrong = GameState(
            tableMastery = Hints.ROADMAP_TABLES.associateWith { m(3) },
            dayLog = listOf(DayLogEntry(today, tables = mapOf(2 to TableTally(10, 0, 15_000)))),
        )
        assertEquals(ReportHeadline.AllStrong, GuardianReport.build(allStrong, today).headline)
    }

    // MARK: GuardianFlow.reduce

    /** 계획서 2-2 표의 모든 전이 + 300초 경계(299 유지, 300 잠금) + 그 밖의 조합은 그대로 */
    @Test fun guardianFlowTransitionsAndRelockBoundary() {
        assertEquals(3.0, GuardianFlow.HOLD_SECONDS)
        assertEquals(300, GuardianFlow.RELOCK_AFTER_AWAY_SECONDS)

        val settingsNoti = GuardianDestination.Settings(SettingsFocus.NOTIFICATIONS)

        // 표의 모든 유효 전이
        assertEquals(
            GuardianStep.Holding(GuardianDestination.Report),
            GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.TapKey),
        )
        assertEquals(
            GuardianStep.KidLocked(GameMode.MISSING),
            GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.TapLocked(GameMode.MISSING)),
        )
        assertEquals(
            GuardianStep.Holding(GuardianDestination.Paywall),
            GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.CallGuardian),
        )
        assertEquals(
            GuardianStep.Holding(GuardianDestination.Paywall),
            GuardianFlow.reduce(GuardianStep.KidLocked(GameMode.TRUEFALSE), GuardianEvent.CallGuardian),
        )
        assertEquals(
            GuardianStep.Holding(settingsNoti),
            GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.Route(settingsNoti)),
        )
        assertEquals(
            GuardianStep.Open(GuardianDestination.Report),
            GuardianFlow.reduce(GuardianStep.Holding(GuardianDestination.Report), GuardianEvent.HoldCompleted),
        )
        assertEquals(
            GuardianStep.Open(GuardianDestination.Paywall),
            GuardianFlow.reduce(GuardianStep.Holding(GuardianDestination.Paywall), GuardianEvent.BirthYearPassed),
        )
        // 이미 열려 있으면 Route 가 다시 묻지 않고 목적지만 바꾼다
        assertEquals(
            GuardianStep.Open(settingsNoti),
            GuardianFlow.reduce(GuardianStep.Open(GuardianDestination.Report), GuardianEvent.Route(settingsNoti)),
        )

        // Dismiss + 300초 경계 (299 유지, 300 잠금)
        val activeSteps = listOf(
            GuardianStep.KidLocked(GameMode.MISSING),
            GuardianStep.Holding(GuardianDestination.Report),
            GuardianStep.Open(GuardianDestination.Report),
        )
        for (step in activeSteps) {
            assertEquals(GuardianStep.Idle, GuardianFlow.reduce(step, GuardianEvent.Dismiss), "$step")
            assertEquals(step, GuardianFlow.reduce(step, GuardianEvent.Resumed(0)), "$step 0s")
            assertEquals(step, GuardianFlow.reduce(step, GuardianEvent.Resumed(299)), "$step 299s")
            assertEquals(GuardianStep.Idle, GuardianFlow.reduce(step, GuardianEvent.Resumed(300)), "$step 300s")
            assertEquals(GuardianStep.Idle, GuardianFlow.reduce(step, GuardianEvent.Resumed(600)), "$step 600s")
        }

        // 그 밖의 조합은 그대로(빠른 두 번 누르기 등 무시)
        assertEquals(GuardianStep.Idle, GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.HoldCompleted))
        assertEquals(GuardianStep.Idle, GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.BirthYearPassed))
        assertEquals(GuardianStep.Idle, GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.Dismiss))
        assertEquals(GuardianStep.Idle, GuardianFlow.reduce(GuardianStep.Idle, GuardianEvent.Resumed(300)))

        val kid = GuardianStep.KidLocked(GameMode.MISSING)
        for (ignored in listOf(
            GuardianEvent.TapKey,
            GuardianEvent.TapLocked(GameMode.SURVIVAL),
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
            GuardianEvent.Route(GuardianDestination.Report),
        )) {
            assertEquals(kid, GuardianFlow.reduce(kid, ignored), "KidLocked + $ignored")
        }

        val holding = GuardianStep.Holding(GuardianDestination.Report)
        for (ignored in listOf(
            GuardianEvent.TapKey,
            GuardianEvent.TapLocked(GameMode.MISSING),
            GuardianEvent.CallGuardian,
            GuardianEvent.Route(GuardianDestination.Paywall),
        )) {
            assertEquals(holding, GuardianFlow.reduce(holding, ignored), "Holding + $ignored")
        }

        val open = GuardianStep.Open(GuardianDestination.Report)
        for (ignored in listOf(
            GuardianEvent.TapKey,
            GuardianEvent.TapLocked(GameMode.MISSING),
            GuardianEvent.CallGuardian,
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
        )) {
            assertEquals(open, GuardianFlow.reduce(open, ignored), "Open + $ignored")
        }
    }

    // MARK: NavTarget.from (6개 알림 이동 × 이용권 유무)

    @Test fun navTargetMapsAllSixRoutesForFreeAndPremium() {
        for (premium in listOf(false, true)) {
            assertEquals(NavTarget.Tab(NavTab.HOME), NavTarget.from(PushRoute.HOME, premium))
            assertEquals(NavTarget.Tab(NavTab.MAP), NavTarget.from(PushRoute.LEARN, premium))
            assertEquals(NavTarget.Guardian(GuardianDestination.Report), NavTarget.from(PushRoute.PROFILE, premium))
            assertEquals(NavTarget.Guardian(GuardianDestination.Report), NavTarget.from(PushRoute.PROFILE_REPORT, premium))
            assertEquals(
                NavTarget.Guardian(GuardianDestination.Settings(SettingsFocus.NOTIFICATIONS)),
                NavTarget.from(PushRoute.SETTINGS_NOTIFICATIONS, premium),
            )
        }
        assertEquals(NavTarget.Tab(NavTab.HOME), NavTarget.from(PushRoute.PAYWALL, isPremium = true))
        assertEquals(NavTarget.Guardian(GuardianDestination.Paywall), NavTarget.from(PushRoute.PAYWALL, isPremium = false))
    }

    // MARK: Theme.initial (계획서 2-10)

    @Test fun themeInitialDefaultsToLightAndRespectsSavedValues() {
        val logged = mutableListOf<String>()
        assertEquals(Theme.LIGHT, Theme.initial(null) { logged += it })
        assertEquals(Theme.DARK, Theme.initial("dark") { logged += it })
        assertEquals(Theme.LIGHT, Theme.initial("light") { logged += it })
        assertEquals(Theme.SYSTEM, Theme.initial("system") { logged += it })
        assertEquals(emptyList(), logged)

        // 손상 문자열 → 낮(LIGHT) + 콜백으로 로그 알림
        assertEquals(Theme.LIGHT, Theme.initial("sepia") { logged += it })
        assertEquals(Theme.LIGHT, Theme.initial("") { logged += it })
        assertEquals(Theme.LIGHT, Theme.initial("DARK") { logged += it })
        assertEquals(listOf("sepia", "", "DARK"), logged)
    }

    // MARK: 잠김 카드 한국어 조사 (계획서 3-9, 6-1)

    @Test fun lockedCardKoreanTopicParticles() {
        assertEquals("빈칸 추리는", KoreanReading.topic("빈칸 추리"))
        assertEquals("OX 퀴즈는", KoreanReading.topic("OX 퀴즈"))
        assertEquals("60초 챌린지는", KoreanReading.topic("60초 챌린지"))
        assertEquals("서바이벌은", KoreanReading.topic("서바이벌"))
    }

    // MARK: U3 디자인 토대 (WorldTheme·WorldArt)

    @Test fun worldThemeTokensAndArtAssets() {
        assertFalse(DayWorldColors.isDark)
        assertTrue(NightWorldColors.isDark)
        assertNotEquals(DayWorldColors.line, NightWorldColors.line, "밤 line 은 판보다 어두운 #0F1236")
        assertEquals(DayWorldColors.sun, NightWorldColors.sun)

        // 계획서 5-3 그림 15종 전부 고유 리소스 ID 로 연결되고 실제 drawable-nodpi/world_*.webp 파일이 존재
        assertEquals(15, WorldArt.ALL_NAMES.size)
        val ids = WorldArt.ALL_NAMES.map { WorldArt.res(it) }
        assertEquals(15, ids.toSet().size)
        for (name in WorldArt.ALL_NAMES) {
            val file = File("src/main/res/drawable-nodpi/world_$name.webp")
            assertTrue(file.isFile && file.length() in 1_000L..200_000L, "크기 확인: $file (${file.length()} bytes)")
        }
        assertFailsWith<IllegalArgumentException> { WorldArt.res("does_not_exist") }

        // 2~19단 전부 섬 그림 대응
        for (table in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            assertTrue(WorldArt.isle(table) in ids, "table=$table")
        }
    }
}
