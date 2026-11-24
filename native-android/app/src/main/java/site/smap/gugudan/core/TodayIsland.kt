package site.smap.gugudan.core

import site.smap.gugudan.core.guardian.SkillStatus
import site.smap.gugudan.core.guardian.TableSkill
import site.smap.gugudan.core.guardian.TableSkills

// 오늘의 섬 선택 순수 로직 (계획서 2-6 — 홈 말풍선, 지도 강조, 보호자 리포트 권유가 같은 함수를 쓴다)

sealed interface TodayIsland {
    enum class Reason { WEAK, NEXT }

    data class Table(val table: Int, val reason: Reason) : TodayIsland
    data object Mixed : TodayIsland

    companion object {
        /**
         * 1. 약한 단이 있으면 가장 약한 단(TableSkills.weak 첫 번째). 이유 WEAK.
         * 2. 없으면 로드맵 순서(Hints.ROADMAP_TABLES)에서 최근 기록이 STRONG 인 단을 뺀 뒤
         *    별이 가장 적은 단(동률이면 로드맵 순서). 이유 NEXT.
         * 3. 남은 단이 없거나 모두 별 3개면 Mixed.
         */
        fun pick(mastery: Map<Int, TableMastery>, skills: Map<Int, TableSkill>): TodayIsland {
            TableSkills.weak(skills).firstOrNull()?.let { weakest ->
                return Table(weakest, Reason.WEAK)
            }
            val candidates = Hints.ROADMAP_TABLES.filter { skills[it]?.status != SkillStatus.STRONG }
            val fewest = candidates.minOfOrNull { mastery[it]?.stars ?: 0 } ?: return Mixed
            if (fewest >= 3) return Mixed
            val table = candidates.first { (mastery[it]?.stars ?: 0) == fewest }
            return Table(table, Reason.NEXT)
        }
    }
}
