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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.smap.gugudan.R
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
        Point(Icons.Filled.TrendingUp, stringResource(R.string.level_up_2),
            stringResource(R.string.earn_xp_for_every_correct_answer_and)),
        Point(Icons.Filled.LocalFireDepartment, stringResource(R.string.streak),
            stringResource(R.string.practice_every_day_to_grow_your_streak)),
        Point(Icons.Filled.Star, stringResource(R.string.mastery),
            stringResource(R.string.collect_stars_on_each_table_to_master)),
    )

    Column(
        // 태블릿에서 카드·버튼이 화면 끝까지 늘어나지 않게
        Modifier.fillMaxSize().background(gg.bg).systemBarsPadding().readableWidth(560.dp).padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            Pill(bg = gg.accent.copy(alpha = 0.15f), fg = gg.accent) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp))
                Text(stringResource(R.string.times_tables_1_minute_a_day))
            }
            Spacer(Modifier.height(12.dp))
            // 제목 한 문장 — 언어마다 어순이 달라 조각으로 붙이지 않고, ** 로 감싼 부분만 강조색을 입힌다 (iOS 와 같은 문자열)
            Text(
                emphasized(stringResource(R.string.times_tables_made_fun_like_a_game), SpanStyle(color = gg.accent)),
                style = suite(FontWeight.ExtraBold, 38).copy(lineHeight = 46.sp),
                color = gg.text,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.earn_levels_streaks_and_mastery_as_you),
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
            Text(stringResource(R.string.get_started))
        }
    }
}
