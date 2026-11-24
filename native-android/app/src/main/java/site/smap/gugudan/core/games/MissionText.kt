package site.smap.gugudan.core.games

import site.smap.gugudan.R
import site.smap.gugudan.core.Strings

// 오늘의 미션 문구 — 홈 카드와 게임 결과 창이 같은 문구를 쓴다 (iOS 카탈로그 키 그대로, tools/i18n/android.py 가 만든 리소스)

object MissionText {
    /** 미션 한 줄 — 예: "계단 15칸 오르기" */
    fun title(strings: Strings, mission: Mission): String {
        val n = mission.target
        return when (mission.kind) {
            MissionKind.STAIRS_STEPS -> strings.plural(R.plurals.mission_stairs_steps, n, n)
            MissionKind.MONSTERS_DEFEATED -> strings.plural(R.plurals.mission_monsters_defeated, n, n)
            MissionKind.BEST_STREAK -> strings.get(R.string.mission_best_streak, n)
            MissionKind.STARS_EARNED -> strings.plural(R.plurals.mission_stars_earned, n, n)
        }
    }

    /**
     * 결과 창 한 줄(빈 문자열 = 그리지 않음): 3개가 이번 판으로 처음 다 됨 → 홈 보물상자 안내,
     * 새로 넘은 미션이 있음 → "미션 완료! " + 첫 미션
     */
    fun resultNote(strings: Strings, completed: List<Mission>, allDone: Boolean): String = when {
        allDone -> strings.get(R.string.mission_all_done)
        completed.isNotEmpty() -> strings.get(R.string.mission_completed, title(strings, completed.first()))
        else -> ""
    }
}
