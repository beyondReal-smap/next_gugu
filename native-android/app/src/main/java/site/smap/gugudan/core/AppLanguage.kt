package site.smap.gugudan.core

import java.util.Locale

// 앱 언어 — 화면 문구·음성 팩·폰트·서버 locale 이 모두 이 값을 따른다 (AppLanguage.swift 이식).
// Locale.getDefault() 가 아니라 리소스가 실제로 고른 값(R.string.app_language_code)으로 정한다 —
// fr 기기는 Locale 이 fr 이지만 화면은 기본 values/(영어)다.
// Android 13+ 앱별 언어를 바꾸면 프로세스는 그대로 두고 Activity 만 다시 만든다 → MainActivity.onCreate 마다 다시 정한다.

enum class AppLanguage(val code: String) {
    KO("ko"), EN("en"), JA("ja"), HI("hi"), VI("vi"), ID("id");

    val supportsReadAloud: Boolean
        get() = this == KO || this == EN || this == JA

    /** 그 언어로 쓴 언어 이름 — 어느 언어 화면에서든 자기 언어를 알아볼 수 있게 번역하지 않는다 */
    val nativeName: String
        get() = when (this) {
            KO -> "한국어"  // i18n:skip
            EN -> "English"
            JA -> "日本語"
            HI -> "हिन्दी"
            VI -> "Tiếng Việt"
            ID -> "Bahasa Indonesia"
        }

    companion object {
        /** 현재 앱 언어 — MainActivity.onCreate(·서비스 진입점)에서 [update] 로 정한다. 테스트 기본값은 ko */
        @Volatile
        var current: AppLanguage = KO
            private set

        /** 날짜·시각·요일 서식용 locale — 언어는 앱 언어, 지역은 기기 지역 (fr 기기도 영어 서식) */
        @Volatile
        var formatLocale: Locale = Locale.KOREA
            private set

        /** @param code 리소스 값 app_language_code, @param region 기기 지역(ISO 3166, 없으면 "") */
        fun update(code: String, region: String) {
            current = of(code)
            formatLocale = if (region.isEmpty()) Locale(current.code) else Locale(current.code, region)
        }

        fun of(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: error("지원하지 않는 앱 언어 코드: $code")
    }
}
