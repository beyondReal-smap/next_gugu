package site.smap.gugudan.services

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Strings
import site.smap.gugudan.R

/** Context 리소스로 읽는 [Strings] — Activity·서비스·워커가 자기 Context 로 만든다 */
class AndroidStrings(private val context: Context) : Strings {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
    override fun plural(id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)

    companion object {
        /** 리소스가 고른 언어로 [AppLanguage] 를 맞춘다 — 앱 진입점(Activity·FCM 서비스·워커)마다 부른다.
         *  지역은 시스템 설정의 첫 locale — 앱별 언어(예: ja)를 골라도 기기 지역(KR)을 유지한다 */
        fun updateLanguage(context: Context) {
            val system = Resources.getSystem().configuration.locales
            AppLanguage.update(context.getString(R.string.app_language_code), if (system.isEmpty) "" else system[0].country)
        }
    }
}

/** 화면에서 Core 서식 문구(Hints·WeeklyReport·PushCopy 등)에 넘길 [Strings] — 설정(언어)이 바뀌면 새로 만든다 */
@Composable
fun rememberStrings(): Strings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AndroidStrings(context) }
}
