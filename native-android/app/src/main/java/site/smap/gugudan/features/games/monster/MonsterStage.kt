package site.smap.gugudan.features.games.monster

import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.Motion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 구구 몬스터 월드 상태 (Unity 시험판 MonsterView.cs 이식, iOS MonsterStage.swift 대응) — 체력 숫자를 단 몬스터 한 마리의 움직임.
// 등장(위에서 떨어짐)·흔들림(오답)·터짐(처치: 기절한 몬스터가 커지며 떠올라 사라지고 고리가 퍼짐)만 다룬다.
// 엔진이 프레임마다 update 하고, drawMonsterScene 이 그린다. 좌표는 월드 유닛(y 위가 +).
// 생동감(계획서 game-juice, 공식은 Motion): 기다리는 동안 가끔 깡총, 처치 순간 화면 흔들림 + 기절 그림 하얀 번쩍임.
// 동작 줄이기면 깡총·화면 흔들림은 끈다(번쩍임은 짧은 사건 반응이라 둠).

class MonsterStage(
    /** 동작 줄이기(애니메이터 길이 배율 0) — 깡총·처치 화면 흔들림을 끈다 */
    private val reduceMotion: Boolean = false,
) {
    /** 처치 연출 — 방금 물리친 몬스터(기절 그림). x·y = 처치 순간 몬스터 자리, t = 0 → 1 */
    data class Pop(val kind: MonsterKind, val x: Double, val y: Double, val t: Double = 0.0)

    var kind = MonsterKind.GREEN
        private set
    var hp = 0
        private set
    /** 지금 몬스터 중심(흔들림·떨어짐·통통 포함) */
    var x = 0.0
        private set
    var y = MONSTER_Y
        private set
    /** 숨 쉬듯 가로세로 찌그러짐(가로 배율, 세로 = 2 - 가로) */
    var squash = 1.0
        private set
    /** 오답 때 몸이 붉어지는 정도(1 → 0) */
    var flash = 0.0
        private set
    var pop: Pop? = null
        private set
    /** 깡총(위로 뜬 높이는 y 에 이미 더함, 배율은 발 기준으로 그림) */
    var hop = Motion.Hop(0.0, 1.0, 1.0)
        private set
    /** 처치 화면 흔들림(유닛) — 숲 배경을 뺀 무대 전체를 이만큼 옮겨 그린다 */
    var impact = Motion.Shake(0.0, 0.0)
        private set
    private var dropT = 1.0
    private var dropDelay = 0.0
    private var shake = 0.0
    /** 깡총 진행도(1 = 안 뜀)·다음 깡총까지 기다린 시간 */
    private var hopT = 1.0
    private var hopWait = 0.0
    private var impactLeft = 0.0

    /** 다음 몬스터가 기다리는 동안은 숨김 */
    val visible: Boolean get() = dropDelay <= 0

    /** 기절 그림 하얀 번쩍임(1 → 0, 처치 뒤 IMPACT_FLASH_SEC 동안) */
    val popFlash: Double get() = pop?.let { Motion.impactFlash(it.t * POP_SEC) } ?: 0.0

    /** 새 몬스터 — drop 이면 위에서 떨어지며 들어온다(처치 직후면 0.2초 쉬었다가) */
    fun spawn(hp: Int, kind: MonsterKind, drop: Boolean) {
        require(hp > 0) { "체력은 1 이상: $hp" }
        this.kind = kind
        this.hp = hp
        dropT = if (drop) 0.0 else 1.0
        dropDelay = if (drop && pop != null) NEXT_DROP_DELAY else 0.0
        shake = 0.0
        flash = 0.0
        hopT = 1.0
        hopWait = 0.0
    }

    /** 오답 — 흔들리며 붉어짐 */
    fun hit() {
        shake = SHAKE_SEC
        flash = 1.0
    }

    /** 처치 — spawn 보다 먼저 불러야 방금 물리친 몬스터가 기절 그림으로 남는다 */
    fun defeat() {
        pop = Pop(kind, x, y)
        if (!reduceMotion) impactLeft = Motion.IMPACT_SEC
    }

    /** 새 판: 연출 없이 제자리 */
    fun reset() {
        kind = MonsterKind.GREEN
        hp = 0
        x = 0.0
        y = MONSTER_Y
        squash = 1.0
        flash = 0.0
        pop = null
        dropT = 1.0
        dropDelay = 0.0
        shake = 0.0
        hopT = 1.0
        hopWait = 0.0
        impactLeft = 0.0
        hop = Motion.Hop(0.0, 1.0, 1.0)
        impact = Motion.Shake(0.0, 0.0)
    }

    /** time = 흐른 시간(통통·숨쉬기 위상), 쉬는 동안 멈춤 */
    fun update(dt: Double, time: Double) {
        var nextY = MONSTER_Y + sin(time * 2.6) * 0.12
        if (dropDelay > 0) dropDelay = max(0.0, dropDelay - dt)
        // else 가 아님: 기다림이 끝난 그 프레임부터 바로 위에서 떨어지게(한 프레임 제자리 노출 방지)
        if (dropDelay <= 0 && dropT < 1) {
            dropT = min(1.0, dropT + dt / DROP_SEC)
            val e = 1 - (1 - dropT) * (1 - dropT)
            nextY += (1 - e) * DROP_HEIGHT
        }
        var nextX = 0.0
        if (shake > 0) {
            shake = max(0.0, shake - dt)
            nextX = sin(shake * 70) * 0.3 * (shake / SHAKE_SEC)
        }
        squash = 1 + 0.03 * sin(time * 5.2)
        flash = max(0.0, flash - dt / SHAKE_SEC)
        pop?.let { p ->
            val t = min(1.0, p.t + dt / POP_SEC)
            pop = if (t >= 1) null else p.copy(t = t)
        }
        updateHop(dt)
        x = nextX
        y = nextY + hop.dy
        impactLeft = max(0.0, impactLeft - dt)
        impact = Motion.impactShake(impactLeft)
    }

    /**
     * 깡총: 몬스터가 서서 기다릴 때(등장 낙하·오답 흔들림·처치 연출이 없을 때)만 시계가 흐르고 HOP_EVERY_SEC 마다 한 번 뜀.
     * 뛰는 중에 오답·처치가 오면 그 자리에 내려놓는다(흔들림·기절 연출이 우선)
     */
    private fun updateHop(dt: Double) {
        val waiting = !reduceMotion && hp > 0 && dropDelay <= 0 && dropT >= 1 && shake <= 0 && pop == null
        when {
            !waiting -> {
                hopT = 1.0
                hopWait = 0.0
            }
            hopT < 1 -> hopT = min(1.0, hopT + dt / Motion.HOP_SEC)
            else -> {
                hopWait += dt
                if (hopWait >= Motion.HOP_EVERY_SEC) {
                    hopWait = 0.0
                    hopT = 0.0
                }
            }
        }
        hop = Motion.hop(hopT)
    }

    companion object {
        /** 몬스터 중심 높이(흔들림 없을 때) */
        const val MONSTER_Y = 1.6
        /** 그림 높이(유닛). 배 패널 폭 ≈ 0.45 × 4.8 = 2.2유닛에 두 자리 숫자가 들어감 */
        const val BODY_SIZE = 4.8
        const val DROP_SEC = 0.25
        const val DROP_HEIGHT = 4.0
        const val SHAKE_SEC = 0.45
        /** 기절한 얼굴이 보일 만큼 */
        const val POP_SEC = 0.4
        /**
         * 처치 직후 다음 몬스터는 이만큼 숨겼다가 떨어뜨린다 — 바로 떨어뜨리면 사라지는 기절 몬스터와 겹쳐
         * 새 체력 숫자가 기절 몬스터 배에 비쳐 보였음(Unity 에뮬레이터 녹화로 확인)
         */
        const val NEXT_DROP_DELAY = 0.2
        /** 체력 글자 크기(유닛) — Unity TextMesh 글자 크기 140 × 0.085 */
        const val HP_EM = 1.19

        /** 몬스터 발 높이(흔들림 없을 때) — 그루터기 윗면을 여기에 맞춤 */
        val feetY: Double get() = MONSTER_Y - BODY_SIZE * (0.5 - GameArt.FEET_PAD)
    }
}
