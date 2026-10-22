package site.smap.gugudan.core

// 영어 읽기 (iOS Core/EnglishReading.swift 이식) — 문제 낭독(음성 팩)과 화면 낭독 라벨을 만든다.
// 영어권에는 한국어 "칠 팔 오십육" 같은 전통 낭송이 없어 "Seven times eight is fifty-six." 문장형으로 읽는다.
// 음성 파일 문장과 글자 단위로 같아야 하므로(tools/voice/phrases.py) 규칙을 바꾸면 음성도 다시 만든다.

object EnglishReading {
    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** 기수 (0~99) — 56 → "fifty-six", 40 → "forty", 13 → "thirteen" */
    fun cardinal(n: Int): String {
        require(n in 0 until 100) { "읽을 수 없는 수: $n" }
        if (n < 20) return ONES[n]
        val unit = n % 10
        return TENS[n / 10] + if (unit == 0) "" else "-" + ONES[unit]
    }

    /** 문장 첫 글자만 대문자로 */
    private fun sentence(text: String): String = text.take(1).uppercase() + text.drop(1)

    /**
     * 세션 문제 낭독문
     * - 일반: "What is seven times eight?"
     * - 빈칸 추리: "Fifty-six is seven times what?"
     * - OX(True or False): "Seven times eight is fifty-four. True or false?"
     */
    fun question(p: Problem, mode: GameMode, statement: Statement?): String {
        val a = cardinal(p.a)
        val b = cardinal(p.b)
        return when (mode) {
            GameMode.MISSING -> sentence("${cardinal(p.a * p.b)} is $a times what?")
            GameMode.TRUEFALSE ->
                if (statement == null) "What is $a times $b?"
                else sentence("$a times $b is ${cardinal(statement.shown)}. True or false?")
            else -> "What is $a times $b?"
        }
    }

    /** 구구단 외우기 한 줄 (음성) — "Seven times eight is fifty-six." */
    fun chant(a: Int, b: Int): String = sentence("${cardinal(a)} times ${cardinal(b)} is ${cardinal(a * b)}.")

    // MARK: 화면 문구용 (숫자는 아라비아 숫자)

    /** "What is 7 times 8?" */
    fun questionLabel(a: Int, b: Int): String = "What is $a times $b?"

    /** "7 times 8 is 56" */
    fun equationLabel(a: Int, b: Int): String = "$a times $b is ${a * b}"

    /** 정답 안내 — "The answer is 56." */
    fun answerIs(n: Int): String = "The answer is $n."

    /** 오답 설명 — "Not 54. It's 56." */
    fun notButIs(given: Int, answer: Int): String = "Not $given. It's $answer."
}
