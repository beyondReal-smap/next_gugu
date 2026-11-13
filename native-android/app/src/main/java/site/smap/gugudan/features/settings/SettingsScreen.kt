package site.smap.gugudan.features.settings

import android.content.Intent
import android.os.Build
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import java.time.ZoneId
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.R
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.PromoVisibility
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.core.Theme
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.core.PushCopy
import site.smap.gugudan.features.reminder.rememberEnableReminder
import site.smap.gugudan.features.reminder.rememberNotificationAccess
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.rememberStrings
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
    val strings = rememberStrings()
    val scope = rememberCoroutineScope()

    var restoring by remember { mutableStateOf(false) }
    var restoreNotice by remember { mutableStateOf<Int?>(null) }   // @StringRes
    var confirmReset by remember { mutableStateOf(false) }
    val enableReminder = rememberEnableReminder { }
    val push = LocalPush.current
    val router = LocalRouter.current
    val notificationAccess = rememberNotificationAccess()
    // 알림 딥링크(settings/notifications)로 열면 알림 섹션까지 내려간다 — 섹션 위치는 첫 배치 후에 알 수 있다
    val scrollState = rememberScrollState()
    var notificationsY by remember { mutableIntStateOf(-1) }
    LaunchedEffect(router.settingsFocusNotifications, notificationsY) {
        if (router.settingsFocusNotifications && notificationsY >= 0) {
            scrollState.animateScrollTo(notificationsY)
            router.settingsFocusNotifications = false
        }
    }
    // 광고 동의 흐름: 보호자 확인 → 동의 시트 → 서버 기록 → 처리 결과 알림창
    var marketingStep by remember { mutableStateOf(MarketingStep.NONE) }
    var marketingBusy by remember { mutableStateOf(false) }
    var marketingResult by remember { mutableStateOf<Pair<String?, String>?>(null) }
    // 보호자 학습 소식은 기록 보관 계정(이메일 연결 + 이용권)만 받을 수 있다
    val learningEligible = auth.isPermanent && premium.isPremium

    BackHandler(onBack = onClose)

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(Modifier.fillMaxSize().background(gg.bg).statusBarsPadding()) {
        // 헤더
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.settings), style = suite(FontWeight.ExtraBold, 17), color = gg.text,
                modifier = Modifier.align(Alignment.Center).semantics { heading() })
            PressableCard(modifier = Modifier.align(Alignment.CenterEnd), onClick = onClose) {
                Box(
                    Modifier.height(48.dp).clip(CircleShape).background(gg.surface2).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.done), style = suite(FontWeight.Bold, 15), color = gg.accent)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState)
                .navigationBarsPadding()
                .readableWidth()
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 학습
            SettingsGroup(stringResource(R.string.settings_group_learning)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(Icons.Filled.TrackChanges, stringResource(R.string.daily_goal),
                        stringResource(R.string.correct_answers_to_aim_for_each_day))
                    ChoiceChips(
                        options = DAILY_GOAL_OPTIONS,
                        selected = game.state.dailyGoal,
                        label = { strings.plural(R.plurals.answers, it, it) },
                        modifier = Modifier.padding(start = 36.dp),
                    ) { goal ->
                        game.setDailyGoal(goal)
                        reminder.reschedule(game.state)
                    }
                }
                if (AppLanguage.current.supportsReadAloud) {
                    SettingsDivider()
                    ToggleRow(
                        Icons.Filled.RecordVoiceOver, stringResource(R.string.read_problems_aloud),
                        stringResource(R.string.reads_each_new_problem_aloud),
                        checked = prefs.readAloud,
                    ) { prefs.updateReadAloud(it) }
                }
            }

            // 화면
            SettingsGroup(stringResource(R.string.display)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLabel(Icons.Filled.Contrast, stringResource(R.string.theme), null)
                    ChoiceChips(
                        options = listOf(Theme.SYSTEM, Theme.LIGHT, Theme.DARK),
                        selected = theme.theme,
                        label = { strings.get(ThemeStore.labelRes(it)) },
                        modifier = Modifier.padding(start = 36.dp),
                    ) { theme.set(it) }
                }
                // 앱 언어는 시스템 설정 > 앱 > 구구 어드벤처 > 언어에서 바꾼다 (Android 13+ 앱별 언어, iOS 와 같은 UX).
                // 바꾸면 시스템이 Activity 를 다시 만든다. 12 이하는 기기 언어를 따르므로 행을 두지 않는다
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SettingsDivider()
                    LinkRow(Icons.Filled.Language, stringResource(R.string.language), AppLanguage.current.nativeName) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
                        )
                    }
                }
            }

            // 소리와 진동
            SettingsGroup(stringResource(R.string.sound_haptics)) {
                ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.sound_effects), null, prefs.soundOn) { prefs.updateSound(it) }
                SettingsDivider()
                ToggleRow(Icons.Filled.Vibration, stringResource(R.string.vibration), stringResource(R.string.a_light_buzz_when_you_tap_a_button_or), prefs.hapticsOn) {
                    prefs.updateHaptics(it)
                }
            }

            // 알림
            Column(
                Modifier.onGloballyPositioned { notificationsY = it.positionInParent().y.toInt() },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsGroup(stringResource(R.string.notifications)) {
                    // 기존 사용자 1회 안내 — 공지 알림이 꺼져 있고 아직 닫지 않았을 때만 (공지를 켜거나 닫으면 다시 안 보인다)
                    if (push.settings.showsNoticeHint) {
                        NoticeHintRow(onDismiss = { push.dismissNoticeHint() })
                        SettingsDivider()
                    }
                    ToggleRow(
                        Icons.Filled.Notifications, stringResource(R.string.practice_reminder),
                        stringResource(R.string.once_a_day_we_ll_remind_you_about_today),
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
                            Text(stringResource(R.string.reminder_time), style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                            HourStepButton(Icons.Filled.Remove, stringResource(R.string.s_1_hour_earlier),
                                enabled = hour > ReminderPlanner.ALLOWED_HOURS.first) {
                                reminder.setHour(hour - 1, game.state)
                            }
                            Text(ReminderPlanner.hourLabel(hour), style = suite(FontWeight.Bold, 14),
                                color = gg.accent, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
                            HourStepButton(Icons.Filled.Add, stringResource(R.string.s_1_hour_later),
                                enabled = hour < ReminderPlanner.ALLOWED_HOURS.last) {
                                reminder.setHour(hour + 1, game.state)
                            }
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
                        Icons.Filled.Campaign, stringResource(R.string.announcements),
                        stringResource(R.string.important_news_such_as_updates_and),
                        push.settings.serviceOptIn,
                    ) { on ->
                        if (!on) push.setService(false)
                        else notificationAccess { allowed ->
                            if (allowed) push.setService(true) else push.markDeniedBySystem()
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
                        Icons.Filled.FamilyRestroom, stringResource(R.string.weekly_progress_for_parents),
                        if (learningEligible) stringResource(R.string.we_ll_let_you_know_when_the_weekly)
                        else stringResource(R.string.available_after_you_link_an_email_and),
                        checked = learningEligible && push.settings.learningOptIn,
                        enabled = learningEligible,
                    ) { on ->
                        if (!on) push.setLearning(false)
                        else notificationAccess { allowed ->
                            if (allowed) push.setLearning(true) else push.markDeniedBySystem()
                        }
                    }
                    // 광고 토글은 한국 Play 결제 국가에만 보인다 — 이미 켜 둔 사람에게는 끌 수 있게 계속 보인다 (iOS 와 같다)
                    if (PromoVisibility.showsToggle(premium.billingCountry, push.settings.marketingOptIn)) {
                        SettingsDivider()
                        val consentDate = PushCopy.localDate(push.settings.marketingOptInAt, ZoneId.systemDefault())
                        ToggleRow(
                            Icons.Filled.CardGiftcard, stringResource(R.string.events_offers_promotional),
                            if (push.settings.marketingOptIn && consentDate != null) PushCopy.consentSubtitle(strings, consentDate)
                            else PushCopy.marketingOffSubtitle(strings),
                            checked = push.settings.marketingOptIn,
                            enabled = !marketingBusy,
                        ) { on ->
                            if (on) {
                                notificationAccess { allowed ->
                                    if (allowed) marketingStep = MarketingStep.GATE else push.markDeniedBySystem()
                                }
                            } else {
                                // 끄기는 즉시 — 서버 기록이 실패해도 대기열에 두고 기기 날짜로 결과를 알린다
                                marketingBusy = true
                                scope.launch {
                                    val done = push.revokeMarketing()
                                    marketingBusy = false
                                    marketingResult = PushCopy.revokedTitle(strings) to PushCopy.revokedMessage(strings, done.date)
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.all_features_work_even_with),
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
                // 앱에서는 켰지만 시스템 설정에서 꺼진 경우 — 앱이 대신 켤 수 없어 설정으로 안내한다
                if (reminder.deniedBySystem || push.deniedBySystem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.gugu_adventure_notifications_are_turned),
                            style = suite(FontWeight.Medium, 12), color = gg.warning, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.open_settings), style = suite(FontWeight.Bold, 12), color = gg.accent,
                            modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable {
                                Haptics.impactLight()
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
                SettingsGroup(stringResource(R.string.purchases)) {
                    if (premium.isPremium) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.lifetime_pass), style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                            Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.active), style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                            }
                        }
                    } else {
                        LinkRow(Icons.Filled.AutoAwesome, stringResource(R.string.lifetime_pass_the_price_of_a_coffee), premium.price) { premium.openPaywall() }
                    }
                    SettingsDivider()
                    LinkRow(Icons.Filled.Refresh, stringResource(R.string.restore_purchases),
                        if (restoring) stringResource(R.string.checking) else null) {
                        if (!restoring) {
                            restoring = true
                            restoreNotice = null
                            premium.restore { ok ->
                                restoring = false
                                restoreNotice = if (ok) R.string.purchase_confirmed_turning_on_record
                                                else R.string.no_purchases_to_restore
                                // 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
                                // 서버 구매 등록과 보호자 권한을 시도한다 (PaywallScreen 과 동일).
                                if (ok) scope.launch { sync.enableGuardianSync(auth, premium.lastPurchaseToken) }
                            }
                        }
                    }
                    SettingsDivider()
                    LinkRow(Icons.Filled.Description, stringResource(R.string.terms_of_use), null) { open(PremiumConfig.Legal.TERMS) }
                    SettingsDivider()
                    LinkRow(Icons.Filled.VerifiedUser, stringResource(R.string.privacy_policy), null) { open(PremiumConfig.Legal.PRIVACY) }
                }
                restoreNotice?.let { Text(stringResource(it), style = suite(FontWeight.Medium, 12), color = gg.textMuted) }
            }

            // 앱 정보
            SettingsGroup(stringResource(R.string.about)) {
                val versionDescription = stringResource(R.string.version_build_description, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                        .clearAndSetSemantics { contentDescription = versionDescription },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.version), style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                }
            }

            // 기록 초기화
            if (!confirmReset) {
                GGButton(variant = GGButtonVariant.GHOST, onClick = { confirmReset = true }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.reset_learning_records), color = gg.danger)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.levels_stars_achievements_and_records),
                        style = suite(FontWeight.Bold, 13), color = gg.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GGButton(variant = GGButtonVariant.SURFACE, modifier = Modifier.weight(1f),
                            onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
                        GGButton(variant = GGButtonVariant.DANGER, modifier = Modifier.weight(1f),
                            onClick = { game.resetProgress(); confirmReset = false }) { Text(stringResource(R.string.confirm_reset)) }
                    }
                }
            }
        }
    }
    // 광고 동의 시트·결과 알림창 — 별도 창(Dialog)에 띄워 설정 화면 레이아웃을 건드리지 않는다
    when (marketingStep) {
        MarketingStep.NONE -> Unit
        MarketingStep.GATE -> GuardianGateSheet(
            onCancel = { marketingStep = MarketingStep.NONE },
            onPassed = { marketingStep = MarketingStep.CONSENT },
        )
        MarketingStep.CONSENT -> MarketingConsentSheet(
            busy = marketingBusy,
            onCancel = { marketingStep = MarketingStep.NONE },
            onAgree = {
                marketingBusy = true
                scope.launch {
                    val result = push.grantMarketing()
                    marketingBusy = false
                    marketingStep = MarketingStep.NONE
                    marketingResult = when (result) {
                        is PushStore.MarketingResult.Recorded ->
                            PushCopy.grantedTitle(strings) to PushCopy.grantedMessage(strings, result.date)
                        PushStore.MarketingResult.Failed -> null to PushCopy.failedMessage(strings)
                    }
                }
            },
        )
    }
    marketingResult?.let { (title, message) ->
        PushResultDialog(title, message) { marketingResult = null }
    }
}

private enum class MarketingStep { NONE, GATE, CONSENT }

// MARK: 설정 행 구성 요소

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val gg = LocalGG.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = suite(FontWeight.Bold, 14), color = gg.textMuted, modifier = Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().ggCard(16.dp), content = content)
    }
}

/** 설정 알림 섹션 맨 위 1회 안내 — 새로 생긴 공지 알림을 알린다 */
@Composable
private fun NoticeHintRow(onDismiss: () -> Unit) {
    val gg = LocalGG.current
    val dismissLabel = stringResource(R.string.dismiss)
    Row(
        Modifier.fillMaxWidth().background(gg.accent.copy(alpha = 0.08f))
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.new_turn_on_announcements_to_hear_about),
            style = suite(FontWeight.Bold, 13), color = gg.text, modifier = Modifier.weight(1f),
        )
        // 보이는 아이콘은 18dp, 누르는 영역은 48dp
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button) { Haptics.impactLight(); onDismiss() }
                .semantics { contentDescription = dismissLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, null, tint = gg.textMuted, modifier = Modifier.size(18.dp))
        }
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
                // 비활성(보호자 학습 소식 자격 없음) — 기본값은 다크 테마에서 흰색으로 튀어 꺼진 상태처럼 흐리게 둔다
                disabledUncheckedTrackColor = gg.surface2.copy(alpha = 0.5f),
                disabledUncheckedThumbColor = gg.textMuted.copy(alpha = 0.4f),
                disabledUncheckedBorderColor = gg.border,
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
