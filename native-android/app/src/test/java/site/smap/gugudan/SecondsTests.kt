package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Seconds
import java.util.Locale
import kotlin.test.assertEquals

/** 초 표시 — 언어별 단위와 소수 한 자리 (iOS Core/Seconds.swift 와 같은 결과) */
class SecondsTests {
    @Test fun secondsPerLanguage() {
        assertEquals("1.5초", Seconds.text(TestStrings(AppLanguage.KO), 1.46, Locale.KOREA))
        assertEquals("1.5s", Seconds.text(TestStrings(AppLanguage.EN), 1.46, Locale.US))
        assertEquals("0.0びょう", Seconds.text(TestStrings(AppLanguage.JA), 0.0, Locale.JAPAN))
        // 소수점 기호는 서식 locale 을 따른다
        assertEquals("1,5s", Seconds.text(TestStrings(AppLanguage.EN), 1.5, Locale.GERMANY))
    }
}
