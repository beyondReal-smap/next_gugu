package site.smap.gugudan.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.designsystem.GGButton
import site.smap.gugudan.designsystem.GGButtonVariant
import site.smap.gugudan.designsystem.LocalGG
import site.smap.gugudan.designsystem.suite

// 미구현 화면 자리표시자 — 각 Phase에서 실제 구현으로 교체
@Composable
fun PhasePlaceholder(title: String, phase: String, onExit: (() -> Unit)? = null) {
    val gg = LocalGG.current
    Column(
        Modifier.fillMaxSize().background(gg.bg).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = suite(FontWeight.ExtraBold, 24), color = gg.text)
        Text("${phase}에서 구현 예정", style = suite(FontWeight.Bold, 13), color = gg.textMuted)
        if (onExit != null) {
            GGButton(variant = GGButtonVariant.SURFACE, onClick = onExit) { Text("닫기") }
        }
    }
}
