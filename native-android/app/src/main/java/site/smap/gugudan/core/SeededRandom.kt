package site.smap.gugudan.core

// 결정적 시드 난수 — 같은 시드면 iOS(Swift)·웹(JS)과 비트 단위로 같은 수열을 낸다 (SeededRandom.swift 이식).
// 구구 계단·구구 몬스터 규칙 테스트(골든 벡터)가 쓴다.

object SeededRandom {
    /** mulberry32 — [0, 1) 균등 난수. 앱 관례의 `random: () -> Double` 자리에 그대로 넣는다 */
    fun mulberry32(seed: UInt): () -> Double {
        var a = seed
        return {
            a += 0x6d2b79f5u
            var t = (a xor (a shr 15)) * (1u or a)
            t = (t + ((t xor (t shr 7)) * (61u or t))) xor t
            (t xor (t shr 14)).toDouble() / 4294967296.0
        }
    }
}
