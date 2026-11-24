import Foundation

// 구구 몬스터 월드 상태 (Unity 시험판 MonsterView.cs 이식) — 체력 숫자를 단 몬스터 한 마리의 움직임.
// 등장(위에서 떨어짐)·흔들림(오답)·터짐(처치: 기절한 몬스터가 커지며 떠올라 사라지고 고리가 퍼짐)만 다룬다.
// 생동감(Motion): 가만히 기다리면 약 3초마다 깡총, 처치 순간 화면 흔들림 + 기절 그림 흰 번쩍.
// 동작 줄이기면 깡총·화면 흔들림을 끈다(번쩍은 짧은 사건 반응이라 남김).
// 엔진이 프레임마다 update 하고, MonsterScene 이 그린다. 좌표는 월드 유닛(y 위가 +).

struct MonsterStage: Equatable {
    /// 몬스터 중심 높이(흔들림 없을 때)
    static let monsterY = 1.6
    /// 그림 높이(유닛). 배 패널 폭 ≈ 0.45 × 4.8 = 2.2유닛에 두 자리 숫자가 들어감
    static let bodySize = 4.8
    static let dropSec = 0.25
    static let dropHeight = 4.0
    static let shakeSec = 0.45
    /// 기절한 얼굴이 보일 만큼
    static let popSec = 0.4
    /// 처치 직후 다음 몬스터는 이만큼 숨겼다가 떨어뜨린다 — 바로 떨어뜨리면 사라지는 기절 몬스터와 겹쳐
    /// 새 체력 숫자가 기절 몬스터 배에 비쳐 보였음(Unity 에뮬레이터 녹화로 확인)
    static let nextDropDelay = 0.2
    /// 체력 글자 크기(유닛) — Unity TextMesh 글자 크기 140 × 0.085
    static let hpEm = 1.19

    /// 몬스터 발 높이(흔들림 없을 때) — 그루터기 윗면을 여기에 맞춤
    static var feetY: Double { monsterY - bodySize * (0.5 - Double(GameArt.feetPad)) }

    /// 처치 연출 — 방금 물리친 몬스터(기절 그림)
    struct Pop: Equatable {
        let kind: MonsterKind
        /// 처치 순간 몬스터 자리
        let x: Double
        let y: Double
        /// 0 → 1
        var t: Double = 0

        /// 기절 그림 위에 덮는 흰색 세기(처치 직후 잠깐)
        var whiteFlash: Double { Motion.impactFlash(elapsed: t * MonsterStage.popSec) }
    }

    private(set) var kind: MonsterKind = .green
    private(set) var hp = 0
    /// 지금 몬스터 중심(흔들림·떨어짐·통통 포함)
    private(set) var x = 0.0
    private(set) var y = MonsterStage.monsterY
    /// 숨 쉬듯 가로세로 찌그러짐(가로 배율, 세로 = 2 - 가로)
    private(set) var squash = 1.0
    /// 오답 때 몸이 붉어지는 정도(1 → 0)
    private(set) var flash = 0.0
    private(set) var pop: Pop?
    private var dropT = 1.0
    private var dropDelay = 0.0
    private var shake = 0.0
    /// 깡총 진행(1 = 뛰지 않음)과 다음 깡총까지 남은 기다림
    private var hopT = 1.0
    private var hopWait = Motion.hopEverySec
    /// 처치 화면 흔들림 남은 시간
    private var impactLeft = 0.0
    /// 동작 줄이기(손쉬운 사용) — 깡총·처치 화면 흔들림을 끈다
    var reduceMotion = false

    /// 다음 몬스터가 기다리는 동안은 숨김
    var visible: Bool { dropDelay <= 0 }

    /// 깡총 찌그러짐·늘어남(가로, 세로) — 발을 기준으로
    var hopScale: (x: Double, y: Double) {
        let h = Motion.hop(hopT)
        return (h.sx, h.sy)
    }

    /// 처치 화면 흔들림 — 카메라가 움직인 거리(유닛)
    var cameraShake: (x: Double, y: Double) {
        guard !reduceMotion else { return (0, 0) }
        let s = Motion.impactShake(left: impactLeft)
        return (s.dx, s.dy)
    }

    /// 새 몬스터 — drop 이면 위에서 떨어지며 들어온다(처치 직후면 0.2초 쉬었다가)
    mutating func spawn(hp: Int, kind: MonsterKind, drop: Bool) {
        precondition(hp > 0, "체력은 1 이상: \(hp)")
        self.kind = kind
        self.hp = hp
        dropT = drop ? 0 : 1
        dropDelay = drop && pop != nil ? Self.nextDropDelay : 0
        shake = 0
        flash = 0
        hopT = 1
        hopWait = Motion.hopEverySec
    }

    /// 오답 — 흔들리며 붉어짐
    mutating func hit() {
        shake = Self.shakeSec
        flash = 1
    }

    /// 처치 — spawn 보다 먼저 불러야 방금 물리친 몬스터가 기절 그림으로 남는다. 화면이 짧게 흔들린다(오답에는 없음)
    mutating func defeat() {
        pop = Pop(kind: kind, x: x, y: y)
        impactLeft = Motion.impactSec
    }

    /// 새 판: 연출 없이 제자리(동작 줄이기 설정은 그대로)
    mutating func reset() {
        let calm = reduceMotion
        self = MonsterStage()
        reduceMotion = calm
    }

    /// time = 흐른 시간(통통·숨쉬기 위상), 쉬는 동안 멈춤
    mutating func update(_ dt: Double, time: Double) {
        var y = Self.monsterY + sin(time * 2.6) * 0.12
        if dropDelay > 0 { dropDelay = max(0, dropDelay - dt) }
        // else 가 아님: 기다림이 끝난 그 프레임부터 바로 위에서 떨어지게(한 프레임 제자리 노출 방지)
        if dropDelay <= 0 && dropT < 1 {
            dropT = min(1, dropT + dt / Self.dropSec)
            let e = 1 - (1 - dropT) * (1 - dropT)
            y += (1 - e) * Self.dropHeight
        }
        var x = 0.0
        if shake > 0 {
            shake = max(0, shake - dt)
            x = sin(shake * 70) * 0.3 * (shake / Self.shakeSec)
        }
        self.x = x
        self.y = y
        squash = 1 + 0.03 * sin(time * 5.2)
        flash = max(0, flash - dt / Self.shakeSec)
        if var p = pop {
            p.t = min(1, p.t + dt / Self.popSec)
            pop = p.t >= 1 ? nil : p
        }
        impactLeft = max(0, impactLeft - dt)

        // 가끔 깡총 — 가만히 기다릴 때만 3초를 센다. 등장 낙하·오답 흔들림·처치 연출이 끼면 멈추고 다시 센다
        let idle = visible && dropT >= 1 && shake <= 0 && pop == nil && !reduceMotion
        if !idle {
            hopT = 1
            hopWait = Motion.hopEverySec
        } else if hopT < 1 {
            hopT = min(1, hopT + dt / Motion.hopSec)
        } else {
            hopWait -= dt
            if hopWait <= 0 {
                hopT = 0
                hopWait = Motion.hopEverySec
            }
        }
        self.y += Motion.hop(hopT).dy
    }
}
