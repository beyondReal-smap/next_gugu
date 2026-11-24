package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.Test
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.PlayRewards
import site.smap.gugudan.core.games.WardrobeError
import site.smap.gugudan.store.DexDefeat
import site.smap.gugudan.store.PlayRewardsDebug
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import site.smap.gugudan.store.PlayRoundRecord
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구구 계단·구구 몬스터 보상 저장소(gugu.play.v1) — iOS PlayRewardsStoreTests.swift 와 같은 의미.
 * 저장·읽기·손상 격리, 어드벤처 별 조각 1:1 이관(처음 한 번, 어드벤처 키는 지우지 않음),
 * 별·최고 기록은 결과 저장(recordRound) 때만 쌓이고 도감·옷장은 누르는 순간 저장.
 * Persistence 는 Context 가 필요해 메모리 저장소(MemoryRewardsStorage)로 같은 읽기·쓰기·격리 경로를 지난다.
 */
class PlayRewardsStoreTests {

    // MARK: 도우미

    /** 원문 그대로 들고 있는 저장소 — 격리는 키마다 원문 목록으로 남긴다(Persistence.quarantine 대응) */
    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        val quarantined = mutableMapOf<String, MutableList<String>>()

        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) {
            quarantined.getOrPut(key) { mutableListOf() }.add(raw)
        }

        fun quarantinedCount(key: String): Int = quarantined[key]?.size ?: 0
    }

    private val playKey = "gugu.test.play"
    private val adventureKey = "gugu.test.adventure"
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeStore(storage: MemoryRewardsStorage, debug: PlayRewardsDebug? = null) =
        PlayRewardsStore(storage, key = playKey, adventureKey = adventureKey, debug = debug)

    private fun saved(storage: MemoryRewardsStorage): PlayRewards =
        json.decodeFromString(PlayRewards.serializer(), assertNotNull(storage.values[playKey]))

    /** 어드벤처 저장본(AdventureProgress JSON) 모양 그대로 — 이관은 starShards 만 읽는다 */
    private fun adventureJSON(shards: Int): String =
        """{"version":1,"defeatedNpcs":["r2-boss"],"battlesWon":3,"battlesLost":1,"achievements":[],""" +
            """"starShards":$shards,"ownedColors":["mint"],"ownedHats":[],"equippedColor":"mint"}"""

    // MARK: 저장·읽기

    /** 저장본이 없으면 빈 보상으로 시작하고, 이관 표시까지 바로 저장한다 */
    @Test fun freshStoreStartsEmptyAndPersists() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)

        assertEquals(0, store.stars)
        assertEquals(0, store.best(PlayGame.STAIRS))
        assertEquals(0, store.best(PlayGame.MONSTER))
        assertNull(store.equipped)
        assertEquals(0, store.dexTotal)
        assertTrue(saved(storage).adventureMigrated)
        assertEquals(0, storage.quarantinedCount(playKey))
    }

    /** 별·기록·도감·옷장을 쓴 뒤 다시 열면 그대로 읽힌다 */
    @Test fun roundTripRestoresEverything() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        store.recordRound(PlayGame.STAIRS, score = 12, stars = 30, bestStreak = 12)
        store.recordRound(PlayGame.MONSTER, score = 5, stars = 4, bestStreak = 3)
        store.recordDefeat(MonsterKind.ORANGE)
        store.buy(Accessory.RIBBON)

        val reopened = makeStore(storage)

        assertEquals(store.rewards, reopened.rewards)
        assertEquals(30 + 4 - Accessory.RIBBON.price, reopened.stars)
        assertEquals(12, reopened.best(PlayGame.STAIRS))
        assertEquals(5, reopened.best(PlayGame.MONSTER))
        assertEquals(Accessory.RIBBON, reopened.equipped)
        assertTrue(reopened.owns(Accessory.RIBBON))
        assertEquals(1, reopened.dexTotal)
        assertTrue(MonsterDex.isSeen(MonsterKind.ORANGE, reopened.dexSeenMask))
    }

    /** 못 읽는 저장본은 원본을 격리하고 새 보상으로 시작한다(덮어쓴 뒤에는 정상 저장본) */
    @Test fun corruptSaveIsQuarantinedAndReplaced() {
        val storage = MemoryRewardsStorage()
        val corrupt = "{깨진 보상"
        storage.values[playKey] = corrupt

        val store = makeStore(storage)

        assertEquals(0, store.stars)
        assertEquals<List<String>?>(listOf(corrupt), storage.quarantined[playKey])
        assertEquals(store.rewards, saved(storage))
    }

    /** 형식은 맞아도 규칙 밖(갖지 않은 장신구를 씀·음수 별·모르는 비트)이면 조용히 고치지 않고 격리한다 */
    @Test fun ruleBreakingSavesAreQuarantined() {
        val cases = listOf(
            """{"stars":3,"ownedMask":0,"equipped":0,"adventureMigrated":true}""",
            """{"stars":-1,"adventureMigrated":true}""",
            """{"stars":1,"ownedMask":1024,"adventureMigrated":true}""",
        )
        for (raw in cases) {
            val storage = MemoryRewardsStorage()
            storage.values[playKey] = raw

            val store = makeStore(storage)

            assertEquals(0, store.stars, raw)
            assertNull(store.equipped, raw)
            assertEquals(1, storage.quarantinedCount(playKey), raw)
        }
    }

    // MARK: 어드벤처 별 조각 이관

    /** 별 조각은 1:1 로 처음 한 번만 옮긴다. 어드벤처 저장본은 그대로 둔다 */
    @Test fun adventureShardsMigrateOnlyOnce() {
        val storage = MemoryRewardsStorage()
        val adventure = adventureJSON(shards = 17)
        storage.values[adventureKey] = adventure

        val first = makeStore(storage)
        assertEquals(17, first.stars)
        assertTrue(saved(storage).adventureMigrated)

        // 다시 열어도(앱 재실행) 또 더하지 않는다 — 옷장에서 쓴 뒤에도 마찬가지
        val second = makeStore(storage)
        assertEquals(17, second.stars)
        second.buy(Accessory.RIBBON)
        val third = makeStore(storage)
        assertEquals(17 - Accessory.RIBBON.price, third.stars)

        assertEquals(adventure, storage.values[adventureKey])
        assertEquals(0, storage.quarantinedCount(adventureKey))
    }

    /** 이관은 기존 별에 더한다(이관 표시가 없는 저장본 — 이관 전에 별을 받은 경우) */
    @Test fun migrationAddsToExistingStars() {
        val storage = MemoryRewardsStorage()
        storage.values[playKey] = """{"stars":5}"""
        storage.values[adventureKey] = adventureJSON(shards = 8)

        assertEquals(13, makeStore(storage).stars)
    }

    /** 어드벤처를 안 한 기기(키 없음)는 0개 이관 — 이관 표시는 남겨 다음에 다시 보지 않는다 */
    @Test fun missingAdventureSaveMigratesNothing() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)

        assertEquals(0, store.stars)
        assertTrue(saved(storage).adventureMigrated)
        assertNull(storage.values[adventureKey])

        // 나중에 어드벤처 저장본이 생겨도 다시 옮기지 않는다
        storage.values[adventureKey] = adventureJSON(shards = 9)
        assertEquals(0, makeStore(storage).stars)
    }

    /** 못 읽는 어드벤처 저장본(깨진 JSON·음수 조각)은 격리되고 옮길 조각은 0 */
    @Test fun corruptAdventureSaveMigratesNothing() {
        for (raw in listOf("{깨진 어드벤처", adventureJSON(shards = -3))) {
            val storage = MemoryRewardsStorage()
            storage.values[adventureKey] = raw

            assertEquals(0, makeStore(storage).stars, raw)
            assertEquals<List<String>?>(listOf(raw), storage.quarantined[adventureKey], raw)
        }
    }

    /** starShards 가 없던 구버전 어드벤처 저장본은 0개(어드벤처 디코더와 같음) — 격리하지 않음 */
    @Test fun adventureSaveWithoutShardsMigratesZero() {
        val storage = MemoryRewardsStorage()
        storage.values[adventureKey] = """{"version":1,"defeatedNpcs":[],"battlesWon":0}"""

        assertEquals(0, makeStore(storage).stars)
        assertEquals(0, storage.quarantinedCount(adventureKey))
    }

    // MARK: 결과 저장(별·최고 기록)

    /** 별은 결과 저장 때만 쌓이고, 점수가 지난 기록보다 높을 때만 새 기록(0점은 기록 아님) */
    @Test fun recordRoundAddsStarsAndKeepsBest() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        // 2026-11-23 미션(계단 20·몬스터 10·연속 5)을 넘지 않는 판들 — 미션 완료는 DailyMissionsTests 가 본다
        val day = LocalDate.of(2026, 11, 23)
        fun round(score: Int, stars: Int) = store.recordRound(PlayGame.STAIRS, score = score, stars = stars, bestStreak = 0, now = day)

        assertEquals(PlayRoundRecord(0, false, 0, emptyList(), false), round(score = 0, stars = 0))
        assertEquals(PlayRoundRecord(0, true, 9, emptyList(), false), round(score = 6, stars = 9))
        assertEquals(PlayRoundRecord(6, false, 11, emptyList(), false), round(score = 6, stars = 2))
        assertEquals(PlayRoundRecord(6, false, 12, emptyList(), false), round(score = 4, stars = 1))

        assertEquals(6, store.best(PlayGame.STAIRS))
        assertEquals(0, store.best(PlayGame.MONSTER))   // 게임마다 따로
        assertEquals(12, saved(storage).stars)
        assertEquals(6, saved(storage).stairsBest)
    }

    /** 도감 처치는 별을 건드리지 않고 바로 저장한다(처음 만난 종만 도감 칸이 채워짐) */
    @Test fun recordDefeatSavesDexWithoutStars() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)

        assertEquals(DexDefeat(newlySeen = true, unlocked = null), store.recordDefeat(MonsterKind.GREEN))
        assertEquals(DexDefeat(newlySeen = false, unlocked = null), store.recordDefeat(MonsterKind.GREEN))
        assertEquals(0, store.stars)
        assertEquals(2, saved(storage).dexTotal)
        assertEquals(1, MonsterDex.seenCount(saved(storage).dexSeenMask))
    }

    /** 열 번째 처치에서 분홍이 해금 — 해금 알림은 저장소가 돌려준다 */
    @Test fun recordDefeatReportsUnlock() {
        val store = makeStore(MemoryRewardsStorage())
        repeat(9) { store.recordDefeat(MonsterKind.SKY) }
        assertEquals(MonsterKind.PINK, store.recordDefeat(MonsterKind.SKY).unlocked)
    }

    // MARK: 옷장

    /** 사면 별이 줄고 바로 쓴다. 별이 모자라거나 이미 가졌으면 던지고 아무것도 바꾸지 않는다 */
    @Test fun buyEquipAndErrors() {
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage)
        store.recordRound(PlayGame.STAIRS, score = 1, stars = 40, bestStreak = 1)

        assertEquals(
            WardrobeError.NotEnoughStars(Accessory.PARTY_HAT, lack = 10),
            assertFailsWith<WardrobeError.NotEnoughStars> { store.buy(Accessory.PARTY_HAT) },
        )
        assertEquals(40, store.stars)
        store.buy(Accessory.FLOWER_CROWN)
        assertEquals(10, store.stars)
        assertEquals(Accessory.FLOWER_CROWN, store.equipped)
        assertEquals(
            WardrobeError.AlreadyOwned(Accessory.FLOWER_CROWN),
            assertFailsWith<WardrobeError.AlreadyOwned> { store.buy(Accessory.FLOWER_CROWN) },
        )

        store.equip(null)
        assertNull(saved(storage).equippedAccessory)
        store.equip(Accessory.FLOWER_CROWN)
        assertEquals(Accessory.FLOWER_CROWN, saved(storage).equippedAccessory)
        assertEquals(10, saved(storage).stars)
        // 갖지 않은 장신구를 쓰라는 호출은 화면 쪽 버그 — 던진다
        assertFailsWith<IllegalArgumentException> { store.equip(Accessory.CROWN) }
    }

    // MARK: DEBUG 스크린숏 훅

    /** debug.gugu.play_* — 저장본에 덮어쓰고(장신구는 가진 것으로 만들어 씀), 잘못된 값은 멈춘다 */
    @Test fun debugHooksOverrideSave() {
        val props = mapOf(
            "debug.gugu.play_stars" to "250",
            "debug.gugu.play_equip" to "wizardHat",
            "debug.gugu.play_stairs_best" to "23",
        )
        val storage = MemoryRewardsStorage()
        val store = makeStore(storage, PlayRewardsDebug.from { props[it] })

        assertEquals(250, store.stars)
        assertEquals(Accessory.WIZARD_HAT, store.equipped)
        assertEquals(23, store.best(PlayGame.STAIRS))
        assertEquals(store.rewards, saved(storage))
        assertEquals("flowerCrown", PlayRewardsDebug.debugName(Accessory.FLOWER_CROWN))

        assertFailsWith<IllegalArgumentException> { PlayRewardsDebug.from { if (it == "debug.gugu.play_stars") "-1" else null } }
        assertFailsWith<IllegalArgumentException> { PlayRewardsDebug.from { if (it == "debug.gugu.play_equip") "cape" else null } }
    }

    /** debug.gugu.play_dex=N — 누적 처치 N, 그때까지 해금된 종은 모두 만남(9 → 처음 4종, 다음 처치에 분홍 해금) */
    @Test fun debugDexHookMarksUnlockedKindsSeen() {
        val store = makeStore(MemoryRewardsStorage(), PlayRewardsDebug.from { if (it == "debug.gugu.play_dex") "9" else null })
        assertEquals(9, store.dexTotal)
        assertEquals(4, MonsterDex.seenCount(store.dexSeenMask))
        assertFalse(MonsterDex.isSeen(MonsterKind.PINK, store.dexSeenMask))
        assertEquals(MonsterKind.PINK, store.recordDefeat(MonsterKind.GREEN).unlocked)

        val all = makeStore(MemoryRewardsStorage(), PlayRewardsDebug.from { if (it == "debug.gugu.play_dex") "70" else null })
        assertEquals(MonsterDex.KINDS, MonsterDex.seenCount(all.dexSeenMask))
        assertFailsWith<IllegalArgumentException> { PlayRewardsDebug.from { if (it == "debug.gugu.play_dex") "x" else null } }
    }
}
