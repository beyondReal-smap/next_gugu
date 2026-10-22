package site.smap.gugudan.features.reminder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import site.smap.gugudan.services.LocalNotifications
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalReminder

/**
 * 알림 권한을 확보하는 함수를 돌려준다. Android 13 이상이면서 권한이 없으면 시스템 권한 창을 띄운다.
 * 권한 창은 Activity 결과 API 가 필요해 화면 쪽에 둔다. 학습 알림·원격 알림이 같은 창을 쓴다.
 *
 * onResult(true) = 권한 창에서 허용했거나 요청이 필요 없음(Android 12 이하는 설치 시 허용).
 * 시스템 설정에서 알림이 꺼졌는지는 호출부가 LocalNotifications.isAuthorized 로 확인한다.
 */
@Composable
fun rememberRequestNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && LocalNotifications.needsRuntimePermission(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            currentOnResult(true)
        }
    }
}

/**
 * 원격 알림 토글용 — 권한을 확보한 뒤 알림을 실제로 보낼 수 있는지(시스템 설정 포함)를 콜백으로 알려 준다.
 * 권한이 이미 거부된 상태면 시스템 창이 뜨지 않고 바로 false 가 온다 → 호출부가 시스템 설정으로 안내한다.
 */
@Composable
fun rememberNotificationAccess(): (onResult: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val request = rememberRequestNotificationPermission { granted ->
        val callback = pending
        pending = null
        callback?.invoke(granted && LocalNotifications.isAuthorized(context))
    }
    return { callback ->
        pending = callback
        request()
    }
}

/**
 * 학습 알림을 켜는 함수를 돌려준다. 권한 결과를 ReminderStore 에 넘긴다.
 *
 * onResult(true) = 켜짐, false = 거절되었거나 시스템 설정에서 꺼져 있음
 */
@Composable
fun rememberEnableReminder(onResult: (Boolean) -> Unit): (Int) -> Unit {
    val reminder = LocalReminder.current
    val game = LocalGame.current
    var pendingHour by remember { mutableIntStateOf(reminder.settings.hour) }

    // Android 12 이하는 설치 시 허용 — 시스템 설정에서 꺼졌는지만 ReminderStore 가 확인한다
    val request = rememberRequestNotificationPermission { granted ->
        onResult(reminder.applyPermissionResult(granted, pendingHour, game.state))
    }
    return { hour ->
        pendingHour = hour
        request()
    }
}
