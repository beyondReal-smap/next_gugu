package site.smap.gugudan.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.DailyMissionLog
import site.smap.gugudan.core.games.DailyMissionPlan
import site.smap.gugudan.core.games.Mission
import site.smap.gugudan.core.games.MissionBoard
import site.smap.gugudan.core.games.MissionItem
import site.smap.gugudan.core.games.MissionKind
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.PlayRewards
import site.smap.gugudan.core.games.Wardrobe
import site.smap.gugudan.core.games.WeekStamp
import site.smap.gugudan.services.Persistence
import java.time.LocalDate

// 구구 계단·구구 몬스터 보상 저장소 — 모은 별·옷장·도감·최고 기록 (키 gugu.play.v1, iOS PlayRewardsStore.swift 와 같은 규칙)
// 저장 시점은 시험판 그대로: 별·최고 기록은 결과 화면을 띄울 때 한 번(판 도중 나가면 안 쌓임),
// 도감은 처치하는 순간(새 몬스터를 만난 경험은 판 결과와 묶지 않음). 옷장 사기·쓰기·벗기는 누르는 순간.
// 오늘의 미션 진행도도 결과 화면 때 함께 쌓인다(판 도중 나가면 별처럼 안 쌓임). "오늘"은 Commit.todayStr(기기 현지 달력).
// 처음 한 번 어드벤처 별 조각(gugu.adventure.v1 의 starShards)을 별로 1:1 옮긴다(대표님 결정 Q2).
// 어드벤처 타입(core/adventure)에는 기대지 않고 원본 JSON 에서 starShards 만 읽는다 — 그 폴더는 2.9.0 에서 지웠다.
// 어드벤처 키도 지우지 않는다.

/** 결과 화면 한 번의 저장 결과 */
data class PlayRoundRecord(
    /** 이번 판을 넣기 전의 최고 기록 */
    val previousBest: Int,
    /** 이번 판이 새 기록인지(0점은 기록이 아님) */
    val isRecord: Boolean,
    /** 받은 별을 넣은 뒤의 모은 별 */
    val starTotal: Int,
    /** 이번 판으로 새로 목표를 넘은 오늘의 미션(미션 순서대로) */
    val missionsCompleted: List<Mission>,
    /** 이번 판으로 오늘의 미션 3개가 처음 다 됨 → 홈에서 보물상자를 열 수 있음 */
    val allMissionsDone: Boolean,
)

/** 처치 한 번의 도감 변화 */
data class DexDefeat(
    /** 이 종을 처음 물리침 → 도감 칸이 채워짐 */
    val newlySeen: Boolean,
    /** 이번 처치로 새로 해금된 종(없으면 null) → 다음 몬스터로 등장 */
    val unlocked: MonsterKind?,
)

/**
 * 저장본 원문 읽기·쓰기·격리. 앱은 Persistence(SharedPreferences)를, JVM 단위 테스트는 메모리 구현을 넘긴다 —
 * Persistence 는 Context 가 있어야 만들 수 있고 android.util.Log 를 부르기 때문이다.
 * 해석(JSON)은 저장소가 직접 해서 테스트도 실제 읽기 경로를 그대로 지난다.
 */
interface PlayRewardsStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    /** 못 읽는 원문을 덮어쓰기 전에 남긴다(Persistence.quarantine) */
    fun quarantine(key: String, raw: String, cause: Throwable)
}

class PersistencePlayRewardsStorage(private val persistence: Persistence) : PlayRewardsStorage {
    override fun read(key: String): String? = persistence.getString(key)
    override fun write(key: String, value: String) = persistence.putString(key, value)
    override fun quarantine(key: String, raw: String, cause: Throwable) = persistence.quarantine(key, raw, cause)
}

/**
 * DEBUG 스크린숏 훅(iOS GUGU_PLAY_* 와 같은 뜻) — 저장본에 덮어쓴다.
 * stars = 모은 별, equip = 가진 것으로 만들고 씀, stairsBest = 구구 계단 최고 기록(깃발),
 * dex = 몬스터 누적 처치 N(그때까지 해금된 종은 모두 만난 것으로 — 9 면 다음 처치로 10번째가 되며 새 종이 바로 등장),
 * missions = 오늘의 미션 화면(iOS GUGU_PLAY_MISSIONS): half 첫 미션만 완료·둘째 절반 / ready 3개 다 채움·상자 안 엶 /
 * opened 다 채움·연 상태 + 이번 주 월요일부터 오늘까지 도장
 */
data class PlayRewardsDebug(
    val stars: Int? = null,
    val equip: Accessory? = null,
    val stairsBest: Int? = null,
    val dex: Int? = null,
    val missions: Missions? = null,
) {
    enum class Missions { HALF, READY, OPENED }

    companion object {
        /**
         * prop("debug.gugu.play_stars") 처럼 시스템 프로퍼티를 읽는 함수를 받는다(DevProps.get — 릴리스는 늘 null).
         * 잘못된 값은 조용히 넘기지 않고 멈춘다
         */
        fun from(prop: (String) -> String?): PlayRewardsDebug = PlayRewardsDebug(
            stars = prop("debug.gugu.play_stars")?.let { count("debug.gugu.play_stars", it) },
            equip = prop("debug.gugu.play_equip")?.let { raw ->
                requireNotNull(Accessory.entries.firstOrNull { debugName(it) == raw }) {
                    "debug.gugu.play_equip 는 ${Accessory.entries.map(::debugName)} 중 하나: $raw"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
                }
            },
            stairsBest = prop("debug.gugu.play_stairs_best")?.let { count("debug.gugu.play_stairs_best", it) },
            dex = prop("debug.gugu.play_dex")?.let { count("debug.gugu.play_dex", it) },
            missions = prop("debug.gugu.play_missions")?.let { raw ->
                requireNotNull(Missions.entries.firstOrNull { it.name.lowercase() == raw }) {
                    "debug.gugu.play_missions 는 half·ready·opened 중 하나: $raw"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
                }
            },
        )

        /** iOS 훅과 같은 이름(String(describing:)) — FLOWER_CROWN → flowerCrown */
        fun debugName(item: Accessory): String =
            item.name.lowercase().split('_').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar(Char::uppercase) }
                .joinToString("")

        private fun count(name: String, raw: String): Int {
            val n = raw.toIntOrNull()
            require(n != null && n >= 0) { "$name 은 0 이상 정수: $raw" }
            return n
        }
    }
}

/** key·adventureKey 는 테스트가 고유 키를 넘겨 실제 저장본과 섞이지 않게 한다(iOS 와 같은 인자) */
class PlayRewardsStore(
    private val storage: PlayRewardsStorage,
    private val key: String = Persistence.PLAY_REWARDS_KEY,
    adventureKey: String = Persistence.ADVENTURE_KEY,
    debug: PlayRewardsDebug? = null,
) {
    var rewards: PlayRewards by mutableStateOf(PlayRewards())
        private set

    init {
        // 못 읽는 저장본은 원본을 격리하고 새 저장본으로 시작(GameStore 와 같은 관례)
        var r = load() ?: PlayRewards()
        if (!r.adventureMigrated) {
            r = r.copy(stars = Math.addExact(r.stars, adventureShards(adventureKey)), adventureMigrated = true)
        }
        if (debug != null) r = applyDebug(r, debug, LocalDate.now())
        rewards = r
        persist()
    }

    // MARK: 읽기

    val stars: Int get() = rewards.stars
    val equipped: Accessory? get() = rewards.equippedAccessory
    val dexTotal: Int get() = rewards.dexTotal
    val dexSeenMask: Int get() = rewards.dexSeenMask

    fun best(game: PlayGame): Int = rewards.best(game)

    fun owns(item: Accessory): Boolean = Wardrobe.owns(item, rewards.ownedMask)

    // MARK: 결과 화면

    /**
     * 결과 화면을 띄우는 순간 한 번: 받은 별을 쌓고, 점수가 지난 최고 기록보다 높으면 새 기록으로 저장하고,
     * 오늘의 미션 진행도(계단·몬스터·별 누적, 연속은 max)를 더한다. bestStreak = 이번 판의 최고 연속 정답
     */
    fun recordRound(game: PlayGame, score: Int, stars: Int, bestStreak: Int, now: LocalDate = LocalDate.now()): PlayRoundRecord {
        require(score >= 0) { "점수는 0 이상이어야 함: $score" }
        require(stars >= 0) { "받은 별은 0 이상이어야 함: $stars" }
        require(bestStreak >= 0) { "최고 연속은 0 이상이어야 함: $bestStreak" }
        val previous = rewards.best(game)
        val isRecord = score > 0 && score > previous
        val today = Commit.todayStr(now)
        val plan = DailyMissionPlan.missions(today)
        val before = todayLog(today)
        val after = before.adding(game, score, stars, bestStreak)
        var next = rewards.copy(
            stars = Math.addExact(rewards.stars, stars),   // 넘치면 던짐 — 음수로 돌지 않게
            missionLog = after,
        )
        if (isRecord) {
            next = when (game) {
                PlayGame.STAIRS -> next.copy(stairsBest = score)
                PlayGame.MONSTER -> next.copy(monsterBest = score)
            }
        }
        rewards = next
        persist()
        return PlayRoundRecord(
            previousBest = previous, isRecord = isRecord, starTotal = rewards.stars,
            missionsCompleted = plan.filter { !before.reached(it) && after.reached(it) },
            allMissionsDone = !plan.all(before::reached) && plan.all(after::reached),
        )
    }

    // MARK: 오늘의 미션

    /** 홈 카드용 — 오늘 미션 3개의 진행도(목표에서 자름)·상자 상태·이번 주 도장. rewards 를 읽으므로 화면이 바뀜을 따라간다 */
    fun missionBoard(now: LocalDate = LocalDate.now()): MissionBoard {
        val today = Commit.todayStr(now)
        val log = todayLog(today)
        return MissionBoard(
            items = DailyMissionPlan.missions(today).map { m ->
                MissionItem(m, progress = minOf(log.progress(m.kind), m.target), done = log.reached(m))
            },
            chestOpened = log.chestOpened,
            week = DailyMissionPlan.week(now).map { day ->
                val key = Commit.todayStr(day)
                WeekStamp(day, stamped = key in rewards.chestDays, isToday = key == today)
            },
        )
    }

    /** 보물상자 열기: 별 +10, 오늘 연 것으로 표시하고 오늘을 도장에 찍는다. 준비 전·이미 엶은 화면이 먼저 막아야 하는 상황 — 던진다 */
    fun openChest(now: LocalDate = LocalDate.now()): Int {
        check(missionBoard(now).chestReady) { "보물상자를 열 수 없는 상태: ${rewards.missionLog}" }
        val today = Commit.todayStr(now)
        rewards = rewards.copy(
            stars = Math.addExact(rewards.stars, DailyMissionPlan.CHEST_STARS),
            missionLog = todayLog(today).copy(chestOpened = true),
            // 기기 날짜를 되돌린 경우에도 오름차순·중복 없음을 지킨다
            chestDays = (rewards.chestDays + today).distinct().sorted().takeLast(DailyMissionPlan.CHEST_DAYS_CAP),
        )
        persist()
        return DailyMissionPlan.CHEST_STARS
    }

    /** 저장된 log 가 오늘 것이 아니면 진행도 0 인 새 log */
    private fun todayLog(today: String): DailyMissionLog =
        rewards.missionLog?.takeIf { it.day == today } ?: DailyMissionLog(day = today)

    // MARK: 도감 (구구 몬스터)

    /** 처치하는 순간: 누적 처치 +1, 처음 만난 종이면 도감에 넣고 바로 저장 */
    fun recordDefeat(kind: MonsterKind): DexDefeat {
        val before = rewards.dexTotal
        val after = before + 1
        val newlySeen = !MonsterDex.isSeen(kind, rewards.dexSeenMask)
        rewards = rewards.copy(
            dexTotal = after,
            dexSeenMask = if (newlySeen) MonsterDex.markSeen(kind, rewards.dexSeenMask) else rewards.dexSeenMask,
        )
        persist()
        return DexDefeat(newlySeen = newlySeen, unlocked = MonsterDex.newlyUnlocked(before, after))
    }

    // MARK: 옷장

    /** 사면 바로 쓴다(시험판 WardrobeStore.Buy). 이미 가졌거나 별이 모자라면 WardrobeError — 화면이 먼저 막아야 하는 상황 */
    fun buy(item: Accessory) {
        val next = Wardrobe.buy(item, rewards.stars, rewards.ownedMask)
        rewards = rewards.copy(stars = next.balance, ownedMask = next.ownedMask, equipped = item.ordinal)
        persist()
    }

    /** null = 벗기. 갖지 않은 것을 쓰라는 호출은 화면 쪽 버그 */
    fun equip(item: Accessory?) {
        if (item != null) require(owns(item)) { "갖지 않은 장신구는 쓸 수 없음: $item" }
        rewards = rewards.copy(equipped = item?.ordinal)
        persist()
    }

    // MARK: 저장

    private fun load(): PlayRewards? {
        val raw = storage.read(key) ?: return null
        return try {
            json.decodeFromString(PlayRewards.serializer(), raw)
        } catch (e: Exception) {
            // 형식이 깨졌거나(SerializationException) 규칙 밖 값(init 의 IllegalArgumentException) — 덮어쓰기 전에 원본을 남긴다
            storage.quarantine(key, raw, e)
            null
        }
    }

    private fun persist() = storage.write(key, json.encodeToString(PlayRewards.serializer(), rewards))

    /**
     * 어드벤처 저장본에서 별 조각 잔액만 읽는다. 키가 없으면(어드벤처를 안 한 기기) 0.
     * 못 읽는 저장본(음수 조각 포함)은 격리하고 0 — 2.8 까지도 어드벤처 화면이 못 읽는 저장본을 초기값으로 덮어 조각이 0 이었다
     */
    private fun adventureShards(adventureKey: String): Int {
        val raw = storage.read(adventureKey) ?: return 0
        return try {
            legacyAdventureShards(raw)
        } catch (e: Exception) {
            storage.quarantine(adventureKey, raw, e)
            0
        }
    }

    companion object {
        /** Persistence 와 같은 설정 — 모르는 키는 건너뛰고(새 버전 저장본) 기본값도 적는다 */
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * 옛 어드벤처 저장본(2.8 까지의 AdventureProgress JSON)의 starShards — 나머지 필드는 보지 않는다.
         * starShards 가 없던 구버전 저장본은 0(옛 어드벤처 디코더와 같음). 객체가 아니거나 정수가 아니거나 음수면 던진다
         */
        internal fun legacyAdventureShards(raw: String): Int {
            val value = Json.parseToJsonElement(raw).jsonObject["starShards"] ?: return 0
            val shards = value.jsonPrimitive.int
            require(shards >= 0) { "어드벤처 별 조각이 음수: $shards" }
            return shards
        }

        private fun applyDebug(r: PlayRewards, debug: PlayRewardsDebug, now: LocalDate): PlayRewards {
            var next = r
            debug.stars?.let { next = next.copy(stars = it) }
            debug.equip?.let { next = next.copy(ownedMask = next.ownedMask or it.bit, equipped = it.ordinal) }
            debug.stairsBest?.let { next = next.copy(stairsBest = it) }
            debug.dex?.let { n ->
                val seen = MonsterKind.entries.take(MonsterDex.unlockedCount(n)).fold(0) { mask, kind -> mask or kind.bit }
                next = next.copy(dexTotal = n, dexSeenMask = seen)
            }
            debug.missions?.let { next = debugMissions(next, it, now) }
            return next
        }

        /** 오늘 미션을 목표만큼 채운 log 를 심는다(half = 첫 미션 완료·둘째 절반·셋째 0) */
        private fun debugMissions(r: PlayRewards, mode: PlayRewardsDebug.Missions, now: LocalDate): PlayRewards {
            val today = Commit.todayStr(now)
            val plan = DailyMissionPlan.missions(today)
            val fill = when (mode) {
                PlayRewardsDebug.Missions.HALF -> listOf(plan[0].target, plan[1].target / 2, 0)
                PlayRewardsDebug.Missions.READY, PlayRewardsDebug.Missions.OPENED -> plan.map { it.target }
            }
            var log = DailyMissionLog(day = today)
            for ((m, value) in plan.zip(fill)) {
                log = when (m.kind) {
                    MissionKind.STAIRS_STEPS -> log.copy(stairsSteps = value)
                    MissionKind.MONSTERS_DEFEATED -> log.copy(monstersDefeated = value)
                    MissionKind.BEST_STREAK -> log.copy(bestStreak = value)
                    MissionKind.STARS_EARNED -> log.copy(starsEarned = value)
                }
            }
            // 안 연 상태면 오늘 도장도 빼서 맞춘다(앞서 opened 로 심은 저장본 위에 half·ready 를 덮어도 어긋나지 않게)
            if (mode != PlayRewardsDebug.Missions.OPENED) return r.copy(missionLog = log, chestDays = r.chestDays - today)
            // 이번 주 월요일부터 오늘까지 도장(iOS 훅과 같음)
            val stamped = DailyMissionPlan.week(now).filter { it <= now }.map(Commit::todayStr)
            return r.copy(
                missionLog = log.copy(chestOpened = true),
                chestDays = (r.chestDays + stamped).distinct().sorted().takeLast(DailyMissionPlan.CHEST_DAYS_CAP),
            )
        }
    }
}
