package site.smap.gugudan

import android.content.SharedPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import site.smap.gugudan.services.DeviceMigration
import site.smap.gugudan.services.DeviceMigrationGate
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.QuarantineResult
import site.smap.gugudan.services.causeToLog
import site.smap.gugudan.services.deleteQuarantine
import site.smap.gugudan.services.migrateDeviceKeys
import site.smap.gugudan.services.purgeExpiredQuarantineFiles
import site.smap.gugudan.services.quarantinedFiles
import site.smap.gugudan.services.writeQuarantine
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// 영속화 검증 — iOS Persistence 격리 테스트(CoreTests "손상 저장본 격리") 패리티.
// Persistence 는 Context 에 의존하므로 Log 를 부르지 않는 순수 함수(writeQuarantine 등)를 검증한다.

class PersistenceTests {

    @get:Rule val tmp = TemporaryFolder()

    private fun quarantineDir() = File(tmp.root, "quarantine")

    // MARK: 손상 저장본 격리

    /** 원문을 그대로, 키 접두 파일명으로 남긴다 */
    @Test fun quarantineKeepsRawTextUnderKeyPrefix() {
        val dir = quarantineDir()
        val result = writeQuarantine(dir, "gugu.progress.v1", "{손상된 저장본", 19, 1_000L)

        assertTrue(result is QuarantineResult.Written)
        val files = quarantinedFiles(dir, "gugu.progress.v1")
        assertEquals(1, files.size)
        assertEquals("{손상된 저장본", files.single().readText())
        assertTrue(files.single().name.startsWith("gugu.progress.v1-b19-1000-"))
    }

    /** 실패가 반복돼도 키당 3개까지만 남기고, 앞선(원본에 가까운) 것을 보존한다 */
    @Test fun quarantineKeepsFirstThreeOnly() {
        val dir = quarantineDir()
        val results = (1..4).map { writeQuarantine(dir, "k", "corrupt-$it", 1, it.toLong()) }

        assertEquals(QuarantineResult.LimitReached, results[3])
        assertEquals(
            listOf("corrupt-1", "corrupt-2", "corrupt-3"),
            quarantinedFiles(dir, "k").map { it.readText() }.sorted(),
        )
    }

    /** 격리 폴더를 못 만들어도 예외를 던지지 않고 Failed 로 알린다 — 앱은 계속 진행한다 */
    @Test fun quarantineFailureIsReportedNotThrown() {
        val blocker = tmp.newFile("quarantine")   // 폴더 자리를 파일이 차지한 상태

        assertTrue(writeQuarantine(blocker, "k", "x", 1, 1L) is QuarantineResult.Failed)
    }

    /** 삭제는 그 키의 파일만 지운다 */
    @Test fun deleteQuarantineTouchesOnlyThatKey() {
        val dir = quarantineDir()
        writeQuarantine(dir, "a", "1", 1, 1L)
        writeQuarantine(dir, "b", "2", 1, 2L)

        assertEquals(emptyList(), deleteQuarantine(dir, "a"))
        assertTrue(quarantinedFiles(dir, "a").isEmpty())
        assertEquals(1, quarantinedFiles(dir, "b").size)
    }

    /** 폴더가 아직 없어도(대부분의 실행) 조회·삭제는 조용히 빈 결과다 */
    @Test fun missingDirectoryIsEmptyNotError() {
        val dir = quarantineDir()

        assertTrue(quarantinedFiles(dir, "k").isEmpty())
        assertEquals(emptyList(), deleteQuarantine(dir, "k"))
    }

    /** 보존 기한(30일)이 지난 격리본만 지운다 — 기한 안쪽은 남기고, 시각을 알 수 없는 파일도 남긴다 */
    @Test fun purgeRemovesOnlyExpiredQuarantine() {
        val dir = quarantineDir()
        val hour = 60L * 60 * 1000
        val day = 24 * hour
        val now = 1_000L * day
        // 키당 상한(3개) 때문에 키를 나눠 쓴다. 내용으로 파일을 구분해 수정 시각을 맞춘다:
        // 31일 전(만료) / 29일 23시간(기한 안) / 어제 / 시각 미상(0)
        writeQuarantine(dir, "a", "expired", 1, 1L)
        writeQuarantine(dir, "a", "edge", 1, 2L)
        writeQuarantine(dir, "b", "recent", 1, 3L)
        writeQuarantine(dir, "b", "unknown", 1, 4L)
        val modified = mapOf(
            "expired" to now - 31 * day,
            "edge" to now - (29 * day + 23 * hour),
            "recent" to now - day,
            "unknown" to 0L,
        )
        val files = dir.listFiles()!!
        assertEquals(4, files.size)
        for (file in files) file.setLastModified(modified.getValue(file.readText()))

        val result = purgeExpiredQuarantineFiles(dir, now)

        assertEquals(1, result.removed.size)
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf("edge", "recent", "unknown"), dir.listFiles()!!.map { it.readText() }.sorted())
    }

    /** 폴더가 없으면(대부분의 실행) 아무 일도 하지 않는다 */
    @Test fun purgeWithoutQuarantineDirectoryDoesNothing() {
        val result = purgeExpiredQuarantineFiles(quarantineDir(), 1_000_000_000_000L)

        assertTrue(result.removed.isEmpty())
        assertTrue(result.failed.isEmpty())
    }

    /** 기기에 묶인 값은 원인 예외를 로그에 남기지 않는다 — 예외 메시지에 입력 JSON 조각(토큰)이 담긴다 */
    @Test fun causeIsDroppedForDeviceKeysOnly() {
        val cause = IllegalStateException("JSON input: {\"accessToken\":\"SECRET\"")

        Persistence.DEVICE_KEYS.forEach { assertNull(causeToLog(it, cause), it) }
        assertSame(cause, causeToLog(Persistence.PROGRESS_KEY, cause))
        assertNull(causeToLog(Persistence.PROGRESS_KEY, null))
    }

    /** 재로그인 대기 표시는 세션의 짝 — 백업에서 빠지는 기기 파일에 함께 둬야 복원 기기에서 익명 발급이 막히지 않는다 */
    @Test fun reauthMarkerIsDeviceKey() {
        assertTrue(Persistence.AUTH_REAUTH_KEY in Persistence.DEVICE_KEYS)
        assertTrue(Persistence.AUTH_SESSION_KEY in Persistence.DEVICE_KEYS)
    }

    // MARK: 기기 값 이관 (gugu → gugu_device)

    private val session = Persistence.AUTH_SESSION_KEY
    private val identity = Persistence.LEARNING_IDENTITY_KEY
    private val outbox = Persistence.LEARNING_OUTBOX_KEY

    /** 옛 "gugu" 파일 — 기기 값 3키 + 기기 값이 아닌 키(진행도). 디스크에도 이미 저장된 상태다 */
    private fun legacyWithDeviceKeys(commitOk: Boolean = true) = FakePrefs(
        commitOk,
        initial = mapOf(session to "S-old", identity to "I-old", outbox to "O-old", Persistence.PROGRESS_KEY to "P"),
    )

    /** 업데이트: 3키가 기기 파일로 값 그대로 옮겨지고, 진행도 등 나머지는 옛 파일에 남는다 */
    @Test fun migrationMovesOnlyDeviceKeys() {
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs()

        assertEquals(DeviceMigration.DONE, migrateDeviceKeys(legacy, device))

        assertEquals("S-old", device.map[session])
        assertEquals("I-old", device.map[identity])
        assertEquals("O-old", device.map[outbox])
        assertEquals(true, device.map[Persistence.DEVICE_MIGRATED_KEY])
        assertFalse(device.map.containsKey(Persistence.PROGRESS_KEY))
        assertEquals(setOf(Persistence.PROGRESS_KEY), legacy.map.keys)   // 기기 키만 빠졌다
        assertEquals(device.map.toMap(), device.disk)                    // 디스크에도 저장됐다
    }

    /** 신규 설치: 옮길 값이 없어도 마커는 남긴다 */
    @Test fun migrationOnFreshInstallWritesMarkerOnly() {
        val legacy = FakePrefs(initial = mapOf(Persistence.PROGRESS_KEY to "P"))
        val device = FakePrefs()

        assertEquals(DeviceMigration.DONE, migrateDeviceKeys(legacy, device))

        assertEquals(setOf(Persistence.DEVICE_MIGRATED_KEY), device.map.keys)
        assertEquals(setOf(Persistence.PROGRESS_KEY), legacy.map.keys)
    }

    /** 마커가 있으면 복사하지 않는다 — 원본 삭제가 실패했던 옛 값이 사용자가 지운 뒤에 되살아나면 안 된다 */
    @Test fun migrationWithMarkerNeverResurrectsLegacyValues() {
        val legacy = legacyWithDeviceKeys()
        // 이미 이관됐고, 세션·아웃박스는 사용자가 지운 상태 (예: 계정 삭제)
        val device = FakePrefs(
            initial = mapOf(Persistence.DEVICE_MIGRATED_KEY to true, identity to "I-new"),
        )

        assertEquals(DeviceMigration.DONE, migrateDeviceKeys(legacy, device))

        assertEquals("I-new", device.map[identity])
        assertFalse(device.map.containsKey(session))
        assertFalse(device.map.containsKey(outbox))
        assertEquals(setOf(Persistence.PROGRESS_KEY), legacy.map.keys)   // 원본만 정리한다
    }

    /** 값이 겹치면 기기 파일이 우선한다 */
    @Test fun migrationKeepsDeviceValueOnConflict() {
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs(initial = mapOf(identity to "I-dev"))

        migrateDeviceKeys(legacy, device)

        assertEquals("I-dev", device.map[identity])
        assertEquals("S-old", device.map[session])
    }

    /** 기기 파일 기록이 실패하면 원본은 손대지 않는다 — 이번 실행은 옛 위치를 계속 쓴다(라우팅 끔) */
    @Test fun migrationCopyFailureKeepsLegacyUntouched() {
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs(commitOk = false)

        val result = migrateDeviceKeys(legacy, device)

        assertEquals(DeviceMigration.COPY_FAILED, result)
        assertFalse(result.routesToDevice())
        assertEquals(setOf(session, identity, outbox, Persistence.PROGRESS_KEY), legacy.map.keys)
        assertEquals(legacy.map.toMap(), legacy.disk)   // 옛 파일은 메모리도 디스크도 그대로
        assertTrue(device.disk.isEmpty())               // 디스크에는 아무것도 저장되지 않았다
    }

    /** 원본 삭제만 실패하면 데이터는 기기 파일에 안전하다 — 라우팅은 켜고, 다음 실행에서 원본만 정리한다 */
    @Test fun migrationCleanupFailureIsRetriedNextRun() {
        val legacy = legacyWithDeviceKeys(commitOk = false)   // legacy 는 삭제(commit)에만 쓰인다
        val device = FakePrefs()

        val first = migrateDeviceKeys(legacy, device)
        assertEquals(DeviceMigration.CLEANUP_PENDING, first)
        assertTrue(first.routesToDevice())
        assertEquals("S-old", device.disk[session])            // 기기 파일에는 안전하게 저장됐다
        assertTrue(legacy.disk.containsKey(session))           // 옛 파일의 디스크에는 아직 남아 있다

        // 프로세스 재시작 — 메모리를 버리고 디스크에서 다시 연다. 마커가 있으므로 다시 복사하지 않고 원본만 정리한다
        val legacy2 = legacy.restart()
        val device2 = device.restart()
        assertEquals(DeviceMigration.DONE, migrateDeviceKeys(legacy2, device2))
        assertEquals(setOf(Persistence.PROGRESS_KEY), legacy2.map.keys)
        assertEquals("S-old", device2.map[session])
    }

    /** MainActivity·미니게임 화면이 각자 Persistence 를 만들어도 이관은 몇 번을 돌려도 결과가 같다 */
    @Test fun migrationIsIdempotent() {
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs()
        migrateDeviceKeys(legacy, device)
        val snapshot = device.map.toMap()

        repeat(3) { assertEquals(DeviceMigration.DONE, migrateDeviceKeys(legacy, device)) }

        assertEquals(snapshot, device.map)
    }

    /**
     * 실제 SharedPreferences 는 디스크 기록이 실패해도 메모리 맵을 되돌리지 않고, 프로세스 안에서 같은 객체를 돌려준다.
     * 게이트가 없으면 실패 뒤 만들어진 두 번째 인스턴스가 메모리의 마커를 보고 옛 값만 지워 세션·installId·아웃박스를 잃는다.
     */
    @Test fun copyFailureIsRememberedForTheRestOfTheProcess() {
        val gate = DeviceMigrationGate()
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs(commitOk = false)   // 디스크 기록 실패

        // 인스턴스 A — 실패. 그런데 실제처럼 메모리 맵은 이미 바뀌어 마커가 들어 있다
        assertEquals(DeviceMigration.COPY_FAILED, gate.run(legacy, device))
        assertEquals(true, device.map[Persistence.DEVICE_MIGRATED_KEY])
        assertTrue(device.disk.isEmpty())

        // 인스턴스 B — 같은 프로세스에서 디스크가 회복돼도, 이관을 다시 시도하지 않고 옛 값을 지우지 않는다
        device.commitOk = true
        assertEquals(DeviceMigration.COPY_FAILED, gate.run(legacy, device))
        assertEquals(setOf(session, identity, outbox, Persistence.PROGRESS_KEY), legacy.map.keys)
        assertEquals(legacy.map.toMap(), legacy.disk)

        // 프로세스가 다시 시작하면 처음부터 시도해 이관된다
        val device2 = device.restart()
        val legacy2 = legacy.restart()
        assertEquals(DeviceMigration.DONE, DeviceMigrationGate().run(legacy2, device2))
        assertEquals("S-old", device2.map[session])
        assertEquals(setOf(Persistence.PROGRESS_KEY), legacy2.map.keys)
    }

    /** 이관이 성공했다면 게이트는 아무것도 막지 않는다 */
    @Test fun gateDoesNotBlockAfterSuccess() {
        val gate = DeviceMigrationGate()
        val legacy = legacyWithDeviceKeys()
        val device = FakePrefs()

        assertEquals(DeviceMigration.DONE, gate.run(legacy, device))
        assertEquals(DeviceMigration.DONE, gate.run(legacy, device))
        assertEquals("S-old", device.map[session])
    }

    // MARK: 백업 규칙

    /** 백업 규칙의 제외 경로가 실제 기기 파일 이름과 어긋나면(이름 변경 등) 토큰이 조용히 백업에 실린다 */
    @Test fun backupRulesExcludeExactlyTheDeviceFile() {
        val file = "${Persistence.DEVICE_PREFS_NAME}.xml"
        val expected = setOf(file, "$file.bak")
        // 단위 테스트의 작업 디렉터리는 모듈(app/) 이다
        fun body(name: String) = File("src/main/res/xml/$name").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")   // 주석 제거
        fun excluded(text: String) = Regex("""<exclude\s+domain="sharedpref"\s+path="([^"]+)"""")
            .findAll(text).map { it.groupValues[1] }.toList()

        val legacyRules = body("backup_rules.xml")
        assertEquals(expected, excluded(legacyRules).toSet())
        assertFalse(legacyRules.contains("<include"), "<include> 가 있으면 나머지가 전부 백업에서 빠진다")

        // API 31+ — 클라우드 백업과 기기 이전 양쪽 모두에 걸려야 한다 (하나만 빠져도 토큰이 새 기기로 복제된다)
        val extraction = body("data_extraction_rules.xml")
        assertFalse(extraction.contains("<include"), "<include> 가 있으면 나머지가 전부 백업에서 빠진다")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val block = Regex("<$section>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL)
                .find(extraction)?.groupValues?.get(1)
            assertTrue(block != null, "$section 블록이 없다")
            assertEquals(expected, excluded(block).toSet(), section)
        }
    }

    /** 매니페스트가 두 규칙 파일을 실제로 가리키는가 — 속성이 빠지면 XML 이 맞아도 제외가 조용히 풀려 토큰이 백업에 실린다 */
    @Test fun manifestWiresBothBackupRules() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")   // 주석 제거

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""), "API 28~30 연결 누락")
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""), "API 31+ 연결 누락")
    }
}

/**
 * 메모리 SharedPreferences — Robolectric 없이 이관을 검증한다.
 * 실제 SharedPreferencesImpl 처럼 commit()/apply() 는 디스크 기록 성공 여부와 무관하게 **메모리 맵을 먼저 바꾼다**.
 * commitOk=false 면 디스크(disk)는 갱신되지 않고 commit() 이 false 를 돌려준다(저장공간 부족 등 디스크 오류 재현).
 */
private class FakePrefs(var commitOk: Boolean = true, initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    /** 메모리 맵 — 프로세스 안에서 getSharedPreferences 가 돌려주는 캐시 객체의 상태 */
    val map = initial.toMutableMap()
    /** 디스크에 실제로 남은 내용 */
    var disk: Map<String, Any> = initial.toMap()

    /** 프로세스 재시작 — 메모리를 버리고 디스크 내용으로 다시 연다 */
    fun restart() = FakePrefs(commitOk = true, initial = disk)

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any>()
        private val removes = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) removes += key else puts[key] = value
            return this
        }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { puts[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { puts[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { removes += key; return this }
        override fun clear(): SharedPreferences.Editor { clearAll = true; return this }

        override fun commit(): Boolean {
            applyToMemory()
            if (!commitOk) return false
            disk = map.toMap()
            return true
        }
        override fun apply() {
            applyToMemory()
            if (commitOk) disk = map.toMap()
        }

        private fun applyToMemory() {
            if (clearAll) map.clear()
            removes.forEach { map.remove(it) }
            map.putAll(puts)
        }
    }
}
