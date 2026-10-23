package site.smap.gugudan.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// 구구 팡팡 — 떨어지는 숫자 풍선 중 미션에 맞는 것만 눌러 터뜨린다 (Missile Command식 탭 조준 + 웨이브 미션)
// 순수 규칙만 담는다: 미션·풍선 생성·판정·난이도·체험. 화면 문구는 없다(미션은 sealed class + 값, 화면이 번역한다).
// 좌표계: x 0~1 = 놀이 영역 가로 비율, y 0~1 = 등장선(0) → 지붕선(1) 진행. 시간 단위는 ms.
// iOS Core/Pop.swift 와 1:1 미러 — 같은 random 열이면 같은 결과가 나오도록 규칙 계산에는 사칙연산만 쓴다(sin 등 금지).

/** 웨이브 미션 — 화면이 문구로 바꾼다 ("7단 정답만 팡!", "곱해서 36 만들기") */
sealed class PopMission {
    /** 배수 사냥 — table 의 배수(table × 1~9)만 터뜨린다 */
    data class Multiples(val table: Int) : PopMission()
    /** 짝꿍 보스 — 곱해서 target 이 되는 작은 풍선 두 개를 차례로 터뜨린다 */
    data class PairBoss(val target: Int) : PopMission()
}

enum class PopBalloonKind {
    /** 배수 사냥의 보통 풍선 (정답·오답 모두) */
    NORMAL,
    /** 황금 정답 풍선 — 터뜨리면 잠깐 느려진다 (웨이브 2부터 웨이브마다 1개) */
    GOLDEN,
    /** 짝꿍 보스 웨이브의 작은 풍선 — 떨어지지 않고 제자리에 있다 */
    SMALL,
}

data class PopBalloon(
    val id: Int,
    val value: Int,
    val kind: PopBalloonKind,
    /** 미션에 맞는 풍선인지 — 배수 사냥: table 의 배수, 짝꿍 보스: 어떤 짝꿍 쌍의 한쪽 */
    val correct: Boolean,
    val x: Double,
    val y: Double,
)

data class PopBoss(
    val value: Int,
    /** 남은 짝꿍 수 — 0 이 되면 보스를 물리친다 */
    val hitsLeft: Int,
    /** 0(등장) → 1(지붕선). 1 에 닿으면 하트 -1 후 웨이브가 끝난다 */
    val y: Double,
)

/** 곱해서 보스 수가 되는 짝꿍 (a ≤ b, 둘 다 1~9, b ≥ 2) */
data class PopPair(val a: Int, val b: Int)

/** 배수 사냥 웨이브에 내보낼 풍선 순서 (값은 내보내는 순간 화면과 겹치지 않게 정한다) */
enum class PopSlot { CORRECT, GOLDEN, DISTRACTOR }

/** advance/tap 한 번이 만든 일 — 소리·햅틱·입자 연출용. 다음 호출에서 비워진다 */
sealed class PopEvent {
    /** 미션 안내가 끝나고 풍선이 움직이기 시작 */
    data class WaveStarted(val wave: Int) : PopEvent()
    /** 정답 풍선을 터뜨림 (points = 이번에 얻은 점수) */
    data class Popped(val id: Int, val value: Int, val points: Int, val golden: Boolean) : PopEvent()
    /** 미션에 안 맞는 풍선을 누름 — 콤보 초기화 + 재장전. 풍선은 그대로 남는다 */
    data class WrongTap(val id: Int, val value: Int) : PopEvent()
    /** 재장전 중이라 탭을 무시함 */
    data object Reloading : PopEvent()
    /** 정답 풍선이 지붕선에 닿음 — 하트 -1 */
    data class Missed(val id: Int, val value: Int) : PopEvent()
    /** 오답 풍선이 지붕선을 그냥 지나감 (감점 없음) */
    data class Passed(val id: Int, val value: Int) : PopEvent()
    /** 보스 웨이브: 짝꿍 첫 번째를 터뜨림 (PopState.pending 에 남는다) */
    data class BossPick(val id: Int, val value: Int, val points: Int) : PopEvent()
    /** 보스 웨이브: 짝꿍을 완성해 보스를 맞힘 */
    data class BossHit(val a: Int, val b: Int, val points: Int, val hitsLeft: Int) : PopEvent()
    data object BossDefeated : PopEvent()
    /** 보스가 지붕선에 닿음 — 하트 -1 */
    data object BossEscaped : PopEvent()
    data class WaveCleared(val wave: Int) : PopEvent()
    /** 황금 풍선으로 느려짐 시작 (Pop.SLOW_DURATION_MS 동안) */
    data object SlowMoStarted : PopEvent()
    /** 하트를 다 써서 판이 끝남 */
    data object GameOver : PopEvent()
    /** 무료 체험 웨이브를 다 깨서 판이 끝남 */
    data object TrialEnded : PopEvent()
}

data class PopState(
    val phase: RunnerPhase,
    /** 고른 단 (null = 섞어서 — 웨이브마다 로드맵 순서로 바뀐다) */
    val table: Int?,
    /** 지금 웨이브 (1부터) */
    val wave: Int,
    /** 지금 웨이브의 단 — 섞어서면 Pop.waveTable 이 정한다 */
    val waveTable: Int,
    val mission: PopMission,
    /** 미션 안내 남은 시간 — 0 보다 크면 풍선이 움직이지 않고 탭도 받지 않는다 */
    val introMs: Double = 0.0,
    /** 화면의 풍선 (배수 사냥: 떨어지는 풍선, 보스: 제자리 작은 풍선 6개) */
    val balloons: List<PopBalloon> = emptyList(),
    /** 짝꿍 보스 웨이브에서만 있다 */
    val boss: PopBoss? = null,
    /** 보스 웨이브에서 먼저 터뜨린 짝꿍 한쪽 ("6 × ?") */
    val pending: Int? = null,
    /** 아직 내보내지 않은 풍선 순서 (배수 사냥) */
    val queue: List<PopSlot> = emptyList(),
    /** 다음 풍선까지 남은 시간 (0 이하면 자리가 나는 대로 내보낸다) */
    val spawnMs: Double = 0.0,
    /** 이번 웨이브에서 처리한 정답 풍선 수 (터뜨림 + 놓침) — Pop.WAVE_GOAL 이 되면 웨이브 끝 */
    val handled: Int = 0,
    val score: Int = 0,
    val hearts: Int = Pop.MAX_HEARTS,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    /** 오답 뒤 재장전 남은 시간 — 0 보다 크면 탭을 무시한다 */
    val reloadMs: Double = 0.0,
    /** 황금 풍선 느려짐 남은 시간 (실제 시간 기준) */
    val slowMs: Double = 0.0,
    val nextId: Int = 1,
    /** 결과 화면용 통계 */
    val pops: Int = 0,
    val wrongTaps: Int = 0,
    val bossesDefeated: Int = 0,
    /** 무료 체험에서 남은 웨이브 수(지금 웨이브 포함). null 이면 제한 없음, 0 이면 체험을 다 해서 끝난 판 */
    val trialLeft: Int? = null,
    /** 마지막 advance/tap 이 만든 일 */
    val events: List<PopEvent> = emptyList(),
)

object Pop {
    // MARK: 규칙 상수

    const val MAX_HEARTS = 3
    /** 배수 사냥 웨이브의 정답 풍선 수 — 모두 처리(터뜨림·놓침)하면 웨이브 끝 */
    const val WAVE_GOAL = 6
    /** 웨이브 시작 전 미션 안내 시간 */
    const val INTRO_MS = 1800.0
    /** 웨이브 3마다 짝꿍 보스 */
    const val BOSS_EVERY = 3
    const val BOSS_FALL_MS = 20_000.0
    /** 보스를 물리치는 데 필요한 짝꿍 수 (실기 조정 대상) */
    const val BOSS_HITS = 3
    const val POINTS_PER_CORRECT = 10
    /** 오답 탭 뒤 재장전 시간 */
    const val RELOAD_MS = 800.0
    const val GOLDEN_FROM_WAVE = 2
    const val SLOW_DURATION_MS = 4000.0
    /** 느려짐 동안 풍선·보스가 움직이는 비율 */
    const val SLOW_FACTOR = 0.5
    /** 이 웨이브까지는 오답을 먼 수에서 먼저 고른다 (첫 웨이브는 쉽게) */
    const val FAR_DISTRACTOR_WAVES = 1

    // MARK: 좌표 상수 (x·탭 반경은 가로 폭 단위)

    /** 떨어지는 풍선이 나오는 세로 줄 수 — 줄 가운데 x = (줄 + 0.5) / LANES */
    const val LANES = 5
    /** 같은 줄의 앞 풍선이 이만큼 내려가야 그 줄에 새 풍선을 낸다 (겹침 방지) */
    const val LANE_CLEAR_Y = 0.3
    const val BALLOON_DIAMETER = 0.15
    const val BOSS_DIAMETER = 0.26
    const val BOSS_X = 0.5
    /** 누른 곳에서 풍선 중심까지 이 거리 안이면 맞힌 것으로 본다 (넉넉하게) */
    const val TAP_RADIUS = 0.11
    /** 가장 가까운 풍선과 거리 차이가 이 안이면 '겹침'으로 보고 지붕에 가까운 풍선을 고른다 */
    const val TIE_TOLERANCE = 0.02
    /** 보스 웨이브 작은 풍선 자리 6개 (x, y) — 가운데로 내려오는 보스와 겹치지 않게 양옆에 둔다 */
    val SMALL_SLOTS: List<Pair<Double, Double>> = listOf(
        0.11 to 0.38, 0.27 to 0.58, 0.11 to 0.78,
        0.89 to 0.38, 0.73 to 0.58, 0.89 to 0.78,
    )

    // MARK: 난이도

    fun isBossWave(wave: Int): Boolean = wave % BOSS_EVERY == 0

    /** 고른 단이 있으면 그 단, 섞어서(null)면 로드맵 순서(2·5·3·4·6·9·7·8)를 웨이브마다 돈다 */
    fun waveTable(wave: Int, table: Int?): Int =
        table ?: Hints.ROADMAP_TABLES[(wave - 1) % Hints.ROADMAP_TABLES.size]

    /** 풍선이 등장선에서 지붕선까지 내려오는 시간 — 7초 → 6초 → 웨이브마다 0.5초씩, 하한 3.5초 */
    fun fallMs(wave: Int): Double =
        if (wave <= 1) 7000.0 else max(3500.0, 6000.0 - 500.0 * (wave - 2).toDouble())

    /** 화면에 동시에 있는 풍선 수 상한 — 3 → 4 → … 최대 6 */
    fun concurrent(wave: Int): Int = min(6, 2 + wave)

    /** 오답 비율(%) — 40 → 50 → 최대 60 */
    fun wrongPercent(wave: Int): Int = min(60, 30 + 10 * wave)

    /** 웨이브의 오답 풍선 수 — 정답 6개에 오답 비율을 맞춘다 (40% → 4, 50% → 6, 60% → 9) */
    fun distractorCount(wave: Int): Int {
        val p = wrongPercent(wave)
        return (WAVE_GOAL * p + (100 - p) / 2) / (100 - p)
    }

    /** 콤보 배율 — 콤보 5부터 ×2, 10부터 ×3 (이번 정답까지 센 콤보 기준) */
    fun multiplier(combo: Int): Int = if (combo >= 10) 3 else if (combo >= 5) 2 else 1

    // MARK: 짝꿍

    /** n 을 만드는 짝꿍 전부 (a ≤ b, 1~9, b ≥ 2) — 순서를 바꾼 쌍은 같은 쌍으로 본다 */
    fun pairs(n: Int): List<PopPair> = (1..9).mapNotNull { a ->
        if (n % a != 0) return@mapNotNull null
        val b = n / a
        if (b >= a && b <= 9 && b >= 2) PopPair(a, b) else null
    }

    /** v 가 target 의 어떤 짝꿍 한쪽인지 */
    fun isPairMember(v: Int, target: Int): Boolean = pairs(target).any { it.a == v || it.b == v }

    /** 보스 수 — 그 웨이브 단 × 2~9 (단과 곱하는 수의 짝꿍이 반드시 있다) */
    fun bossTarget(table: Int, random: () -> Double): Int = table * (2 + pick(8, random))

    // MARK: 풍선 값

    /** 오답 후보 — 모두 1~81, table 의 배수가 아니고, 서로 겹치지 않는다 (각각 오름차순) */
    data class DistractorPool(
        /** 배수 ±1 (63 → 62, 64) */
        val near: List<Int>,
        /** 이웃 단의 곱 (7단이면 6단·8단의 곱) */
        val neighbour: List<Int>,
        /** 배수의 숫자 뒤집기 (56 → 65) */
        val reversal: List<Int>,
        /** 그 밖의 먼 수 */
        val far: List<Int>,
    )

    fun distractorPool(table: Int): DistractorPool {
        val t = table
        fun ok(v: Int): Boolean = v in 1..81 && v % t != 0
        val multiples = (1..9).map { t * it }
        val near = multiples.flatMap { listOf(it - 1, it + 1) }.filter(::ok).distinct().sorted()
        val neighbour = listOf(t - 1, t + 1)
            .filter { it in Problems.MIN_TABLE..Problems.MAX_TABLE }
            .flatMap { n -> (1..9).map { n * it } }
            .filter { ok(it) && it !in near }
            .distinct().sorted()
        val reversal = multiples
            .filter { it >= 10 && it % 10 != 0 }
            .map { (it % 10) * 10 + it / 10 }
            .filter { ok(it) && it !in near && it !in neighbour }
            .distinct().sorted()
        val far = (1..81).filter { ok(it) && it !in near && it !in neighbour && it !in reversal }
        return DistractorPool(near, neighbour, reversal, far)
    }

    /** 오답 값 — 첫 웨이브는 먼 수, 그 뒤는 헷갈리는 수(근접 3 : 이웃 단 2 : 뒤집기 1)를 먼저. exclude(화면의 값)와 겹치지 않는다 */
    fun distractor(table: Int, wave: Int, exclude: List<Int>, random: () -> Double): Int {
        val pool = distractorPool(table)
        val far = pool.far.filter { it !in exclude }
        if (wave <= FAR_DISTRACTOR_WAVES && far.isNotEmpty()) return far[pick(far.size, random)]

        val tiers = listOf(pool.near to 3, pool.neighbour to 2, pool.reversal to 1)
            .map { (values, weight) -> values.filter { it !in exclude } to weight }
            .filter { it.first.isNotEmpty() }
        if (tiers.isNotEmpty()) {
            var r = pick(tiers.sumOf { it.second }, random)
            for ((values, weight) in tiers) {
                if (r < weight) return values[pick(values.size, random)]
                r -= weight
            }
        }
        // 화면 풍선은 많아야 6개라 먼 수가 바닥나는 일은 없다
        check(far.isNotEmpty()) { "오답 후보 없음: ${table}단" }
        return far[pick(far.size, random)]
    }

    /** 정답 값 — table × 1~9 중 화면에 없는 것 */
    fun correctValue(table: Int, exclude: List<Int>, random: () -> Double): Int {
        val options = (1..9).map { table * it }.filter { it !in exclude }
        check(options.isNotEmpty()) { "정답 후보 없음: ${table}단" }
        return options[pick(options.size, random)]
    }

    /** 보스 웨이브 작은 풍선 6개 값 — 짝꿍 한 쌍 + 어떤 짝꿍에도 안 드는 수 4개, 자리는 섞는다 */
    fun smallValues(target: Int, random: () -> Double): List<Int> {
        val all = pairs(target)
        val pair = all[pick(all.size, random)]
        val others = shuffled((1..9).filter { !isPairMember(it, target) }, random)
        // 1~9 중 짝꿍에 드는 수는 많아야 4개(예: 12 → 2·3·4·6)라 남는 수가 늘 5개 이상이다
        check(others.size >= SMALL_SLOTS.size - 2) { "보스 $target: 오답 후보 부족" }
        return shuffled(listOf(pair.a, pair.b) + others.take(SMALL_SLOTS.size - 2), random)
    }

    // MARK: 생성

    /** trial: 무료 체험 웨이브 수 (null = 제한 없음, 보통 PremiumConfig.POP_TRIAL_WAVES) */
    fun create(
        table: Int?,
        phase: RunnerPhase = RunnerPhase.RUNNING,
        trial: Int? = null,
        random: () -> Double = { Random.nextDouble() },
    ): PopState {
        require(table == null || table in Problems.MIN_TABLE..Problems.MAX_TABLE) { "단 범위 밖: $table" }
        val s = PopState(
            phase = phase, table = table, wave = 0, waveTable = waveTable(1, table),
            mission = PopMission.Multiples(waveTable(1, table)), trialLeft = trial,
        )
        return startWave(s, 1, random)
    }

    // MARK: 진행

    /** dt(ms) 만큼 진행 — 미션 안내 → 풍선 이동·지붕선 판정 → 웨이브 끝 → 새 풍선 */
    fun advance(state: PopState, dt: Double, random: () -> Double = { Random.nextDouble() }): PopState {
        var s = state.copy(events = emptyList())
        if (s.phase != RunnerPhase.RUNNING || !dt.isFinite() || dt <= 0) return s

        val worldDt = if (s.slowMs > 0) dt * SLOW_FACTOR else dt
        s = s.copy(reloadMs = max(0.0, s.reloadMs - dt), slowMs = max(0.0, s.slowMs - dt))

        if (s.introMs > 0) {
            val introMs = max(0.0, s.introMs - dt)
            s = s.copy(introMs = introMs)
            if (introMs == 0.0) s = s.copy(events = s.events + PopEvent.WaveStarted(s.wave))
            return s
        }

        s.boss?.let { boss ->
            val moved = boss.copy(y = min(1.0, boss.y + worldDt / BOSS_FALL_MS))
            s = s.copy(boss = moved)
            if (moved.y >= 1) {
                s = loseHeart(s.copy(events = s.events + PopEvent.BossEscaped))
                if (s.phase == RunnerPhase.RUNNING) s = clearWave(s, random)
            }
            return s
        }

        val fall = fallMs(s.wave)
        val kept = mutableListOf<PopBalloon>()
        for (b in s.balloons) {
            val balloon = b.copy(y = b.y + worldDt / fall)
            if (balloon.y < 1) {
                kept += balloon
            } else if (balloon.correct) {
                s = s.copy(events = s.events + PopEvent.Missed(balloon.id, balloon.value), handled = s.handled + 1)
                s = loseHeart(s)
            } else {
                s = s.copy(events = s.events + PopEvent.Passed(balloon.id, balloon.value))
            }
        }
        s = s.copy(balloons = kept)
        if (s.phase != RunnerPhase.RUNNING) return s
        if (s.handled >= WAVE_GOAL) return clearWave(s, random)

        val spawnMs = s.spawnMs - worldDt
        s = s.copy(spawnMs = spawnMs)
        if (spawnMs <= 0) s = spawn(s.copy(spawnMs = 0.0), random)
        return s
    }

    // MARK: 탭

    /**
     * 누른 곳(x 0~1, y 0~1)에서 맞힐 풍선 id — 반경 안에서 가장 가까운 풍선, 겹치면 지붕에 가까운(y 큰) 풍선, 그다음 id 작은 풍선.
     * aspect = 등장선~지붕선 높이 ÷ 놀이 영역 폭 (y 차이를 가로 폭 단위로 바꾼다). 보스는 대상이 아니다.
     */
    fun target(state: PopState, x: Double, y: Double, aspect: Double): Int? {
        if (!x.isFinite() || !y.isFinite() || !aspect.isFinite() || aspect <= 0) return null
        val inRange = state.balloons.mapNotNull { b ->
            val dx = b.x - x
            val dy = (b.y - y) * aspect
            val d = sqrt(dx * dx + dy * dy)
            if (d <= TAP_RADIUS) b to d else null
        }
        val nearest = inRange.minOfOrNull { it.second } ?: return null
        val ties = inRange.filter { it.second <= nearest + TIE_TOLERANCE }.map { it.first }
        return ties.sortedWith(compareByDescending<PopBalloon> { it.y }.thenBy { it.id }).first().id
    }

    /** 누른 곳으로 탭 — 맞힐 풍선이 없으면 아무 일도 없다 */
    fun tap(
        state: PopState,
        x: Double,
        y: Double,
        aspect: Double,
        random: () -> Double = { Random.nextDouble() },
    ): PopState {
        val id = target(state, x, y, aspect) ?: return state.copy(events = emptyList())
        return tap(state, id, random)
    }

    /** 풍선 id 로 탭 — 미션 안내 중·재장전 중이면 무시 */
    fun tap(state: PopState, id: Int, random: () -> Double = { Random.nextDouble() }): PopState {
        var s = state.copy(events = emptyList())
        if (s.phase != RunnerPhase.RUNNING || s.introMs != 0.0) return s
        val index = s.balloons.indexOfFirst { it.id == id }
        if (index < 0) return s
        if (s.reloadMs > 0) return s.copy(events = listOf(PopEvent.Reloading))
        val balloon = s.balloons[index]

        val boss = s.boss
        if (boss != null) return tapSmall(s, index, boss, random)
        if (!balloon.correct) return wrongTap(s, balloon)

        s = s.copy(balloons = s.balloons.filterIndexed { i, _ -> i != index })
        val (awarded, points) = award(s)
        s = awarded.copy(handled = awarded.handled + 1)
        val golden = balloon.kind == PopBalloonKind.GOLDEN
        s = s.copy(events = s.events + PopEvent.Popped(balloon.id, balloon.value, points, golden))
        if (golden) s = s.copy(slowMs = SLOW_DURATION_MS, events = s.events + PopEvent.SlowMoStarted)
        if (s.handled >= WAVE_GOAL) s = clearWave(s, random)
        return s
    }

    // MARK: 기록

    /** 단별 최고 점수 저장 키 ("all" = 섞어서, "2"~"9") — Persistence.POP_BEST_KEY 아래 {키: 점수} */
    fun bestKey(table: Int?): String = table?.toString() ?: "all"

    /** 저장본에서 알 수 없는 키·음수 점수를 걸러 낸다 (다른 미니게임 loadBests 와 같은 규칙) */
    fun sanitizeBests(raw: Map<String, Int>): Map<String, Int> {
        val allowed = (listOf("all") + (Problems.MIN_TABLE..Problems.MAX_TABLE).map { it.toString() }).toSet()
        return raw.filter { it.key in allowed && it.value >= 0 }
    }

    // MARK: 내부

    /** 0 until count 중 하나 (random 이 1 에 아주 가까워도 범위를 넘지 않게) */
    private fun pick(count: Int, random: () -> Double): Int = min((random() * count).toInt(), count - 1)

    /** Fisher–Yates (Runner.shuffled 와 같은 순서로 random 을 쓴다) */
    private fun <T> shuffled(values: List<T>, random: () -> Double): List<T> {
        val result = values.toMutableList()
        var i = result.size - 1
        while (i > 0) {
            val j = min((random() * (i + 1)).toInt(), i)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
            i -= 1
        }
        return result
    }

    private fun laneX(lane: Int): Double = (lane.toDouble() + 0.5) / LANES.toDouble()

    private fun startWave(state: PopState, wave: Int, random: () -> Double): PopState {
        val waveTable = waveTable(wave, state.table)
        val s = state.copy(
            wave = wave, waveTable = waveTable, introMs = INTRO_MS,
            handled = 0, pending = null, spawnMs = 0.0,
        )
        if (isBossWave(wave)) {
            val target = bossTarget(waveTable, random)
            return makeSmalls(
                s.copy(mission = PopMission.PairBoss(target), boss = PopBoss(target, BOSS_HITS, 0.0), queue = emptyList()),
                target, random,
            )
        }
        val slots = (List(WAVE_GOAL) { PopSlot.CORRECT } + List(distractorCount(wave)) { PopSlot.DISTRACTOR }).toMutableList()
        if (wave >= GOLDEN_FROM_WAVE) slots[0] = PopSlot.GOLDEN
        return s.copy(
            mission = PopMission.Multiples(waveTable), boss = null,
            queue = shuffled(slots, random), balloons = emptyList(),
        )
    }

    /** 작은 풍선 6개를 새로 깐다 (id 를 이어 붙인다) */
    private fun makeSmalls(state: PopState, target: Int, random: () -> Double): PopState {
        val values = smallValues(target, random)
        val balloons = values.mapIndexed { i, value ->
            PopBalloon(
                id = state.nextId + i, value = value, kind = PopBalloonKind.SMALL,
                correct = isPairMember(value, target),
                x = SMALL_SLOTS[i].first, y = SMALL_SLOTS[i].second,
            )
        }
        return state.copy(balloons = balloons, nextId = state.nextId + values.size)
    }

    /** 다음 풍선 하나 — 자리(동시 수·빈 줄)가 없으면 다음 프레임에 다시 */
    private fun spawn(state: PopState, random: () -> Double): PopState {
        val slot = state.queue.firstOrNull() ?: return state
        if (state.balloons.size >= concurrent(state.wave)) return state
        val busy = state.balloons.filter { it.y < LANE_CLEAR_Y }.map { min((it.x * LANES).toInt(), LANES - 1) }.toSet()
        val free = (0 until LANES).filter { it !in busy }
        if (free.isEmpty()) return state
        val lane = free[pick(free.size, random)]
        val onScreen = state.balloons.map { it.value }
        val value = if (slot == PopSlot.DISTRACTOR) distractor(state.waveTable, state.wave, onScreen, random)
        else correctValue(state.waveTable, onScreen, random)
        val balloon = PopBalloon(
            id = state.nextId, value = value,
            kind = if (slot == PopSlot.GOLDEN) PopBalloonKind.GOLDEN else PopBalloonKind.NORMAL,
            correct = slot != PopSlot.DISTRACTOR, x = laneX(lane), y = 0.0,
        )
        return state.copy(
            balloons = state.balloons + balloon, nextId = state.nextId + 1, queue = state.queue.drop(1),
            spawnMs = fallMs(state.wave) / concurrent(state.wave).toDouble(),
        )
    }

    /** 보스 웨이브 작은 풍선 탭 — 첫 번째는 짝꿍에 드는 수, 두 번째는 첫 번째와 곱해 보스 수가 되는 수 */
    private fun tapSmall(state: PopState, index: Int, boss: PopBoss, random: () -> Double): PopState {
        val balloon = state.balloons[index]
        val first = state.pending
        if (first != null) {
            if (first * balloon.value != boss.value) return wrongTap(state, balloon)
            val removed = state.copy(balloons = state.balloons.filterIndexed { i, _ -> i != index })
            val (awarded, points) = award(removed)
            val hit = boss.copy(hitsLeft = boss.hitsLeft - 1)
            var s = awarded.copy(
                boss = hit, pending = null,
                events = awarded.events + PopEvent.BossHit(first, balloon.value, points, hit.hitsLeft),
            )
            if (hit.hitsLeft == 0) {
                s = s.copy(bossesDefeated = s.bossesDefeated + 1, events = s.events + PopEvent.BossDefeated)
                return clearWave(s, random)
            }
            // 새 짝꿍 세트 — 매번 완성할 수 있는 쌍이 하나 이상 있다
            return makeSmalls(s, boss.value, random)
        }
        if (!balloon.correct) return wrongTap(state, balloon)
        val removed = state.copy(balloons = state.balloons.filterIndexed { i, _ -> i != index })
        val (awarded, points) = award(removed)
        val s = awarded.copy(
            pending = balloon.value,
            events = awarded.events + PopEvent.BossPick(balloon.id, balloon.value, points),
        )
        // 빈자리를 채운다 — 짝이 화면에 없으면 짝을, 있으면 짝꿍에 안 드는 수를
        val present = s.balloons.map { it.value }
        val partner = boss.value / balloon.value
        val value = if (partner in present) filler(boss.value, present, random) else partner
        val refill = PopBalloon(
            id = s.nextId, value = value, kind = PopBalloonKind.SMALL,
            correct = isPairMember(value, boss.value), x = balloon.x, y = balloon.y,
        )
        val balloons = s.balloons.toMutableList().apply { add(index, refill) }
        return s.copy(balloons = balloons, nextId = s.nextId + 1)
    }

    /** 빈자리용 수 — 화면에 없는 '짝꿍에 안 드는 수', 없으면 화면에 없는 짝꿍 수, 그것도 없으면 아무 '짝꿍에 안 드는 수' */
    private fun filler(target: Int, exclude: List<Int>, random: () -> Double): Int {
        val others = (1..9).filter { !isPairMember(it, target) }
        val members = (1..9).filter { isPairMember(it, target) }
        val fresh = others.filter { it !in exclude }
        if (fresh.isNotEmpty()) return fresh[pick(fresh.size, random)]
        val freshMembers = members.filter { it !in exclude }
        if (freshMembers.isNotEmpty()) return freshMembers[pick(freshMembers.size, random)]
        return others[pick(others.size, random)]
    }

    /** 정답 처리 — 콤보를 올리고 배율을 곱한 점수를 더한다. (새 상태, 이번 점수) */
    private fun award(state: PopState): Pair<PopState, Int> {
        val combo = state.combo + 1
        val points = POINTS_PER_CORRECT * multiplier(combo)
        return state.copy(
            combo = combo, maxCombo = max(state.maxCombo, combo), pops = state.pops + 1,
            score = state.score + points,
        ) to points
    }

    private fun wrongTap(state: PopState, balloon: PopBalloon): PopState = state.copy(
        combo = 0, reloadMs = RELOAD_MS, wrongTaps = state.wrongTaps + 1,
        events = state.events + PopEvent.WrongTap(balloon.id, balloon.value),
    )

    private fun loseHeart(state: PopState): PopState {
        val s = state.copy(combo = 0)
        if (s.hearts <= 0) return s
        val hearts = s.hearts - 1
        if (hearts == 0) return s.copy(hearts = 0, phase = RunnerPhase.OVER, events = s.events + PopEvent.GameOver)
        return s.copy(hearts = hearts)
    }

    /** 웨이브 끝 — 남은 풍선을 치우고, 체험이 끝났으면 판을 끝내고, 아니면 다음 웨이브 */
    private fun clearWave(state: PopState, random: () -> Double): PopState {
        var s = state.copy(
            events = state.events + PopEvent.WaveCleared(state.wave),
            balloons = emptyList(), boss = null, pending = null, queue = emptyList(),
        )
        val left = s.trialLeft
        if (left != null) {
            s = s.copy(trialLeft = left - 1)
            if (left <= 1) return s.copy(phase = RunnerPhase.OVER, events = s.events + PopEvent.TrialEnded)
        }
        return startWave(s, s.wave + 1, random)
    }
}
