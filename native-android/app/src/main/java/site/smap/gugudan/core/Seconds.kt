package site.smap.gugudan.core

import site.smap.gugudan.R
import java.util.Locale

// 초 표시 — "1.5초" / "1.5s" / "1.5びょう" (iOS Core/Seconds.swift 이식). 소수점 기호도 앱 언어 서식을 따른다.

object Seconds {
    /** 소수 한 자리 초 — 남은 시간·평균 속도 */
    fun text(strings: Strings, seconds: Double, locale: Locale = AppLanguage.formatLocale): String =
        strings.get(R.string.s_7, String.format(locale, "%.1f", seconds))
}
