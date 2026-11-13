package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 다국어 리소스 — 세 언어 키 집합이 같아야 한국어 기기에 영어가 새지 않는다 (iOS LocalizationTests 와 같은 취지)
class LocalizationTests {

    @Test fun newLanguagesHaveCompleteResources() {
        for (folder in listOf("values-hi", "values-vi", "values-in")) {
            assertEquals(names("values"), names(folder), folder)
        }
        for (code in listOf("hi", "vi", "id")) {
            val language = AppLanguage.of(code)
            assertEquals(code, TestStrings(language).get(R.string.app_language_code))
            assertTrue(TestStrings(language).get(R.string.learn).isNotBlank())
        }
    }


    private fun names(folder: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"(?![^>]*translatable="false")""")
            .findAll(File("src/main/res/$folder/strings.xml").readText()).map { it.groupValues[1] }.toSet()

    @Test fun everyLanguageHasTheSameKeys() {
        val en = names("values")
        assertTrue("app_name" in en && "app_language_code" in en)
        assertEquals(en, names("values-ko"), "values-ko 키 차이")
        assertEquals(en, names("values-ja"), "values-ja 키 차이")
    }

    @Test fun resolvesAppNameAndLanguagePerLocale() {
        assertEquals("구구 어드벤처", TestStrings(AppLanguage.KO).get(R.string.app_name))
        assertEquals("Gugu Adventure", TestStrings(AppLanguage.EN).get(R.string.app_name))
        assertEquals("九九アドベンチャー", TestStrings(AppLanguage.JA).get(R.string.app_name))
        for (language in AppLanguage.entries) {
            assertEquals(language.code, TestStrings(language).get(R.string.app_language_code))
        }
    }

    @Test fun defaultResourcesAreEnglish() {
        // 기본 values/ = 영어 — fr 기기 등 미지원 언어는 영어로 보인다 (resources.properties 와 일치)
        assertTrue(File("src/main/res/resources.properties").readText().contains("unqualifiedResLocale=en-US"))
        assertEquals("en", Regex("""name="app_language_code">(\w+)<""")
            .find(File("src/main/res/values/strings.xml").readText())!!.groupValues[1])
    }

    @Test fun appLanguageCodes() {
        assertEquals(AppLanguage.JA, AppLanguage.of("ja"))
        assertFailsWith<IllegalStateException> { AppLanguage.of("fr") }
        AppLanguage.update("ja", "KR")
        assertEquals(java.util.Locale("ja", "KR"), AppLanguage.formatLocale)
        AppLanguage.update("ko", "KR")
    }
}
