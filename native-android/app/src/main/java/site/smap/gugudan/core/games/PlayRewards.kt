package site.smap.gugudan.core.games

import kotlinx.serialization.Serializable

// A·B 게임 보상 저장본 (키 gugu.play.v1, PlayRewardsStore 가 읽고 씀 — iOS PlayRewards.swift 와 같은 필드·규칙)
// 모은 별(옷장 잔고)·가진/쓰는 장신구·몬스터 도감·게임별 최고 기록·어드벤처 별 조각 이관 표시를 한 덩어리로 둔다.
// Unity 시험판은 PlayerPrefs 키 여러 개(stars.total·wardrobe.*·dex.*·best.*)였는데, 앱은 GameState 처럼 JSON 하나로 묶는다.

/** 최고 기록을 따로 두는 게임 */
enum class PlayGame {
    /** 구구 계단 — 오른 계단 */
    STAIRS,
    /** 구구 몬스터 — 물리친 몬스터 */
    MONSTER,
}

/**
 * 필드를 나중에 늘려도 구버전 저장본이 읽히게 모든 필드에 기본값을 둔다(누락 키 = 기본값, GameState 와 같은 방식).
 * 타입이 틀리거나 값이 규칙 밖이면 init 에서 던져(역직렬화도 init 을 거친다) Persistence 가 원본을 격리하게 둔다 —
 * 조용히 고치지 않는다. copy() 도 init 을 거치므로 저장소가 규칙 밖 값을 만들 수 없다.
 */
@Serializable
data class PlayRewards(
    val version: Int = 1,
    /** 모은 별 잔고 — 결과 화면 때 쌓이고 옷장에서 쓰면 줄어든다 */
    val stars: Int = 0,
    /** 가진 장신구 비트 묶음(Accessory.bit) */
    val ownedMask: Int = 0,
    /** 쓰고 있는 장신구(Accessory.ordinal — iOS rawValue 와 같음). null = 맨머리 */
    val equipped: Int? = null,
    /** 구구 몬스터 누적 처치(판을 넘어 쌓임 — 도감 해금 기준) */
    val dexTotal: Int = 0,
    /** 한 번이라도 물리친 종 비트 묶음(MonsterKind.bit) */
    val dexSeenMask: Int = 0,
    /** 구구 계단 최고 기록(오른 계단) */
    val stairsBest: Int = 0,
    /** 구구 몬스터 최고 기록(물리친 몬스터) */
    val monsterBest: Int = 0,
    /** 어드벤처 별 조각을 별로 옮겼는지 — 처음 한 번만 옮긴다 */
    val adventureMigrated: Boolean = false,
    /** 오늘의 미션 진행도(가장 최근에 쓴 날 하나) — 2.9.0 전 저장본은 null */
    val missionLog: DailyMissionLog? = null,
    /** 보물상자를 연 날(오름차순·중복 없음·최근 14개) — 이번 주 도장 */
    val chestDays: List<String> = emptyList(),
) {
    init {
        require(stars >= 0 && dexTotal >= 0 && stairsBest >= 0 && monsterBest >= 0) { "음수 값이 있는 보상 저장본" }
        chestDays.forEach(DailyMissionPlan::parseDay)
        require(chestDays.size <= DailyMissionPlan.CHEST_DAYS_CAP && chestDays.zipWithNext().all { (a, b) -> a < b }) {
            "보물상자 연 날이 오름차순·중복 없음·최근 ${DailyMissionPlan.CHEST_DAYS_CAP}개가 아님: $chestDays"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
        }
        require((ownedMask and ALL_ACCESSORIES.inv()) == 0 && (dexSeenMask and ALL_KINDS.inv()) == 0) {
            "알 수 없는 장신구·몬스터 비트: owned=$ownedMask seen=$dexSeenMask"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
        }
        if (equipped != null) {
            require(equipped in Accessory.entries.indices && Wardrobe.owns(Accessory.entries[equipped], ownedMask)) {
                "갖지 않은 장신구를 쓰고 있음: $equipped"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
            }
        }
    }

    /** 쓰고 있는 장신구 */
    val equippedAccessory: Accessory? get() = equipped?.let { Accessory.entries[it] }

    fun best(game: PlayGame): Int = when (game) {
        PlayGame.STAIRS -> stairsBest
        PlayGame.MONSTER -> monsterBest
    }

    companion object {
        private val ALL_ACCESSORIES = Accessory.entries.fold(0) { mask, item -> mask or item.bit }
        private val ALL_KINDS = MonsterKind.entries.fold(0) { mask, kind -> mask or kind.bit }
    }
}
