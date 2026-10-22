package site.smap.gugudan.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import site.smap.gugudan.R
import site.smap.gugudan.core.PlannedReminder
import site.smap.gugudan.core.PushChannels
import site.smap.gugudan.core.ReminderPlanner
import java.time.LocalTime
import java.util.concurrent.TimeUnit

// 로컬 알림 예약 (iOS Services/LocalNotifications.swift 대응) — WorkManager 로 예약만 한다.
// 무엇을 언제 보낼지는 core/ReminderPlanner 가 정한다. 서버를 거치지 않아 새 개인정보를 수집하지 않는다.

object LocalNotifications {
    private const val TAG = "Reminder"
    private const val CHANNEL_ID = PushChannels.LEARNING_REMINDER
    private const val WORK_TAG = "gugu-reminder"

    /**
     * 알림 채널 4종을 한 번에 만든다 (앱 시작·표시 직전). 이미 있으면 이름·설명만 갱신되고 사용자가 바꾼 중요도는 유지된다.
     * 학습 알림(로컬)은 기존 그대로, 나머지 셋은 원격 푸시용 — 공지 채널이 FCM 기본 채널이다(매니페스트).
     */
    fun ensureChannel(context: Context) {
        val channels = listOf(
            NotificationChannel(CHANNEL_ID, "학습 알림", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "정해진 시간에 오늘의 구구단을 알려드려요" },
            NotificationChannel(PushChannels.SERVICE_NOTICE, "공지·서비스 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "업데이트·점검 같은 중요한 소식을 알려 드려요." },
            NotificationChannel(PushChannels.GUARDIAN_REPORT, "보호자 학습 소식", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "매주 학습 리포트가 준비되면 알려 드려요." },
            NotificationChannel(PushChannels.PROMOTION, "이벤트·혜택 알림(광고)", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "새 기능·이벤트 소식을 받아요." },
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    /** Android 13 이상에서 아직 알림 권한이 없으면 true — 이때만 런타임 요청을 띄운다 */
    fun needsRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** 권한과 시스템 설정 모두에서 알림을 보낼 수 있는지 */
    fun isAuthorized(context: Context): Boolean =
        !needsRuntimePermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 이 앱이 예약한 학습 알림을 모두 지우고 새 계획으로 교체한다 */
    fun replace(context: Context, reminders: List<PlannedReminder>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(WORK_TAG)
        val now = System.currentTimeMillis()
        reminders.forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((reminder.fireAtEpochMs - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    ReminderWorker.KEY_ID to reminder.id,
                    ReminderWorker.KEY_TITLE to reminder.title,
                    ReminderWorker.KEY_BODY to reminder.body,
                ))
                .addTag(WORK_TAG)
                .build()
            work.enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, request)
        }
        Log.i(TAG, "예약 교체 — 새로 ${reminders.size}건")
    }

    internal fun show(context: Context, id: String, title: String, body: String) {
        ensureChannel(context)
        // 서비스가 화면(MainActivity)을 직접 알지 않도록 패키지의 런처 인텐트로 연다
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = launch?.let {
            PendingIntent.getActivity(
                context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // 예약 뒤에 사용자가 권한을 거둔 경우 — 보낼 수 없으니 조용히 끝낸다
            Log.w(TAG, "알림 권한 없음 — 표시 생략: ${e.message}")
        }
    }
}

/** 예약 시각에 깨어나 알림을 띄운다 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!LocalNotifications.isAuthorized(applicationContext)) return Result.success()
        // WorkManager 는 절전 때문에 늦게 실행될 수 있다. 밀려서 야간에 깨어났다면 보내지 않는다.
        if (LocalTime.now().hour !in ReminderPlanner.ALLOWED_HOURS) {
            Log.i("Reminder", "야간이라 알림 생략")
            return Result.success()
        }
        LocalNotifications.show(
            applicationContext,
            id = inputData.getString(KEY_ID) ?: return Result.success(),
            title = inputData.getString(KEY_TITLE) ?: return Result.success(),
            body = inputData.getString(KEY_BODY) ?: return Result.success(),
        )
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
