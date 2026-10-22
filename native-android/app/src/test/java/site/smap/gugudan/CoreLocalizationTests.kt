package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.*
import site.smap.gugudan.core.adventure.AdvAchievements
import site.smap.gugudan.core.adventure.Battle
import site.smap.gugudan.core.adventure.BattleStyle
import site.smap.gugudan.core.adventure.Shop
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.services.GuardianDenial
import site.smap.gugudan.services.PermissionDenial
import site.smap.gugudan.services.PushApi
import site.smap.gugudan.store.ThemeStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core 문구가 언어별로 바뀌는지 (iOS GuguTests/CoreLocalizationTests.swift 미러링).
 * iOS 는 실행 언어를 바꿔 같은 테스트를 세 번 돌리지만, Android 는 언어별 TestStrings·language 인자를 넘겨 한 번에 확인한다.
 */
class CoreLocalizationTests {
    private val ko = TestStrings(AppLanguage.KO)
    private val en = TestStrings(AppLanguage.EN)
    private val ja = TestStrings(AppLanguage.JA)
    private val hangul = Regex("[가-힣]")

    private fun all(id: Int, vararg args: Any) = Triple(ko.get(id, *args), en.get(id, *args), ja.get(id, *args))

    @Test fun modeNames() {
        assertEquals(Triple("학습", "Practice", "れんしゅう"), all(Modes.def(GameMode.PRACTICE).nameRes))
        assertEquals(Triple("OX 퀴즈", "True or False", "○×クイズ"), all(Modes.def(GameMode.TRUEFALSE).nameRes))
    }

    @Test fun achievementAndLevelText() {
        assertEquals(Triple("첫 정답", "First Correct", "はじめての 正かい"), all(Achievements.get("first_correct")!!.nameRes))
        assertEquals(Triple("구구단 입문자", "Times Table Beginner", "九九ビギナー"), all(Level.titleRes(1)))
    }

    @Test fun worldNamesAndDefaultGreeting() {
        val meadow = World.region(2)!!
        assertEquals(Triple("새싹 들판", "Sprout Meadow", "ふたばの のはら"), all(meadow.nameRes))
        val npc = meadow.npcs.first()
        assertEquals(Triple("2단으로 승부하자!", "Let's battle with the 2 times table!", "2のだんで しょうぶだ！"),
            all(npc.greetingRes, npc.table))
        // 보스 인사말은 단을 받지 않는다 — 화면이 단을 함께 넘겨도 그대로
        val boss = meadow.npcs.last()
        assertEquals("들판을 지나가려면 2단쯤은 술술 나와야지!", ko.get(boss.greetingRes, boss.table))
    }

    /** 정적 데이터는 문자열 대신 리소스 ID 만 든다 — 모든 이름이 세 언어로 읽히고, 영어·일본어에 한국어가 새지 않는다 */
    @Test fun everyCoreNameResolvesPerLanguage() {
        val ids = buildList {
            Modes.all.values.forEach { addAll(listOf(it.nameRes, it.taglineRes, it.detailRes)) }
            Achievements.all.forEach { addAll(listOf(it.nameRes, it.descriptionRes)) }
            AdvAchievements.all.forEach { addAll(listOf(it.nameRes, it.descriptionRes)) }
            (1..40).forEach { add(Level.titleRes(it)) }
            World.regions.forEach { r -> add(r.nameRes); r.npcs.forEach { addAll(listOf(it.nameRes, it.greetingRes)) } }
            Shop.colors.forEach { add(it.nameRes) }
            Shop.hats.forEach { add(it.nameRes) }
            BattleStyle.entries.forEach { add(Battle.styleNameRes(it)) }
            Theme.entries.forEach { add(ThemeStore.labelRes(it)) }
            GuardianDenial.entries.forEach { add(it.messageRes) }
            PermissionDenial.entries.forEach { add(it.messageRes) }
        }
        for (id in ids) {
            val (k, e, j) = all(id, 2)
            assertTrue(hangul.containsMatchIn(k), "ko 에 한국어가 없음: $k")
            assertFalse(hangul.containsMatchIn(e), "en 에 한국어가 남음: $e")
            assertFalse(hangul.containsMatchIn(j), "ja 에 한국어가 남음: $j")
        }
    }

    /** 힌트 제목은 번역되고, 식 부분은 언어와 무관하게 같다 */
    @Test fun hintTitleTranslatesButMathStays() {
        for ((strings, title) in listOf(ko to "10단에서 한 번 빼기", en to "Times 10, take one away", ja to "10のだんから 1回分 ひく")) {
            val hint = Hints.coach(strings, 9, 7)
            assertEquals(title, hint.title)
            assertEquals("70 − 7 = ?", hint.scaffold)
            assertEquals("9 × 7 = 70 − 7 = 63", hint.full)
        }
        assertEquals("Double the 2s", Hints.coach(en, 4, 3).title)
        assertEquals("2のだんを 2ばいに", Hints.coach(ja, 4, 3).title)
    }

    @Test fun reorderedArgumentsKeepMeaning() {
        // 기본 전략(1~9 밖) 은 화면에 나오지 않지만 서식 순서는 같아야 한다 — 위치 지정 인자 확인
        assertEquals(Triple("7씩 3번 더해 봐요", "Try adding 7, 3 times", "7を 3回 たしてみよう"),
            all(R.string.try_adding_times, 7, 3))
    }

    @Test fun weekdayAndHourLabels() {
        // 2026-09-29 는 화요일. 영어 시각은 숫자와 AM/PM 사이에 좁은 줄바꿈 없는 공백(U+202F) — iOS 와 같다
        assertEquals("화", WeeklyReport.weekdayLabel("2026-09-29", AppLanguage.KO, Locale.KOREA))
        assertEquals("T", WeeklyReport.weekdayLabel("2026-09-29", AppLanguage.EN, Locale.US))
        assertEquals("火", WeeklyReport.weekdayLabel("2026-09-29", AppLanguage.JA, Locale.JAPAN))
        assertEquals("화요일", WeeklyReport.weekdayName("2026-09-29", Locale.KOREA))
        assertEquals("Tuesday", WeeklyReport.weekdayName("2026-09-29", Locale.US))
        assertEquals("火曜日", WeeklyReport.weekdayName("2026-09-29", Locale.JAPAN))
        assertEquals("오후 6시", ReminderPlanner.hourLabel(18, AppLanguage.KO, Locale.KOREA))
        assertEquals("6\u202FPM", ReminderPlanner.hourLabel(18, AppLanguage.EN, Locale.US))
        assertEquals("12\u202FPM", ReminderPlanner.hourLabel(12, AppLanguage.EN, Locale.US))
        assertEquals("9\u202FAM", ReminderPlanner.hourLabel(9, AppLanguage.EN, Locale("en", "KR")))
        assertEquals("18時", ReminderPlanner.hourLabel(18, AppLanguage.JA, Locale.JAPAN))
        assertEquals("12時", ReminderPlanner.hourLabel(12, AppLanguage.JA, Locale.JAPAN))
        // 24시간제 지역의 영어 — iOS 템플릿 "j" 처럼 그 지역 서식을 따른다
        assertEquals("18", ReminderPlanner.hourLabel(18, AppLanguage.EN, Locale.UK))
    }

    @Test fun pushDates() {
        val date = LocalDate.of(2026, 10, 22)
        assertEquals("2026년 10월 22일", PushCopy.longDate(date, AppLanguage.KO, Locale.KOREA))
        assertEquals("October 22, 2026", PushCopy.longDate(date, AppLanguage.EN, Locale.US))
        assertEquals("2026年10月22日", PushCopy.longDate(date, AppLanguage.JA, Locale.JAPAN))
        assertEquals("2026.10.22", PushCopy.shortDate(date, AppLanguage.KO, Locale.KOREA))
        assertEquals("Agreed on Oct 22, 2026", PushCopy.consentSubtitle(en, date, Locale.US))
        assertEquals("Gugu Adventure · October 22, 2026\nYour consent to event & offer notifications has been recorded.",
            PushCopy.grantedMessage(en, date, Locale.US))
        assertEquals("九九アドベンチャー · 2026年10月22日\nイベント・特典のお知らせの受信停止を受け付けました。",
            PushCopy.revokedMessage(ja, date, Locale.JAPAN))
    }

    /** 영어만 복수형이 갈린다 — 1개/여러 개 */
    @Test fun englishPluralForms() {
        val id = R.plurals.just_more_correct_answers_to_reach_today
        assertEquals("오늘 목표까지 정답 1개 남았어요.", ko.plural(id, 1, 1))
        assertEquals("Just 1 more correct answer to reach today's goal.", en.plural(id, 1, 1))
        assertEquals("Just 3 more correct answers to reach today's goal.", en.plural(id, 3, 3))
        assertEquals("きょうの もくひょうまで あと 3もん！", ja.plural(id, 3, 3))
    }

    @Test fun weeklyParentTipPerLanguage() {
        val today = "2026-09-29"
        val missed = WeeklyReport.build(listOf(DayLogEntry(today, correct = 5, wrong = 2, msSum = 1, misses = mapOf("7x8" to 2))), today)
        assertEquals("자주 헷갈리는 문제는 7 × 8이에요. 7단 표를 소리 내어 함께 외워 보세요.", missed.parentTip(ko))
        // 한국어 서술격 조사는 붙지 않고 식만
        assertEquals("The trickiest one this week is 7 × 8. Try saying the 7 times table out loud together.", missed.parentTip(en))
        assertEquals("よく間違える問題は 7 × 8 です。7の段を声に出して一緒に覚えてみましょう。", missed.parentTip(ja))
    }

    @Test fun reminderMessagesPerLanguage() {
        val seoul = ZoneId.of("Asia/Seoul")
        val state = GameState(lastPlayedDate = "2026-09-21", streak = 4, dailyGoal = 20)
        val settings = ReminderSettings(enabled = true, hour = 18, askedPermission = true)
        val now = ZonedDateTime.of(2026, 9, 22, 10, 0, 0, 0, seoul)
        val first = { s: Strings -> ReminderPlanner.plan(s, state, settings, now).first() }
        assertEquals("연속 기록 이어가기", first(ko).title)
        assertEquals("4일 연속 중이에요! 오늘도 한 판 하면 5일째예요.", first(ko).body)
        assertEquals("You're on a 4-day streak! Play today to make it 5 days.", first(en).body)
        assertEquals("4日 れんぞく中！きょうも 1回 あそぶと 5日目だよ。", first(ja).body)
        // 같은 계획(시각·식별자)에 문구만 언어별
        assertEquals(ReminderPlanner.plan(ko, state, settings, now).map { it.id to it.fireAtEpochMs },
            ReminderPlanner.plan(en, state, settings, now).map { it.id to it.fireAtEpochMs })
    }

    // MARK: 푸시·결제·법무 (M6)

    /** 공지 토픽 — 한국어는 기존 이름 유지(구버전 기기와 같은 토픽), 다른 언어는 언어별 토픽 */
    @Test fun noticeTopicsPerLanguage() {
        assertEquals(setOf("notice", "notice-android"), PushTopics.noticeTopics("android", AppLanguage.KO))
        assertEquals(setOf("notice-en", "notice-en-android"), PushTopics.noticeTopics("android", AppLanguage.EN))
        assertEquals(setOf("notice-ja", "notice-ja-android"), PushTopics.noticeTopics("android", AppLanguage.JA))
        assertEquals(setOf("notice-en", "notice-en-ios"), PushTopics.noticeTopics("ios", AppLanguage.EN))
        val on = PushSettings(serviceOptIn = true, marketingOptIn = true)
        assertEquals(setOf("notice-ja", "notice-ja-android", "promo"), PushTopics.desired(on, language = AppLanguage.JA))
        // 언어를 바꾸면 기존 diff 가 이전 언어 토픽을 해제하고 새 언어 토픽을 구독한다
        val diff = PushTopics.diff(PushTopics.desired(on, language = AppLanguage.EN), PushTopics.desired(on, language = AppLanguage.KO))
        assertEquals(setOf("notice-en", "notice-en-android"), diff.subscribe)
        assertEquals(setOf("notice", "notice-android"), diff.unsubscribe)
    }

    @Test fun promoToggleShownOnlyInKoreanPlayCountry() {
        assertTrue(PromoVisibility.showsToggle("KR", marketingOn = false))
        assertFalse(PromoVisibility.showsToggle("US", marketingOn = false))
        assertFalse(PromoVisibility.showsToggle("JP", marketingOn = false))
        assertFalse(PromoVisibility.showsToggle(null, marketingOn = false))
        // 이미 켜 둔 사람에게는 끌 수 있도록 계속 보인다
        assertTrue(PromoVisibility.showsToggle("US", marketingOn = true))
        assertTrue(PromoVisibility.showsToggle(null, marketingOn = true))
    }

    /** 서버는 이 값으로 알림 언어를 고른다 — 기기 언어가 아니라 앱 언어 + 기기 지역 */
    @Test fun localeTagUsesAppLanguageAndDeviceRegion() {
        assertEquals("ko-KR", PushApi.localeTag(AppLanguage.KO, Locale.KOREA))
        assertEquals("en-FR", PushApi.localeTag(AppLanguage.EN, Locale.FRANCE))
        assertEquals("ja-JP", PushApi.localeTag(AppLanguage.JA, Locale.JAPAN))
        assertEquals("ja-KR", PushApi.localeTag(AppLanguage.JA, Locale.KOREA))
        assertEquals("en", PushApi.localeTag(AppLanguage.EN, Locale("en")))
        assertEquals("Asia/Seoul", PushApi.timeZoneId(ZoneId.of("Asia/Seoul")))
    }

    @Test fun legalUrlsPerLanguage() {
        assertEquals("https://gugu.smap.site/terms", PremiumConfig.Legal.url("terms", AppLanguage.KO))
        assertEquals("https://gugu.smap.site/en/privacy", PremiumConfig.Legal.url("privacy", AppLanguage.EN))
        assertEquals("https://gugu.smap.site/ja/delete-account", PremiumConfig.Legal.url("delete-account", AppLanguage.JA))
    }
}
