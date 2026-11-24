package site.smap.gugudan.core

import site.smap.gugudan.core.guardian.SkillStatus
import site.smap.gugudan.core.guardian.TableSkill

// 섬 시트 추천·예시 식 순수 로직 (계획서 2-7 — iOS Core/IslandPlan.swift 대응)

sealed interface IslandMethod {
    /** ① 배우기 — 표 보고 외우기 */
    data object TableStudy : IslandMethod
    /** ② 풀기 — 학습 모드 */
    data class Mode(val mode: GameMode) : IslandMethod
}

object IslandPlan {
    const val STUDY_MULTIPLIER = 2
    const val SAMPLE_MULTIPLIER = 3

    /**
     * "추천" 딱지 위치 — 약한 단이거나 처음 가는 단(별 0, 최근 기록 0)이면 표 보고 외우기,
     * 아니면 차근차근(GameMode.PRACTICE).
     */
    fun recommended(table: Int, stars: Int, skill: TableSkill): IslandMethod {
        require(skill.table == table) { "다른 단의 실력: ${skill.table} ≠ $table" }
        return if (skill.status == SkillStatus.WEAK || (stars == 0 && skill.attempts == 0)) {
            IslandMethod.TableStudy
        } else {
            IslandMethod.Mode(GameMode.PRACTICE)
        }
    }

    /** 타일에 그릴 예시 식 — 단이면 (단, 3), 섞어서(null)면 (7, 8) */
    fun sample(table: Int?): Problem =
        if (table != null) Problem(table, SAMPLE_MULTIPLIER) else Problem(7, 8)

    /** OX 퀴즈 타일에 그릴 틀린 곱 (곱 + 1) */
    fun oxShown(sample: Problem): Int = sample.a * sample.b + 1

    /** ① 배우기 타일의 식 칩 하나: "단 × 2 = 곱" */
    fun studyChip(table: Int): String = "$table × $STUDY_MULTIPLIER = ${table * STUDY_MULTIPLIER}"
}
