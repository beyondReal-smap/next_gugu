plugins {
    id("com.android.application") version "8.9.2" apply false
    // SceneView 4.24.0이 Kotlin 2.4.0으로 빌드됨 → metadata 호환 위해 툴체인 상향
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    // Play Console 업로드 — Play Developer API 서비스 계정 사용
    id("com.github.triplet.play") version "3.12.1" apply false
    // FCM 원격 푸시 — app/google-services.json(gitignore)을 리소스로 변환한다
    id("com.google.gms.google-services") version "4.5.0" apply false
}
