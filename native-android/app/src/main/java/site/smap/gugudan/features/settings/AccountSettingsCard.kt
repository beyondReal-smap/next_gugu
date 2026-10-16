package site.smap.gugudan.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.store.AuthStore
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalSync
import site.smap.gugudan.store.SyncStore
import kotlin.math.ceil

// 계정 — 보호자 이메일로 로그인해 이용권과 학습 기록을 지킨다 (ProfileScreen 에서 설정 화면으로 이동).
// 처음 쓰는 주소는 지금 계정에 연결하고, 이미 있는 주소는 그 계정으로 로그인한다.
// 로그아웃·계정 삭제(App Store 5.1.1(v) / Play 데이터 삭제 요건)도 여기서 제공한다.

/** showsAccountManagement=false 면 로그아웃·계정 삭제를 숨긴다 — 결제 직후 로그인 권유(페이월)에서 쓴다 */
@Composable
fun AccountSettingsCard(showsAccountManagement: Boolean = true) {
    val gg = LocalGG.current
    val auth = LocalAuth.current
    val sync = LocalSync.current
    val premium = LocalPremium.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deleteNotice by remember { mutableStateOf<String?>(null) }

    if (auth.state is AuthStore.State.Disabled) return

    // 계정이 바뀔 때(삭제·로그아웃·다른 계정 로그인) 예전 계정에 묶인 기기 귀속·큐·서버 부여 이용권을 버린다
    val clearAccountLocalState: () -> Unit = {
        sync.resetForAccountChange()
        premium.clearServerGrant()
    }

    val requestCode: () -> Unit = { scope.launch { auth.requestEmailCode(email) } }

    // 로그아웃 — 되돌리기 쉬운 동작이지만 서버 사본이 사라질 수 있어 한 번 확인한다
    val signOutControl: @Composable (String) -> Unit = { label ->
        SignOutControl(
            label = label,
            confirming = confirmSignOut,
            signingOut = signingOut,
            pendingCount = sync.pendingCount,
            onAsk = {
                confirmSignOut = true
                // 확인하는 동안 남은 기록을 올려 본다 — 다 올라가면 경고가 사라진다
                scope.launch { sync.flushNow(auth) }
            },
            onCancel = { confirmSignOut = false },
            onConfirm = {
                if (!signingOut) {
                    signingOut = true
                    scope.launch {
                        auth.signOut(onLocalSessionCleared = clearAccountLocalState)
                        signingOut = false
                        confirmSignOut = false
                        email = ""
                        code = ""
                    }
                }
            },
        )
    }

    Column(
        Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = gg.accent, modifier = Modifier.size(16.dp))
            Text("기록 지키기", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
        }
        val promotion = auth.promotion
        val authState = auth.state
        // 코드 입력 중인 주소와 목적 (보낸 뒤·확인 중 모두 같은 화면)
        val codeTarget: Pair<String, AuthStore.CodePurpose>? = when (promotion) {
            is AuthStore.Promotion.CodeSent -> promotion.email to promotion.purpose
            is AuthStore.Promotion.Verifying -> promotion.email to promotion.purpose
            else -> null
        }
        when {
            codeTarget != null -> {
                CodeEntry(
                    email = codeTarget.first,
                    purpose = codeTarget.second,
                    error = (promotion as? AuthStore.Promotion.CodeSent)?.error,
                    verifying = promotion is AuthStore.Promotion.Verifying,
                    pendingCount = sync.pendingCount,
                    code = code,
                    onCodeChange = { code = it },
                    onConfirm = {
                        scope.launch {
                            auth.confirmEmailCode(code, onAccountSwitch = clearAccountLocalState)
                            if (auth.promotion is AuthStore.Promotion.Done) code = ""
                        }
                    },
                    onReset = { auth.resetPromotion(); code = "" },
                    lastCodeSentAt = auth.lastCodeSentAt,
                    onResend = { scope.launch { auth.resendCode() } },
                )
            }
            authState is AuthStore.State.NeedsReauth -> {
                // 기억해 둔 이메일을 미리 채운다
                LaunchedEffect(authState.email) {
                    val remembered = authState.email
                    if (email.isEmpty() && remembered != null) email = remembered
                }
                AccountRow(
                    Icons.Filled.Error, gg.warning,
                    "다시 로그인해 주세요",
                    "로그인이 만료됐어요. 그동안 공부한 기록은 기기에 쌓아 두었다가 다시 로그인하면 올려요.",
                )
                EmailEntry(
                    intro = "보호자 이메일로 받은 6자리 코드를 넣으면 다시 로그인돼요.",
                    hint = null,
                    email = email,
                    onEmailChange = { email = it },
                    sending = promotion is AuthStore.Promotion.Sending,
                    onSubmit = requestCode,
                )
                if (showsAccountManagement) signOutControl("이 기기에서 로그아웃")
            }
            auth.isPermanent -> {
                AccountRow(
                    Icons.Filled.VerifiedUser, gg.success,
                    auth.email?.let { "$it 로 로그인돼 있어요" } ?: "계정이 연결돼 있어요",
                    if (premium.isPremium) {
                        "다른 기기에서도 이 이메일로 로그인하면 이용권과 학습 기록을 이어서 쓸 수 있어요."
                    } else {
                        "다른 기기에서도 이 이메일로 로그인할 수 있어요. 학습 기록 보관은 이용권 사용자에게 켜져요."
                    },
                )
                Text("별·레벨·코인은 이 기기에만 저장돼요.", style = suite(FontWeight.Medium, 12), color = gg.textMuted)
                if (showsAccountManagement) signOutControl("로그아웃")
            }
            else -> EmailEntry(
                intro = "보호자 이메일로 이용권과 기록을 지켜요. 앱을 다시 설치하거나 기기를 바꿔도 같은 이메일로 로그인하면 이어서 쓸 수 있어요. 비밀번호는 필요 없어요.",
                hint = "이미 쓰던 이메일이 있으면 그 주소를 넣어 주세요.",
                email = email,
                onEmailChange = { email = it },
                sending = promotion is AuthStore.Promotion.Sending,
                onSubmit = requestCode,
            )
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

        (promotion as? AuthStore.Promotion.Failed)?.let { WarningText(it.message) }

        if (sync.restoreFailed) WarningText("학습 기록을 불러오지 못했어요. 다음에 다시 시도할게요.")

        if (showsAccountManagement) {
            // 계정 삭제 — 되돌릴 수 없으므로 「기록 초기화」와 같은 2단 확인을 둔다
            Box(Modifier.fillMaxWidth().height(1.dp).background(gg.border))

            deleteNotice?.let {
                Text(it, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
            }

            if (auth.needsReauth) {
                // 삭제 요청엔 유효한 로그인이 필요하다
                Text(
                    "계정 삭제는 다시 로그인한 뒤에 할 수 있어요.",
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
            } else if (confirmDeleteAccount) {
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
                                    // 기기에 남은 신원·아웃박스·서버 부여 이용권은 서버 삭제 직후(익명 재로그인을 기다리기 전)에 정리한다
                                    val ok = auth.deleteAccount(onLocalSessionCleared = clearAccountLocalState)
                                    deletingAccount = false
                                    confirmDeleteAccount = false
                                    if (ok) email = ""
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
                            Haptics.impactLight()
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

/** 이메일 입력 — 익명 계정(연결 또는 로그인)과 재로그인에서 함께 쓴다 */
@Composable
private fun EmailEntry(
    intro: String,
    hint: String?,
    email: String,
    onEmailChange: (String) -> Unit,
    sending: Boolean,
    onSubmit: () -> Unit,
) {
    val gg = LocalGG.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(intro, style = suite(FontWeight.Medium, 13), color = gg.textMuted)
        hint?.let { Text(it, style = suite(FontWeight.Medium, 12), color = gg.textMuted) }
        OutlinedTextField(
            value = email, onValueChange = onEmailChange,
            textStyle = suite(FontWeight.Medium, 15),
            placeholder = { Text("보호자 이메일", style = suite(FontWeight.Medium, 15)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true, colors = ggTextFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD, onClick = onSubmit) {
            Text(if (sending) "보내는 중…" else "확인 코드 받기")
        }
    }
}

@Composable
private fun CodeEntry(
    email: String,
    purpose: AuthStore.CodePurpose,
    error: String?,
    verifying: Boolean,
    pendingCount: Int,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    lastCodeSentAt: Long?,
    onResend: () -> Unit,
) {
    val gg = LocalGG.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (purpose) {
            AuthStore.CodePurpose.LINK ->
                Text("$email 로 6자리 확인 코드를 보냈어요.", style = suite(FontWeight.Medium, 13), color = gg.textMuted)
            AuthStore.CodePurpose.SIGN_IN -> {
                Text(
                    "$email 은 이미 쓰던 계정이에요. 메일로 받은 6자리 코드를 넣으면 그 계정으로 로그인해요.",
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                )
                Text(
                    "이 기기의 별·레벨·코인은 그대로 두고, 계정의 이용권과 학습 기록을 불러와요.",
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
                if (pendingCount > 0) {
                    WarningText("다른 계정으로 로그인하면 아직 서버에 올리지 못한 학습 기록 ${pendingCount}개는 서버에 남지 않아요.")
                }
            }
        }
        OutlinedTextField(
            value = code, onValueChange = onCodeChange,
            textStyle = suite(FontWeight.ExtraBold, 20),
            placeholder = { Text("확인 코드", style = suite(FontWeight.ExtraBold, 20)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, colors = ggTextFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { WarningText(it) }
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD, onClick = onConfirm) {
            Text(
                when {
                    verifying -> "확인 중…"
                    purpose == AuthStore.CodePurpose.SIGN_IN -> "로그인"
                    else -> "연결 완료하기"
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PressableCard(onClick = onReset) {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    Text("주소 다시 입력", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                }
            }
            ResendButton(lastCodeSentAt, onResend)
        }
    }
}

/**
 * 입력칸 색 — Material 기본값은 앱 테마(LocalGG)를 몰라 다크 테마에서도 글자가 검게 나온다.
 * 글자·커서·테두리·플레이스홀더를 앱 색 토큰으로 맞춘다.
 */
@Composable
private fun ggTextFieldColors(): TextFieldColors {
    val gg = LocalGG.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = gg.text,
        unfocusedTextColor = gg.text,
        cursorColor = gg.accent,
        focusedBorderColor = gg.accent,
        unfocusedBorderColor = gg.border,
        focusedPlaceholderColor = gg.textMuted,
        unfocusedPlaceholderColor = gg.textMuted,
        focusedContainerColor = gg.surface2,
        unfocusedContainerColor = gg.surface2,
    )
}

/** 코드 재발송 — 서버 제한(같은 주소 60초)에 맞춰 남은 시간을 보여 준다 */
@Composable
private fun ResendButton(lastCodeSentAt: Long?, onResend: () -> Unit) {
    val gg = LocalGG.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastCodeSentAt) {
        while (true) {
            now = System.currentTimeMillis()
            val sentAt = lastCodeSentAt ?: break
            if (now - sentAt >= AuthStore.RESEND_INTERVAL_MILLIS) break
            delay(1_000)
        }
    }
    val remaining = lastCodeSentAt
        ?.let { ceil((AuthStore.RESEND_INTERVAL_MILLIS - (now - it)) / 1000.0).toInt() }
        ?: 0
    PressableCard(enabled = remaining <= 0, onClick = onResend) {
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            Text(
                if (remaining > 0) "코드 다시 보내기 (${remaining}초)" else "코드 다시 보내기",
                style = suite(FontWeight.Bold, 12), color = gg.textMuted,
            )
        }
    }
}

/** 로그아웃 — 누르면 확인 화면을 펼치고, 미업로드 기록이 남아 있으면 경고한다 */
@Composable
private fun SignOutControl(
    label: String,
    confirming: Boolean,
    signingOut: Boolean,
    pendingCount: Int,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val gg = LocalGG.current
    if (confirming) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("로그아웃할까요?", style = suite(FontWeight.Bold, 13), color = gg.text)
            Text(
                "이 기기의 별·레벨·코인과 이 기기에서 산 이용권은 그대로예요. 계정으로 받은 이용권과 학습 기록 보관은 다시 로그인하면 돌아와요.",
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
            if (pendingCount > 0) {
                WarningText("아직 서버에 올리지 못한 학습 기록 ${pendingCount}개는 로그아웃하면 서버에 남지 않아요.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GGButton(
                    onClick = onCancel,
                    variant = GGButtonVariant.SURFACE,
                    modifier = Modifier.weight(1f),
                ) { Text("취소", style = suite(FontWeight.Bold, 14), color = gg.text) }
                GGButton(
                    onClick = onConfirm,
                    variant = GGButtonVariant.DANGER,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (signingOut) "로그아웃 중…" else "로그아웃",
                        style = suite(FontWeight.Bold, 14), color = gg.accentFg,
                    )
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable {
                    Haptics.impactLight()
                    onAsk()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = gg.textMuted, modifier = Modifier.size(18.dp))
            Text(label, style = suite(FontWeight.Bold, 13), color = gg.textMuted)
        }
    }
}

@Composable
private fun WarningText(message: String) {
    Text(message, style = suite(FontWeight.Bold, 12), color = LocalGG.current.warning)
}
