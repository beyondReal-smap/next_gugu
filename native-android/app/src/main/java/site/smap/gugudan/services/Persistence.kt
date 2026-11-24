package site.smap.gugudan.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.json.Json
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.PushRegistrationState
import site.smap.gugudan.core.PushSettings
import site.smap.gugudan.core.ReviewPromptLog
import java.io.File
import java.io.IOException
import java.util.UUID

// 영속화 — iOS UserDefaults 대응(SharedPreferences + kotlinx JSON). 저장 키 동일 유지.

class Persistence(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gugu", Context.MODE_PRIVATE)
    /**
     * 기기에 묶인 값(세션·학습 신원·아웃박스)만 담는 별도 파일 — 백업 규칙(res/xml)이 이 파일만 제외한다.
     * 진행도가 든 "gugu" 는 기기를 바꿀 때 유일한 복구 수단이라 백업에 그대로 둔다.
     */
    private val devicePrefs: SharedPreferences =
        context.getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE)
    /** 손상 저장본 격리 폴더 — noBackupFilesDir 라 클라우드 백업·기기 이전에서 자동 제외된다 (세션 원문이 새지 않게) */
    private val quarantineDir = File(context.noBackupFilesDir, "quarantine")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 이관이 끝났을 때만 기기 값을 별도 파일로 라우팅한다 — 이관이 실패하면 이번 실행은 옛 위치를 계속 쓴다 */
    private val deviceRouting: Boolean

    init {
        val migration = migrationGate.run(prefs, devicePrefs)
        deviceRouting = migration.routesToDevice()
        when (migration) {
            DeviceMigration.DONE -> Unit
            DeviceMigration.CLEANUP_PENDING -> Log.w(TAG, "기기 값 이관: 원본 삭제 실패 — 다음 실행에서 원본만 정리")
            DeviceMigration.COPY_FAILED -> Log.e(TAG, "기기 값 이관 실패 — 이번 실행은 옛 위치를 계속 사용, 다음 실행에서 재시도")
        }
    }

    companion object {
        private const val TAG = "Persistence"
        /** 키마다 남기는 격리본 상한 — 실패가 반복돼도 쌓이지 않게 하고, 앞선(원본에 가까운) 것을 보존한다 */
        internal const val MAX_QUARANTINE_PER_KEY = 3
        /** 격리본 보존 기한(30일) — 손상 원문에는 학습 기록·식별자가 들어 있으므로 복구 핫픽스에 필요한 기간만 둔다 */
        internal const val QUARANTINE_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
        /** 이관 실패를 프로세스 안에서 기억한다 — 실패한 뒤 만들어진 Persistence(미니게임 화면·Activity 재생성)도 같은 결과를 따른다 */
        private val migrationGate = DeviceMigrationGate()

        const val PROGRESS_KEY = "gugu.progress.v1"
        /** 2.8 까지의 3D 어드벤처 저장본 — 2.9.0 에서 어드벤처는 없어졌지만 별 조각 이관 원본이라 키를 남긴다(PlayRewardsStore 가 읽기만 한다) */
        const val ADVENTURE_KEY = "gugu.adventure.v1"
        const val THEME_KEY = "gugu.theme"
        const val PREMIUM_KEY = "gugu.premium.v1"
        /** 서버(구매 원장)가 부여한 프리미엄 — 스토어 구매와 따로 둬야 Play 재확인이 이 값을 지우지 않는다 */
        const val SERVER_PREMIUM_KEY = "gugu.premium.server.v1"
        const val SOUND_KEY = "gugu.sound"
        /** 구구 계단·구구 몬스터 보상 — 모은 별·옷장·도감·최고 기록 (PlayRewardsStore, iOS 와 같은 키) */
        const val PLAY_REWARDS_KEY = "gugu.play.v1"
        /** 구구 계단·구구 몬스터 문제 순서 선택 — 순서대로·무작위 (GameOrderStore, iOS 와 같은 키). 아이 설정이라 백업 대상("gugu") */
        const val GAME_ORDER_KEY = "gugu.gameOrder.v1"

        /** 익명 계정 세션 (토큰 — 앱 전용 저장소) */
        const val AUTH_SESSION_KEY = "gugu.auth.session.v1"
        /** 재로그인 대기 표시 ({userId, email}) — 세션과 같은 파일에 둬야 백업 복원 뒤에도 둘이 어긋나지 않는다 */
        const val AUTH_REAUTH_KEY = "gugu.auth.reauth.v1"

        /** 서버 동기화 — 학습 신원 / 오프라인 아웃박스 */
        const val LEARNING_IDENTITY_KEY = "gugu.learning.identity.v1"
        const val LEARNING_OUTBOX_KEY = "gugu.learning.outbox.v1"
        /** 학습 알림 설정 (켜짐·시각·권유 여부) */
        const val REMINDER_KEY = "gugu.reminder.v1"
        /** 앱 평가 요청 이력 (마지막 요청 시각·버전) — iOS 와 같은 키 */
        const val REVIEW_KEY = "gugu.review.v1"

        /** 원격 푸시 — 푸시 전용 설치 식별자(UUID). 학습 installId 와 달리 계정이 바뀌어도 유지한다 */
        const val PUSH_INSTALL_KEY = "gugu.push.install.v1"
        /** 원격 푸시 — 등록 상태(구독 토픽·등록 계정·토큰 지문) */
        const val PUSH_STATE_KEY = "gugu.push.state.v1"
        /** 원격 푸시 — 사용자 설정(공지·보호자 소식·광고 동의) */
        const val PUSH_SETTINGS_KEY = "gugu.push.v1"

        /** 기기에 묶인 값을 담는 별도 prefs 파일 이름 — res/xml 백업 규칙이 `gugu_device.xml` 을 제외한다 */
        internal const val DEVICE_PREFS_NAME = "gugu_device"
        /** 기기 값 이관 완료 마커 (gugu_device 파일 안에 기록한다) */
        internal const val DEVICE_MIGRATED_KEY = "gugu.device.migrated.v1"
        /**
         * 기기에 묶여 백업·기기 이전에 실리면 안 되는 키 — 세션 토큰, 이 설치의 학습 신원(installId),
         * 그 신원에 딸린 아웃박스. 신원과 아웃박스는 서로 참조(learnerId/deviceId)하므로 함께 옮긴다 —
         * 신원만 빼면 복원된 기기에 주인 없는 이벤트가 남아 서버 거부 목록에 영구히 쌓인다.
         * 서버 부여 프리미엄도 이 기기의 계정(세션)에 딸린 값이라 함께 둔다 — 다른 기기로 복원되면 계정이 달라진다.
         * 재로그인 대기 표시도 세션의 짝이라 함께 둔다 — 세션 없이 표시만 복원되면 익명 발급이 막힌다.
         * 원격 푸시 3종도 이 설치에 묶인다 — 광고 동의가 새 기기로 따라가면 서버 기록 없는 동의로 광고를 받게 된다.
         */
        internal val DEVICE_KEYS = setOf(
            AUTH_SESSION_KEY, AUTH_REAUTH_KEY, LEARNING_IDENTITY_KEY, LEARNING_OUTBOX_KEY, SERVER_PREMIUM_KEY,
            PUSH_INSTALL_KEY, PUSH_STATE_KEY, PUSH_SETTINGS_KEY,
        )
    }

    /** 기기에 묶인 키는 별도 파일로, 나머지는 "gugu" 로 — 이관이 안 끝났으면 전부 옛 위치 */
    private fun prefsFor(key: String): SharedPreferences =
        if (deviceRouting && key in DEVICE_KEYS) devicePrefs else prefs

    fun loadGameState(): GameState? = decodeOrNull(PROGRESS_KEY) {
        json.decodeFromString(GameState.serializer(), it)
    }

    fun saveGameState(state: GameState) =
        putString(PROGRESS_KEY, json.encodeToString(GameState.serializer(), state))

    fun loadPushSettings(): PushSettings? = decodeOrNull(PUSH_SETTINGS_KEY) {
        json.decodeFromString(PushSettings.serializer(), it)
    }

    fun savePushSettings(settings: PushSettings) =
        putString(PUSH_SETTINGS_KEY, json.encodeToString(PushSettings.serializer(), settings))

    fun loadReviewLog(): ReviewPromptLog? = decodeOrNull(REVIEW_KEY) {
        json.decodeFromString(ReviewPromptLog.serializer(), it)
    }

    fun saveReviewLog(log: ReviewPromptLog) =
        putString(REVIEW_KEY, json.encodeToString(ReviewPromptLog.serializer(), log))

    fun loadPushState(): PushRegistrationState? = decodeOrNull(PUSH_STATE_KEY) {
        json.decodeFromString(PushRegistrationState.serializer(), it)
    }

    fun savePushState(state: PushRegistrationState) =
        putString(PUSH_STATE_KEY, json.encodeToString(PushRegistrationState.serializer(), state))

    /** 저장본을 읽는다. 키가 없으면 null(신규 설치). 있는데 못 읽으면 원문을 격리한 뒤 null — 호출부가 기본값으로 시작해 곧바로 저장하기 때문이다 */
    private inline fun <T> decodeOrNull(key: String, decode: (String) -> T): T? {
        val raw = prefsFor(key).getString(key, null) ?: return null
        return try {
            decode(raw)
        } catch (e: Exception) {
            Log.e(TAG, "로드 실패($key)", causeToLog(key, e))
            quarantine(key, raw, e)
            null
        }
    }

    fun getString(key: String): String? = prefsFor(key).getString(key, null)
    fun putString(key: String, value: String?) {
        prefsFor(key).edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    /** 의도적 삭제 — 우리가 만든 손상 저장본 사본도 함께 지운다 (계정 삭제 뒤에 사본이 남지 않게) */
    fun remove(key: String) {
        prefsFor(key).edit().remove(key).apply()
        clearQuarantine(key)
    }

    // MARK: 손상 저장본 격리
    //
    // 저장본을 못 읽었을 때 호출부는 기본값으로 시작하고 곧바로 저장한다 — 원본이 그대로 덮어써진다.
    // 서버에는 진행도가 없어 되살릴 곳이 없으므로, 덮어쓰기 전에 원문을 파일로 남겨 둔다.
    // 사용자는 이 파일에 접근할 수 없다 — 다음 빌드가 읽어 들여 복구하는 경로를 위한 보존이다.
    // 격리가 실패해도 앱은 계속 진행한다 (디스크가 가득 찬 채 매번 죽는 상황을 만들지 않는다).

    fun quarantine(key: String, raw: String, cause: Throwable? = null) {
        val logCause = causeToLog(key, cause)
        when (val result = writeQuarantine(quarantineDir, key, raw, BuildConfig.VERSION_CODE, System.currentTimeMillis())) {
            is QuarantineResult.Written -> Log.w(TAG, "손상 저장본 격리: ${result.name} (${raw.length}자)", logCause)
            QuarantineResult.LimitReached ->
                Log.w(TAG, "격리 상한($MAX_QUARANTINE_PER_KEY) 도달 — 새 격리본은 남기지 않음($key)", logCause)
            is QuarantineResult.Failed ->
                Log.e(TAG, "격리 실패($key) — 저장본 오류: ${logCause?.javaClass?.simpleName}", result.cause)
        }
    }

    /** 이 키의 격리본을 모두 지운다 — 기록 초기화·계정 삭제 뒤에 사본이 남지 않게 */
    fun clearQuarantine(key: String) {
        deleteQuarantine(quarantineDir, key).forEach { Log.w(TAG, "격리본 삭제 실패: $it") }
    }

    /** 보존 기한(30일)이 지난 격리본을 지운다 — 앱 시작(MainActivity) 때 부른다. 폴더가 없으면(대부분의 실행) 아무 일도 하지 않는다 */
    fun purgeExpiredQuarantine() {
        val result = purgeExpiredQuarantineFiles(quarantineDir, System.currentTimeMillis())
        if (result.removed.isNotEmpty()) {
            Log.i(TAG, "보존 기한(${QUARANTINE_RETENTION_MILLIS / DAY_MILLIS}일)이 지난 격리본 ${result.removed.size}개 삭제")
        }
        result.failed.forEach { Log.w(TAG, "만료 격리본 삭제 실패: $it") }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** 기기 값 이관 결과 */
internal enum class DeviceMigration {
    /** 이관 완료(또는 이미 끝남) */
    DONE,
    /** 기기 파일에는 안전하게 옮겼으나 원본 삭제가 실패 — 다음 실행에서 원본만 정리한다 */
    CLEANUP_PENDING,
    /** 기기 파일 기록이 실패 — 이번 실행은 옛 위치를 계속 쓰고 다음 실행에서 다시 시도한다 */
    COPY_FAILED;

    /** 데이터가 기기 파일에 안전히 있을 때만 그쪽으로 읽고 쓴다 (실패 시 세션이 "없음"으로 읽히는 사고 방지) */
    fun routesToDevice(): Boolean = this != COPY_FAILED
}

/**
 * 기기에 묶인 키(세션·학습 신원·아웃박스)를 옛 파일(legacy)에서 기기 전용 파일(device)로 옮긴다.
 * - 3키 복사와 마커를 한 번의 commit 으로 기록한다(파일 단위로 원자적). 성공했을 때만 원본을 지운다.
 * - 마커가 이미 있으면 복사하지 않고 원본만 정리한다 — 원본 삭제가 실패한 뒤 사용자가 값을 지웠어도
 *   (계정 삭제 등) 옛 값이 다시 살아나지 않게 한다.
 * - 값이 겹치면 기기 파일이 우선한다.
 * - apply() 가 아니라 commit() 을 쓴다 — 순서와 성공 여부를 확인해야 한다.
 * Log 를 부르지 않는 순수 함수 — JVM 단위 테스트에서 android.util.Log 는 모킹되지 않는다.
 */
internal fun migrateDeviceKeys(legacy: SharedPreferences, device: SharedPreferences): DeviceMigration {
    if (!device.getBoolean(Persistence.DEVICE_MIGRATED_KEY, false)) {
        val copy = device.edit()
        for (key in Persistence.DEVICE_KEYS) {
            val value = legacy.getString(key, null) ?: continue
            if (!device.contains(key)) copy.putString(key, value)
        }
        copy.putBoolean(Persistence.DEVICE_MIGRATED_KEY, true)
        if (!copy.commit()) return DeviceMigration.COPY_FAILED
    }
    if (Persistence.DEVICE_KEYS.none { legacy.contains(it) }) return DeviceMigration.DONE
    val cleanup = legacy.edit()
    Persistence.DEVICE_KEYS.forEach { cleanup.remove(it) }
    return if (cleanup.commit()) DeviceMigration.DONE else DeviceMigration.CLEANUP_PENDING
}

/**
 * 프로세스 안에서 이관 실패를 기억한다.
 * 실제 SharedPreferences 는 디스크 기록이 실패해도 메모리 맵을 되돌리지 않고, 프로세스 안에서는 같은 객체를 돌려준다.
 * 그래서 이관이 실패한 뒤 만들어진 두 번째 Persistence(미니게임 3종 화면, 글꼴·언어 변경에 따른 Activity 재생성)가
 * 메모리에 남은 마커를 보고 복사를 건너뛴 채 옛 값만 지워, 세션·installId·아웃박스를 잃을 수 있었다.
 * 한 번 실패하면 이 프로세스의 나머지 인스턴스도 이관을 다시 시도하지 않고 옛 위치를 쓴다 (다음 실행에서 처음부터 재시도).
 * Persistence 는 메인 스레드에서만 만들어지므로 "검사 후 실행"이 원자적이지 않아도 안전하다(@Volatile 은 가시성용).
 */
internal class DeviceMigrationGate {
    @Volatile private var copyFailed = false

    fun run(legacy: SharedPreferences, device: SharedPreferences): DeviceMigration {
        if (copyFailed) return DeviceMigration.COPY_FAILED
        val result = migrateDeviceKeys(legacy, device)
        if (result == DeviceMigration.COPY_FAILED) copyFailed = true
        return result
    }
}

/**
 * 격리 로그에 남길 원인 예외 — 기기에 묶인 값(세션·신원·아웃박스)은 항상 null.
 * kotlinx 예외 메시지에는 입력 JSON 조각(`JSON input: …`)이 담겨 토큰·식별자가 로그로 새기 때문이다.
 * 호출부의 주의에 기대지 않고 여기서 강제한다.
 */
internal fun causeToLog(key: String, cause: Throwable?): Throwable? =
    if (key in Persistence.DEVICE_KEYS) null else cause

internal sealed interface QuarantineResult {
    data class Written(val name: String) : QuarantineResult
    data object LimitReached : QuarantineResult
    data class Failed(val cause: IOException) : QuarantineResult
}

/** 이 키의 격리 파일들 — 파일명은 `<키>-b<빌드>-<시각>-<임의>.json` */
internal fun quarantinedFiles(dir: File, key: String): List<File> =
    dir.listFiles { file -> file.name.startsWith("$key-") }?.toList().orEmpty()

/** 원문을 격리 폴더에 그대로 남긴다. Log 를 부르지 않는 순수 함수 — JVM 단위 테스트에서 android.util.Log 는 모킹되지 않는다 */
internal fun writeQuarantine(dir: File, key: String, raw: String, buildTag: Int, nowMillis: Long): QuarantineResult =
    try {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("격리 폴더를 만들 수 없음: $dir")  // i18n:skip
        if (quarantinedFiles(dir, key).size >= Persistence.MAX_QUARANTINE_PER_KEY) {
            QuarantineResult.LimitReached
        } else {
            val name = "$key-b$buildTag-$nowMillis-${UUID.randomUUID().toString().take(8)}.json"
            File(dir, name).writeText(raw)
            QuarantineResult.Written(name)
        }
    } catch (e: IOException) {
        QuarantineResult.Failed(e)
    }

/** 지우지 못한 파일 이름들 (모두 지웠으면 빈 목록) */
internal fun deleteQuarantine(dir: File, key: String): List<String> =
    quarantinedFiles(dir, key).filterNot { it.delete() }.map { it.name }

internal data class QuarantinePurge(val removed: List<String>, val failed: List<String>)

/**
 * 마지막 수정 시각이 maxAgeMillis 보다 오래된 격리 파일을 지운다 (키와 무관하게 폴더 전체).
 * 격리 파일은 한 번 쓰고 고치지 않으므로 수정 시각이 곧 격리한 시각이다.
 * 시각을 알 수 없는 파일(lastModified 가 0)은 지우지 않고 남긴다 — 모르면 보존이 안전하다.
 * Log 를 부르지 않는 순수 함수라 JVM 테스트에서 now 를 주입해 검증한다.
 */
internal fun purgeExpiredQuarantineFiles(
    dir: File,
    nowMillis: Long,
    maxAgeMillis: Long = Persistence.QUARANTINE_RETENTION_MILLIS,
): QuarantinePurge {
    val expired = dir.listFiles { file ->
        file.isFile && file.lastModified() > 0 && nowMillis - file.lastModified() > maxAgeMillis
    }?.toList().orEmpty()
    val (gone, failed) = expired.partition { it.delete() }
    return QuarantinePurge(gone.map { it.name }, failed.map { it.name })
}
