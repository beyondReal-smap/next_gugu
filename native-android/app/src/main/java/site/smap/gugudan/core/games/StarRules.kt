package site.smap.gugudan.core.games

// 별 코인 규칙 (A·B 공통, Unity 시험판 Core/StarRules.cs 이식, iOS StarRules.swift 와 1:1)
// 맞힐 때마다 바로 손에 들어오는 작은 보상 + 연속 정답의 큰 보상.
// - 정답 1개 = 별 1
// - 연속 정답이 5, 10, 15… 가 되는 순간 별 +2 (콤보 보너스)
// - A 에서 꼭대기(×topStep, 보물)에 닿으면 별 +5
// 판이 끝나면(결과 화면) 받은 별이 기기의 "모은 별"에 쌓인다. 수치는 아이 관찰 전 초안이다.

object StarRules {
    const val PER_CORRECT = 1
    const val COMBO_EVERY = 5
    const val COMBO_BONUS = 2
    const val TREASURE_BONUS = 5

    /** streak = 이번 정답까지 센 연속 정답 수(1 이상) */
    fun forCorrect(streak: Int): Int {
        require(streak >= 1) { "정답 직후 연속 수는 1 이상이어야 함: $streak" }
        return PER_CORRECT + (if (isComboMilestone(streak)) COMBO_BONUS else 0)
    }

    fun isComboMilestone(streak: Int): Boolean = streak > 0 && streak % COMBO_EVERY == 0
}
