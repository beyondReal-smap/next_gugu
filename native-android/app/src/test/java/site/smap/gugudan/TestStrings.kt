package site.smap.gugudan

import org.w3c.dom.Element
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Strings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 단위 테스트용 [Strings] — 생성된 strings.xml(values · values-ko · values-ja)을 직접 읽는다 (Robolectric 없이).
 * 리소스 ID → 이름은 R 클래스 필드로 찾고, 해당 언어에 없으면 기본 values/(영어)로 폴백한다 — 실제 해석 규칙과 같다.
 */
class TestStrings(private val language: AppLanguage = AppLanguage.KO) : Strings {

    override fun get(id: Int, vararg args: Any): String {
        val name = names(R.string::class.java)[id] ?: error("R.string 에 없는 ID: $id")
        val text = (table(language).strings[name] ?: table(null).strings[name]) ?: error("문자열 없음: $name")
        return if (args.isEmpty()) text else String.format(locale, text, *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = names(R.plurals::class.java)[id] ?: error("R.plurals 에 없는 ID: $id")
        val forms = (table(language).plurals[name] ?: table(null).plurals[name]) ?: error("복수형 없음: $name")
        // 영어만 one/other, 한국어·일본어는 other 하나
        val quantity = if (language == AppLanguage.EN && count == 1 && "one" in forms) "one" else "other"
        return String.format(locale, forms.getValue(quantity), *args)
    }

    private val locale: Locale get() = Locale(language.code)

    private class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    companion object {
        private val tables = mutableMapOf<AppLanguage?, Table>()
        private val ids = mutableMapOf<Class<*>, Map<Int, String>>()

        private fun names(type: Class<*>): Map<Int, String> = synchronized(ids) {
            ids.getOrPut(type) { type.fields.associate { it.getInt(null) to it.name } }
        }

        /** null = 기본 values/(영어) */
        private fun table(language: AppLanguage?): Table = synchronized(tables) {
            tables.getOrPut(language) {
                val folder = when (language) {
                    null, AppLanguage.EN -> "values"
                    else -> "values-${language.code}"
                }
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(File("src/main/res/$folder/strings.xml"))
                val strings = mutableMapOf<String, String>()
                val plurals = mutableMapOf<String, Map<String, String>>()
                val stringNodes = doc.getElementsByTagName("string")
                for (i in 0 until stringNodes.length) {
                    val e = stringNodes.item(i) as Element
                    strings[e.getAttribute("name")] = unescape(e.textContent)
                }
                val pluralNodes = doc.getElementsByTagName("plurals")
                for (i in 0 until pluralNodes.length) {
                    val e = pluralNodes.item(i) as Element
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to unescape(item.textContent)
                    }
                }
                Table(strings, plurals)
            }
        }

        /** aapt 이스케이프 해제 — 감싼 큰따옴표, \' \" \n \t \\ \@ \? */
        private fun unescape(raw: String): String {
            val text = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    out.append(when (val n = text[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> n })
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
