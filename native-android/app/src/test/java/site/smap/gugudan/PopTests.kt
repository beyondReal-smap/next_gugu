package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.*
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.services.Persistence
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구구 팡팡 순수 규칙 — iOS PopTests.swift 와 같은 시드·같은 기대값(골든 벡터)으로 두 플랫폼 규칙이 같은지 지킨다.
 * 한쪽 규칙을 바꾸면 양쪽 테스트의 기대값을 함께 바꿔야 한다.
 */
class PopTests {

    // 도우미

    private fun rng(seed: Int): () -> Double = World.mulberry32(seed.toUInt())

    /** 미션 안내를 건너뛴 상태 */
    private fun started(s: PopState): PopState = Pop.advance(s, Pop.INTRO_MS)

    private fun balloon(
        id: Int, value: Int, correct: Boolean, x: Double = 0.5, y: Double = 0.5,
        kind: PopBalloonKind = PopBalloonKind.NORMAL,
    ) = PopBalloon(id, value, kind, correct, x, y)

    /** 손으로 꾸민 배수 사냥 장면 — 새 풍선이 나오지 않게 queue 를 비운다 */
    private fun scene(table: Int, balloons: List<PopBalloon>): PopState =
        started(Pop.create(table, random = rng(1))).copy(queue = emptyList(), balloons = balloons)

    /** 손으로 꾸민 짝꿍 보스 장면 */
    private fun bossScene(target: Int, values: List<Int>): PopState =
        started(Pop.create(4, random = rng(1))).copy(
            wave = 3,
            mission = PopMission.PairBoss(target),
            boss = PopBoss(target, Pop.BOSS_HITS, 0.0),
            queue = emptyList(),
            balloons = values.mapIndexed { i, v ->
                balloon(
                    101 + i, v, correct = Pop.isPairMember(v, target),
                    x = Pop.SMALL_SLOTS[i].first, y = Pop.SMALL_SLOTS[i].second, kind = PopBalloonKind.SMALL,
                )
            },
        )

    private enum class Policy { PERFECT, SLOPPY, LAZY }

    private data class Run(
        val score: Int = 0, val wave: Int = 0, val hearts: Int = 0, val maxCombo: Int = 0, val pops: Int = 0,
        val wrongTaps: Int = 0, val bossesDefeated: Int = 0,
        val over: Boolean = false,
        val trialLeft: Int? = null,
        /** id 순서로 처음 나온 풍선 값 30개 */
        val values: List<Int> = emptyList(),
        val missions: List<PopMission> = emptyList(),
    )

    /**
     * 50ms 프레임으로 돌리는 자동 플레이어. 200ms 마다 한 번 누른다.
     * PERFECT: 지붕에 가장 가까운 정답 / 보스면 짝꿍, SLOPPY: 2초마다 오답 하나를 섞음, LAZY: 누르지 않음
     */
    private fun play(
        table: Int?, seed: Int, frames: Int, policy: Policy, trial: Int? = null,
        check: ((PopState) -> Unit)? = null,
    ): Pair<Run, PopState> {
        val random = rng(seed)
        var s = Pop.create(table, trial = trial, random = random)
        val values = mutableListOf<Int>()
        val missions = mutableListOf<PopMission>()
        var lastId = 0
        fun record() {
            for (b in s.balloons.sortedBy { it.id }) {
                if (b.id > lastId) {
                    values += b.value
                    lastId = b.id
                }
            }
            if (missions.lastOrNull() != s.mission) missions += s.mission
            check?.invoke(s)
        }
        record()
        for (f in 0 until frames) {
            s = Pop.advance(s, 50.0, random)
            record()
            if (s.phase != RunnerPhase.RUNNING) break
            if (policy == Policy.LAZY || f % 4 != 0) continue
            val boss = s.boss
            val wrong = s.balloons.firstOrNull { !it.correct }
            val targetId: Int? = if (policy == Policy.SLOPPY && f % 40 == 0 && wrong != null) {
                wrong.id
            } else if (boss != null) {
                val first = s.pending
                if (first != null) s.balloons.firstOrNull { it.value * first == boss.value }?.id
                else s.balloons.firstOrNull { it.correct }?.id
            } else {
                s.balloons
                    .filter { it.correct && it.y >= 0.35 }
                    .sortedWith(compareByDescending<PopBalloon> { it.y }.thenBy { it.id })
                    .firstOrNull()?.id
            }
            if (targetId != null) {
                s = Pop.tap(s, targetId, random)
                record()
                if (s.phase != RunnerPhase.RUNNING) break
            }
        }
        val run = Run(
            score = s.score, wave = s.wave, hearts = s.hearts, maxCombo = s.maxCombo, pops = s.pops,
            wrongTaps = s.wrongTaps, bossesDefeated = s.bossesDefeated,
            over = s.phase == RunnerPhase.OVER, trialLeft = s.trialLeft,
            values = values.take(30), missions = missions,
        )
        return run to s
    }

    // 난이도

    @Test fun difficultyCurve() {
        assertEquals(listOf(7000.0, 6000.0, 5500.0, 5000.0, 4500.0, 4000.0, 3500.0, 3500.0), (1..8).map { Pop.fallMs(it) })
        assertEquals(3500.0, Pop.fallMs(40), "하한 3.5초")
        assertEquals(listOf(3, 4, 5, 6, 6), (1..5).map { Pop.concurrent(it) })
        assertEquals(listOf(40, 50, 60, 60), (1..4).map { Pop.wrongPercent(it) })
        assertEquals(listOf(4, 6, 9, 9), (1..4).map { Pop.distractorCount(it) })
        assertEquals(listOf(false, false, true, false, false, true, false, false, true), (1..9).map { Pop.isBossWave(it) })
    }

    @Test fun comboMultiplier() {
        assertEquals(listOf(1, 1, 1, 2, 2, 3, 3, 3), listOf(0, 1, 4, 5, 9, 10, 11, 30).map { Pop.multiplier(it) })
    }

    @Test fun mixedWaveTablesFollowRoadmap() {
        assertEquals(listOf(2, 5, 3, 4, 6, 9, 7, 8, 2), (1..9).map { Pop.waveTable(it, null) })
        assertEquals(listOf(7, 7, 7, 7), (1..4).map { Pop.waveTable(it, 7) })
    }

    // 풍선 값

    @Test fun distractorPoolForSeven() {
        val pool = Pop.distractorPool(7)
        assertEquals(listOf(6, 8, 13, 15, 20, 22, 27, 29, 34, 36, 41, 43, 48, 50, 55, 57, 62, 64), pool.near)
        assertEquals(listOf(12, 16, 18, 24, 30, 32, 40, 54, 72), pool.neighbour)
        assertEquals(listOf(53, 65), pool.reversal)
    }

    @Test fun distractorPoolsNeverContainMultiples() {
        for (t in 2..9) {
            val pool = Pop.distractorPool(t)
            val all = pool.near + pool.neighbour + pool.reversal + pool.far
            assertEquals(all.size, all.toSet().size, "${t}단: 후보끼리 겹침")
            assertTrue(all.all { it in 1..81 && it % t != 0 }, "${t}단")
            assertFalse(pool.near.isEmpty() || pool.neighbour.isEmpty() || pool.far.isEmpty(), "${t}단")
        }
    }

    @Test fun spawnedBalloonsMatchMission() {
        for (t in listOf<Int?>(null) + (2..9).toList()) {
            for (seed in 1..12) {
                play(t, seed, 1600, Policy.SLOPPY) { s ->
                    // 보스 작은 풍선은 제곱 짝꿍(6·6)이 있어 같은 수가 둘일 수 있다
                    if (s.boss != null) return@play
                    val values = s.balloons.map { it.value }
                    assertEquals(values.size, values.toSet().size, "화면에 같은 수가 둘: $values")
                    for (b in s.balloons) {
                        if (b.correct) {
                            assertEquals(0, b.value % s.waveTable)
                            assertTrue(b.value / s.waveTable in 1..9)
                        } else {
                            assertTrue(b.value % s.waveTable != 0, "오답에 배수: ${b.value} / ${s.waveTable}단")
                            assertTrue(b.value in 1..81)
                        }
                    }
                }
            }
        }
    }

    @Test fun firstWaveUsesFarDistractors() {
        val far = Pop.distractorPool(7).far.toSet()
        play(7, 5, 400, Policy.PERFECT) { s ->
            if (s.wave != 1) return@play
            for (b in s.balloons) if (!b.correct) assertTrue(b.value in far, "${b.value}")
        }
    }

    // 짝꿍 보스

    @Test fun pairs() {
        assertEquals(listOf(PopPair(4, 9), PopPair(6, 6)), Pop.pairs(36))
        assertEquals(listOf(PopPair(3, 8), PopPair(4, 6)), Pop.pairs(24))
        assertEquals(listOf(PopPair(1, 8), PopPair(2, 4)), Pop.pairs(8))
        assertEquals(listOf(PopPair(9, 9)), Pop.pairs(81))
        assertEquals(emptyList(), Pop.pairs(11))
    }

    @Test fun bossAlwaysHasCompletablePair() {
        for (t in 2..9) {
            val random = rng(t * 31)
            repeat(40) {
                val target = Pop.bossTarget(t, random)
                assertFalse(Pop.pairs(target).isEmpty(), "보스 $target")
                val values = Pop.smallValues(target, random)
                assertEquals(Pop.SMALL_SLOTS.size, values.size)
                val hasPair = values.indices.any { i -> values.indices.any { j -> i != j && values[i] * values[j] == target } }
                assertTrue(hasPair, "보스 $target: $values")
            }
        }
    }

    @Test fun everyPairIsAcceptedInAnyOrder() {
        val values = listOf(3, 8, 4, 6, 5, 7)
        var s = bossScene(24, values)
        s = Pop.tap(s, 101, rng(2))          // 3
        assertEquals(3, s.pending)
        assertEquals(listOf<PopEvent>(PopEvent.BossPick(101, 3, 10)), s.events)
        assertEquals(6, s.balloons.size, "빈자리를 채운다")
        s = Pop.tap(s, 102, rng(2))          // 8
        assertEquals(2, s.boss?.hitsLeft)
        assertEquals(listOf<PopEvent>(PopEvent.BossHit(3, 8, 10, 2)), s.events)
        assertNull(s.pending)

        // 다른 짝꿍, 거꾸로 (6 → 4)
        var again = bossScene(24, values).copy(boss = s.boss)
        again = Pop.tap(again, 104, rng(3))  // 6
        again = Pop.tap(again, 103, rng(3))  // 4
        assertEquals(1, again.boss?.hitsLeft)

        // 마지막 짝꿍(8 → 3)으로 보스를 물리치면 다음 웨이브
        var last = bossScene(24, values).copy(boss = again.boss)
        last = Pop.tap(last, 102, rng(4))
        last = Pop.tap(last, 101, rng(4))
        assertNull(last.boss)
        assertEquals(1, last.bossesDefeated)
        assertEquals(4, last.wave)
        assertTrue(PopEvent.BossDefeated in last.events)
        assertTrue(PopEvent.WaveCleared(3) in last.events)
    }

    @Test fun squarePairNeedsTwoBalloons() {
        var s = bossScene(36, listOf(6, 1, 2, 3, 5, 7))
        s = Pop.tap(s, 101, rng(5))          // 6 — 짝 6 이 화면에 없으니 빈자리를 6 으로
        assertEquals(6, s.pending)
        assertEquals(6, s.balloons[0].value)
        s = Pop.tap(s, s.balloons[0].id, rng(5))
        assertEquals(2, s.boss?.hitsLeft)
    }

    @Test fun bossWrongTaps() {
        var s = bossScene(24, listOf(3, 8, 4, 6, 5, 7))
        s = Pop.tap(s, 105, rng(6))          // 5 — 짝꿍에 안 듦
        assertEquals(listOf<PopEvent>(PopEvent.WrongTap(105, 5)), s.events)
        assertEquals(Pop.RELOAD_MS, s.reloadMs)
        assertEquals(6, s.balloons.size, "오답 풍선은 남는다")
        s = Pop.advance(s, Pop.RELOAD_MS)
        s = Pop.tap(s, 103, rng(6))          // 4
        s = Pop.tap(s, 101, rng(6))          // 3 — 4 × 3 ≠ 24
        assertEquals(listOf<PopEvent>(PopEvent.WrongTap(101, 3)), s.events)
        assertEquals(4, s.pending, "첫 짝꿍은 그대로")
        assertEquals(0, s.combo)
    }

    @Test fun bossEscapeCostsHeartAndEndsWave() {
        var s = bossScene(24, listOf(3, 8, 4, 6, 5, 7))
        s = Pop.advance(s, Pop.BOSS_FALL_MS)
        assertEquals(Pop.MAX_HEARTS - 1, s.hearts)
        assertEquals(PopEvent.BossEscaped, s.events.first())
        assertTrue(PopEvent.WaveCleared(3) in s.events)
        assertEquals(4, s.wave)
    }

    // 판정

    @Test fun comboScoring() {
        var s = scene(7, (0 until 5).map { balloon(10 + it, 7 * (it + 1), correct = true, x = it * 0.2 + 0.1) })
        val points = mutableListOf<Int>()
        for (id in 10 until 15) {
            s = Pop.tap(s, id)
            (s.events.first() as? PopEvent.Popped)?.let { points += it.points }
        }
        assertEquals(listOf(10, 10, 10, 10, 20), points)
        assertEquals(60, s.score)
        assertEquals(5, s.combo)

        val tenth = Pop.tap(scene(7, listOf(balloon(1, 14, correct = true))).copy(combo = 9), 1)
        assertEquals(listOf<PopEvent>(PopEvent.Popped(1, 14, 30, false)), tenth.events)
    }

    @Test fun heartLostOnlyWhenCorrectBalloonReachesRoof() {
        var s = scene(
            7,
            listOf(
                balloon(50, 14, correct = true, x = 0.1, y = 0.999),
                balloon(51, 15, correct = false, x = 0.3, y = 0.999),
                balloon(52, 21, correct = true, x = 0.5, y = 0.2),
            ),
        ).copy(combo = 3)
        s = Pop.advance(s, 100.0)
        assertEquals(Pop.MAX_HEARTS - 1, s.hearts)
        assertEquals(listOf(PopEvent.Missed(50, 14), PopEvent.Passed(51, 15)), s.events)
        assertEquals(1, s.handled)
        assertEquals(0, s.combo)
        assertEquals(listOf(52), s.balloons.map { it.id })

        // 오답 탭은 하트를 깎지 않는다 — 콤보 초기화 + 재장전
        var w = scene(7, listOf(balloon(60, 15, correct = false), balloon(61, 21, correct = true, x = 0.1))).copy(combo = 4)
        w = Pop.tap(w, 60)
        assertEquals(Pop.MAX_HEARTS, w.hearts)
        assertEquals(0, w.combo)
        assertEquals(800.0, w.reloadMs)
        assertEquals(listOf<PopEvent>(PopEvent.WrongTap(60, 15)), w.events)
        w = Pop.tap(w, 61)
        assertEquals(listOf<PopEvent>(PopEvent.Reloading), w.events, "재장전 중 탭은 무시")
        assertEquals(0, w.score)
        w = Pop.advance(w, 800.0)
        w = Pop.tap(w, 61)
        assertEquals(10, w.score)
    }

    @Test fun gameOverWhenHeartsRunOut() {
        var s = scene(3, listOf(balloon(1, 6, correct = true, y = 0.999), balloon(2, 9, correct = true, x = 0.1, y = 0.999)))
            .copy(hearts = 1)
        s = Pop.advance(s, 50.0)
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(0, s.hearts, "0 아래로 내려가지 않는다")
        assertEquals(1, s.events.count { it == PopEvent.GameOver })
    }

    @Test fun goldenBalloonSlowsTime() {
        var s = scene(
            5,
            listOf(
                balloon(1, 10, correct = true, kind = PopBalloonKind.GOLDEN),
                balloon(2, 15, correct = true, x = 0.1, y = 0.1),
            ),
        ).copy(wave = 2)
        s = Pop.tap(s, 1)
        assertEquals(listOf(PopEvent.Popped(1, 10, 10, true), PopEvent.SlowMoStarted), s.events)
        assertEquals(Pop.SLOW_DURATION_MS, s.slowMs)
        s = Pop.advance(s, 100.0)
        assertEquals(0.1 + 50.0 / 6000.0, s.balloons[0].y, 1e-12, "느려짐 동안 절반 속도")
        s = Pop.advance(s, Pop.SLOW_DURATION_MS)
        assertEquals(0.0, s.slowMs)
    }

    @Test fun goldenOnlyFromWaveTwo() {
        val random = rng(77)
        val first = Pop.create(6, random = random)
        assertEquals(6 + 4, first.queue.size)
        assertFalse(PopSlot.GOLDEN in first.queue)

        // 웨이브 1 마지막 정답을 터뜨려 웨이브 2 로 — 정답 6(그중 황금 1) + 오답 6
        var s = started(first).copy(handled = Pop.WAVE_GOAL - 1, queue = emptyList(), balloons = listOf(balloon(900, 6, correct = true)))
        s = Pop.tap(s, 900, random)
        assertEquals(2, s.wave)
        assertEquals(12, s.queue.size)
        assertEquals(1, s.queue.count { it == PopSlot.GOLDEN })
    }

    // 체험

    @Test fun trialEndsAfterFirstWave() {
        val (run, s) = play(3, 11, 3000, Policy.PERFECT, trial = PremiumConfig.POP_TRIAL_WAVES)
        assertEquals(1, PremiumConfig.POP_TRIAL_WAVES)
        assertTrue(run.over)
        assertEquals(1, run.wave)
        assertEquals(0, run.trialLeft)
        assertEquals(listOf(PopEvent.WaveCleared(1), PopEvent.TrialEnded), s.events.takeLast(2))
        assertEquals(Pop.WAVE_GOAL, run.pops)

        val (free, _) = play(3, 11, 1200, Policy.PERFECT)
        assertNull(free.trialLeft)
        assertTrue(free.wave >= 2, "체험이 아니면 다음 웨이브로")
    }

    @Test fun heartsRunningOutDuringTrialIsGameOver() {
        val (run, s) = play(3, 11, 3000, Policy.LAZY, trial = 1)
        assertTrue(run.over)
        assertEquals(0, run.hearts)
        assertEquals(1, run.trialLeft, "체험을 다 하기 전에 하트로 끝남")
        assertEquals(PopEvent.GameOver, s.events.last())
    }

    // 탭 판정

    @Test fun tapResolution() {
        val s = scene(
            7,
            listOf(
                balloon(1, 14, correct = true, x = 0.5, y = 0.5),
                balloon(2, 21, correct = true, x = 0.5, y = 0.6),
                balloon(3, 28, correct = true, x = 0.1, y = 0.3),
                balloon(4, 35, correct = true, x = 0.2, y = 0.3),
            ),
        )
        assertEquals(2, Pop.target(s, 0.5, 0.55, 1.0), "겹치면 지붕에 가까운 풍선")
        assertEquals(1, Pop.target(s, 0.5, 0.51, 1.0), "확실히 가까우면 가까운 풍선")
        assertEquals(3, Pop.target(s, 0.15, 0.3, 1.0), "같은 높이면 id 작은 풍선")
        assertNull(Pop.target(s, 0.9, 0.1, 1.0), "반경 밖")
        assertEquals(1, Pop.target(s, 0.5, 0.4, 1.0))
        assertNull(Pop.target(s, 0.5, 0.4, 2.0), "세로 차이는 aspect 만큼 커진다")
        assertNull(Pop.target(s, Double.NaN, 0.5, 1.0))

        val tapped = Pop.tap(s, 0.5, 0.55, 1.0)
        assertEquals(listOf<PopEvent>(PopEvent.Popped(2, 21, 10, false)), tapped.events)
        assertEquals(emptyList(), Pop.tap(s, 0.9, 0.1, 1.0).events)
    }

    @Test fun bossIsNotATapTarget() {
        val s = bossScene(24, listOf(3, 8, 4, 6, 5, 7))
        assertNull(Pop.target(s, Pop.BOSS_X, 0.0, 1.4))
        assertEquals(102, Pop.target(s, 0.27, 0.58, 1.4))
    }

    @Test fun introBlocksTapsAndMovement() {
        var s = Pop.create(4, random = rng(3))
        assertEquals(Pop.INTRO_MS, s.introMs)
        assertEquals(PopMission.Multiples(4), s.mission)
        s = Pop.advance(s, Pop.INTRO_MS - 1)
        assertTrue(s.balloons.isEmpty())
        s = Pop.advance(s, 1.0)
        assertEquals(listOf<PopEvent>(PopEvent.WaveStarted(1)), s.events)
        s = Pop.advance(s, 16.0, rng(3))
        assertEquals(1, s.balloons.size, "안내가 끝나면 첫 풍선")
    }

    // 기록

    @Test fun bestKeys() {
        assertEquals("all", Pop.bestKey(null))
        assertEquals("7", Pop.bestKey(7))
        assertEquals(mapOf("all" to 120, "7" to 80), Pop.sanitizeBests(mapOf("all" to 120, "7" to 80, "1" to 5, "x" to 9, "3" to -1)))
        assertEquals("gugu.pop.best.v1", Persistence.POP_BEST_KEY)
    }

    // 골든 벡터 (iOS PopTests.swift 와 같은 값)

    @Test fun goldenTable7Perfect() {
        val (run, _) = play(7, 42, 2400, Policy.PERFECT)
        assertEquals(
            Run(
                score = 1580, wave = 10, hearts = 3, maxCombo = 57, pops = 57, wrongTaps = 0, bossesDefeated = 3,
                over = false, trialLeft = null,
                values = listOf(21, 68, 14, 49, 7, 56, 58, 23, 14, 51, 42, 21, 65, 22, 40, 53, 28, 8, 28, 72, 49, 21, 3, 4, 7, 2, 7, 8, 5, 2),
                missions = listOf(
                    PopMission.Multiples(7), PopMission.PairBoss(49), PopMission.Multiples(7), PopMission.PairBoss(49),
                    PopMission.Multiples(7), PopMission.PairBoss(28), PopMission.Multiples(7),
                ),
            ),
            run,
        )
    }

    @Test fun goldenMixedSloppy() {
        val (run, _) = play(null, 2026, 2400, Policy.SLOPPY)
        assertEquals(
            Run(
                score = 650, wave = 10, hearts = 3, maxCombo = 9, pops = 54, wrongTaps = 46, bossesDefeated = 3,
                over = false, trialLeft = null,
                values = listOf(25, 47, 4, 49, 12, 4, 69, 18, 6, 10, 35, 15, 51, 18, 10, 54, 30, 42, 30, 52, 6, 45, 9, 4, 6, 2, 7, 1, 8, 8),
                missions = listOf(
                    PopMission.Multiples(2), PopMission.Multiples(5), PopMission.PairBoss(9), PopMission.Multiples(4),
                    PopMission.Multiples(6), PopMission.PairBoss(54), PopMission.Multiples(7), PopMission.Multiples(8),
                    PopMission.PairBoss(6), PopMission.Multiples(5),
                ),
            ),
            run,
        )
    }

    @Test fun goldenTable9Lazy() {
        val (run, _) = play(9, 9, 2400, Policy.LAZY)
        assertEquals(
            Run(
                score = 0, wave = 1, hearts = 0, maxCombo = 0, pops = 0, wrongTaps = 0, bossesDefeated = 0,
                over = true, trialLeft = null,
                values = listOf(59, 27, 36, 52, 54, 72, 81),
                missions = listOf(PopMission.Multiples(9)),
            ),
            run,
        )
    }
}
