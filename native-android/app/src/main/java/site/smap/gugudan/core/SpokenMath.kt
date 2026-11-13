package site.smap.gugudan.core

import site.smap.gugudan.R

// 언어별 읽기 고르기 (iOS Core/SpokenMath.swift 이식) — 화면 낭독 라벨·오답 설명·문제 낭독문을 앱 언어에 맞는 읽기 규칙에서 가져온다.
// 언어가 셋뿐이라 인터페이스 없이 when 하나로 고른다. 테스트는 language 를 넘겨 세 언어를 한 번에 확인한다.

object SpokenMath {
    /** 세션 문제 낭독문 — 음성 파일 문장과 같고, 접근성 라벨로도 쓴다 */
    fun question(p: Problem, mode: GameMode, statement: Statement?, language: AppLanguage = AppLanguage.current): String =
        when (language) {
            AppLanguage.KO -> KoreanReading.question(p, mode, statement)
            AppLanguage.EN -> EnglishReading.question(p, mode, statement)
            AppLanguage.JA -> JapaneseReading.question(p, mode, statement)
            AppLanguage.HI -> when {
                mode == GameMode.MISSING -> "${p.a} को किस संख्या से गुणा करने पर ${p.a * p.b} मिलता है?"
                mode == GameMode.TRUEFALSE && statement != null -> "${p.a} गुणा ${p.b} बराबर ${statement.shown}। सही या गलत?"
                else -> questionLabel(p.a, p.b, language)
            }
            AppLanguage.VI -> when {
                mode == GameMode.MISSING -> "${p.a} nhân bao nhiêu bằng ${p.a * p.b}?"
                mode == GameMode.TRUEFALSE && statement != null -> "${p.a} nhân ${p.b} bằng ${statement.shown}. Đúng hay sai?"
                else -> questionLabel(p.a, p.b, language)
            }
            AppLanguage.ID -> when {
                mode == GameMode.MISSING -> "${p.a} dikali berapa hasilnya ${p.a * p.b}?"
                mode == GameMode.TRUEFALSE && statement != null -> "${p.a} kali ${p.b} sama dengan ${statement.shown}. Benar atau salah?"
                else -> questionLabel(p.a, p.b, language)
            }
        }

    /** 구구단 표 화면의 읽기 열 — 영어는 "7 × 8 = 56"과 같은 내용이라 숨긴다(null) */
    fun chantText(a: Int, b: Int, language: AppLanguage = AppLanguage.current): String? = when (language) {
        AppLanguage.KO -> KoreanReading.chant(a, b)
        AppLanguage.EN, AppLanguage.HI, AppLanguage.VI, AppLanguage.ID -> null
        AppLanguage.JA -> JapaneseReading.chant(a, b)
    }

    /** 문제 낭독 라벨 — "7 곱하기 8은?" / "What is 7 times 8?" / "7 かける 8 は？" */
    fun questionLabel(a: Int, b: Int, language: AppLanguage = AppLanguage.current): String = when (language) {
        AppLanguage.KO -> "$a 곱하기 ${KoreanReading.withTopic(b)}?"  // i18n:skip
        AppLanguage.EN -> EnglishReading.questionLabel(a, b)
        AppLanguage.JA -> JapaneseReading.questionLabel(a, b)
        AppLanguage.HI -> "$a गुणा $b कितना होता है?"
        AppLanguage.VI -> "$a nhân $b bằng bao nhiêu?"
        AppLanguage.ID -> "Berapa $a kali $b?"
    }

    /** 개수 뒤에 붙는 단위("12 개" / "12 こ") — 영어는 단위 없이 숫자만 쓴다(null) */
    fun countSuffix(strings: Strings): String? =
        if (strings.language == AppLanguage.EN) null else strings.get(R.string.count)

    /** 낭독 라벨 두 문장 잇기 — 일본어는 "。", 한국어·영어는 ". " (빈 문장은 뺀다) */
    fun sentences(vararg parts: String?, language: AppLanguage = AppLanguage.current): String =
        parts.filterNot { it.isNullOrEmpty() }.joinToString(if (language == AppLanguage.JA) "。" else ". ")

    /** 곱셈식 라벨 — "7 곱하기 8" / "7 times 8" / "7 かける 8" */
    fun factLabel(a: Int, b: Int, language: AppLanguage = AppLanguage.current): String = when (language) {
        AppLanguage.KO -> "$a 곱하기 $b"  // i18n:skip
        AppLanguage.EN -> "$a times $b"
        AppLanguage.JA -> "$a かける $b"
        AppLanguage.HI -> "$a गुणा $b"
        AppLanguage.VI -> "$a nhân $b"
        AppLanguage.ID -> "$a kali $b"
    }

    /** 식 낭독 라벨 — "7 곱하기 8은 56" / "7 times 8 is 56" / "7 かける 8 は 56" */
    fun equationLabel(a: Int, b: Int, language: AppLanguage = AppLanguage.current): String = when (language) {
        AppLanguage.KO -> "$a 곱하기 ${KoreanReading.withTopic(b)} ${a * b}"  // i18n:skip
        AppLanguage.EN -> EnglishReading.equationLabel(a, b)
        AppLanguage.JA -> JapaneseReading.equationLabel(a, b)
        AppLanguage.HI -> "$a गुणा $b बराबर ${a * b}"
        AppLanguage.VI -> "$a nhân $b bằng ${a * b}"
        AppLanguage.ID -> "$a kali $b sama dengan ${a * b}"
    }

    /** 정답 안내 — "정답은 56이에요" */
    fun answerIs(n: Int, language: AppLanguage = AppLanguage.current): String = when (language) {
        AppLanguage.KO -> "정답은 ${KoreanReading.withCopula(n)}"  // i18n:skip
        AppLanguage.EN -> EnglishReading.answerIs(n)
        AppLanguage.JA -> JapaneseReading.answerIs(n)
        AppLanguage.HI -> "उत्तर $n है।"
        AppLanguage.VI -> "Đáp án là $n."
        AppLanguage.ID -> "Jawabannya $n."
    }

    /** 오답 설명 — "54가 아니라 56이에요" */
    fun notButIs(given: Int, answer: Int, language: AppLanguage = AppLanguage.current): String = when (language) {
        AppLanguage.KO -> KoreanReading.notButIs(given, answer)
        AppLanguage.EN -> EnglishReading.notButIs(given, answer)
        AppLanguage.JA -> JapaneseReading.notButIs(given, answer)
        AppLanguage.HI -> "उत्तर $given नहीं, $answer है।"
        AppLanguage.VI -> "Không phải $given, mà là $answer."
        AppLanguage.ID -> "Bukan $given, melainkan $answer."
    }
}
