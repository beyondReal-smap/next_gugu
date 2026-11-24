package site.smap.gugudan.features.games.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 새 그림 없이 캐릭터를 살아 있게 보이게 하는 움직임 공식 (iOS Motion.swift 와 같은 공식·상수 — 계획서 .claude/plans/game-juice.md)
// 모두 순수 함수: 시간·진행도를 받아 배율·기울기·위치만 돌려준다. 시간·사건은 Stage 가 들고, 화면(Scene)은 Stage 값을 그리기만 한다.
// 배율은 1 = 그대로, 기울기는 화면 기준 시계 방향 +(도), 위치는 월드 유닛(y 위가 +). 위상(phase)은 한 주기 대비(0~1).
// 동작 줄이기(애니메이터 길이 배율 0)는 부르는 쪽이 판단한다 — 이 공식은 언제나 같은 값을 낸다.

object Motion {
    /** 가로·세로 배율 */
    data class Scale(val x: Double, val y: Double)

    /** 깡총: 발 기준 위로 뜬 높이(유닛) + 가로·세로 배율 */
    data class Hop(val dy: Double, val sx: Double, val sy: Double)

    /** 화면 흔들림(유닛, y 위가 +) */
    data class Shake(val dx: Double, val dy: Double)

    /** 홈 카드·옷장 미리 보기 숨쉬기: 위로 뜬 높이(dp, 위가 +) + 크기 배율 */
    data class CardBreath(val lift: Double, val scale: Double)

    // MARK: 1. 서 있기 숨쉬기 (구구 계단)

    const val BREATH_SEC = 1.6
    /** 세로로 늘어나는 폭(1 ± 이만큼) — 가로는 반대로 BREATH_X 만큼 */
    const val BREATH_Y = 0.035
    const val BREATH_X = 0.025

    /** 들이쉬면 세로로 늘고 가로로 줄어든다(발 기준으로 늘려야 발판에 붙어 있음) */
    fun breath(time: Double, phase: Double = 0.0): Scale {
        val s = sin(2 * PI * (time / BREATH_SEC + phase))
        return Scale(1 - BREATH_X * s, 1 + BREATH_Y * s)
    }

    // MARK: 2. 점프 늘어남·기울기 (구구 계단)

    /** 올라가는 동안 세로로 최대 이만큼 늘고 가로로 줄어든다 */
    const val JUMP_STRETCH_Y = 0.18
    const val JUMP_STRETCH_X = 0.10
    /** 늘어남이 커졌다 줄어드는 구간(점프 진행도 0 → 이 값) — 그 뒤 내려오는 동안은 원래 크기 */
    const val JUMP_STRETCH_END = 0.5
    const val JUMP_TILT_DEG = 10.0

    /** jumpT = 점프 진행도(0 출발 → 1 착지). 0·1 에서 정확히 (1, 1) */
    fun jumpStretch(jumpT: Double): Scale {
        if (jumpT <= 0 || jumpT >= JUMP_STRETCH_END) return Scale(1.0, 1.0)
        val k = sin(PI * jumpT / JUMP_STRETCH_END)
        return Scale(1 - JUMP_STRETCH_X * k, 1 + JUMP_STRETCH_Y * k)
    }

    /** 가는 방향(dir: 왼쪽 −1, 위로만 0, 오른쪽 +1)으로 기울었다 착지 때 0. 오른쪽으로 가면 시계 방향(+) */
    fun jumpTilt(jumpT: Double, dir: Int): Double {
        if (jumpT <= 0 || jumpT >= 1) return 0.0
        return JUMP_TILT_DEG * dir.coerceIn(-1, 1) * sin(PI * jumpT)
    }

    // MARK: 5. 가끔 깡총 (구구 몬스터)

    const val HOP_SEC = 0.35
    /** 기다리는 동안 이만큼마다 한 번 뜀(초) */
    const val HOP_EVERY_SEC = 3.0
    const val HOP_HEIGHT = 0.25
    /** 뛰기 전 웅크림 끝·공중 끝(진행도) — 그 뒤는 착지 찌그러짐 */
    const val HOP_CROUCH_END = 0.2
    const val HOP_AIR_END = 0.8
    /** 웅크림·착지 찌그러짐(세로 −, 가로 +) */
    const val HOP_SQUASH_Y = 0.08
    const val HOP_SQUASH_X = 0.06
    /** 공중 늘어남(세로 +, 가로 −) */
    const val HOP_STRETCH_Y = 0.06
    const val HOP_STRETCH_X = 0.04

    /** t = 깡총 진행도(0 → 1). 시작·끝에서 정확히 (0, 1, 1) — 배율은 발 기준 */
    fun hop(t: Double): Hop {
        if (t <= 0 || t >= 1) return Hop(0.0, 1.0, 1.0)
        return when {
            t < HOP_CROUCH_END -> {
                val c = sin(PI * t / HOP_CROUCH_END)
                Hop(0.0, 1 + HOP_SQUASH_X * c, 1 - HOP_SQUASH_Y * c)
            }
            t < HOP_AIR_END -> {
                val a = sin(PI * (t - HOP_CROUCH_END) / (HOP_AIR_END - HOP_CROUCH_END))
                Hop(HOP_HEIGHT * a, 1 - HOP_STRETCH_X * a, 1 + HOP_STRETCH_Y * a)
            }
            else -> {
                val l = sin(PI * (t - HOP_AIR_END) / (1 - HOP_AIR_END))
                Hop(0.0, 1 + HOP_SQUASH_X * l, 1 - HOP_SQUASH_Y * l)
            }
        }
    }

    // MARK: 4. 처치 타격감 (구구 몬스터)

    const val IMPACT_SEC = 0.12
    /** 화면 흔들림 최대 폭(유닛, x·y 각각) */
    const val IMPACT_AMPLITUDE = 0.12
    /** 기절 그림이 하얗게 번쩍이는 시간(처치 순간부터) */
    const val IMPACT_FLASH_SEC = 0.08
    /** 흔들림 빠르기(라디안/초) — x·y 를 다르게 해 대각선 한 줄로만 흔들리지 않게 */
    const val IMPACT_FREQ_X = 105.0
    const val IMPACT_FREQ_Y = 85.0

    /** left = 흔들림 남은 시간(초). 남은 만큼 줄어들며 0 이면 (0, 0) */
    fun impactShake(left: Double): Shake {
        if (left <= 0) return Shake(0.0, 0.0)
        val k = min(1.0, left / IMPACT_SEC) * IMPACT_AMPLITUDE
        return Shake(k * sin(left * IMPACT_FREQ_X), k * cos(left * IMPACT_FREQ_Y))
    }

    /** elapsed = 처치 뒤 흐른 시간(초) → 하얀 번쩍임 세기(1 → 0) */
    fun impactFlash(elapsed: Double): Double =
        if (elapsed < 0) 0.0 else max(0.0, 1 - elapsed / IMPACT_FLASH_SEC)

    // MARK: 6. 홈 게임 카드 그림·옷장 큰 구구 숨쉬기

    const val CARD_BREATH_SEC = 2.4
    /** 위아래 폭(dp·pt) */
    const val CARD_BREATH_LIFT = 2.0
    /** 크기 1 ± 이만큼 */
    const val CARD_BREATH_SCALE = 0.02
    /** 홈 게임 카드끼리 위상 차이(한 주기 대비) — 두 카드가 같이 움직이지 않게 */
    const val CARD_BREATH_PHASE_STEP = 0.25

    /** 천천히 위아래로 뜨며 살짝 커졌다 작아짐(가로세로 같이) */
    fun cardBreath(time: Double, phase: Double = 0.0): CardBreath {
        val s = sin(2 * PI * (time / CARD_BREATH_SEC + phase))
        return CardBreath(CARD_BREATH_LIFT * s, 1 + CARD_BREATH_SCALE * s)
    }
}
