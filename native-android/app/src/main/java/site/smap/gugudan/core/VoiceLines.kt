package site.smap.gugudan.core

// 소리 내어 읽을 문장 → 미리 만든 음성 파일(키) 연결 (iOS Core/VoiceLines.swift 이식).
// 문장이 552개로 한정돼 있어 ElevenLabs 로 한 번 생성해 앱에 넣었다 (tools/voice).
// 키 규칙은 tools/voice/phrases.py 와 같고, 두 쪽이 어긋나면 VoiceLinesTests 가 실패한다.
// 키는 언어와 무관하고, 파일은 언어별 폴더(assets/voice/<언어>/)에 있다.

/** 읽을 한 줄 — key 로 음성 파일을 찾고, text 는 그 파일이 읽는 문장이다 */
data class VoiceLine(val key: String, val text: String)

object VoiceLines {
    /** 세션 문제 낭독 — 모드별 문장 (SpokenMath.question 과 같은 글) */
    fun question(p: Problem, mode: GameMode, statement: Statement?, language: AppLanguage = AppLanguage.current): VoiceLine =
        when {
            mode == GameMode.MISSING ->
                VoiceLine("m-${p.a}x${p.b}", SpokenMath.question(p, GameMode.MISSING, null, language))
            mode == GameMode.TRUEFALSE && statement != null ->
                VoiceLine("ox-${p.a}x${p.b}-${statement.shown}", SpokenMath.question(p, GameMode.TRUEFALSE, statement, language))
            else -> plain(p, language)
        }

    /**
     * 구구단 외우기 한 줄 — "칠 팔은 오십육." / "Seven times eight is fifty-six." / "しちは ごじゅうろく。"
     * 한국어는 화면 글(KoreanReading.chant)과 달리 마침표로 끝낸다 — 줄마다 끝을 내려 읽는 억양이 나오게 한다.
     * 곱하는 수가 2면 "삼, 이는 육."처럼 쉼표로 끊는다 — 붙여 읽으면 '이'가 앞 숫자와 섞여 "삼위는"으로 들린다(음성 검수에서 확인)
     */
    fun chant(a: Int, b: Int, language: AppLanguage = AppLanguage.current): VoiceLine {
        val key = "c-${a}x$b"
        return when (language) {
            AppLanguage.KO -> {
                val head = KoreanReading.sino(a) + if (b == 2) "," else ""
                VoiceLine(key, "$head ${KoreanReading.topic(KoreanReading.sino(b))} ${KoreanReading.sino(a * b)}.")
            }
            AppLanguage.EN -> VoiceLine(key, EnglishReading.chant(a, b))
            AppLanguage.JA -> VoiceLine(key, JapaneseReading.chant(a, b) + "。")
        }
    }

    private fun plain(p: Problem, language: AppLanguage) =
        VoiceLine("q-${p.a}x${p.b}", SpokenMath.question(p, GameMode.PRACTICE, null, language))
}
