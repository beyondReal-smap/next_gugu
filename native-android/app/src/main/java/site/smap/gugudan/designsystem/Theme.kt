package site.smap.gugudan.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import site.smap.gugudan.R
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Theme as AppTheme

// 디자인 토큰 — iOS Theme.swift / globals.css 팔레트 이식 (라이트/다크 명시 선택)

data class GGColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentFg: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    companion object {
        // 모드 틴트 (Tailwind 500) — 공용
        val sky = Color(0xFF0EA5E9)
        val amber = Color(0xFFF59E0B)
        val rose = Color(0xFFF43F5E)
        val violet = Color(0xFF8B5CF6)
        val emerald = Color(0xFF10B981)
        val indigo = Color(0xFF6366F1)
        val purple = Color(0xFF9333EA)
    }
}

val LightColors = GGColors(
    bg = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1F5F9),
    text = Color(0xFF0F172A), textMuted = Color(0xFF64748B), border = Color(0xFFE2E8F0),
    accent = Color(0xFF6366F1), accentStrong = Color(0xFF4F46E5), accentFg = Color(0xFFFFFFFF),
    success = Color(0xFF10B981), warning = Color(0xFFF59E0B), danger = Color(0xFFF43F5E),
    isDark = false,
)

val DarkColors = GGColors(
    bg = Color(0xFF080B16), surface = Color(0xFF111727), surface2 = Color(0xFF1E273C),
    text = Color(0xFFF1F5F9), textMuted = Color(0xFF94A3B8), border = Color(0xFF273149),
    accent = Color(0xFF818CF8), accentStrong = Color(0xFFA5B4FC), accentFg = Color(0xFF0A0D19),
    success = Color(0xFF34D399), warning = Color(0xFFFBBF24), danger = Color(0xFFFB7185),
    isDark = true,
)

val LocalGG = staticCompositionLocalOf { DarkColors }

// SUITE 폰트
val Suite = FontFamily(
    Font(R.font.suite_light, FontWeight.Light),
    Font(R.font.suite_regular, FontWeight.Normal),
    Font(R.font.suite_medium, FontWeight.Medium),
    Font(R.font.suite_semibold, FontWeight.SemiBold),
    Font(R.font.suite_bold, FontWeight.Bold),
    Font(R.font.suite_extrabold, FontWeight.ExtraBold),
    Font(R.font.suite_heavy, FontWeight.Black),
)

// 일본어 폰트(M PLUS Rounded 1c, OFL 서브셋 — tools/i18n/subset_ja_font.py) — SUITE 에는 가나·한자 글리프가 없다.
// 둥근 글꼴이라 브랜드 톤에 맞고 iOS 와 같은 서브셋이다. M+ 에 SemiBold 가 없어 Bold 로 맞춘다 (Theme.swift 와 같음)
val MPlusRounded = FontFamily(
    Font(R.font.mplusrounded1c_light, FontWeight.Light),
    Font(R.font.mplusrounded1c_regular, FontWeight.Normal),
    Font(R.font.mplusrounded1c_medium, FontWeight.Medium),
    Font(R.font.mplusrounded1c_bold, FontWeight.SemiBold),
    Font(R.font.mplusrounded1c_bold, FontWeight.Bold),
    Font(R.font.mplusrounded1c_extrabold, FontWeight.ExtraBold),
    Font(R.font.mplusrounded1c_black, FontWeight.Black),
)

/** 앱 언어에 맞는 글꼴 — 화면은 언어가 바뀌면 Activity 째 다시 그려지므로 그때 다시 고른다 */
fun suite(weight: FontWeight, size: Int) = TextStyle(
    fontFamily = when (AppLanguage.current) {
        AppLanguage.JA -> MPlusRounded
        AppLanguage.HI, AppLanguage.VI, AppLanguage.ID -> FontFamily.Default
        AppLanguage.KO, AppLanguage.EN -> Suite
    },
    fontWeight = weight, fontSize = size.sp,
)

/** hex 문자열(#rrggbb) → Color (어드벤처 NPC/테마 색용) */
fun hexColor(hex: String): Color {
    val s = hex.removePrefix("#")
    val v = s.toLong(16)
    return Color(
        red = ((v shr 16) and 0xff) / 255f,
        green = ((v shr 8) and 0xff) / 255f,
        blue = (v and 0xff) / 255f,
    )
}

/** 테마 설정을 실제 다크 여부로 — SYSTEM 은 기기 설정을 따른다 */
@Composable
fun resolvedDark(theme: AppTheme): Boolean = when (theme) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.DARK -> true
    AppTheme.LIGHT -> false
}

@Composable
fun GuguTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = if (resolvedDark(theme)) DarkColors else LightColors
    CompositionLocalProvider(LocalGG provides colors, content = content)
}
