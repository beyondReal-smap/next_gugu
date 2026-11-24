import SwiftUI

// 구구 계단의 월드 그림 상태 (Unity 시험판 StairsView.cs·StormCloudView·BestFlagView 의 움직임 부분 이식)
// 발판 줄·캐릭터(점프·찌그러짐·흔들림·자세)·지나온 계단·보물 상자·카메라·먹구름 삼킴·최고 기록 깃발을 프레임마다 움직인다.
// 생동감(Motion): 서 있을 때 숨쉬기, 점프 중 늘어남·기울기, 착지 먼지. 동작 줄이기면 숨쉬기·늘어남·기울기를 끈다(먼지는 남김).
// 규칙은 모르고, 엔진이 알려 주는 사건(새 줄·착지·실패·보물·잡힘·신기록)만 받는다. 그리기는 StairsScene.
// 좌표는 월드 유닛(y 위가 +). 출발 발판 높이 0, 한 칸 = rowHeight.

/// 곱해 입히는 색(발판) — 섞기(lerp)가 필요해 RGB 로 둔다
struct GameRGB: Equatable {
    var r: Double
    var g: Double
    var b: Double

    init(_ r: Double, _ g: Double, _ b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    /// #RRGGBB 정수
    init(hex: Int) {
        self.init(Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    func lerp(to other: GameRGB, _ t: Double) -> GameRGB {
        GameRGB(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t)
    }

    var color: Color { Color(red: r, green: g, blue: b) }

    static let correct = GameRGB(hex: 0x3DDC84)
    static let wrong = GameRGB(hex: 0xFF5A5F)
    static let wall = GameRGB(hex: 0x47538A)
}

struct StairsStage {
    static let rowHeight = 2.4
    static let platformWidth = 2.3
    static let platformHeight = 0.9
    /// 칸 사이 간격(유닛) — 가운데 칸 x = 0
    static let laneSpacing = 2.85
    /// 구구 그림 한 변(유닛). 다음 줄 발판에 머리가 닿지 않는 크기
    static let characterSize = 1.3
    /// 캐릭터가 화면 아래쪽에 오도록(카메라 = 서 있는 높이 + 이만큼)
    static let cameraLead = 3.0
    static let chestSize = 0.75
    static let happySec = 0.45
    static let jumpSec = 0.28
    static let jumpArc = 1.3
    static let fallAccel = 22.0
    static let fallSec = 0.4
    static let keepRowsBelow = 5
    static let chestPopSec = 0.25
    static let chestOpenDelay = 0.35
    /// 젤리 발판 윗면 양 끝이 둥글게 내려가므로 상자를 끝에서 이만큼 안쪽에
    static let chestInset = 0.15
    /// 젤리 방울(다리)을 뺀 몸통 중심이 그림 중심보다 이만큼(발판 높이 대비) 위 — 숫자를 몸통 가운데에
    static let platformBodyLift = 0.047
    /// 먹구름이 잡은 뒤 캐릭터를 덮을 만큼 더 차오르는 높이(유닛)·시간
    static let swallowRise = 1.6
    static let swallowSec = 0.5
    static let flagPopSec = 0.5
    static let flagFadeSec = 0.6
    static let shakeSec = 0.35
    /// 착지 먼지: 착지마다 크림색 원 6알이 발 양옆으로 0.3초 동안 퍼지며 사라짐. 풀은 고정 12칸(연달아 착지해도 겹쳐 보이게)
    static let dustPool = 12
    static let dustCount = 6
    static let dustSec = 0.3
    /// 출발 자리(발 중심에서 좌우·위, 유닛)·빠르기(바깥 알일수록 빠르고 높게)·감속(초당 비율)·반지름
    static let dustSpread = 0.22
    static let dustLift = 0.04
    static let dustSpeedX = (base: 1.4, step: 0.6)
    static let dustSpeedY = (base: 0.2, step: 0.35)
    static let dustDrag = 5.0
    static let dustRadius = 0.128

    /// 착지 먼지 한 알(월드 유닛). 퍼지며 커지고 흐려진다
    struct Dust: Equatable {
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var age = 0.0
        var alive = false

        var progress: Double { age / StairsStage.dustSec }
        var radius: Double { StairsStage.dustRadius * (1 + progress) }
        var alpha: Double { 0.85 * (1 - progress) }
    }

    struct Lane: Equatable {
        var visible = true
        var label = ""
        var color = GameRGB.wall
        var alpha = 1.0
        var falling = false
        var fallT = 0.0
        var glowLeft = 0.0
        var glowColor = GameRGB.correct
    }

    struct Row: Equatable {
        let height: Int
        var base: GameRGB
        /// 밟은 칸(정답) — 고르지 않은 칸은 떨어져 사라지고 이 칸이 계단으로 남음
        var chosen: Int?
        var lanes: [Lane]
    }

    struct Chest: Equatable {
        let height: Int
        let lane: Int
        var t = 0.0

        var open: Bool { t >= StairsStage.chestOpenDelay }

        /// 살짝 크게 튀어나왔다가 제자리(ease-out-back)
        var scale: Double {
            let x = min(1, t / StairsStage.chestPopSec) - 1
            return 1 + 2.7 * x * x * x + 1.7 * x * x
        }
    }

    /// 최고 기록 깃발: 지난 최고 기록 높이의 점선 + 오른쪽 끝 깃발. 넘으면 통통 튀고 점선은 사라짐
    struct Flag: Equatable {
        var best = 0
        var passed = false
        var pop = 0.0
        var fade = 0.0

        var visible: Bool { best > 0 }
        var popScale: Double { 1 + 0.35 * sin(pop * .pi) }
        var lineAlpha: Double { passed ? 0.75 * fade : 0.75 }
    }

    private(set) var rows: [Int: Row] = [:]
    private(set) var chests: [Chest] = []
    private(set) var pose: GameArt.GuguPose = .idle
    private var poseHold = 0.0
    private var jumpFrom = (x: 0.0, y: 0.0)
    private var jumpTo = (x: 0.0, y: 0.0)
    private var jumpT = 1.0
    /// 점프 방향(-1 왼쪽 · 0 바로 위 · +1 오른쪽) — 기울기 쪽
    private var jumpDir = 0
    private var squash = 0.0
    private var shake = 0.0
    /// 서 있기 숨쉬기 시간 — 점프·흔들림 중에는 0 으로 두어, 다시 서면 제자리(배율 1)에서 이어지게
    private var breathT = 0.0
    private(set) var dust = [Dust](repeating: Dust(), count: StairsStage.dustPool)
    /// 동작 줄이기(손쉬운 사용) — 숨쉬기·점프 늘어남·기울기를 끈다
    var reduceMotion = false
    /// 발 위치(월드) — 점프 중이면 공중
    private(set) var characterX = 0.0
    private(set) var characterY = 0.0
    private(set) var standingHeight = 0
    private(set) var standingLane = 1
    private(set) var cameraY = 0.0
    private(set) var accessory: Accessory?
    /// 먹구름 삼킴(0~1)
    private(set) var swallow = 0.0
    private(set) var flag = Flag()

    // MARK: - 좌표

    static func laneX(_ lane: Int) -> Double { Double(lane - 1) * laneSpacing }
    /// 발판 중심
    static func platformY(_ height: Int) -> Double { Double(height) * rowHeight }
    /// 숫자 중심
    static func labelY(_ height: Int) -> Double { platformY(height) + 0.02 + platformHeight * platformBodyLift }
    /// 발 위치 = 발판 윗면. 먹구름 윗면도 같은 기준이라 "발에 닿음 = 잡힘"
    static func feetY(_ height: Double) -> Double { height * rowHeight + platformHeight * 0.5 }
    static func cameraTarget(standing: Int) -> Double { Double(standing) * rowHeight + cameraLead }

    /// 그림 배율(가로, 세로) — 발을 기준으로. 착지 찌그러짐 × 점프 늘어남 × 서 있기 숨쉬기
    var characterScale: (x: Double, y: Double) {
        let x = 1 + 0.25 * squash
        let y = 1 - 0.25 * squash
        guard !reduceMotion else { return (x, y) }
        let stretch = Motion.jumpStretch(jumpT)
        let breath = Motion.breath(time: breathT)
        return (x * stretch.sx * breath.sx, y * stretch.sy * breath.sy)
    }

    /// 기울기(도, + = 오른쪽) — 몸 가운데를 축으로, 가는 방향으로 기울었다 착지 때 0
    var characterTilt: Double { reduceMotion ? 0 : Motion.jumpTilt(jumpT, dir: jumpDir) }

    /// 상자 중심(월드)
    static func chestCenter(_ chest: Chest) -> (x: Double, y: Double) {
        (laneX(chest.lane) + platformWidth * 0.5 - chestSize * 0.5 - chestInset,
         platformY(chest.height) + platformHeight * 0.5 + chestSize * (0.5 - Double(GameArt.feetPad)))
    }

    // MARK: - 사건

    /// 새 판: 출발 발판 하나(가운데, "출발") 위에 선 구구, 카메라 제자리
    mutating func reset(startLabel: String, accessory: Accessory?, best: Int) {
        rows = [:]
        chests = []
        setPose(.idle, hold: 0)
        standingHeight = 0
        standingLane = 1
        var ground = Row(height: 0, base: .wall, chosen: 1, lanes: Array(repeating: Lane(), count: StairsSession.lanes))
        for lane in 0..<StairsSession.lanes {
            ground.lanes[lane].visible = lane == 1
            ground.lanes[lane].label = lane == 1 ? startLabel : ""
        }
        rows[0] = ground
        (characterX, characterY) = (Self.laneX(1), Self.feetY(0))
        jumpT = 1
        jumpDir = 0
        squash = 0
        shake = 0
        breathT = 0
        for i in dust.indices { dust[i].alive = false }
        cameraY = Self.cameraTarget(standing: 0)
        self.accessory = accessory
        swallow = 0
        flag = Flag(best: best)
    }

    /// 다음 줄(풀어야 할 칸) 발판 3개를 숫자와 함께 깐다. 오답 공개 뒤 같은 칸을 다시 낼 때도 같은 줄을 다시 칠한다
    mutating func showNextRow(_ q: StairsQuestion, color: GameRGB) {
        let h = standingHeight + 1
        var row = rows[h] ?? Row(height: h, base: color, chosen: nil, lanes: [])
        row.base = color
        row.chosen = nil
        row.lanes = q.choices.map { Lane(visible: true, label: "\($0)", color: color) }
        rows[h] = row
    }

    mutating func land(lane: Int) {
        let h = standingHeight + 1
        guard var row = rows[h] else { preconditionFailure("착지할 줄이 없음: \(h)") }
        row.chosen = lane
        for i in row.lanes.indices where i != lane { row.lanes[i].falling = true }
        row.lanes[lane].glowLeft = 0.35
        row.lanes[lane].glowColor = .correct
        rows[h] = row
        jumpFrom = (characterX, characterY)
        jumpTo = (Self.laneX(lane), Self.feetY(Double(h)))
        jumpT = 0
        jumpDir = jumpTo.x > jumpFrom.x ? 1 : jumpTo.x < jumpFrom.x ? -1 : 0
        setPose(.jump, hold: 0)
        standingHeight = h
        standingLane = lane
        rows = rows.filter { $0.key >= standingHeight - Self.keepRowsBelow }
        chests.removeAll { $0.height < standingHeight - Self.keepRowsBelow }
    }

    /// 오답: 고른 발판이 빨갛게 떨어지고 정답 발판은 공개 동안 초록(숫자는 흰색 그대로 — 발판 색으로만 알림)
    mutating func miss(lane: Int, correctLane: Int) {
        let h = standingHeight + 1
        guard var row = rows[h] else { preconditionFailure("오답 표시할 줄이 없음: \(h)") }
        row.lanes[lane].falling = true
        row.lanes[lane].color = .wrong
        row.lanes[correctLane].glowLeft = RoundClock.revealSec
        row.lanes[correctLane].glowColor = .correct
        rows[h] = row
        shake = Self.shakeSec
        setPose(.oops, hold: RoundClock.revealSec)   // 정답 공개(입력 잠금) 동안 '앗!'
    }

    /// 먹구름에 잡힘: '앗!' 표정으로 흔들리고, 판이 끝났으니 다시 기본 표정으로 돌아가지 않음
    mutating func caught() {
        shake = Self.shakeSec
        setPose(.oops, hold: .infinity)
    }

    /// 꼭대기 완료: 그 계단 오른쪽 끝에 닫힌 상자가 튀어나왔다가 열린다(캐릭터와 겹치지 않는 자리)
    mutating func placeTreasure() {
        chests.append(Chest(height: standingHeight, lane: standingLane))
    }

    mutating func celebrateFlag() {
        flag.passed = true
        flag.pop = 1
        flag.fade = 1
    }

    // MARK: - 프레임

    mutating func update(_ dt: Double, caught: Bool) {
        if jumpT < 1 {
            jumpT = min(1, jumpT + dt / Self.jumpSec)
            characterX = jumpFrom.x + (jumpTo.x - jumpFrom.x) * jumpT
            characterY = jumpFrom.y + (jumpTo.y - jumpFrom.y) * jumpT + sin(jumpT * .pi) * Self.jumpArc
            if jumpT >= 1 {
                squash = 1
                puffDust()
                // 점프 중에 다음 문제를 틀렸으면 '앗!' 이 우선 — 점프 자세일 때만 기뻐함으로 바꾼다
                if pose == .jump { setPose(.happy, hold: Self.happySec) }
            }
        }
        if poseHold > 0 {
            poseHold = max(0, poseHold - dt)
            if poseHold <= 0 && jumpT >= 1 { setPose(.idle, hold: 0) }
        }
        squash = max(0, squash - dt * 6)
        shake = max(0, shake - dt)
        if jumpT >= 1 {
            characterX = Self.laneX(standingLane) + sin(shake * 60) * 0.15 * (shake / Self.shakeSec)
            characterY = Self.feetY(Double(standingHeight))
        }
        // 숨쉬기는 서 있고 흔들리지 않을 때만 흐른다
        breathT = jumpT >= 1 && shake <= 0 ? breathT + dt : 0
        // 먼지: 옆으로 퍼지며 느려지고 흐려짐
        let drag = max(0, 1 - Self.dustDrag * dt)
        for i in dust.indices where dust[i].alive {
            dust[i].age += dt
            if dust[i].age >= Self.dustSec {
                dust[i].alive = false
                continue
            }
            dust[i].x += dust[i].vx * dt
            dust[i].y += dust[i].vy * dt
            dust[i].vx *= drag
            dust[i].vy *= drag
        }

        for (h, var row) in rows {
            for lane in row.lanes.indices where row.lanes[lane].visible {
                if row.lanes[lane].falling {
                    // 고를 수 없는 발판임이 바로 보이도록 빠르게 떨어지며 흐려진다
                    row.lanes[lane].fallT += dt
                    let t = row.lanes[lane].fallT
                    row.lanes[lane].alpha = min(1, max(0, 1 - t / Self.fallSec))
                    if t >= Self.fallSec && row.chosen != nil {
                        row.lanes[lane].visible = false
                        row.lanes[lane].label = ""
                    }
                }
                if row.lanes[lane].glowLeft > 0 {
                    row.lanes[lane].glowLeft = max(0, row.lanes[lane].glowLeft - dt)
                    if !row.lanes[lane].falling {
                        let k = min(1, max(0, row.lanes[lane].glowLeft * 4))
                        row.lanes[lane].color = row.base.lerp(to: row.lanes[lane].glowColor, k)
                    }
                }
            }
            rows[h] = row
        }

        for i in chests.indices where chests[i].t < Self.chestOpenDelay {
            chests[i].t = min(Self.chestOpenDelay, chests[i].t + dt)
        }

        if caught { swallow = min(1, swallow + dt / Self.swallowSec) }
        flag.pop = max(0, flag.pop - dt / Self.flagPopSec)
        if flag.fade > 0 { flag.fade = max(0, flag.fade - dt / Self.flagFadeSec) }

        cameraY += (Self.cameraTarget(standing: standingHeight) - cameraY) * (1 - exp(-8 * dt))
    }

    /// 떨어지는 발판이 내려간 거리(유닛)
    static func fallDrop(_ lane: Lane) -> Double {
        lane.falling ? fallAccel * lane.fallT * lane.fallT : 0
    }

    /// 착지 순간 발 양옆으로 dustCount 알(왼쪽·오른쪽 번갈아, 바깥 알일수록 빠르고 높게 — 무작위 없음). 풀이 차 있으면 남는 만큼만
    private mutating func puffDust() {
        var started = 0
        for i in dust.indices where started < Self.dustCount && !dust[i].alive {
            let side = started % 2 == 0 ? -1.0 : 1.0
            let ring = Double(started / 2)
            dust[i] = Dust(x: characterX + side * Self.dustSpread, y: characterY + Self.dustLift,
                           vx: side * (Self.dustSpeedX.base + Self.dustSpeedX.step * ring),
                           vy: Self.dustSpeedY.base + Self.dustSpeedY.step * ring,
                           age: 0, alive: true)
            started += 1
        }
    }

    private mutating func setPose(_ next: GameArt.GuguPose, hold: Double) {
        pose = next
        poseHold = hold
    }
}
