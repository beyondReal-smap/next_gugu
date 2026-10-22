package site.smap.gugudan.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.R
import site.smap.gugudan.core.PushKind
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// FCM 래퍼 (iOS Services/RemotePush.swift 대응) — 토큰·토픽·표시만 맡는다. 언제 무엇을 할지는 store/PushStore 가 정한다.
//
// 매니페스트에서 자동 초기화를 껐으므로(firebase_messaging_auto_init_enabled=false) 이 객체가 token() 을 부르기 전에는
// 토큰이 만들어지지 않는다. 켤 때 자동 초기화를 함께 켜고(토큰 자동 갱신), 모두 끄면 토큰을 지우고 다시 끈다.

object RemotePush {
    private const val TAG = "Push"

    /** 앱 화면이 보이는지 — MainActivity onStart/onStop 이 갱신한다. 메시징 서비스(다른 스레드)가 읽는다 */
    @Volatile var appInForeground: Boolean = false

    /** 세션·미니게임·어드벤처 진행 중 — 이때 온 알림은 소리·배너 없이 목록에만 둔다 */
    @Volatile var playing: Boolean = false

    /** 알림 탭 → MainActivity 로 넘기는 data 키 (FCM 시스템 알림도 같은 이름으로 extras 에 담아 준다) */
    const val EXTRA_LINK = "link"
    const val EXTRA_KIND = "kind"
    /** 우리가 띄운 알림 표시 — FCM 시스템 알림은 google.message_id 가 붙는다 */
    const val EXTRA_FROM_PUSH = "gugu.push"
    private const val EXTRA_FCM_MESSAGE_ID = "google.message_id"

    private val messaging: FirebaseMessaging get() = FirebaseMessaging.getInstance()

    /** 토큰을 확보한다 (자동 초기화도 켠다 — 이후 토큰 갱신은 Firebase 가 맡는다) */
    suspend fun token(): String {
        messaging.isAutoInitEnabled = true
        val token = messaging.token.await()
        // 콘솔 테스트 발송용 — 디버그 빌드에서만 원문을 남긴다
        if (BuildConfig.DEBUG) Log.d(TAG, "FCM 토큰(디버그): $token")
        return token
    }

    suspend fun subscribe(topic: String) { messaging.subscribeToTopic(topic).await() }
    suspend fun unsubscribe(topic: String) { messaging.unsubscribeFromTopic(topic).await() }

    /** 모두 껐을 때 — 토큰을 지우고 자동 초기화를 다시 끈다 (다음 실행에서 토큰이 새로 생기지 않게) */
    suspend fun deleteToken() {
        messaging.deleteToken().await()
        messaging.isAutoInitEnabled = false
    }

    /** 이 인텐트가 알림을 눌러 연 것인지 */
    fun isPushIntent(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        return extras.getBoolean(EXTRA_FROM_PUSH, false) ||
            extras.containsKey(EXTRA_FCM_MESSAGE_ID) || extras.containsKey(EXTRA_LINK)
    }

    /** 앱이 켜져 있을 때 받은 메시지를 직접 띄운다 (꺼져 있을 때는 시스템이 기본 채널로 띄운다) */
    internal fun show(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        kind: String?,
        link: String?,
        quiet: Boolean,
    ) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            Log.w(TAG, "제목·본문 없는 메시지 — 표시 생략 kind=$kind")
            return
        }
        LocalNotifications.ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.putExtra(EXTRA_FROM_PUSH, true)
            ?.putExtra(EXTRA_LINK, link)
            ?.putExtra(EXTRA_KIND, kind)
        // 알림마다 요청 코드를 달리한다 — 같으면 앞 알림의 link 가 뒤 알림 것으로 덮인다
        val open = launch?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, PushKind.channelFor(kind))
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            // 조용히 = 소리·진동·배너 없이 알림 목록에만
            .setSilent(quiet)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // 받은 뒤에 사용자가 권한을 거둔 경우 — 보낼 수 없으니 기록만 남긴다
            Log.w(TAG, "알림 권한 없음 — 표시 생략: ${e.message}")
        }
    }
}

/** Play 서비스 Task 를 코루틴으로 기다린다 (kotlinx-coroutines-play-services 의존성 없이) */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
