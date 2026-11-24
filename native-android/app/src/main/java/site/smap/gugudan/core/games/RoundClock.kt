package site.smap.gugudan.core.games

import kotlin.math.max

// A·B 게임 공통 시계 (Unity 시험판 Core/RoundClock.cs 이식, iOS RoundClock.swift 와 1:1)
// 첫 동작에 시작, 오답 공개 0.6초 동안 입력 잠금, 종료 순간 확정.
// B 는 60초 제한. A 는 제한 없이(UNLIMITED) 돌다가 먹구름에 잡히는 순간 end() 로 끝난다.
// 시간 단위는 초. 쉬는 창이 열린 동안에는 화면이 tick 을 부르지 않아 시계가 멈춘다.

enum class RoundPhase {
    /** 첫 동작 전 — 시간이 아직 흐르지 않음 */
    WAITING,
    RUNNING,
    OVER,
}

/** 입력을 채점하지 않은 이유 (받아들였으면 null) */
enum class ActionReject {
    /** 판이 끝난 뒤 */
    OVER,
    /** 오답 뒤 정답을 보여 주는 중 */
    REVEALING,
    /** 없는 칸·카드 */
    INVALID,
}

class RoundClock(val limitSec: Double = DURATION_SEC) {
    init {
        require(limitSec > 0) { "limitSec 는 0보다 커야 함: $limitSec" }
    }

    var phase: RoundPhase = RoundPhase.WAITING
        private set
    var elapsed: Double = 0.0
        private set
    var revealRemaining: Double = 0.0
        private set

    /** 남은 시간(초) — 무제한이면 +∞ */
    val remaining: Double get() = max(0.0, limitSec - elapsed)
    val isRevealing: Boolean get() = revealRemaining > 0

    /** 이 입력을 받을 수 있는지. 순서: 종료 → 없는 입력 → 공개 중 */
    fun check(valid: Boolean): ActionReject? {
        if (phase == RoundPhase.OVER) return ActionReject.OVER
        if (!valid) return ActionReject.INVALID
        if (isRevealing) return ActionReject.REVEALING
        return null
    }

    fun markAction() {
        if (phase == RoundPhase.WAITING) phase = RoundPhase.RUNNING
    }

    fun beginReveal() {
        revealRemaining = REVEAL_SEC
    }

    /** 시간과 무관한 종료(A: 먹구름에 잡힘). 진행 중에만 부를 수 있다 — 그 밖의 호출은 규칙 쪽 버그 */
    fun end() {
        // 멤버 check(valid:) 와 헷갈리지 않게 표준 라이브러리 check 를 이름으로 부른다
        kotlin.check(phase == RoundPhase.RUNNING) { "end() 는 진행 중에만 부를 수 있음: $phase" }
        revealRemaining = 0.0
        phase = RoundPhase.OVER
    }

    /** dt(초) 만큼 흘린다. 오답 공개가 이번 tick 에 끝났으면 true (시간 종료로 끝난 경우는 false) */
    fun tick(dt: Double): Boolean {
        require(dt >= 0) { "dt 는 0 이상이어야 함: $dt" }  // NaN 도 여기서 걸린다
        if (phase != RoundPhase.RUNNING || dt <= 0) return false

        elapsed += dt
        if (elapsed >= limitSec) {
            elapsed = limitSec
            revealRemaining = 0.0
            phase = RoundPhase.OVER
            return false
        }
        if (revealRemaining <= 0) return false
        revealRemaining -= dt
        if (revealRemaining > 0) return false
        revealRemaining = 0.0
        return true
    }

    /**
     * 값 복사본. Swift 는 struct 라 세션이 내보낸 시계를 밖에서 바꿀 수 없다 —
     * Kotlin 세션도 이 복사본을 내보내 화면이 세션 시계를 건드리지 못하게 한다
     */
    fun copy(): RoundClock = RoundClock(limitSec).also {
        it.phase = phase
        it.elapsed = elapsed
        it.revealRemaining = revealRemaining
    }

    companion object {
        const val DURATION_SEC: Double = 60.0
        const val REVEAL_SEC: Double = 0.6
        const val UNLIMITED: Double = Double.POSITIVE_INFINITY
    }
}
