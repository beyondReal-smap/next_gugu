package site.smap.gugudan.core

// 게임 모드 레지스트리 — 모드별 규칙/메타데이터의 단일 출처 (Modes.swift 이식)

enum class ModeKind { FIXED, TIMED, LIVES }

data class ModeDef(
    val id: GameMode,
    val name: String,
    val tagline: String,
    val detail: String,
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
            GameMode.PRACTICE, "학습", "차근차근 기본기",
            "압박 없이 또박또박 — 틀린 문제는 다시 나와요.",
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 0),
        GameMode.TIME_ATTACK to ModeDef(
            GameMode.TIME_ATTACK, "스피드런", "10문제 최고 속도",
            "속도가 곧 점수! 빠르게 풀수록 XP가 올라가요.",
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 4),
        GameMode.CHALLENGE to ModeDef(
            GameMode.CHALLENGE, "60초 챌린지", "제한 시간 최대 점수",
            "60초 동안 몇 문제나 풀 수 있을까요? 최고 기록에 도전!",
            ModeKind.TIMED, 0, 60_000, null, supportsTable = false, scored = true, xpBonus = 4),
        GameMode.SURVIVAL to ModeDef(
            GameMode.SURVIVAL, "서바이벌", "기회는 단 3번",
            "목숨 3개로 버티기! 틀릴 때마다 하트가 하나씩 사라져요.",
            ModeKind.LIVES, 0, null, 3, supportsTable = false, scored = true, xpBonus = 3),
        GameMode.MISSING to ModeDef(
            GameMode.MISSING, "빈칸 추리", "? 에 들어갈 수는",
            "곱셈을 거꾸로! 빈칸의 수를 찾으면 XP 보너스가 커요.",
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 6),
        GameMode.TRUEFALSE to ModeDef(
            GameMode.TRUEFALSE, "OX 퀴즈", "맞으면 O, 틀리면 X",
            "식이 맞는지 순간 판단! 헷갈리는 한 끗 차이를 가려내요.",
            ModeKind.FIXED, 10, null, null, supportsTable = true, scored = false, xpBonus = 0),
        GameMode.ADVENTURE to ModeDef(
            GameMode.ADVENTURE, "어드벤처", "월드에서 대결",
            "3D 월드 주민과 구구단 대결. 학습 모드 기록과는 따로 쌓여요.",
            ModeKind.FIXED, 0, null, null, supportsTable = true, scored = false, xpBonus = 3),
    )

    /** 학습 탭에 노출하는 모드 (MODE_LIST, 표시 순서 고정). ADVENTURE 는 월드 전용이라 제외. */
    val list: List<ModeDef> = listOf(
        all.getValue(GameMode.PRACTICE), all.getValue(GameMode.TIME_ATTACK),
        all.getValue(GameMode.CHALLENGE), all.getValue(GameMode.SURVIVAL),
        all.getValue(GameMode.MISSING), all.getValue(GameMode.TRUEFALSE),
    )

    fun def(mode: GameMode): ModeDef = all.getValue(mode)
}
