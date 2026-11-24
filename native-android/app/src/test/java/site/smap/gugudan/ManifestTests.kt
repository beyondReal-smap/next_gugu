package site.smap.gugudan

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 매니페스트 검증 — 단위 테스트의 작업 디렉터리는 모듈(app/) 이다.

class ManifestTests {

    /**
     * INTERNET 은 라이브러리가 병합해 주는 것에 기대지 않고 앱이 직접 선언한다.
     * 라이브러리가 빼 버리면 계정·학습 기록 서버 통신이 조용히 전부 죽는다.
     */
    @Test fun manifestDeclaresInternetPermission() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")   // 주석 제거

        assertTrue(
            manifest.contains("<uses-permission android:name=\"android.permission.INTERNET\""),
            "앱 매니페스트에 INTERNET 선언이 없다",
        )
    }

    private val manifest: String by lazy {
        File("src/main/AndroidManifest.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    /** 광고 ID 는 쓰지 않는다 — 라이브러리가 병합해 넣어도 지운다 (Play 가족 정책) */
    @Test fun adIdPermissionIsRemoved() {
        assertTrue(
            Regex("""<uses-permission\s+android:name="com\.google\.android\.gms\.permission\.AD_ID"\s+tools:node="remove"""")
                .containsMatchIn(manifest),
            "AD_ID 권한 제거(tools:node=remove) 선언이 없다",
        )
    }

    /** 병합 결과에도 AD_ID 가 없어야 한다 — 빌드 산출물이 있을 때만 검사 (assembleDebug 이후) */
    @Test fun mergedManifestHasNoAdId() {
        val merged = File("build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
        if (!merged.exists()) return
        assertFalse(merged.readText().contains("permission.AD_ID"), "병합 매니페스트에 AD_ID 가 남아 있다")
    }

    /** FCM 자동 초기화 끔 — 알림을 켜기 전에는 토큰을 만들지 않는다 */
    @Test fun messagingAutoInitDisabled() {
        assertTrue(
            Regex("""android:name="firebase_messaging_auto_init_enabled"\s+android:value="false"""").containsMatchIn(manifest),
            "firebase_messaging_auto_init_enabled=false 가 없다",
        )
    }

    @Test fun defaultChannelAndIcon() {
        assertTrue(
            Regex("""default_notification_channel_id"\s+android:value="service_notice"""").containsMatchIn(manifest),
            "기본 알림 채널이 service_notice 가 아니다",
        )
        assertTrue(
            Regex("""default_notification_icon"\s+android:resource="@drawable/ic_stat_reminder"""").containsMatchIn(manifest),
            "기본 알림 아이콘이 없다",
        )
    }

    /** 메시징 서비스는 외부에 노출하지 않고 MESSAGING_EVENT 만 받는다 */
    @Test fun messagingServiceIsPrivate() {
        val service = Regex("""<service\s+android:name="\.services\.GuguMessagingService"[^>]*>(.*?)</service>""",
            RegexOption.DOT_MATCHES_ALL).find(manifest)
        assertTrue(service != null, "GuguMessagingService 선언이 없다")
        assertTrue(service.value.contains("android:exported=\"false\""), "메시징 서비스가 exported=false 가 아니다")
        assertTrue(service.value.contains("com.google.firebase.MESSAGING_EVENT"), "MESSAGING_EVENT 필터가 없다")
    }

    /** 알림을 눌렀을 때 화면을 새로 쌓지 않고 onNewIntent 로 받는다 */
    @Test fun mainActivityIsSingleTop() {
        assertTrue(manifest.contains("android:launchMode=\"singleTop\""), "MainActivity 가 singleTop 이 아니다")
    }
}
