package site.smap.gugudan.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.store.AuthStore
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalSync
import site.smap.gugudan.store.SyncStore

// 계정 — 익명 계정에 이메일을 붙여 기기를 바꿔도 기록이 남게 한다 (ProfileScreen 에서 설정 화면으로 이동).
// 계정 삭제(App Store 5.1.1(v) / Play 데이터 삭제 요건)도 여기서 제공한다.

@Composable
fun AccountSettingsCard() {
    val gg = LocalGG.current
    val auth = LocalAuth.current
    val sync = LocalSync.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deleteNotice by remember { mutableStateOf<String?>(null) }

    if (auth.state is AuthStore.State.Disabled) return

    Column(
        Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = gg.accent, modifier = Modifier.size(16.dp))
            Text("기록 지키기", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
        }
        val promotion = auth.promotion
        when {
            promotion is AuthStore.Promotion.Done -> AccountRow(
                Icons.Filled.VerifiedUser, gg.success,
                "${promotion.email} 에 연결됐어요",
                "기기를 바꿔도 이 주소로 기록을 찾을 수 있어요.",
            )
            promotion is AuthStore.Promotion.CodeSent || promotion is AuthStore.Promotion.Verifying -> {
                val target = (promotion as? AuthStore.Promotion.CodeSent)?.email
                    ?: (promotion as AuthStore.Promotion.Verifying).email
                Text(
                    "$target 로 6자리 확인 코드를 보냈어요.",
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    placeholder = { Text("확인 코드") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                GGButton(
                    variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
                    onClick = { scope.launch { auth.confirmEmailPromotion(code) } },
                ) {
                    Text(if (promotion is AuthStore.Promotion.Verifying) "확인 중…" else "연결 완료하기")
                }
                PressableCard(onClick = { auth.resetPromotion(); code = "" }) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text("주소 다시 입력", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                    }
                }
            }
            auth.isPermanent -> AccountRow(
                Icons.Filled.VerifiedUser, gg.success,
                "계정이 연결돼 있어요", "기기를 바꿔도 기록을 찾을 수 있어요.",
            )
            else -> {
                Text(
                    "보호자 이메일을 넣으면 기기를 바꿔도 기록이 남아요. 비밀번호는 필요 없어요.",
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    placeholder = { Text("보호자 이메일") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                GGButton(
                    variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
                    onClick = { scope.launch { auth.startEmailPromotion(email) } },
                ) {
                    Text(if (promotion is AuthStore.Promotion.Sending) "보내는 중…" else "확인 코드 받기")
                }
            }
        }
        // 귀속 후보가 여럿이면 어느 기록에 이어 붙일지 고르게 한다
        (sync.state as? SyncStore.State.NeedsLearnerChoice)?.let { choice ->
            if (choice.candidates.isNotEmpty()) {
                Text("이어서 쓸 기록을 골라 주세요", style = suite(FontWeight.ExtraBold, 13), color = gg.text)
                choice.candidates.forEach { candidate ->
                    PressableCard(onClick = { scope.launch { sync.chooseLearner(candidate, auth) } }) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(12.dp)).background(gg.surface2)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, null, tint = gg.accent, modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.displayName, style = suite(FontWeight.ExtraBold, 13), color = gg.text)
                                Text("마지막 학습 ${candidate.updatedAt.take(10)}",
                                    style = suite(FontWeight.Medium, 11), color = gg.textMuted)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = gg.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 보호자 검증 전이면 왜 보관이 꺼져 있는지 알려 준다
        (sync.state as? SyncStore.State.WaitingForGuardian)?.let {
            Text(it.message, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }

        (promotion as? AuthStore.Promotion.Failed)?.let {
            Text(it.message, style = suite(FontWeight.Bold, 12), color = gg.warning)
        }

        // 계정 삭제 — 되돌릴 수 없으므로 「기록 초기화」와 같은 2단 확인을 둔다
        Box(Modifier.fillMaxWidth().height(1.dp).background(gg.border))

        deleteNotice?.let {
            Text(it, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }

        if (confirmDeleteAccount) {
            Text(
                "계정과 서버에 보관된 학습 기록을 지웁니다. 되돌릴 수 없어요.",
                style = suite(FontWeight.Bold, 13), color = gg.text,
            )
            Text(
                "기기에 있는 기록과 이용권은 그대로예요. 이용권은 「구매 복원」으로 다시 쓸 수 있어요.",
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GGButton(
                    onClick = { confirmDeleteAccount = false },
                    variant = GGButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f),
                ) { Text("취소", style = suite(FontWeight.Bold, 14), color = gg.text) }
                GGButton(
                    onClick = {
                        if (!deletingAccount) {
                            deletingAccount = true
                            deleteNotice = null
                            scope.launch {
                                val ok = auth.deleteAccount()
                                if (ok) sync.resetAfterAccountDeletion()
                                deletingAccount = false
                                confirmDeleteAccount = false
                                deleteNotice = if (ok) {
                                    "계정과 서버에 보관된 학습 기록을 지웠어요."
                                } else {
                                    "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
                                }
                            }
                        }
                    },
                    variant = GGButtonVariant.DANGER,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (deletingAccount) "삭제 중…" else "삭제 확인",
                        style = suite(FontWeight.Bold, 14), color = gg.accentFg,
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable {
                        deleteNotice = null
                        confirmDeleteAccount = true
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PersonRemove, null, tint = gg.danger, modifier = Modifier.size(18.dp))
                Text("계정 삭제", style = suite(FontWeight.Bold, 13), color = gg.danger)
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, tint: Color, title: String, desc: String) {
    val gg = LocalGG.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
            Text(desc, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }
    }
}
