import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.github.triplet.play")
}

// 릴리스 서명 자격 — keystore.properties(gitignore, 시크릿)에서 로드
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Play 업로드 자격 — play.properties(gitignore) 또는 PLAY_CREDENTIALS 환경변수.
// JSON 키 파일 경로만 참조하고 내용은 저장소에 두지 않는다.
val playProps = Properties().apply {
    val f = rootProject.file("play.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val playCredentialsPath: String? =
    System.getenv("PLAY_CREDENTIALS") ?: playProps.getProperty("credentialsFile")

android {
    namespace = "site.smap.gugudan"
    compileSdk = 36

    defaultConfig {
        applicationId = "site.smap.gugudan"
        minSdk = 28          // SceneView(Filament) 요구 — 기기 커버리지 ~97%
        targetSdk = 36
        versionCode = 15     // vc15: 구구 레인·구구 바구니 모드 추가 + 홈 놀이 묶음 재구성
        versionName = "2.1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Play Console 크래시/ANR 분석용 — SceneView(Filament) 등 네이티브 .so 디버그 심볼을 AAB에 포함
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 업로드 대상은 기본 internal 트랙. 트랙/상태는 CLI 로 덮어쓸 수 있다
// (예: ./gradlew publishReleaseBundle --track internal).
play {
    if (playCredentialsPath != null) {
        serviceAccountCredentials.set(file(playCredentialsPath))
    } else {
        // 자격이 없으면 업로드 태스크만 비활성 — 빌드는 그대로 통과시킨다
        enabled.set(false)
    }
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}

// Kotlin 2.4.0: kotlinOptions DSL 폐기 → compilerOptions로 마이그레이션
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
    implementation("io.github.sceneview:sceneview:4.24.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
}
