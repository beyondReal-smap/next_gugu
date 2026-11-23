package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 영어·일본어 읽기 규칙 (iOS GuguTests/ReadingTests.swift 미러링) — 음성 대본(552줄)과 화면 낭독 라벨의 기준이다
class ReadingTests {

    // 이 클래스의 기대값은 곱하는 수 상한 19 기준이다 — 다른 테스트가 바꿔 둔 전역 설정에 기대지 않게 매번 고정한다
    @Before fun fixMaxMultiplier() { Problems.maxB = 19 }

    @After fun restoreMaxMultiplier() { Problems.maxB = Problems.DEFAULT_MAX_B }

    @Test fun koreanExtendedChantsSeparateTheFactors() {
        val examples = listOf(
            Triple(3, 19, "삼 곱하기 십구는 오십칠"),
            Triple(9, 10, "구 곱하기 십은 구십"),
            Triple(10, 9, "십 곱하기 구는 구십"),
            Triple(10, 2, "십 곱하기 이는 이십"),
            Triple(19, 19, "십구 곱하기 십구는 삼백육십일"),
        )
        for ((a, b, expected) in examples) {
            assertEquals(expected, KoreanReading.chant(a, b))
            assertEquals("$expected.", VoiceLines.chant(a, b, AppLanguage.KO).text)
        }
        for (a in 2..9) for (b in 1..9) {
            assertEquals("${KoreanReading.sino(a)} ${KoreanReading.topic(KoreanReading.sino(b))} ${KoreanReading.sino(a * b)}",
                KoreanReading.chant(a, b))
            val head = KoreanReading.sino(a) + if (b == 2) "," else ""
            assertEquals("$head ${KoreanReading.topic(KoreanReading.sino(b))} ${KoreanReading.sino(a * b)}.",
                VoiceLines.chant(a, b, AppLanguage.KO).text)
        }
    }

    @Test fun englishCardinals() {
        assertEquals("zero", EnglishReading.cardinal(0))
        assertEquals("thirteen", EnglishReading.cardinal(13))
        assertEquals("forty", EnglishReading.cardinal(40))
        assertEquals("fifty-six", EnglishReading.cardinal(56))
        assertEquals("ninety", EnglishReading.cardinal(90))
    }

    @Test fun englishQuestions() {
        val p = Problem(7, 8)
        assertEquals("What is seven times eight?", EnglishReading.question(p, GameMode.PRACTICE, null))
        assertEquals("Fifty-six is seven times what?", EnglishReading.question(p, GameMode.MISSING, null))
        assertEquals("Seven times eight is fifty-four. True or false?",
            EnglishReading.question(p, GameMode.TRUEFALSE, Statement(54, false)))
        assertEquals("Eight times one is eight.", EnglishReading.chant(8, 1))
    }

    @Test fun japaneseNumbers() {
        assertEquals("ぜろ", JapaneseReading.number(0))
        assertEquals("よん", JapaneseReading.number(4))
        assertEquals("じゅう", JapaneseReading.number(10))
        assertEquals("じゅうよん", JapaneseReading.number(14))
        assertEquals("ごじゅうろく", JapaneseReading.number(56))
        assertEquals("ななじゅうきゅう", JapaneseReading.number(79))
        assertEquals("きゅうじゅう", JapaneseReading.number(90))
    }

    @Test fun japaneseQuestions() {
        val p = Problem(7, 8)
        assertEquals("なな かける はち は？", JapaneseReading.question(p, GameMode.PRACTICE, null))
        assertEquals("ごじゅうろく は なな かける なに？", JapaneseReading.question(p, GameMode.MISSING, null))
        assertEquals("なな かける はち は、ごじゅうよん。まるかな？ ばつかな？",
            JapaneseReading.question(p, GameMode.TRUEFALSE, Statement(54, false)))
    }

    /** 九九 표 — 대표 줄, "곱이 한 자리면 が" 규칙, 그리고 72줄 전수를 음성 대본(phrases.py → ja 매니페스트)과 대조한다 */
    @Test fun kukuTable() {
        assertEquals("ににんが し", JapaneseReading.chant(2, 2))
        assertEquals("さざんが く", JapaneseReading.chant(3, 3))
        assertEquals("しちは ごじゅうろく", JapaneseReading.chant(7, 8))
        assertEquals("はっぱ ろくじゅうし", JapaneseReading.chant(8, 8))
        assertEquals("くく はちじゅういち", JapaneseReading.chant(9, 9))

        val manifest = Json.parseToJsonElement(File("src/main/assets/voice/ja/manifest.json").readText())
            .jsonObject.getValue("lines").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val lines = mutableSetOf<String>()
        for (a in 2..9) {
            for (b in 1..9) {
                val line = JapaneseReading.chant(a, b)
                lines += line
                assertEquals(a * b < 10, line.contains("が "), "$a × $b: $line")
                assertEquals(manifest.getValue("c-${a}x$b"), "$line。", "$a × $b 九九 줄이 음성 대본과 다름")
            }
        }
        assertEquals(72, lines.size)
    }

    @Test fun spokenMathPicksLanguage() {
        assertEquals("7 곱하기 8은?", SpokenMath.questionLabel(7, 8, AppLanguage.KO))
        assertEquals("What is 7 times 8?", SpokenMath.questionLabel(7, 8, AppLanguage.EN))
        assertEquals("7 かける 8 は？", SpokenMath.questionLabel(7, 8, AppLanguage.JA))
        assertEquals("7 곱하기 8은 56", SpokenMath.equationLabel(7, 8, AppLanguage.KO))
        assertEquals("7 times 8 is 56", SpokenMath.equationLabel(7, 8, AppLanguage.EN))
        assertEquals("7 かける 8 は 56", SpokenMath.equationLabel(7, 8, AppLanguage.JA))
        assertEquals("7 곱하기 8", SpokenMath.factLabel(7, 8, AppLanguage.KO))
        assertEquals("7 times 8", SpokenMath.factLabel(7, 8, AppLanguage.EN))
        assertEquals("7 かける 8", SpokenMath.factLabel(7, 8, AppLanguage.JA))
        assertEquals("54가 아니라 56이에요", SpokenMath.notButIs(54, 56, AppLanguage.KO))
        assertEquals("Not 54. It's 56.", SpokenMath.notButIs(54, 56, AppLanguage.EN))
        assertEquals("54 じゃなくて 56 だよ", SpokenMath.notButIs(54, 56, AppLanguage.JA))
        assertEquals("정답은 36이에요", SpokenMath.answerIs(36, AppLanguage.KO))
        assertEquals("The answer is 36.", SpokenMath.answerIs(36, AppLanguage.EN))
        assertEquals("こたえは 36", SpokenMath.answerIs(36, AppLanguage.JA))
        assertEquals("칠 팔은 오십육", SpokenMath.chantText(7, 8, AppLanguage.KO))
        assertNull(SpokenMath.chantText(7, 8, AppLanguage.EN))
        assertEquals("しちは ごじゅうろく", SpokenMath.chantText(7, 8, AppLanguage.JA))
        assertEquals(VoiceLine("c-7x8", "しちは ごじゅうろく。"), VoiceLines.chant(7, 8, AppLanguage.JA))
        // 개수 단위 — 영어는 숫자만
        assertEquals("개", SpokenMath.countSuffix(TestStrings(AppLanguage.KO)))
        assertNull(SpokenMath.countSuffix(TestStrings(AppLanguage.EN)))
        assertEquals("こ", SpokenMath.countSuffix(TestStrings(AppLanguage.JA)))
        // 낭독 라벨 두 문장 잇기 — 일본어는 "。", 빈 문장은 뺀다
        assertEquals("7 times 8 is 56. My answer: 5", SpokenMath.sentences("7 times 8 is 56", "My answer: 5", language = AppLanguage.EN))
        assertEquals("7 かける 8 は 56。自分の 答え 5", SpokenMath.sentences("7 かける 8 は 56", "自分の 答え 5", language = AppLanguage.JA))
        assertEquals("7 곱하기 8은 56", SpokenMath.sentences("7 곱하기 8은 56", null, language = AppLanguage.KO))
    }

    /** 한국어 경로는 기존 KoreanReading 직접 호출과 글자 단위로 같아야 한다 (화면 낭독 회귀 방지) */
    @Test fun koreanPathMatchesKoreanReading() {
        for (a in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            for (b in Problems.MIN_B..Problems.SUPPORTED_MAX_B) {
                val p = Problem(a, b)
                for (mode in listOf(GameMode.PRACTICE, GameMode.MISSING)) {
                    assertEquals(KoreanReading.question(p, mode, null), SpokenMath.question(p, mode, null, AppLanguage.KO))
                }
                val st = Statement(a * b + 1, false)
                assertEquals(KoreanReading.question(p, GameMode.TRUEFALSE, st),
                    SpokenMath.question(p, GameMode.TRUEFALSE, st, AppLanguage.KO))
                assertEquals("$a 곱하기 ${KoreanReading.withTopic(b)} ${a * b}", SpokenMath.equationLabel(a, b, AppLanguage.KO))
                assertEquals(KoreanReading.chant(a, b), SpokenMath.chantText(a, b, AppLanguage.KO))
                assertEquals("정답은 ${KoreanReading.withCopula(a * b)}", SpokenMath.answerIs(a * b, AppLanguage.KO))
                assertEquals(KoreanReading.notButIs(a + b, a * b), SpokenMath.notButIs(a + b, a * b, AppLanguage.KO))
            }
        }
    }
}
