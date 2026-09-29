package site.smap.gugudan.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.services.Haptics

// 확인 시트 (iOS ConfirmSheet.swift / 웹 ConfirmSheet.tsx 대응) — 하단 카드 + 딤 배경.
// 아이가 실수로 닫기를 눌러도 쉽게 되돌아가도록 "계속하기"를 큰 기본 버튼으로 둔다.
// 떠 있는 동안 시스템 뒤로가기는 "계속하기"와 같다.

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector = Icons.Filled.Pause,
    tint: Color? = null,
) {
    val gg = LocalGG.current
    val iconTint = tint ?: gg.accent

    BackHandler(enabled = visible, onBack = onCancel)
    LaunchedEffect(visible) { if (visible) Haptics.warning() }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(gg.surface)
                    // 카드 안을 눌러도 배경 탭(취소)으로 새지 않게 막는다
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = title }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = suite(FontWeight.ExtraBold, 20), color = gg.text, textAlign = TextAlign.Center)
                    Text(message, style = suite(FontWeight.Medium, 14), color = gg.textMuted, textAlign = TextAlign.Center)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = onCancel) {
                        Text(cancelLabel)
                    }
                    GGButton(variant = GGButtonVariant.GHOST, size = GGButtonSize.MD, onClick = onConfirm) {
                        Text(confirmLabel, color = gg.danger)
                    }
                }
            }
        }
    }
}
