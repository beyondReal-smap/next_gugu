package site.smap.gugudan.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * `**강조**` 마커가 든 문구 → 마커 사이에만 [style] 을 입힌 AnnotatedString (마커는 지운다).
 * iOS 는 같은 카탈로그 문자열을 AttributedString(localized:) 의 마크다운 강조로 그린다 —
 * 언어마다 어순이 달라 조각으로 붙이지 않고 한 문장 안에서 강조할 곳만 표시한다.
 */
fun emphasized(text: String, style: SpanStyle): AnnotatedString {
    val parts = text.split("**")
    // 마커가 짝이 맞지 않으면 번역 문자열 결함이다 — 그대로 그리면 ** 가 화면에 보인다
    require(parts.size % 2 == 1) { "강조 마커 ** 의 짝이 맞지 않습니다: $text" }
    return buildAnnotatedString {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(style) { append(part) } else append(part)
        }
    }
}
