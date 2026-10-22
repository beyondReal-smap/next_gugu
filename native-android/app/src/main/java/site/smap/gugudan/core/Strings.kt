package site.smap.gugudan.core

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import site.smap.gugudan.R

// Core 서식 문구가 리소스를 읽는 통로 — 앱은 Context.resources, 테스트는 strings.xml 을 직접 읽는 구현을 넘긴다.
// Core 정적 데이터는 문자열 대신 리소스 ID 를 들고, 화면이나 이 통로에서 그때그때 읽는다 — 앱별 언어를 바꾸면
// 프로세스가 살아 있는 채로 Activity 만 다시 만들어지므로 문자열을 들고 있으면 이전 언어가 남는다.
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/** 이 통로가 읽는 언어 — 리소스 app_language_code (테스트는 TestStrings 언어, 앱은 리소스가 고른 언어) */
val Strings.language: AppLanguage get() = AppLanguage.of(get(R.string.app_language_code))
