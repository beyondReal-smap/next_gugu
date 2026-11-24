package site.smap.gugudan.core.games

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 게임 문제 순서 — 구구 계단·구구 몬스터 시작 창의 [순서대로]·[무작위] (2.9.0, 기본 순서대로, iOS ProblemOrder.swift 와 1:1).
// 원시값은 선택 기억 저장본(GameOrderStore, 키 gugu.gameOrder.v1)·디버그 훅(debug.gugu.stairs_order·monster_order)
// 값이라 바꾸지 말 것 — iOS 도 같은 문자열을 쓴다.

@Serializable
enum class ProblemOrder(val rawValue: String) {
    /** 곱하는 수가 1부터 차례로 — 계단: 한 단 × 1 → × 「곱하는 수」, 몬스터: 단 × 1 → × 9 */
    @SerialName("sequential")
    SEQUENTIAL("sequential"),

    /** 단·곱하는 수 둘 다 무작위 — 학습 탭 단 시트에서 고른 단도 쓰지 않는다 */
    @SerialName("random")
    RANDOM("random"),
    ;

    companion object {
        /** 원시값 → 순서. 모르는 값은 null(호출부가 멈출지 정한다) */
        fun fromRawValue(raw: String): ProblemOrder? = entries.firstOrNull { it.rawValue == raw }
    }
}
