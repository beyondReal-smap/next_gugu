package site.smap.gugudan.features.profile

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Theme
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.features.home.modeIcon
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalTheme
import site.smap.gugudan.store.AuthStore
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalSync
import site.smap.gugudan.store.SyncStore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions

// 프로필 탭 (iOS ProfileView 이식) — 레벨/통계/업적/이용권/설정

private val SCORED_MODES = listOf(GameMode.CHALLENGE, GameMode.SURVIVAL)

@Composable
fun ProfileScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val theme = LocalTheme.current
    val premium = LocalPremium.current
    val auth = LocalAuth.current
    val sync = LocalSync.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    var soundOn by remember { mutableStateOf(Sound.enabled) }
    var confirmReset by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var restoreNotice by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deleteNotice by remember { mutableStateOf<String?>(null) }

    val state = game.state
    val level = game.levelInfo
    val accuracyTotal = state.totalCorrect + state.totalWrong
    val accuracy = if (accuracyTotal > 0) Math.round(state.totalCorrect * 100.0 / accuracyTotal).toInt() else 0
    val unlocked = state.unlockedAchievements.toSet()
    val weakProblems = state.wrongPool.entries.sortedByDescending { it.value }.take(6).map { it.key }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더
        Text("프로필", style = suite(FontWeight.ExtraBold, 24), color = gg.text)

        // 레벨 헤더
        Row(
            Modifier.fillMaxWidth().ggCard().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(gg.accent),
                contentAlignment = Alignment.Center) {
                Text("${level.level}", style = suite(FontWeight.ExtraBold, 24), color = gg.accentFg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lv.${level.level} · ${Level.title(level.level)}",
                    style = suite(FontWeight.ExtraBold, 17), color = gg.text)
                GaugeBar(level.progress, height = 8.dp)
                Text("${level.totalXp} XP", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
        }

        // 통계 3열
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(Modifier.weight(1f), "정답", "${state.totalCorrect}")
            MiniStat(Modifier.weight(1f), "정확도", "$accuracy%")
            MiniStat(Modifier.weight(1f), "최고 콤보", "${state.maxCombo}")
        }

        // 최고 기록
        Text("최고 기록", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCORED_MODES.forEach { id ->
                val best = state.bestScores[id] ?: 0
                val tint = ModeStyle.tint(id)
                Row(
                    Modifier.weight(1f).ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(modeIcon(id), null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(Modes.def(id).name, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                        Text(if (best > 0) "${best}점" else "—", style = suite(FontWeight.ExtraBold, 17), color = gg.text)
                    }
                }
            }
        }

        // 정확도 추이
        if (state.recentAccuracy.size >= 2) {
            Column(Modifier.fillMaxWidth().ggCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("최근 정확도 추이", style = suite(FontWeight.Bold, 14), color = gg.text)
                        Text("최근 ${state.recentAccuracy.size}판 · 점선은 50%",
                            style = suite(FontWeight.Medium, 12), color = gg.textMuted)
                    }
                    // 그래프만으로는 현재 수준을 읽을 수 없어 최신값을 숫자로 집어 준다
                    Text("${state.recentAccuracy.last()}%",
                        style = suite(FontWeight.ExtraBold, 24), color = gg.accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // y축이 0~100 고정임을 눈으로 확인할 수 있게 눈금을 적는다
                    Column(
                        Modifier.width(24.dp).height(64.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        listOf("100", "50", "0").forEach {
                            Text(it, style = suite(FontWeight.Bold, 9), color = gg.textMuted)
                        }
                    }
                    Sparkline(
                        state.recentAccuracy,
                        Modifier.weight(1f).height(64.dp),
                        lowerBound = 0f, upperBound = 100f, baseline = 50f, markLast = true,
                    )
                }
            }
        }

        // 집중 공략 문제
        if (weakProblems.isNotEmpty()) {
            Text("집중 공략 문제", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weakProblems.forEach { key ->
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(gg.danger.copy(alpha = 0.1f))
                            .border(1.dp, gg.danger.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(key.replace("x", " × "), style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
                    }
                }
            }
        }

        // 업적 그리드 3열 — 4열에서는 "오백 문제 돌파" 같은 이름이 한 줄로 잘렸다.
        // 정사각 비율을 버리고 라벨 2줄(minHeight)을 확보해 타일 높이를 맞춘다.
        Text("업적 (${unlocked.size}/${Achievements.all.size})", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Achievements.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    val on = a.id in unlocked
                    val tint = (if (on) gg.accent else gg.textMuted).copy(alpha = if (on) 1f else 0.55f)
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) gg.accent.copy(alpha = 0.1f) else gg.surface)
                            .border(1.dp, if (on) gg.accent.copy(alpha = 0.3f) else gg.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(achievementIcon(a.icon), null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(a.name, style = suite(FontWeight.Bold, 11), color = tint,
                            textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 계정 — 익명 계정에 이메일을 붙여 기기를 바꿔도 기록이 남게 한다
        if (auth.state !is AuthStore.State.Disabled) {
            Text("기록 지키기", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            Column(
                Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        Text("이어서 쓸 기록을 골라 주세요",
                            style = suite(FontWeight.ExtraBold, 13), color = gg.text)
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
                                        Text(candidate.displayName,
                                            style = suite(FontWeight.ExtraBold, 13), color = gg.text)
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

                // 계정 삭제 (App Store 5.1.1(v) / Play 데이터 삭제 요건).
                // 되돌릴 수 없으므로 「기록 초기화」와 같은 2단 확인을 둔다.
                Divider()

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
                        Icon(Icons.Filled.PersonRemove, null, tint = gg.danger,
                             modifier = Modifier.size(18.dp))
                        Text("계정 삭제", style = suite(FontWeight.Bold, 13), color = gg.danger)
                    }
                }
            }
        }

        // 이용권
        Text("이용권", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Column(Modifier.fillMaxWidth().ggCard(16.dp)) {
            if (premium.isPremium) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    Text("평생 이용권", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("이용 중", style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                    }
                }
            } else {
                SettingRow(Icons.Filled.AutoAwesome, "평생 이용권 · 커피 한 잔 값", premium.price) { premium.openPaywall() }
            }
            Divider()
            SettingRow(Icons.Filled.Refresh, "구매 복원", if (restoring) "확인 중…" else "") {
                if (!restoring) {
                    restoring = true
                    restoreNotice = null
                    premium.restore { ok ->
                        restoring = false
                        restoreNotice = if (ok) "구매를 확인했어요. 기록 보관을 켜는 중이에요."
                                        else "복원할 구매 내역이 없어요."
                        // 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
                        // 서버 구매 등록과 보호자 권한을 시도한다 (PaywallScreen 과 동일).
                        if (ok) scope.launch { sync.enableGuardianSync(auth, premium.lastPurchaseToken) }
                    }
                }
            }
            Divider()
            SettingRow(Icons.Filled.Description, "이용약관", "") { open(PremiumConfig.Legal.TERMS) }
            Divider()
            SettingRow(Icons.Filled.VerifiedUser, "개인정보처리방침", "") { open(PremiumConfig.Legal.PRIVACY) }
        }
        restoreNotice?.let {
            Text(it, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }

        // 설정
        Text("설정", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Column(Modifier.fillMaxWidth().ggCard(16.dp)) {
            SettingRow(
                if (theme.theme == Theme.DARK) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                "다크 모드", if (theme.theme == Theme.DARK) "켜짐" else "꺼짐",
            ) { theme.toggle() }
            Divider()
            SettingRow(
                if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                "효과음", if (soundOn) "켜짐" else "꺼짐",
            ) {
                soundOn = !soundOn
                Sound.setEnabled(soundOn)
            }
        }

        // 초기화
        if (!confirmReset) {
            GGButton(variant = GGButtonVariant.GHOST, onClick = { confirmReset = true }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Text("기록 초기화", color = gg.danger)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GGButton(variant = GGButtonVariant.SURFACE, modifier = Modifier.weight(1f),
                    onClick = { confirmReset = false }) { Text("취소") }
                GGButton(variant = GGButtonVariant.DANGER, modifier = Modifier.weight(1f),
                    onClick = { game.resetProgress(); confirmReset = false }) { Text("초기화 확인") }
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

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String) {
    val gg = LocalGG.current
    Column(
        modifier.ggCard(16.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = suite(FontWeight.ExtraBold, 20), color = gg.text)
        Text(label, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
    }
}

@Composable
private fun Divider() {
    val gg = LocalGG.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(gg.border))
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, action: String, onTap: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(onClick = { Haptics.impactLight(); onTap() }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
            Text(label, style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
            Text(action, style = suite(FontWeight.Bold, 14), color = gg.accent)
        }
    }
}

// 스파크라인 (iOS Sparkline.swift 이식)
@Composable
fun Sparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    // 고정 y축 범위. null 이면 데이터의 min~max 에 맞춘다.
    //
    // 정확도처럼 눈금 자체에 의미가 있는 값은 반드시 고정해야 한다. 자동 스케일은
    // 90% 로 꾸준한 기록을 바닥에 붙여 0% 처럼 보이게 하고, 88·90·92 의 미세한
    // 차이를 천장까지 과장한다 — 둘 다 사실과 다른 인상을 준다.
    lowerBound: Float? = null,
    upperBound: Float? = null,
    // 해석 기준선 (예: 정확도 50%). 범위 안에 있을 때만 그린다.
    baseline: Float? = null,
    // 마지막 값에 점을 찍어 "현재"를 집어 준다
    markLast: Boolean = false,
) {
    val gg = LocalGG.current
    val c = color ?: gg.accent
    val borderColor = gg.border
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val low = lowerBound ?: data.min().toFloat()
        val high = maxOf(low + 1f, upperBound ?: data.max().toFloat())
        fun yFor(value: Float) =
            size.height - ((value - low) / (high - low)).coerceIn(0f, 1f) * size.height
        val stepX = size.width / (data.size - 1)
        val pts = data.mapIndexed { i, v -> Offset(i * stepX, yFor(v.toFloat())) }
        // 기준선
        if (baseline != null && baseline > low && baseline < high) {
            val y = yFor(baseline)
            drawLine(
                borderColor, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        // 채움
        val fill = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fill, c.copy(alpha = 0.12f))
        // 라인
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (markLast) {
            drawCircle(c, radius = 3.5.dp.toPx(), center = pts.last())
        }
    }
}
