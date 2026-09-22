package site.smap.gugudan.store

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.core.ReminderSettings
import site.smap.gugudan.services.LocalNotifications
import site.smap.gugudan.services.Persistence
import java.time.ZonedDateTime

// 학습 알림 설정 (iOS Store/ReminderStore.swift 대응) — 켜짐 여부·시각을 저장하고 상태가 바뀔 때마다 예약을 교체한다.
// 권한 요청 창은 Activity 결과 API 가 필요해 화면(features/reminder)에서 띄우고, 결과만 여기로 넘긴다.

class ReminderStore(private val context: Context, private val persistence: Persistence) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var settings: ReminderSettings by mutableStateOf(load())
        private set
    /** 앱에서는 켜 두었지만 시스템 설정에서 알림이 꺼진 상태 — 앱이 켤 수 없어 설정으로 안내한다 */
    var deniedBySystem: Boolean by mutableStateOf(false)
        private set

    /** 결과 화면에서 권유할지 — 한 번 물었으면(수락이든 거절이든) 다시 묻지 않는다 */
    val shouldOfferAfterSession: Boolean get() = !settings.askedPermission && !settings.enabled

    fun needsRuntimePermission(): Boolean = LocalNotifications.needsRuntimePermission(context)

    /** 권한 요청 결과를 반영한다. Android 12 이하처럼 요청이 필요 없으면 granted=true 로 부른다. */
    fun applyPermissionResult(granted: Boolean, hour: Int, state: GameState): Boolean {
        val allowed = granted && LocalNotifications.isAuthorized(context)
        settings = settings.copy(
            askedPermission = true,
            enabled = allowed,
            hour = if (allowed) ReminderPlanner.clampHour(hour) else settings.hour,
        )
        deniedBySystem = !allowed
        save()
        reschedule(state)
        return allowed
    }

    /** 결과 화면 권유를 거절 — 다시 묻지 않지만 프로필에서 언제든 켤 수 있다 */
    fun declineOffer() {
        settings = settings.copy(askedPermission = true)
        save()
    }

    fun disable() {
        settings = settings.copy(enabled = false)
        deniedBySystem = false
        save()
        LocalNotifications.replace(context, emptyList())
    }

    fun setHour(hour: Int, state: GameState) {
        settings = settings.copy(hour = ReminderPlanner.clampHour(hour))
        save()
        reschedule(state)
    }

    /** 앱 진입·학습 직후 호출 — 계획을 새로 세워 예약을 통째로 교체한다 */
    fun reschedule(state: GameState, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!settings.enabled) {
            LocalNotifications.replace(context, emptyList())
            return
        }
        // 사용자가 시스템 설정에서 꺼 버렸다면 앱 설정은 그대로 두고 예약만 비운다
        if (!LocalNotifications.isAuthorized(context)) {
            deniedBySystem = true
            LocalNotifications.replace(context, emptyList())
            return
        }
        deniedBySystem = false
        LocalNotifications.replace(context, ReminderPlanner.plan(state, settings, now))
    }

    private fun load(): ReminderSettings {
        val raw = persistence.getString(Persistence.REMINDER_KEY) ?: return ReminderSettings()
        return runCatching { json.decodeFromString(ReminderSettings.serializer(), raw) }
            .onFailure { Log.w("Reminder", "설정 해석 실패 — 기본값 사용: ${it.message}") }
            .getOrDefault(ReminderSettings())
    }

    private fun save() {
        persistence.putString(Persistence.REMINDER_KEY, json.encodeToString(ReminderSettings.serializer(), settings))
    }
}
