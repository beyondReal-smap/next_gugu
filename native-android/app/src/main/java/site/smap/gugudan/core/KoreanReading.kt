package site.smap.gugudan.core

// 한국어 읽기 (iOS Core/KoreanReading.swift 이식) — 문제 낭독(TTS)과 구구단 외우기 문장을 만든다.
// 숫자를 그대로 음성 엔진에 넘기면 "4"를 "사"/"넷"으로 제각각 읽고 조사(은/는)도 틀리므로
// 한자어 수사와 받침 조사를 여기서 직접 만든다.

object KoreanReading {
    private val DIGITS = listOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val UNITS = listOf(1000 to "천", 100 to "백", 10 to "십")

    /** 한자어 수사 (0~9999) — 12 → "십이", 56 → "오십육", 100 → "백", 0 → "영".
     *  자리 값이 1이면 "일"을 생략한다 (십, 백, 천). */
    fun sino(n: Int): String {
        require(n in 0 until 10_000) { "읽을 수 없는 수: $n" }
        if (n == 0) return "영"
        val sb = StringBuilder()
        var rest = n
        for ((value, name) in UNITS) {
            val q = rest / value
            if (q > 0) sb.append(if (q == 1) "" else DIGITS[q]).append(name)
            rest %= value
        }
        if (rest > 0) sb.append(DIGITS[rest])
        return sb.toString()
    }

    /** 마지막 음절에 받침이 있는지 — 한글 음절(가~힣)이 아니면 false */
    fun hasBatchim(word: String): Boolean {
        val last = word.lastOrNull() ?: return false
        val v = last.code
        if (v !in 0xAC00..0xD7A3) return false
        return (v - 0xAC00) % 28 != 0
    }

    /** 방향 조사 로/으로 — 받침이 없거나 ㄹ 받침이면 "로" ("학교로", "서울로"), 그 밖은 "으로" ("들판으로") */
    fun toward(word: String): String {
        val last = word.lastOrNull()?.code ?: return word
        val rieul = last in 0xAC00..0xD7A3 && (last - 0xAC00) % 28 == 8
        return word + if (!hasBatchim(word) || rieul) "로" else "으로"
    }

    /** 주제 조사 은/는을 붙인다 — "삼" → "삼은", "구" → "구는" */
    fun topic(word: String): String = word + if (hasBatchim(word)) "은" else "는"

    /**
     * 세션 문제 낭독문
     * - 일반: "사 곱하기 구는?"
     * - 빈칸 추리: "삼십육은 사 곱하기 몇일까요?"
     *   숫자 바로 뒤에 '이'로 시작하는 말을 붙이지 않는다 — "십이일까요"는 "십일까요"로, "십이 될까요"(10+이)는 12로 들린다
     * - OX 퀴즈: "사 곱하기 구는 삼십육. 맞을까요?"
     */
    fun question(p: Problem, mode: GameMode, statement: Statement?): String {
        val a = sino(p.a)
        val b = sino(p.b)
        return when (mode) {
            GameMode.MISSING -> "${topic(sino(p.a * p.b))} $a 곱하기 몇일까요?"
            GameMode.TRUEFALSE ->
                if (statement == null) "$a 곱하기 ${topic(b)}?"
                else "$a 곱하기 ${topic(b)} ${sino(statement.shown)}. 맞을까요?"
            else -> "$a 곱하기 ${topic(b)}?"
        }
    }

    /** 구구단 외우기 한 줄 — "칠 팔은 오십육" */
    fun chant(a: Int, b: Int): String = "${sino(a)} ${topic(sino(b))} ${sino(a * b)}"

    // MARK: 화면 문구용 (숫자는 아라비아 숫자, 조사는 한자어 읽기 기준)

    /** 주제 조사 — "1" → "1은", "2" → "2는" (화면 낭독용 라벨) */
    fun withTopic(n: Int): String = "$n" + if (hasBatchim(sino(n))) "은" else "는"

    /** 주격 조사 — "36" → "36이", "35" → "35가" */
    fun withSubject(n: Int): String = "$n" + if (hasBatchim(sino(n))) "이" else "가"

    /** 서술형 — "36" → "36이에요", "35" → "35예요" */
    fun withCopula(n: Int): String = "$n" + if (hasBatchim(sino(n))) "이에요" else "예요"

    /** 오답 설명 — "35가 아니라 36이에요" */
    fun notButIs(given: Int, answer: Int): String = "${withSubject(given)} 아니라 ${withCopula(answer)}"
}
