package site.smap.gugudan

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

// 매니페스트 검증 — 단위 테스트의 작업 디렉터리는 모듈(app/) 이다.

class ManifestTests {

    /**
     * INTERNET 은 라이브러리(SceneView 등)가 병합해 주는 것에 기대지 않고 앱이 직접 선언한다.
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
}
