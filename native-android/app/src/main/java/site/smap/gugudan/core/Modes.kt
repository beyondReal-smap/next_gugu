package site.smap.gugudan.core

import androidx.annotation.StringRes
import site.smap.gugudan.R

// 게임 모드 레지스트리 — 모드별 규칙/메타데이터의 단일 출처 (Modes.swift 이식)
// 이름·소개는 리소스 ID 로 둔다 — 앱별 언어를 바꾸면 Activity 만 다시 만들어지므로 문자열을 들고 있으면 이전 언어가 남는다

enum class ModeKind { FIXED, TIMED, LIVES }

data class ModeDef(
    val id: GameMode,
    @StringRes val nameRes: Int,
    @StringRes val taglineRes: Int,
    @StringRes val detailRes: Int,
    val kind: ModeKind,
    val total: Int,
    val timeLimitMs: Int?,
    val lives: Int?,
    val supportsTable: Boolean,
    val scored: Boolean,
    val xpBonus: Int,
)

object Modes {
    val all: Map<GameMode, ModeDef> = mapOf(
        GameMode.PRACTICE to ModeDef(
            GameMode.PRACTICE, R.string.mode_practice_name, R.string.slow_and_steady,
            R.string.no_pressure_one_at_a_time_missed,
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 0),
        GameMode.TIME_ATTACK to ModeDef(
            GameMode.TIME_ATTACK, R.string.speed_run, R.string.s_10_problems_top_speed,
            R.string.speed_is_your_score_the_faster_you,
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 4),
        GameMode.CHALLENGE to ModeDef(
            GameMode.CHALLENGE, R.string.s_60_second_challenge, R.string.beat_the_clock,
            R.string.how_many_can_you_solve_in_60_seconds_go,
            ModeKind.TIMED, 0, 60_000, null, supportsTable = false, scored = true, xpBonus = 4),
        GameMode.SURVIVAL to ModeDef(
            GameMode.SURVIVAL, R.string.survival, R.string.only_3_chances,
            R.string.hang_on_with_3_lives_you_lose_a_heart,
            ModeKind.LIVES, 0, null, 3, supportsTable = false, scored = true, xpBonus = 3),
        GameMode.MISSING to ModeDef(
            GameMode.MISSING, R.string.missing_number, R.string.what_goes_in_the,
            R.string.multiplication_in_reverse_find_the,
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 6),
        GameMode.TRUEFALSE to ModeDef(
            GameMode.TRUEFALSE, R.string.true_or_false, R.string.right_wrong,
            R.string.is_the_equation_right_decide_fast_and,
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 0),
        GameMode.ADVENTURE to ModeDef(
            GameMode.ADVENTURE, R.string.adventure, R.string.battle_in_the_world,
            R.string.battle_the_residents_of_a_3d_world_with,
            ModeKind.FIXED, 0, null, null, supportsTable = true, scored = false, xpBonus = 3),
        // 게임 판의 학습 기록용 — 이름·소개는 기록에서 모드를 가리킬 때만 쓴다(카드 문구는 features/games/GameStyle.kt 의 Minigames).
        // 최고 기록은 게임 보상 저장소(PlayRewardsStore)가 따로 맡아 scored 는 false. 문제 수가 정해지지 않아 total 0.
        // XP 보너스는 0 — 게임은 별 코인으로 따로 보상하고, 빠르게 많이 푸는 판이라 정답당 보너스를 얹지 않는다 (iOS Modes.swift 와 같음)
        GameMode.STAIRS to ModeDef(
            GameMode.STAIRS, R.string.stairs_title, R.string.stairs_mode_tagline,
            R.string.stairs_mode_detail,
            ModeKind.FIXED, 0, null, null, supportsTable = true, scored = false, xpBonus = 0),
        GameMode.MONSTER to ModeDef(
            GameMode.MONSTER, R.string.monster_title, R.string.monster_mode_tagline,
            R.string.monster_mode_detail,
            ModeKind.TIMED, 0, 60_000, null, supportsTable = true, scored = false, xpBonus = 0),
    )

    /**
     * 학습 탭에 노출하는 모드 (MODE_LIST, 표시 순서 고정). ADVENTURE 는 옛 저장본 호환용(2.9.0 에서 3D 어드벤처 제거), STAIRS·MONSTER 는 게임 전용이라 제외.
     * 업적 '모드 탐험가'의 목표치도 이 목록 수라 게임을 넣으면 기존 사용자 업적 기준이 바뀐다.
     */
    val list: List<ModeDef> = listOf(
        all.getValue(GameMode.PRACTICE), all.getValue(GameMode.TIME_ATTACK),
        all.getValue(GameMode.CHALLENGE), all.getValue(GameMode.SURVIVAL),
        all.getValue(GameMode.MISSING), all.getValue(GameMode.TRUEFALSE),
    )

    fun def(mode: GameMode): ModeDef = all.getValue(mode)

    /** 단 시트에 보여 줄 모드 — table null('섞어서')이면 list 전체, 단을 골랐으면 단을 지원하는 모드만 (순서는 list 그대로) */
    fun options(table: Int?): List<ModeDef> = if (table == null) list else list.filter { it.supportsTable }
}
