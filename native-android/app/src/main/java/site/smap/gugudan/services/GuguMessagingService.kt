package site.smap.gugudan.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import site.smap.gugudan.core.PushKind
import site.smap.gugudan.core.PushSettings

// FCM 수신 서비스 (매니페스트 exported=false).
// - onNewToken: 서버 등록은 하지 않는다 — 다음 포그라운드 동기화가 토큰 지문 변화를 보고 다시 등록한다.
// - onMessageReceived: 앱이 켜져 있을 때 온 알림 메시지와 모든 data 메시지가 여기로 온다.
//   광고는 로컬 동의가 꺼져 있으면 버린다(토픽 해제 지연·실패 대비 2차 차단).

class GuguMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 원문은 남기지 않는다 — 디버그 빌드에서는 RemotePush.token() 이 남긴다
        Log.i(TAG, "FCM 토큰 갱신 — 다음 포그라운드에서 서버에 등록")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // 앱이 꺼진 채로 깨어날 수 있다 — 채널 이름 등 앱 언어를 먼저 맞춘다
        AndroidStrings.updateLanguage(applicationContext)
        val data = message.data
        val kind = data[RemotePush.EXTRA_KIND]
        val settings = Persistence(applicationContext).loadPushSettings() ?: PushSettings()
        if (!PushKind.shouldDisplay(kind, settings)) {
            Log.i(TAG, "광고 동의가 꺼져 있어 메시지를 버림")
            return
        }
        val notification = message.notification
        val title = notification?.title ?: data["title"]
        val body = notification?.body ?: data["body"]
        val quiet = PushKind.isQuiet(kind, RemotePush.appInForeground, RemotePush.playing)
        RemotePush.show(
            applicationContext,
            id = (message.messageId ?: "${System.currentTimeMillis()}").hashCode(),
            title = title,
            body = body,
            kind = kind,
            link = data[RemotePush.EXTRA_LINK],
            quiet = quiet,
        )
    }

    private companion object {
        const val TAG = "Push"
    }
}
