package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import site.smap.gugudan.core.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 내장 음성 팩 검사 (iOS VoiceLinesTests.swift 미러링) — 앱이 읽을 수 있는 모든 줄에 음성 파일이 있고,
// 파일이 읽는 문장이 앱 문장과 같은지. 음성 팩은 tools/voice/generate.py build 로 만든다.

class VoiceLinesTests {

    private val voiceDir = File("src/main/assets/voice")

    /** 앱이 읽을 수 있는 모든 줄 (문제·빈칸·OX 의 모든 보기 값·구구단 외우기) */
    private fun allLines(): List<VoiceLine> = buildList {
        for (a in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            for (b in Problems.MIN_B..Problems.MAX_B) {
                val p = Problem(a, b)
                add(VoiceLines.question(p, GameMode.PRACTICE, null))
                add(VoiceLines.question(p, GameMode.MISSING, null))
                for (shown in (listOf(a * b) + Problems.statementCandidates(p)).toSet()) {
                    add(VoiceLines.question(p, GameMode.TRUEFALSE, Statement(shown, shown == a * b)))
                }
                add(VoiceLines.chant(a, b))
            }
        }
    }

    @Test fun questionKeysPerMode() {
        val p = Problem(7, 8)
        for (mode in listOf(GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.ADVENTURE)) {
            assertEquals(VoiceLine("q-7x8", "칠 곱하기 팔은?"), VoiceLines.question(p, mode, null))
        }
        assertEquals("m-7x8", VoiceLines.question(p, GameMode.MISSING, null).key)
        assertEquals(VoiceLine("ox-7x8-49", "칠 곱하기 팔은 사십구. 맞을까요?"),
            VoiceLines.question(p, GameMode.TRUEFALSE, Statement(49, false)))
        assertEquals(VoiceLine("c-7x8", "칠 팔은 오십육."), VoiceLines.chant(7, 8))
        assertEquals(VoiceLine("c-3x2", "삼, 이는 육."), VoiceLines.chant(3, 2))
    }

    @Test fun voicePackCoversEveryLine() {
        val manifestFile = File(voiceDir, "manifest.json")
        assertTrue(manifestFile.exists(), "음성 팩이 없습니다 — python3 tools/voice/generate.py build 를 실행하세요")
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val packed = manifest.getValue("lines").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val lines = allLines()
        assertEquals(lines.map { it.key }.toSet(), packed.keys, "앱 문장 목록과 음성 팩 목록이 다릅니다")
        for (line in lines) {
            assertEquals(line.text, packed[line.key], "문장이 바뀌었습니다 — 음성 팩을 다시 만드세요: ${line.key}")
            assertTrue(File(voiceDir, "${line.key}.m4a").exists(), "음성 파일 없음: ${line.key}")
        }
    }
}
