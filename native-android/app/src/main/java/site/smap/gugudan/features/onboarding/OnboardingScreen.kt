package site.smap.gugudan.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.store.LocalGame

// 온보딩 (iOS OnboardingView 이식)

private data class Point(val icon: ImageVector, val title: String, val desc: String)

@Composable
fun OnboardingScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current

    val points = listOf(
        Point(Icons.Filled.TrendingUp, "레벨업", "정답마다 XP를 모아 레벨을 올려요"),
        Point(Icons.Filled.LocalFireDepartment, "스트릭", "매일 학습하면 연속 기록이 쌓여요"),
        Point(Icons.Filled.Star, "마스터리", "단별로 별을 모아 완전 정복해요"),
    )

    Column(
        Modifier.fillMaxSize().background(gg.bg).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            Pill(bg = gg.accent.copy(alpha = 0.15f), fg = gg.accent) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp))
                Text("매일 1분 구구단")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                buildAnnotatedString {
                    append("구구단,\n게임처럼\n")
                    withStyle(SpanStyle(color = gg.accent)) { append("재미있게") }
                    append(" 배워요")
                },
                style = suite(FontWeight.ExtraBold, 38).copy(lineHeight = 46.sp),
                color = gg.text,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "레벨 · 스트릭 · 마스터리를 모으며 자연스럽게 구구단을 익혀요.",
                style = suite(FontWeight.Medium, 15), color = gg.textMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            points.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(gg.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(p.icon, null, tint = gg.accent, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(p.title, style = suite(FontWeight.Bold, 16), color = gg.text)
                        Text(p.desc, style = suite(FontWeight.Normal, 14), color = gg.textMuted)
                    }
                }
            }
        }

        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = {
            Haptics.impactMedium()
            game.setOnboarded(true)
        }) {
            Text("시작하기")
        }
    }
}
