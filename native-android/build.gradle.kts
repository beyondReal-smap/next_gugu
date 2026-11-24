plugins {
    id("com.android.application") version "8.9.2" apply false
    // Kotlin 2.4.0 — SceneView 4.24.0(Kotlin 2.4.0 빌드) metadata 호환 때문에 올렸다. 2.9.0 에서 SceneView 제거, 툴체인은 그대로
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    // Play Console 업로드 — Play Developer API 서비스 계정 사용
    id("com.github.triplet.play") version "3.12.1" apply false
    // FCM 원격 푸시 — app/google-services.json(gitignore)을 리소스로 변환한다
    id("com.google.gms.google-services") version "4.5.0" apply false
}
