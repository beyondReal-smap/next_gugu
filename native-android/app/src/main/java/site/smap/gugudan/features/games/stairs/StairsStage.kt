package site.smap.gugudan.features.games.stairs

import androidx.compose.ui.graphics.Color
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.StairsQuestion
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.Motion
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// 구구 계단의 월드 그림 상태 (Unity 시험판 StairsView.cs·StormCloudView·BestFlagView 의 움직임 부분 이식, iOS StairsStage.swift 대응)
// 발판 줄·캐릭터(점프·찌그러짐·흔들림·자세)·지나온 계단·보물 상자·카메라·먹구름 삼킴·최고 기록 깃발을 프레임마다 움직인다.
// 생동감(계획서 game-juice): 서 있을 때 숨쉬기·점프 중 늘어남·기울기(공식은 Motion), 착지 먼지. 동작 줄이기면 숨쉬기·늘어남·기울기는 끈다(먼지는 사건 반응이라 둠).
// 규칙은 모르고, 엔진이 알려 주는 사건(새 줄·착지·실패·보물·잡힘·신기록)만 받는다. 그리기는 StairsScene.
// 좌표는 월드 유닛(y 위가 +). 출발 발판 높이 0, 한 칸 = ROW_HEIGHT.

/** 곱해 입히는 색(발판) — 섞기(lerp)가 필요해 RGB 로 둔다 */
data class GameRGB(val r: Double, val g: Double, val b: Double) {
    fun lerp(other: GameRGB, t: Double): GameRGB = GameRGB(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t)

    val color: Color get() = Color(r.toFloat(), g.toFloat(), b.toFloat())

    companion object {
        /** 0xRRGGBB 정수 */
        fun hex(hex: Int): GameRGB =
            GameRGB(((hex shr 16) and 0xFF) / 255.0, ((hex shr 8) and 0xFF) / 255.0, (hex and 0xFF) / 255.0)

        val CORRECT = hex(0x3DDC84)
        val WRONG = hex(0xFF5A5F)
        val WALL = hex(0x47538A)
    }
}

class StairsStage(
    /** 동작 줄이기(애니메이터 길이 배율 0) — 숨쉬기·점프 늘어남·기울기를 끈다 */
    private val reduceMotion: Boolean = false,
) {
    class Lane(
        var visible: Boolean = true,
        var label: String = "",
        var color: GameRGB = GameRGB.WALL,
    ) {
        var alpha = 1.0
        var falling = false
        var fallT = 0.0
        var glowLeft = 0.0
        var glowColor = GameRGB.CORRECT
    }

    class Row(
        val height: Int,
        var base: GameRGB,
        /** 밟은 칸(정답) — 고르지 않은 칸은 떨어져 사라지고 이 칸이 계단으로 남음 */
        var chosen: Int?,
        var lanes: List<Lane>,
    )

    class Chest(val height: Int, val lane: Int) {
        var t = 0.0

        val open: Boolean get() = t >= CHEST_OPEN_DELAY

        /** 살짝 크게 튀어나왔다가 제자리(ease-out-back) */
        val scale: Double
            get() {
                val x = min(1.0, t / CHEST_POP_SEC) - 1
                return 1 + 2.7 * x * x * x + 1.7 * x * x
            }
    }

    /** 최고 기록 깃발: 지난 최고 기록 높이의 점선 + 오른쪽 끝 깃발. 넘으면 통통 튀고 점선은 사라짐 */
    class Flag(val best: Int = 0) {
        var passed = false
        var pop = 0.0
        var fade = 0.0

        val visible: Boolean get() = best > 0
        val popScale: Double get() = 1 + 0.35 * sin(pop * PI)
        val lineAlpha: Double get() = if (passed) 0.75 * fade else 0.75
    }

    /** 찌그러짐 배율(가로, 세로) — 발을 기준으로 */
    data class Squash(val x: Double, val y: Double)

    /** 착지 먼지 한 알 — 고정 풀(DUST_POOL)을 돌려 쓴다(매 프레임 할당 없음) */
    class Dust {
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var age = 0.0
        var alive = false

        /** 0(막 생김) → 1(사라짐) */
        val progress: Double get() = min(1.0, age / DUST_SEC)
        /** 반지름(유닛) — 퍼지며 커진다 */
        val radius: Double get() = DUST_RADIUS * (1 + progress)
        val alpha: Double get() = DUST_ALPHA * (1 - progress)
    }

    private val rowMap = HashMap<Int, Row>()
    /** 높이 → 줄 (지나온 줄은 KEEP_ROWS_BELOW 줄까지만 남김) */
    val rows: Map<Int, Row> get() = rowMap
    private val chestList = mutableListOf<Chest>()
    val chests: List<Chest> get() = chestList
    var pose: GameArt.GuguPose = GameArt.GuguPose.IDLE
        private set
    private var poseHold = 0.0
    private var jumpFromX = 0.0
    private var jumpFromY = 0.0
    private var jumpToX = 0.0
    private var jumpToY = 0.0
    private var jumpT = 1.0
    /** 이번 점프 방향(칸 기준: 왼쪽 −1, 위로만 0, 오른쪽 +1) — 기울기 */
    private var jumpDir = 0
    private var squash = 0.0
    private var shake = 0.0
    /** 숨쉬기 시계(초) — 서 있는 동안 update 의 dt 만 쌓으므로 쉬는 창(루프 멈춤)에서는 숨쉬기도 멈춘다 */
    private var motionTime = 0.0
    /** 발 위치(월드) — 점프 중이면 공중 */
    var characterX = 0.0
        private set
    var characterY = 0.0
        private set
    var standingHeight = 0
        private set
    var standingLane = 1
        private set
    var cameraY = 0.0
        private set
    var accessory: Accessory? = null
        private set
    /** 먹구름 삼킴(0~1) */
    var swallow = 0.0
        private set
    var flag = Flag()
        private set
    val dust: List<Dust> = List(DUST_POOL) { Dust() }

    /** 착지 찌그러짐 × (점프 중 늘어남 | 서 있을 때 숨쉬기). 흔들리는 동안(오답·잡힘)은 숨쉬기 없음 */
    val characterScale: Squash
        get() {
            val x = 1 + 0.25 * squash
            val y = 1 - 0.25 * squash
            if (reduceMotion) return Squash(x, y)
            val m = when {
                jumpT < 1 -> Motion.jumpStretch(jumpT)
                shake <= 0 -> Motion.breath(motionTime)
                else -> return Squash(x, y)
            }
            return Squash(x * m.x, y * m.y)
        }

    /** 점프 중 가는 방향으로 기울기(도, 화면 시계 방향 +) — 착지하면 0 */
    val characterTilt: Double get() = if (reduceMotion) 0.0 else Motion.jumpTilt(jumpT, jumpDir)

    // MARK: 사건

    /** 새 판: 출발 발판 하나(가운데, "출발") 위에 선 구구, 카메라 제자리 */
    fun reset(startLabel: String, accessory: Accessory?, best: Int) {
        rowMap.clear()
        chestList.clear()
        setPose(GameArt.GuguPose.IDLE, 0.0)
        standingHeight = 0
        standingLane = 1
        rowMap[0] = Row(
            height = 0, base = GameRGB.WALL, chosen = 1,
            lanes = List(StairsSession.LANES) { lane -> Lane(visible = lane == 1, label = if (lane == 1) startLabel else "") },
        )
        characterX = laneX(1)
        characterY = feetY(0.0)
        jumpT = 1.0
        jumpDir = 0
        squash = 0.0
        shake = 0.0
        motionTime = 0.0
        dust.forEach { it.alive = false }
        cameraY = cameraTarget(0)
        this.accessory = accessory
        swallow = 0.0
        flag = Flag(best)
    }

    /** 다음 줄(풀어야 할 칸) 발판 3개를 숫자와 함께 깐다. 오답 공개 뒤 같은 칸을 다시 낼 때도 같은 줄을 다시 칠한다 */
    fun showNextRow(q: StairsQuestion, color: GameRGB) {
        val h = standingHeight + 1
        val row = rowMap.getOrPut(h) { Row(h, color, null, emptyList()) }
        row.base = color
        row.chosen = null
        row.lanes = q.choices.map { Lane(visible = true, label = "$it", color = color) }
    }

    fun land(lane: Int) {
        val h = standingHeight + 1
        val row = checkNotNull(rowMap[h]) { "착지할 줄이 없음: $h" }
        row.chosen = lane
        row.lanes.forEachIndexed { i, l -> if (i != lane) l.falling = true }
        row.lanes[lane].glowLeft = 0.35
        row.lanes[lane].glowColor = GameRGB.CORRECT
        jumpFromX = characterX
        jumpFromY = characterY
        jumpToX = laneX(lane)
        jumpToY = feetY(h.toDouble())
        jumpT = 0.0
        jumpDir = (lane - standingLane).sign
        setPose(GameArt.GuguPose.JUMP, 0.0)
        standingHeight = h
        standingLane = lane
        rowMap.keys.removeAll { it < standingHeight - KEEP_ROWS_BELOW }
        chestList.removeAll { it.height < standingHeight - KEEP_ROWS_BELOW }
    }

    /** 오답: 고른 발판이 빨갛게 떨어지고 정답 발판은 공개 동안 초록(숫자는 흰색 그대로 — 발판 색으로만 알림) */
    fun miss(lane: Int, correctLane: Int) {
        val h = standingHeight + 1
        val row = checkNotNull(rowMap[h]) { "오답 표시할 줄이 없음: $h" }
        row.lanes[lane].falling = true
        row.lanes[lane].color = GameRGB.WRONG
        row.lanes[correctLane].glowLeft = RoundClock.REVEAL_SEC
        row.lanes[correctLane].glowColor = GameRGB.CORRECT
        shake = SHAKE_SEC
        setPose(GameArt.GuguPose.OOPS, RoundClock.REVEAL_SEC)   // 정답 공개(입력 잠금) 동안 '앗!'
    }

    /** 먹구름에 잡힘: '앗!' 표정으로 흔들리고, 판이 끝났으니 다시 기본 표정으로 돌아가지 않음 */
    fun caught() {
        shake = SHAKE_SEC
        setPose(GameArt.GuguPose.OOPS, Double.POSITIVE_INFINITY)
    }

    /** 꼭대기 완료: 그 계단 오른쪽 끝에 닫힌 상자가 튀어나왔다가 열린다(캐릭터와 겹치지 않는 자리) */
    fun placeTreasure() {
        chestList.add(Chest(standingHeight, standingLane))
    }

    fun celebrateFlag() {
        flag.passed = true
        flag.pop = 1.0
        flag.fade = 1.0
    }

    // MARK: 프레임

    fun update(dt: Double, caught: Boolean) {
        // 서 있을 때만 흐르고 점프·흔들림 중에는 0 — 착지 뒤 숨쉬기가 늘 원래 크기에서 시작해 한 프레임에 튀지 않게(iOS 와 같음)
        motionTime = if (jumpT >= 1 && shake <= 0) motionTime + dt else 0.0
        if (jumpT < 1) {
            jumpT = min(1.0, jumpT + dt / JUMP_SEC)
            characterX = jumpFromX + (jumpToX - jumpFromX) * jumpT
            characterY = jumpFromY + (jumpToY - jumpFromY) * jumpT + sin(jumpT * PI) * JUMP_ARC
            if (jumpT >= 1) {
                squash = 1.0
                puffDust(characterX, characterY)
                // 점프 중에 다음 문제를 틀렸으면 '앗!' 이 우선 — 점프 자세일 때만 기뻐함으로 바꾼다
                if (pose == GameArt.GuguPose.JUMP) setPose(GameArt.GuguPose.HAPPY, HAPPY_SEC)
            }
        }
        if (poseHold > 0) {
            poseHold = max(0.0, poseHold - dt)
            if (poseHold <= 0 && jumpT >= 1) setPose(GameArt.GuguPose.IDLE, 0.0)
        }
        squash = max(0.0, squash - dt * 6)
        shake = max(0.0, shake - dt)
        if (jumpT >= 1) {
            characterX = laneX(standingLane) + sin(shake * 60) * 0.15 * (shake / SHAKE_SEC)
            characterY = feetY(standingHeight.toDouble())
        }

        for (row in rowMap.values) {
            for (lane in row.lanes) {
                if (!lane.visible) continue
                if (lane.falling) {
                    // 고를 수 없는 발판임이 바로 보이도록 빠르게 떨어지며 흐려진다
                    lane.fallT += dt
                    val t = lane.fallT
                    lane.alpha = min(1.0, max(0.0, 1 - t / FALL_SEC))
                    if (t >= FALL_SEC && row.chosen != null) {
                        lane.visible = false
                        lane.label = ""
                    }
                }
                if (lane.glowLeft > 0) {
                    lane.glowLeft = max(0.0, lane.glowLeft - dt)
                    if (!lane.falling) {
                        val k = min(1.0, max(0.0, lane.glowLeft * 4))
                        lane.color = row.base.lerp(lane.glowColor, k)
                    }
                }
            }
        }

        for (chest in chestList) {
            if (chest.t < CHEST_OPEN_DELAY) chest.t = min(CHEST_OPEN_DELAY, chest.t + dt)
        }

        // 먼지: 옆으로 퍼지며 느려지고 흐려짐
        val drag = max(0.0, 1 - DUST_DRAG * dt)
        for (d in dust) {
            if (!d.alive) continue
            d.age += dt
            if (d.age >= DUST_SEC) {
                d.alive = false
                continue
            }
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.vx *= drag
            d.vy *= drag
        }

        if (caught) swallow = min(1.0, swallow + dt / SWALLOW_SEC)
        flag.pop = max(0.0, flag.pop - dt / FLAG_POP_SEC)
        if (flag.fade > 0) flag.fade = max(0.0, flag.fade - dt / FLAG_FADE_SEC)

        cameraY += (cameraTarget(standingHeight) - cameraY) * (1 - exp(-8 * dt))
    }

    private fun setPose(next: GameArt.GuguPose, hold: Double) {
        pose = next
        poseHold = hold
    }

    /** 착지 순간 발 양옆으로 작은 크림색 먼지 DUST_COUNT 알(왼쪽·오른쪽 번갈아, 바깥쪽일수록 빠르고 높게). 풀이 차 있으면 남는 만큼만 */
    private fun puffDust(feetX: Double, feetY: Double) {
        var started = 0
        for (d in dust) {
            if (started >= DUST_COUNT) break
            if (d.alive) continue
            val side = if (started % 2 == 0) -1.0 else 1.0
            val ring = started / 2
            d.x = feetX + side * DUST_SPREAD
            d.y = feetY + DUST_LIFT
            d.vx = side * (DUST_SPEED_X + DUST_SPEED_X_STEP * ring)
            d.vy = DUST_SPEED_Y + DUST_SPEED_Y_STEP * ring
            d.age = 0.0
            d.alive = true
            started += 1
        }
    }

    companion object {
        const val ROW_HEIGHT = 2.4
        const val PLATFORM_WIDTH = 2.3
        const val PLATFORM_HEIGHT = 0.9
        /** 칸 사이 간격(유닛) — 가운데 칸 x = 0 */
        const val LANE_SPACING = 2.85
        /** 구구 그림 한 변(유닛). 다음 줄 발판에 머리가 닿지 않는 크기 */
        const val CHARACTER_SIZE = 1.3
        /** 캐릭터가 화면 아래쪽에 오도록(카메라 = 서 있는 높이 + 이만큼) */
        const val CAMERA_LEAD = 3.0
        const val CHEST_SIZE = 0.75
        const val HAPPY_SEC = 0.45
        const val JUMP_SEC = 0.28
        const val JUMP_ARC = 1.3
        const val FALL_ACCEL = 22.0
        const val FALL_SEC = 0.4
        const val KEEP_ROWS_BELOW = 5
        const val CHEST_POP_SEC = 0.25
        const val CHEST_OPEN_DELAY = 0.35
        /** 젤리 발판 윗면 양 끝이 둥글게 내려가므로 상자를 끝에서 이만큼 안쪽에 */
        const val CHEST_INSET = 0.15
        /** 젤리 방울(다리)을 뺀 몸통 중심이 그림 중심보다 이만큼(발판 높이 대비) 위 — 숫자를 몸통 가운데에 */
        const val PLATFORM_BODY_LIFT = 0.047
        /** 먹구름이 잡은 뒤 캐릭터를 덮을 만큼 더 차오르는 높이(유닛)·시간 */
        const val SWALLOW_RISE = 1.6
        const val SWALLOW_SEC = 0.5
        const val FLAG_POP_SEC = 0.5
        const val FLAG_FADE_SEC = 0.6
        const val SHAKE_SEC = 0.35
        /** 착지 먼지: 한 번에 몇 알·얼마 동안(초)·풀 크기(빠른 연속 착지 두 번이 겹쳐도 됨) */
        const val DUST_COUNT = 6
        const val DUST_SEC = 0.3
        const val DUST_POOL = 12
        /** 발 중심에서 양옆으로 떨어진 출발 자리·발판 위로 띄운 높이(유닛) */
        const val DUST_SPREAD = 0.22
        const val DUST_LIFT = 0.04
        /** 옆·위 속도(유닛/초) — 안쪽 알부터 바깥쪽 알로 STEP 씩 더 빠르게 */
        const val DUST_SPEED_X = 1.4
        const val DUST_SPEED_X_STEP = 0.6
        const val DUST_SPEED_Y = 0.2
        const val DUST_SPEED_Y_STEP = 0.35
        /** 감속(초당 비율) */
        const val DUST_DRAG = 5.0
        const val DUST_RADIUS = 0.128
        const val DUST_ALPHA = 0.85

        fun laneX(lane: Int): Double = (lane - 1) * LANE_SPACING
        /** 발판 중심 */
        fun platformY(height: Int): Double = height * ROW_HEIGHT
        /** 숫자 중심 */
        fun labelY(height: Int): Double = platformY(height) + 0.02 + PLATFORM_HEIGHT * PLATFORM_BODY_LIFT
        /** 발 위치 = 발판 윗면. 먹구름 윗면도 같은 기준이라 "발에 닿음 = 잡힘" */
        fun feetY(height: Double): Double = height * ROW_HEIGHT + PLATFORM_HEIGHT * 0.5
        fun cameraTarget(standing: Int): Double = standing * ROW_HEIGHT + CAMERA_LEAD

        /** 상자 중심(월드) */
        fun chestCenterX(chest: Chest): Double = laneX(chest.lane) + PLATFORM_WIDTH * 0.5 - CHEST_SIZE * 0.5 - CHEST_INSET
        fun chestCenterY(chest: Chest): Double =
            platformY(chest.height) + PLATFORM_HEIGHT * 0.5 + CHEST_SIZE * (0.5 - GameArt.FEET_PAD)

        /** 떨어지는 발판이 내려간 거리(유닛) */
        fun fallDrop(lane: Lane): Double = if (lane.falling) FALL_ACCEL * lane.fallT * lane.fallT else 0.0
    }
}
