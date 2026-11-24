import Foundation

// 캐릭터 생동감(juice) 공식 — 새 그림 없이 지금 그림의 크기·기울기·위치만 움직인다(2026-11-24 대표님 결정 C안).
// 모두 상수 + 순수 함수. Stage 가 시간·사건(숨쉰 시간·점프 진행·깡총 진행·처치 뒤 남은 시간)을 들고 여기서 값을 얻고,
// 화면(Scene·홈 카드)은 그 값을 그리기만 한다. Android features/games/shared/Motion.kt 와 같은 공식·상수.
// 배율 1 = 제자리, 기울기 = 도(+ = 화면에서 시계 방향 = 오른쪽으로 기울어짐), 거리 = 월드 유닛(y 위가 +),
// 위상(phase) = 한 주기 대비(0~1). 동작 줄이기는 부르는 쪽이 판단한다 — 이 공식은 언제나 같은 값을 낸다.

enum Motion {
    // MARK: - 1. 구구 서 있기 숨쉬기 (계단)

    static let breathSec = 1.6
    /// 세로로 늘어나는 폭(1 ± 이만큼) — 가로는 반대로 breathX 만큼
    static let breathY = 0.035
    static let breathX = 0.025

    /// time: 숨쉬기 시작부터 흐른 초. 0초에 제자리에서 시작해 세로로 먼저 늘어남(들숨) — 발 기준으로 늘려야 발판에 붙어 있음
    static func breath(time: Double, phase: Double = 0) -> (sx: Double, sy: Double) {
        let s = sin(2 * .pi * (time / breathSec + phase))
        return (1 - breathX * s, 1 + breathY * s)
    }

    // MARK: - 2. 구구 점프 늘어남·기울기 (계단)

    static let jumpStretchY = 0.18
    static let jumpStretchX = 0.10
    /// 늘어남이 커졌다 줄어드는 구간(점프 진행 0 → 이 값) — 그 뒤 내려오는 동안은 원래 크기
    static let jumpStretchEnd = 0.5
    static let jumpTiltDeg = 10.0

    /// jumpT: 점프 진행(0 출발 → 1 착지). 0·1 에서 정확히 (1, 1)
    static func jumpStretch(_ jumpT: Double) -> (sx: Double, sy: Double) {
        guard jumpT > 0, jumpT < jumpStretchEnd else { return (1, 1) }
        let k = sin(.pi * jumpT / jumpStretchEnd)
        return (1 - jumpStretchX * k, 1 + jumpStretchY * k)
    }

    /// dir: 가는 방향(-1 왼쪽 · 0 바로 위 · +1 오른쪽). 공중 가운데에서 가장 기울고 출발·착지 때 0
    static func jumpTilt(_ jumpT: Double, dir: Int) -> Double {
        guard jumpT > 0, jumpT < 1 else { return 0 }
        return jumpTiltDeg * Double(min(1, max(-1, dir))) * sin(.pi * jumpT)
    }

    // MARK: - 5. 몬스터 가끔 깡총

    static let hopSec = 0.35
    /// 기다리는 동안 이만큼마다 한 번 뜀(초, 무작위 없음)
    static let hopEverySec = 3.0
    static let hopHeight = 0.25
    /// 뛰기 전 웅크림 끝·공중 끝(진행) — 그 뒤는 착지 찌그러짐
    static let hopCrouchEnd = 0.2
    static let hopAirEnd = 0.8
    /// 웅크림·착지 찌그러짐(세로 −, 가로 +)
    static let hopSquashY = 0.08
    static let hopSquashX = 0.06
    /// 공중 늘어남(세로 +, 가로 −)
    static let hopStretchY = 0.06
    static let hopStretchX = 0.04

    /// t: 깡총 진행(0 → 1). 시작·끝에서 정확히 (0, 1, 1) — 배율은 발 기준(그루터기에 붙은 채 찌그러지게)
    static func hop(_ t: Double) -> (dy: Double, sx: Double, sy: Double) {
        guard t > 0, t < 1 else { return (0, 1, 1) }
        if t < hopCrouchEnd {
            let c = sin(.pi * t / hopCrouchEnd)
            return (0, 1 + hopSquashX * c, 1 - hopSquashY * c)
        }
        if t < hopAirEnd {
            let a = sin(.pi * (t - hopCrouchEnd) / (hopAirEnd - hopCrouchEnd))
            return (hopHeight * a, 1 - hopStretchX * a, 1 + hopStretchY * a)
        }
        let l = sin(.pi * (t - hopAirEnd) / (1 - hopAirEnd))
        return (0, 1 + hopSquashX * l, 1 - hopSquashY * l)
    }

    // MARK: - 4. 몬스터 처치 타격감

    static let impactSec = 0.12
    /// 화면 흔들림 최대 폭(유닛, x·y 각각)
    static let impactAmp = 0.12
    /// 기절 그림이 하얗게 번쩍이는 시간(처치 순간부터)
    static let impactFlashSec = 0.08
    /// 흔들림 빠르기(라디안/초) — x·y 를 다르게 해 대각선 한 줄로만 흔들리지 않게
    static let impactFreqX = 105.0
    static let impactFreqY = 85.0

    /// left: 남은 흔들림 시간(초). 남은 만큼 줄어드는 떨림(카메라 이동, 유닛) — 0 이하면 (0, 0)
    static func impactShake(left: Double) -> (dx: Double, dy: Double) {
        guard left > 0 else { return (0, 0) }
        let k = min(1, left / impactSec) * impactAmp
        return (k * sin(left * impactFreqX), k * cos(left * impactFreqY))
    }

    /// elapsed: 처치 뒤 흐른 초 → 기절 그림 위에 덮는 흰색 세기(1 → 0)
    static func impactFlash(elapsed: Double) -> Double {
        guard elapsed >= 0 else { return 0 }
        return max(0, 1 - elapsed / impactFlashSec)
    }

    // MARK: - 6. 홈 게임 카드 그림·옷장 큰 구구 숨쉬기

    static let cardBreathSec = 2.4
    /// 위아래 폭(pt)
    static let cardBreathLift = 2.0
    /// 크기 1 ± 이만큼
    static let cardBreathScale = 0.02
    /// 홈 게임 카드끼리 위상 차이(한 주기 대비) — 두 카드가 같이 움직이지 않게
    static let cardBreathPhaseStep = 0.25

    /// 천천히 위아래로 뜨며(lift, pt, 위가 +) 살짝 커졌다 작아짐(scale, 가로세로 같이)
    static func cardBreath(time: Double, phase: Double = 0) -> (lift: Double, scale: Double) {
        let s = sin(2 * .pi * (time / cardBreathSec + phase))
        return (cardBreathLift * s, 1 + cardBreathScale * s)
    }
}
