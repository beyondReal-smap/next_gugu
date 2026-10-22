package site.smap.gugudan.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import site.smap.gugudan.core.GuardianGate
import site.smap.gugudan.designsystem.*
import java.time.LocalDate

// 이벤트·혜택(광고) 수신 동의 흐름 — 켤 때만 거친다 (끌 때는 즉시).
// 1) 보호자 확인(출생연도 4자리, 저장 안 함) → 2) 동의 시트 → 3) 서버 기록 후 처리 결과 알림창.
// 문구는 iOS 와 글자 단위로 같다 (.claude/plans/native-push-app-spec.md).

/** 보호자 확인 게이트 — 아이가 혼자 광고 동의를 켜지 못하게 한다 */
@Composable
fun GuardianGateSheet(onCancel: () -> Unit, onPassed: () -> Unit) {
    val gg = LocalGG.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    PushSheet(title = "보호자 확인", icon = Icons.Filled.Shield, onDismiss = onCancel) {
        Text(
            "보호자의 출생연도 4자리를 입력해 주세요.",
            style = suite(FontWeight.Medium, 14), color = gg.textMuted, textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { next ->
                input = next.filter { it in '0'..'9' }.take(4)
                error = false
            },
            singleLine = true,
            placeholder = { Text("예: 1985", style = suite(FontWeight.Medium, 16), color = gg.textMuted) },
            textStyle = suite(FontWeight.Bold, 18).copy(color = gg.text, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = gg.accent,
                unfocusedBorderColor = gg.border,
                errorBorderColor = gg.danger,
                cursorColor = gg.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) {
            Text("출생연도를 다시 확인해 주세요.", style = suite(FontWeight.Bold, 13), color = gg.danger)
        }
        SheetButtons(confirmLabel = "확인", busy = false, onCancel = onCancel) {
            // 입력값은 판단에만 쓰고 저장하지 않는다
            if (GuardianGate.isValidBirthYear(input, LocalDate.now().year)) {
                input = ""
                onPassed()
            } else {
                error = true
            }
        }
    }
}

/** 광고 수신 동의 시트 (선택 동의) */
@Composable
fun MarketingConsentSheet(busy: Boolean, onCancel: () -> Unit, onAgree: () -> Unit) {
    val gg = LocalGG.current
    PushSheet(
        title = "이벤트·혜택 알림 수신 동의 (선택)",
        icon = Icons.Filled.CardGiftcard,
        onDismiss = { if (!busy) onCancel() },
    ) {
        Text(
            "구구 어드벤처의 새 기능, 이벤트, 할인 소식을 앱 알림으로 보내 드려요.\n" +
                "· 보내는 곳: 구구 어드벤처\n" +
                "· 받는 시간: 오전 8시 ~ 오후 9시\n" +
                "· 언제든 설정 > 알림에서 끌 수 있어요.\n" +
                "동의하지 않아도 앱을 그대로 쓸 수 있어요.",
            style = suite(FontWeight.Medium, 14), color = gg.text,
            modifier = Modifier.fillMaxWidth(),
        )
        SheetButtons(
            confirmLabel = if (busy) "처리 중…" else "동의하고 켜기",
            busy = busy,
            onCancel = onCancel,
            onConfirm = onAgree,
        )
    }
}

/** 처리 결과 알림창 (정보통신망법 제50조 ⑦ 처리 결과 통지) */
@Composable
fun PushResultDialog(title: String?, message: String, onDismiss: () -> Unit) {
    val gg = LocalGG.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", style = suite(FontWeight.Bold, 15), color = gg.accent)
            }
        },
        title = title?.let { { Text(it, style = suite(FontWeight.ExtraBold, 18), color = gg.text) } },
        text = { Text(message, style = suite(FontWeight.Medium, 14), color = gg.text) },
        containerColor = gg.surface,
    )
}

// MARK: 공통 시트 (ConfirmSheet 와 같은 하단 카드 모양 — 설정 화면 구조를 바꾸지 않도록 별도 창에 띄운다)

@Composable
private fun PushSheet(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gg = LocalGG.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(gg.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = gg.accent, modifier = Modifier.size(26.dp))
                }
                Text(title, style = suite(FontWeight.ExtraBold, 20), color = gg.text, textAlign = TextAlign.Center)
                content()
            }
        }
    }
}

@Composable
private fun SheetButtons(confirmLabel: String, busy: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val gg = LocalGG.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GGButton(
            variant = GGButtonVariant.SURFACE, size = GGButtonSize.MD, enabled = !busy,
            modifier = Modifier.weight(1f), onClick = onCancel,
        ) { Text("취소", color = gg.text) }
        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD, enabled = !busy,
            modifier = Modifier.weight(1f), onClick = onConfirm,
        ) { Text(confirmLabel) }
    }
}
