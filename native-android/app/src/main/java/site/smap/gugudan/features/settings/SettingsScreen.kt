package site.smap.gugudan.features.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.core.Theme
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.reminder.rememberEnableReminder
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.store.*

// 설정 (iOS SettingsView.swift 이식) — 프로필 톱니바퀴에서 여는 전체 화면.
// 켜기/끄기는 스위치로, 여러 값 중 고르기는 칩으로 보여 준다
// (예전에는 "켜짐/꺼짐" 글자를 눌러야 해서 누를 수 있는 곳인지 알기 어려웠다).

val DAILY_GOAL_OPTIONS = listOf(10, 20, 30, 50)

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val theme = LocalTheme.current
    val prefs = LocalPrefs.current
    val reminder = LocalReminder.current
    val premium = LocalPremium.current
    val auth = LocalAuth.current
    val sync = LocalSync.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var restoring by remember { mutableStateOf(false) }
    var restoreNotice by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val enableReminder = rememberEnableReminder { }

    BackHandler(onBack = onClose)

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(Modifier.fillMaxSize().background(gg.bg).statusBarsPadding()) {
        // 헤더
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Text("설정", style = suite(FontWeight.ExtraBold, 17), color = gg.text,
                modifier = Modifier.align(Alignment.Center).semantics { heading() })
            PressableCard(modifier = Modifier.align(Alignment.CenterEnd), onClick = onClose) {
                Box(
                    Modifier.height(48.dp).clip(CircleShape).background(gg.surface2).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("완료", style = suite(FontWeight.Bold, 15), color = gg.accent)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .readableWidth()
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 학습
            SettingsGroup("학습") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(Icons.Filled.TrackChanges, "하루 목표",
                        "하루에 맞힐 정답 수예요. 목표를 채우면 그날 알림은 쉬어요.")
                    ChoiceChips(
                        options = DAILY_GOAL_OPTIONS,
                        selected = game.state.dailyGoal,
                        label = { "${it}개" },
                        modifier = Modifier.padding(start = 36.dp),
                    ) { goal ->
                        game.setDailyGoal(goal)
                        reminder.reschedule(game.state)
                    }
                }
                SettingsDivider()
                ToggleRow(
                    Icons.Filled.RecordVoiceOver, "문제 읽어주기",
                    "새 문제가 나오면 선생님 목소리로 읽어 줘요.",
                    checked = prefs.readAloud,
                ) { prefs.updateReadAloud(it) }
            }

            // 화면
            SettingsGroup("화면") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(Icons.Filled.Contrast, "테마", null)
                    ChoiceChips(
                        options = listOf(Theme.SYSTEM, Theme.LIGHT, Theme.DARK),
                        selected = theme.theme,
                        label = { ThemeStore.label(it) },
                        modifier = Modifier.padding(start = 36.dp),
                    ) { theme.set(it) }
                }
            }

            // 소리와 진동
            SettingsGroup("소리와 진동") {
                ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, "효과음", null, prefs.soundOn) { prefs.updateSound(it) }
                SettingsDivider()
                ToggleRow(Icons.Filled.Vibration, "진동", "버튼을 누르거나 정답을 맞힐 때 톡 울려요.", prefs.hapticsOn) {
                    prefs.updateHaptics(it)
                }
            }

            // 알림
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("알림") {
                    ToggleRow(
                        Icons.Filled.Notifications, "학습 알림",
                        "하루 한 번, 오늘의 구구단을 알려 드려요. 밤에는 보내지 않아요.",
                        reminder.settings.enabled,
                    ) { on -> if (on) enableReminder(reminder.settings.hour) else reminder.disable() }
                    if (reminder.settings.enabled) {
                        SettingsDivider()
                        // 알림 시각 — 야간 발송을 막기 위해 8~20시 안에서만 고른다
                        val hour = reminder.settings.hour
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.Schedule, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
                            Text("알림 시각", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                            HourStepButton(Icons.Filled.Remove, "한 시간 앞당기기",
                                enabled = hour > ReminderPlanner.ALLOWED_HOURS.first) {
                                reminder.setHour(hour - 1, game.state)
                            }
                            Text(ReminderPlanner.hourLabel(hour), style = suite(FontWeight.Bold, 14),
                                color = gg.accent, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
                            HourStepButton(Icons.Filled.Add, "한 시간 늦추기",
                                enabled = hour < ReminderPlanner.ALLOWED_HOURS.last) {
                                reminder.setHour(hour + 1, game.state)
                            }
                        }
                    }
                }
                // 앱에서는 켰지만 시스템 설정에서 꺼진 경우 — 앱이 대신 켤 수 없어 설정으로 안내한다
                if (reminder.deniedBySystem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("휴대폰 설정에서 구구 어드벤처 알림이 꺼져 있어요.",
                            style = suite(FontWeight.Medium, 12), color = gg.warning, modifier = Modifier.weight(1f))
                        Text(
                            "설정 열기", style = suite(FontWeight.Bold, 12), color = gg.accent,
                            modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            },
                        )
                    }
                }
            }

            // 계정
            AccountSettingsCard()

            // 이용권
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsGroup("이용권") {
                    if (premium.isPremium) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                            Text("평생 이용권", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                            Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("이용 중", style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                            }
                        }
                    } else {
                        LinkRow(Icons.Filled.AutoAwesome, "평생 이용권 · 커피 한 잔 값", premium.price) { premium.openPaywall() }
                    }
                    SettingsDivider()
                    LinkRow(Icons.Filled.Refresh, "구매 복원", if (restoring) "확인 중…" else null) {
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
                    SettingsDivider()
                    LinkRow(Icons.Filled.Description, "이용약관", null) { open(PremiumConfig.Legal.TERMS) }
                    SettingsDivider()
                    LinkRow(Icons.Filled.VerifiedUser, "개인정보처리방침", null) { open(PremiumConfig.Legal.PRIVACY) }
                }
                restoreNotice?.let { Text(it, style = suite(FontWeight.Medium, 12), color = gg.textMuted) }
            }

            // 앱 정보
            SettingsGroup("앱 정보") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                        .clearAndSetSemantics { contentDescription = "버전 ${BuildConfig.VERSION_NAME}, 빌드 ${BuildConfig.VERSION_CODE}" },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
                    Text("버전", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                }
            }

            // 기록 초기화
            if (!confirmReset) {
                GGButton(variant = GGButtonVariant.GHOST, onClick = { confirmReset = true }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Text("학습 기록 초기화", color = gg.danger)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("레벨·별·업적·기록이 모두 처음으로 돌아가요. 되돌릴 수 없어요.",
                        style = suite(FontWeight.Bold, 13), color = gg.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GGButton(variant = GGButtonVariant.SURFACE, modifier = Modifier.weight(1f),
                            onClick = { confirmReset = false }) { Text("취소") }
                        GGButton(variant = GGButtonVariant.DANGER, modifier = Modifier.weight(1f),
                            onClick = { game.resetProgress(); confirmReset = false }) { Text("초기화 확인") }
                    }
                }
            }
        }
    }
}

// MARK: 설정 행 구성 요소

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val gg = LocalGG.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = suite(FontWeight.Bold, 14), color = gg.textMuted, modifier = Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().ggCard(16.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    val gg = LocalGG.current
    Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(gg.border))
}

@Composable
private fun SettingsLabel(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val gg = LocalGG.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = gg.textMuted, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = suite(FontWeight.Bold, 15), color = gg.text)
            if (subtitle != null) Text(subtitle, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val gg = LocalGG.current
    Row(
        Modifier.fillMaxWidth()
            // 행 전체가 하나의 스위치 — TalkBack 이 행과 스위치에서 두 번 멈추지 않고, 어디를 눌러도 켜고 끈다
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) { Haptics.selection(); onChange(it) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsLabel(icon, title, subtitle, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,   // 표시만 — 입력은 행의 toggleable 이 받는다
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = gg.accent,
                checkedThumbColor = gg.accentFg,
                uncheckedTrackColor = gg.surface2,
                uncheckedThumbColor = gg.textMuted,
                uncheckedBorderColor = gg.border,
            ),
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, value: String?, onTap: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(onClick = onTap) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
            Text(title, style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = suite(FontWeight.Bold, 14), color = gg.accent)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HourStepButton(icon: ImageVector, description: String, enabled: Boolean, onTap: () -> Unit) {
    val gg = LocalGG.current
    // 보이는 원은 36dp, 누르는 영역은 48dp
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button) { Haptics.impactLight(); onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(gg.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = if (enabled) gg.text else gg.textMuted.copy(alpha = 0.4f),
                 modifier = Modifier.size(16.dp))
        }
    }
}
