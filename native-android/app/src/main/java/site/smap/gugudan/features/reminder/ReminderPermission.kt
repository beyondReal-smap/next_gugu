package site.smap.gugudan.features.reminder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalReminder

/**
 * 학습 알림을 켜는 함수를 돌려준다. Android 13 이상이면서 권한이 없으면 시스템 권한 창을 띄우고,
 * 그 결과를 ReminderStore 에 넘긴다. 권한 창은 Activity 결과 API 가 필요해 화면 쪽에 둔다.
 *
 * onResult(true) = 켜짐, false = 거절되었거나 시스템 설정에서 꺼져 있음
 */
@Composable
fun rememberEnableReminder(onResult: (Boolean) -> Unit): (Int) -> Unit {
    val reminder = LocalReminder.current
    val game = LocalGame.current
    var pendingHour by remember { mutableIntStateOf(reminder.settings.hour) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(reminder.applyPermissionResult(granted, pendingHour, game.state))
    }
    return { hour ->
        pendingHour = hour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && reminder.needsRuntimePermission()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 이하는 설치 시 허용 — 시스템 설정에서 꺼졌는지만 ReminderStore 가 확인한다
            onResult(reminder.applyPermissionResult(true, hour, game.state))
        }
    }
}
