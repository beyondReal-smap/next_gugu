package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import site.smap.gugudan.core.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 내장 음성 팩 검사 (iOS VoiceLinesTests.swift 미러링) — 언어별로 앱이 읽을 수 있는 모든 줄에 음성 파일이 있고,
// 파일이 읽는 문장이 앱 문장과 같은지. 음성 팩은 tools/voice/generate.py build(또는 sync-android)로 만든다.

class VoiceLinesTests {

    /** 언어별 음성 팩 폴더 — Speech 가 여는 assets/voice/<언어>/ */
    private fun voiceDir(language: AppLanguage) = File("src/main/assets/voice/${language.code}")

    /** 앱이 읽을 수 있는 모든 줄 (문제·빈칸·OX 의 모든 보기 값·구구단 외우기) */
    private fun allLines(language: AppLanguage = AppLanguage.KO): List<VoiceLine> = buildList {
        for (a in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            for (b in Problems.MIN_B..Problems.MAX_B) {
                val p = Problem(a, b)
                add(VoiceLines.question(p, GameMode.PRACTICE, null, language))
                add(VoiceLines.question(p, GameMode.MISSING, null, language))
                for (shown in (listOf(a * b) + Problems.statementCandidates(p)).toSet()) {
                    add(VoiceLines.question(p, GameMode.TRUEFALSE, Statement(shown, shown == a * b), language))
                }
                add(VoiceLines.chant(a, b, language))
            }
        }
    }

    @Test fun questionKeysPerMode() {
        val p = Problem(7, 8)
        val ko = AppLanguage.KO
        for (mode in listOf(GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.ADVENTURE)) {
            assertEquals(VoiceLine("q-7x8", "칠 곱하기 팔은?"), VoiceLines.question(p, mode, null, ko))
        }
        assertEquals("m-7x8", VoiceLines.question(p, GameMode.MISSING, null, ko).key)
        assertEquals(VoiceLine("ox-7x8-49", "칠 곱하기 팔은 사십구. 맞을까요?"),
            VoiceLines.question(p, GameMode.TRUEFALSE, Statement(49, false), ko))
        assertEquals(VoiceLine("c-7x8", "칠 팔은 오십육."), VoiceLines.chant(7, 8, ko))
        assertEquals(VoiceLine("c-3x2", "삼, 이는 육."), VoiceLines.chant(3, 2, ko))
    }

    /** 키는 언어와 무관하고 문장만 언어별로 다르다 — 같은 키로 언어별 폴더의 파일을 연다 */
    @Test fun keysAreSharedAcrossLanguages() {
        val p = Problem(7, 8)
        assertEquals(VoiceLine("q-7x8", "What is seven times eight?"), VoiceLines.question(p, GameMode.PRACTICE, null, AppLanguage.EN))
        assertEquals(VoiceLine("m-7x8", "ごじゅうろく は なな かける なに？"), VoiceLines.question(p, GameMode.MISSING, null, AppLanguage.JA))
        assertEquals(VoiceLine("ox-7x8-54", "Seven times eight is fifty-four. True or false?"),
            VoiceLines.question(p, GameMode.TRUEFALSE, Statement(54, false), AppLanguage.EN))
        assertEquals(VoiceLine("c-7x8", "Seven times eight is fifty-six."), VoiceLines.chant(7, 8, AppLanguage.EN))
        assertEquals(VoiceLine("c-7x8", "しちは ごじゅうろく。"), VoiceLines.chant(7, 8, AppLanguage.JA))
        assertEquals(allLines(AppLanguage.KO).map { it.key }, allLines(AppLanguage.EN).map { it.key })
        assertEquals(allLines(AppLanguage.KO).map { it.key }, allLines(AppLanguage.JA).map { it.key })
    }

    @Test fun voicePackCoversEveryLineKo() = assertPackCoversEveryLine(AppLanguage.KO)
    @Test fun voicePackCoversEveryLineEn() = assertPackCoversEveryLine(AppLanguage.EN)
    @Test fun voicePackCoversEveryLineJa() = assertPackCoversEveryLine(AppLanguage.JA)

    /** 552줄 전수 — 매니페스트(= iOS·phrases.py 와 같은 대본)의 문장과 Kotlin 이 만든 문장이 글자 단위로 같아야 한다 */
    private fun assertPackCoversEveryLine(language: AppLanguage) {
        val dir = voiceDir(language)
        val manifestFile = File(dir, "manifest.json")
        assertTrue(manifestFile.exists(),
            "음성 팩이 없습니다 — python3 tools/voice/generate.py sync-android --lang ${language.code} 를 실행하세요")
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val packed = manifest.getValue("lines").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val lines = allLines(language)
        assertEquals(552, lines.size, "${language.code} 문장 수")
        assertEquals(lines.map { it.key }.toSet(), packed.keys, "${language.code}: 앱 문장 목록과 음성 팩 목록이 다릅니다")
        for (line in lines) {
            assertEquals(line.text, packed[line.key], "${language.code}: 문장이 바뀌었습니다 — 음성 팩을 다시 만드세요: ${line.key}")
            assertTrue(File(dir, "${line.key}.m4a").exists(), "${language.code}: 음성 파일 없음: ${line.key}")
        }
        // 목록에 없는 파일이 남아 있으면 앱 크기만 키운다
        val files = dir.listFiles { f -> f.extension == "m4a" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(packed.keys, files, "${language.code}: 음성 팩 폴더에 목록 밖 파일이 있습니다")
    }
}
