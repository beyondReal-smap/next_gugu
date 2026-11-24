import Foundation

// 결정적 시드 난수 — 같은 시드면 Android(Kotlin)·웹(JS)과 비트 단위로 같은 수열을 낸다.
// 게임 규칙 테스트(골든 벡터)가 쓴다.

enum SeededRandom {
    /// mulberry32 — [0, 1) 균등 난수. 앱 관례의 `random: () -> Double` 자리에 그대로 넣는다
    static func mulberry32(_ seed: UInt32) -> () -> Double {
        var a = seed
        return {
            a = a &+ 0x6d2b79f5
            var t = (a ^ (a >> 15)) &* (1 | a)
            t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
            return Double((t ^ (t >> 14))) / 4294967296
        }
    }
}
