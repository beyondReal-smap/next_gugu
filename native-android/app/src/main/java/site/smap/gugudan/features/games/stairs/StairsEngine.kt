package site.smap.gugudan.features.games.stairs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import site.smap.gugudan.R
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.CommitResult
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.Strings
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.ActionReject
import site.smap.gugudan.core.games.AttemptRecord
import site.smap.gugudan.core.games.MissionText
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RisingCloud
import site.smap.gugudan.core.games.RoundPhase
import site.smap.gugudan.core.games.SkyZone
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.core.games.StarRules
import site.smap.gugudan.features.games.shared.BannerState
import site.smap.gugudan.features.games.shared.GameCommit
import site.smap.gugudan.features.games.shared.GameResultContent
import site.smap.gugudan.features.games.shared.GameResultLayout
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.HudBadgeState
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.features.games.shared.RewardHudState
import site.smap.gugudan.features.games.shared.RoundLearningLog
import site.smap.gugudan.features.games.shared.SparklesState
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// 구구 계단 진행 (Unity 시험판 StairsGame.cs 이식, iOS StairsEngine.swift 대응) — 규칙(StairsSession)·탭 입력·그림 상태·보상 저장을 잇는다.
// 시간 제한 대신 아래에서 먹구름이 차오르고, 잡히면 판이 끝난다(위 배지 줄의 구름 막대 = 구름까지 거리).
// 한 프레임 순서: ① 입력 → 점프·채점  ② 시간 진행  ③ 그림 상태 (잡히기 직전 프레임에 누른 정답은 한 칸을 벌어 살아남음)
// 쉬는 창이 열린 동안에는 프레임 루프를 멈춰 입력·먹구름·그림이 모두 멈춘다. 별·최고 기록은 결과(또는 체험 끝)를 띄울 때 한 번 저장.
// 무료 체험(trial)은 맞힌 문제 수 — 그만큼 오르면 판을 끝내고 체험 종료 패널로(받은 별·기록은 저장).
// 프레임 루프는 화면(StairsScreen)이 wantsFrames 동안 돌리며 advance 를 부른다. 테스트는 advance 를 직접 부른다.
// 화면 구성이 바뀌는 값(단계·쉬는 창·결과 등)은 Compose 상태로, 프레임마다 바뀌는 그림 값은 frame 하나로 다시 그리게 한다.
// 학습 기록(XP·오늘 목표·틀린 문제·서버 원장): 결과(또는 체험 끝)를 띄울 때 완료로, 판 중간에 나가면(abandon) 부분으로 한 번.
// 계단은 판 중에 단이 바뀌므로(보물 → 다음 단, 무작위는 문제마다) 단을 정하지 않고(table null) 넘긴다.
// 문제 순서(순서대로·무작위)는 시작 전 패널에서 바꾸며, 바꾸면 뒤에 비치는 첫 문제도 새 순서로 다시 만든다. [다시 하기]는 같은 순서.
// 무료 체험 판이라도 체험 수를 채우기 전에 잡히면 체험 끝(trialFinished)이 아니다 — 화면은 일반 결과 창([다시 하기])을 띄운다.

class StairsEngine(
    /** 홈(Default)·섞어서(Table(null)) = 2~9단 무작위, Table(n) = n단부터(순서대로일 때만) */
    start: MinigameStart,
    /** 처음 보여 줄 문제 순서 — 화면은 GameOrderStore 의 마지막 선택 */
    order: ProblemOrder,
    private val rewards: PlayRewardsStore,
    private val strings: Strings,
    /** 학습 기록 — 화면은 GameStore.commitSession + 서버 적재, 테스트는 메모리 상태에 적용 */
    private val commit: GameCommit,
    /** 설정 「곱하는 수」 */
    private val topStep: Int = Problems.maxB,
    private val random: () -> Double = { Random.nextDouble() },
    /** DEBUG debug.gugu.stairs_autoplay=N — N칸을 정답 칸 탭으로 자동으로 오른다(스크린숏용, 시작도 자동) */
    autoplay: Int = 0,
    /** 동작 줄이기(애니메이터 길이 배율 0) — 그림의 반복·장식 움직임을 끈다(숨쉬기·점프 늘어남·기울기) */
    reduceMotion: Boolean = false,
) {
    enum class Phase {
        /** 시작 전 패널 */
        READY,
        /** 판 진행(끝난 뒤 결과 창까지 포함) */
        PLAYING,
    }

    /** 위쪽 배지 줄 칸(설계 px, 가운데 x·폭) */
    data class HudSlot(val x: Float, val width: Float)

    // MARK: 화면 구성 상태 (Compose 가 다시 구성)

    var phase by mutableStateOf(Phase.READY)
        private set
    /** 문제 순서 — 시작 전 패널에서만 바뀐다(setOrder). 세터 이름이 setOrder 와 겹치지 않게 상태는 따로 둔다 */
    private var orderState by mutableStateOf(order)
    val order: ProblemOrder get() = orderState
    /** 쉬는 창이 열려 있는지 */
    var paused by mutableStateOf(false)
        private set
    /** 앱이 앞에 있는지(백그라운드면 루프를 멈춤) */
    var sceneActive by mutableStateOf(true)
        private set
    /** 이번 판의 체험 문제 수(null = 제한 없음) */
    var trial by mutableStateOf<Int?>(null)
        private set
    /** 판이 끝났는지(먹구름에 잡힘·체험 끝) */
    var roundOver by mutableStateOf(false)
        private set
    /** 결과 창 내용 — 결과(또는 체험 종료 패널)를 띄우는 순간 한 번 만들고 그때 별·기록을 저장한다 */
    var result by mutableStateOf<GameResultContent?>(null)
        private set
    /** [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초) */
    var restartArmed by mutableStateOf(false)
        private set
    /** 남은 체험 문제 수(지금 문제 포함, 체험이 아니면 null) */
    var trialLeft by mutableStateOf<Int?>(null)
        private set
    var viewport by mutableStateOf<GameViewport?>(null)
        private set
    /** 프레임 번호 — 그림(Canvas)이 이 값을 읽어 프레임마다 다시 그린다 */
    var frame by mutableLongStateOf(0L)
        private set

    // MARK: 그림 상태 (frame 으로 다시 그림)

    var session: StairsSession
        private set
    var round = 0
        private set
    /** 체험 문제 수만큼 올라 판이 끝났는지 */
    var trialFinished = false
        private set
    val stage = StairsStage(reduceMotion)
    val sparkles = SparklesState()
    val reward = RewardHudState()
    val stairsBadge = HudBadgeState()
    val cloudBadge = HudBadgeState()
    val chestBadge = HudBadgeState()
    val altimeter = AltimeterState()
    val banner = BannerState()
    /** 이번 판을 시작할 때의 최고 기록(깃발 높이) — 판 중에는 바뀌지 않음 */
    var bestAtStart = 0
        private set
    /** 흐른 시간(초) — 반짝임·일렁임용, 쉬는 동안 멈춤 */
    var time = 0.0
        private set
    /** 정답 순간 문제 글자가 통통(1 → 0) */
    var problemPulse = 0.0
        private set
    var lastWrong: AttemptRecord? = null
        private set
    /** 판이 끝난 뒤 흐른 시간 */
    var endedFor = 0.0
        private set
    var resultShownFor = 0.0
        private set
    /** 이번 판의 학습 기록 — 결과 창의 "+N XP"·레벨 업·업적 알림이 읽는다 */
    val learning = RoundLearningLog(GameMode.STAIRS, table = null)

    /** 단 시트에서 고른 시작 단(null = 2~9 무작위) — 무작위 순서에서는 쓰지 않는다 */
    private val firstTable: Int? = when (start) {
        MinigameStart.Default -> null
        is MinigameStart.Table -> start.table
    }
    private val pendingTaps = mutableListOf<Offset>()
    private var zone = SkyZone.MEADOW
    private var recordShown = false
    private var caughtShown = false
    private var shownQuestionId = -1
    private var autoplayLeft = autoplay
    private var autoplayWait = 0.6

    init {
        require(autoplay >= 0) { "autoplay 는 0 이상: $autoplay" }
        session = makeSession(order)
        resetRound()
    }

    /** 무작위 순서는 고른 시작 단을 쓰지 않는다(단·곱하는 수 둘 다 무작위) */
    private fun makeSession(order: ProblemOrder): StairsSession =
        StairsSession(topStep, order, if (order == ProblemOrder.SEQUENTIAL) firstTable else null, random)

    // MARK: 화면에서 읽는 값

    val pauseButtonVisible: Boolean get() = phase == Phase.PLAYING && !roundOver
    /** 프레임 루프를 돌릴지 — 시작 전·쉬는 동안·백그라운드에는 멈춤 */
    val wantsFrames: Boolean get() = phase == Phase.PLAYING && !paused && sceneActive
    /** "잡혔어요!" — 잡힌 뒤 결과가 뜨기 전까지 */
    val caughtTextVisible: Boolean get() = session.caught && result == null
    /** 첫 점프 전 안내 — 시작 후 첫 탭 전에는 먹구름도 멈춰 있음 */
    val hintVisible: Boolean get() = phase == Phase.PLAYING && session.clock.phase == RoundPhase.WAITING
    val accessory: Accessory? get() = stage.accessory

    /** 문제 판 윗줄 — 순서대로 "7단 · 3번째 계단", 무작위는 단이 문제마다 바뀌어 "3번째 계단"(보물까지 몇 번째 칸인지)만 */
    val tableLabel: String
        get() = when (order) {
            ProblemOrder.SEQUENTIAL -> {
                val p = session.current.problem
                strings.get(R.string.stairs_table_step, p.a, p.b)
            }
            ProblemOrder.RANDOM -> strings.get(R.string.stairs_step, session.step)
        }

    /** 문제 판 글자 — 오답 공개 중이면 정답 식 */
    val problemText: String
        get() {
            val wrong = lastWrong
            if (session.clock.isRevealing && wrong != null) {
                return "${wrong.problem.a} × ${wrong.problem.b} = ${wrong.problem.a * wrong.problem.b}"
            }
            val p = session.current.problem
            return "${p.a} × ${p.b}"
        }

    val problemRevealing: Boolean get() = session.clock.isRevealing && lastWrong != null

    /** 학습 기록(commitSession) 연결용 — 채점 기록(문제 = 단 × 칸, given = 고른 발판 숫자) */
    val answerRecords: List<AnswerRecord> get() = session.answerRecords
    /** 판 길이(ms) — 첫 점프부터 잡힐(또는 체험이 끝날) 때까지 */
    val roundDurationMs: Int get() = (session.clock.elapsed * 1000).roundToInt()

    // MARK: 조작

    /** 화면 크기·안전 영역 — 레인 경계·코인 출발점·배지 위치 계산에 쓴다 */
    fun updateViewport(next: GameViewport) {
        if (viewport == next) return
        viewport = next
        frame += 1
        if (autoplayLeft > 0 && phase == Phase.READY) start(trial = null)
    }

    /** 시작 전 패널의 [시작]. trial: 무료 체험 문제 수(이용권이 있으면 null) */
    fun start(trial: Int?) {
        check(phase == Phase.READY) { "이미 시작한 판: $phase" }
        checkNotNull(viewport) { "화면 크기를 먼저 알려야 함(updateViewport)" }
        applyTrial(trial)
        phase = Phase.PLAYING
        Sound.tap()
    }

    /** 시작 전 패널의 [순서대로]·[무작위] — 뒤에 비치는 첫 문제도 새 순서로 다시 만든다(새 세션). 같은 순서면 그대로 */
    fun setOrder(next: ProblemOrder) {
        check(phase == Phase.READY) { "문제 순서는 시작 전에만 바꿈: $phase" }
        if (next == order) return
        orderState = next
        session = makeSession(next)
        resetRound()
    }

    /** 결과 창 [다시 하기] — 같은 순서·같은 시작 단으로 새 판. 쌓인 탭은 버린다(다시 하기를 누른 탭이 새 판 첫 입력으로 새지 않게) */
    fun restart(trial: Int?) {
        // 결과 창이 없으면(이미 새 판을 연 뒤 연타가 한 번 더 온 것) 무시
        if (!restartArmed) return
        session = makeSession(order)
        resetRound()
        applyTrial(trial)
        Sound.tap()
    }

    /** 손가락이 닿은 곳(화면 px). 다음 프레임에 처리한다 */
    fun enqueueTap(point: Offset) {
        if (phase != Phase.PLAYING || paused || result != null) return
        pendingTaps.add(point)
    }

    /** [⏸]·뒤로 가기 → 쉬는 창. 판이 끝난 뒤에는 열지 않음(결과로 넘어가는 1초를 멈추지 않게) */
    fun openPause() {
        if (!pauseButtonVisible || paused) return
        paused = true
        pendingTaps.clear()
    }

    /** [계속 하기] — 쌓인 탭은 버린다([계속 하기]를 누른 탭이 같은 프레임에 점프로 새지 않게) */
    fun resume() {
        if (!paused) return
        paused = false
        pendingTaps.clear()
    }

    /** 앱이 백그라운드로 가면: 진행 중이면 쉬는 창, 그 밖에는 루프만 멈춤 */
    fun sceneBecameInactive() {
        if (pauseButtonVisible && !paused) openPause()
        sceneActive = false
    }

    /** 앱으로 돌아오면: 쉬는 창이 아니면(끝난 뒤 결과 대기·결과 창) 루프가 다시 돈다(wantsFrames) */
    fun sceneBecameActive() {
        sceneActive = true
    }

    /**
     * 판 중간에 나감([메뉴로 나가기]·뒤로 가기로 닫기 등) — 아직 기록하지 않은 판이면 푼 답만 기록한다.
     * 판이 끝났으면(잡힘·체험 끝 뒤 결과를 띄우기 전) 완료로, 아니면 부분으로. 결과를 띄웠으면 이미 기록함(두 번 불러도 한 번)
     */
    fun abandon() {
        if (phase != Phase.PLAYING) return
        closeLearning(partial = !isRoundOver())
    }

    /**
     * Android 뒤로 가기. true = 게임이 받았음(화면에 남음), false = 화면이 나가야 함.
     * 시작 전 패널·결과 창 → 나가기 / 진행 중 → 쉬는 창 / 쉬는 창 → 그대로(나가기는 창의 [메뉴로 나가기]로만 —
     * 연타로 판을 잃지 않게) / 잡힌 뒤 결과를 기다리는 1초 → 그대로
     */
    fun pressBack(): Boolean {
        if (phase == Phase.READY || result != null) return false
        if (paused) return true
        if (pauseButtonVisible) {
            openPause()
            return true
        }
        return true
    }

    // MARK: 프레임

    /** 한 프레임. 루프가 부르고, 테스트는 직접 부른다. 쉬는 동안·시작 전에는 아무것도 하지 않는다 */
    fun advance(rawDt: Double) {
        val viewport = viewport ?: return
        if (phase != Phase.PLAYING || paused) return
        require(rawDt >= 0) { "dt 는 0 이상이어야 함: $rawDt" }
        val dt = min(rawDt, MAX_FRAME_SEC)
        time += dt
        val camera = StairsCamera(viewport.size, stage.cameraY)

        autoplay(dt, camera, viewport)

        // ① 입력 — [⏸] 위 탭은 빼기(화면 x 로 칸을 고르므로 왼쪽 칸 점프로 샘)
        val taps = pendingTaps.toList()
        pendingTaps.clear()
        if (!isRoundOver()) {
            val pauseFrame = PauseLayout.buttonFrame(viewport)
            for (point in taps) {
                if (isRoundOver()) break
                if (pauseFrame.contains(point)) continue
                tap(camera.lane(point.x), camera, viewport)
            }
        }

        // ② 시간
        if (!isRoundOver()) {
            session.tick(dt)
            syncRow()   // 오답 공개가 끝나 같은 칸을 다시 냈으면 발판을 다시 깐다
        }
        roundOver = isRoundOver()
        if (roundOver) {
            if (result == null) {
                endedFor += dt
                if (endedFor >= RESULT_DELAY_SEC) showResult()
            } else {
                resultShownFor += dt
                restartArmed = resultShownFor >= GameResultLayout.ARM_DELAY_SEC
            }
        }

        // ③ 그림 상태
        render(dt, viewport)
        trialLeft = trial?.let { max(0, it - session.climbed) }
        frame += 1
    }

    private fun isRoundOver(): Boolean = session.caught || trialFinished

    // MARK: 점프

    private fun tap(lane: Int, camera: StairsCamera, viewport: GameViewport) {
        val table = session.table
        val starsBefore = session.stars
        val q = session.current
        val jump = session.jump(lane)
        val attempt = jump.attempt
        if (attempt == null) {
            if (jump.reject == ActionReject.REVEALING) Sound.tap()   // 정답 공개 중 — "아직이야" 짧은 소리
            return
        }

        if (attempt.correct) {
            stage.land(lane)
            Sound.correct()
            Haptics.impactLight()
            problemPulse = 1.0
            // 밟은 발판 윗면에서 별이 터지고, 받은 별 코인이 위쪽 별 배지로 날아감
            val topX = StairsStage.laneX(lane)
            val topY = StairsStage.platformY(stage.standingHeight) + StairsStage.PLATFORM_HEIGHT * 0.5
            val big = StarRules.isComboMilestone(session.streak) || jump.clearedTable
            sparkles.burst(topX, topY, big)
            reward.earn(
                camera.point(topX, topY), coinBadgeIcon(viewport),
                earned = session.stars - starsBefore, total = session.stars, unit = viewport.unit,
            )
            if (jump.clearedTable) {
                stage.placeTreasure()
                when (order) {
                    ProblemOrder.SEQUENTIAL -> banner.show(strings.get(R.string.stairs_table_cleared, table))
                    // 단이 문제마다 바뀌어 칸 기준
                    ProblemOrder.RANDOM -> banner.show(strings.get(R.string.stairs_steps_cleared, topStep))
                }
            }
            val reached = SkyZone.at(session.climbed)
            if (reached != zone) {
                zone = reached
                banner.show(strings.get(R.string.stairs_zone_arrived, zoneName(reached, strings)))
            }
            // 지난 최고 기록을 처음 넘는 순간: 깃발이 튀고 큰 반짝이 + 알림(판마다 한 번)
            if (!recordShown && bestAtStart > 0 && session.climbed > bestAtStart) {
                recordShown = true
                stage.celebrateFlag()
                val (fx, fy) = StairsScene.flagCenter(camera, bestAtStart)
                sparkles.burst(fx, fy, big = true)
                banner.show(strings.get(R.string.new_record_2))
            }
            val limit = trial
            if (limit != null && session.climbed >= limit) trialFinished = true
        } else {
            lastWrong = attempt
            stage.miss(lane, q.correctLane)
            Sound.wrong()
            Haptics.error()
        }
        reward.setStreak(session.streak)
        syncRow()
    }

    private fun syncRow() {
        val q = session.current
        if (q.id == shownQuestionId) return
        stage.showNextRow(q, color(q.problem.a))
        shownQuestionId = q.id
    }

    // MARK: 판 시작·끝

    private fun applyTrial(limit: Int?) {
        if (limit != null) require(limit > 0) { "체험 문제 수는 1 이상: $limit" }
        trial = limit
        trialLeft = limit?.let { max(0, it - session.climbed) }
    }

    private fun resetRound() {
        round += 1
        pendingTaps.clear()
        bestAtStart = rewards.best(PlayGame.STAIRS)
        stage.reset(strings.get(R.string.stairs_start_label), rewards.equipped, bestAtStart)
        sparkles.reset()
        reward.reset()
        stairsBadge.reset(0)
        chestBadge.reset(0)
        cloudBadge.reset(null)
        cloudBadge.setBar(session.cloud.gap(standing = 0) / RisingCloud.MAX_GAP, danger = false)
        altimeter.reset(bestAtStart)
        banner.clear()
        zone = SkyZone.MEADOW
        recordShown = false
        caughtShown = false
        lastWrong = null
        shownQuestionId = -1
        problemPulse = 0.0
        endedFor = 0.0
        result = null
        resultShownFor = 0.0
        restartArmed = false
        trialFinished = false
        roundOver = false
        learning.reset()
        syncRow()
        frame += 1
    }

    /** 결과를 띄우는 순간 한 번: 받은 별을 쌓고 최고 기록·오늘의 미션을 갱신(체험 끝도 같음) */
    private fun showResult() {
        val record = rewards.recordRound(PlayGame.STAIRS, score = session.climbed, stars = session.stars, bestStreak = session.bestStreak)
        val done = closeLearning(partial = false)
        val miss = session.missToShow
        result = GameResultContent(
            note = GameResultContent.note(record, strings),
            missionNote = MissionText.resultNote(strings, record.missionsCompleted, record.allMissionsDone),
            mainLabel = strings.get(R.string.game_result_steps_climbed),
            mainValue = session.climbed,
            subLabel = strings.get(R.string.game_result_treasures),
            subValue = session.treasures,
            miss = miss?.problem,
            missGiven = miss?.let { strings.get(R.string.game_result_picked, it.given) },
            stars = session.stars,
            starTotal = record.starTotal,
            xpEarned = done?.xpEarned,
        )
        resultShownFor = 0.0
        restartArmed = false
        Sound.complete()
    }

    /** 학습 기록을 한 번 넘긴다(판마다 한 번 — RoundLearningLog 가 막음). 답이 없으면 null */
    private fun closeLearning(partial: Boolean): CommitResult? =
        learning.close(session.answerRecords, session.bestStreak, roundDurationMs, partial, commit)

    // MARK: 그림 상태

    private fun render(dt: Double, viewport: GameViewport) {
        if (session.caught && !caughtShown) {
            caughtShown = true
            stage.caught()
            Sound.wrong()
            Haptics.error()
        }
        if (session.caught) banner.clear()   // 잡힌 뒤에는 알림을 지움("잡혔어요!" 만)

        stage.update(dt, session.caught)
        sparkles.update(dt)
        banner.update(dt)
        problemPulse = max(0.0, problemPulse - dt * 5)

        // 배지 줄: 오른 계단 · 먹구름까지 거리(가득 = MAX_GAP 칸, 1칸 미만이면 빨강) · 보물 · 별(코인이 닿을 때마다)
        val gap = session.cloud.gap(session.climbed)
        stairsBadge.setValue(session.climbed)
        cloudBadge.setBar(gap / RisingCloud.MAX_GAP, danger = gap < 1)
        chestBadge.setValue(session.treasures)
        stairsBadge.update(dt)
        cloudBadge.update(dt)
        chestBadge.update(dt)
        for (index in reward.update(dt, coinBadgeIcon(viewport))) Sound.coin(index)
        // 불꽃은 캐릭터 옆을 따라다님(오른쪽 발판에서는 화면 밖으로 나가지 않게 왼쪽에)
        val camera = StairsCamera(viewport.size, stage.cameraY)
        val side = if (stage.standingLane == StairsSession.LANES - 1) -1.0 else 1.0
        reward.followCombo(camera.point(stage.characterX + COMBO_OFFSET_X * side, stage.characterY + COMBO_OFFSET_Y))
        altimeter.update(dt, session.climbed, bestAtStart)
    }

    /** 별 배지 아이콘 가운데(화면 px) — 코인이 날아가 닿는 곳 */
    private fun coinBadgeIcon(viewport: GameViewport): Offset =
        viewport.safeTop(HUD_COIN.x - HUD_COIN.width / 2 + HudBadgeState.HEIGHT / 2, PauseLayout.HUD_ROW_Y)

    // MARK: DEBUG 자동 정답

    /** 정답 칸 가운데를 실제 탭 경로(큐)로 누른다 */
    private fun autoplay(dt: Double, camera: StairsCamera, viewport: GameViewport) {
        if (autoplayLeft <= 0 || isRoundOver() || session.clock.isRevealing) return
        autoplayWait -= dt
        if (autoplayWait > 0) return
        autoplayWait = 0.45
        autoplayLeft -= 1
        pendingTaps.add(Offset(camera.laneCenterX(session.current.correctLane), viewport.size.height * 0.6f))
    }

    companion object {
        /** 판이 끝나고 결과(또는 체험 종료 패널)를 띄우기까지(초) — "잡혔어요!" 를 보여 주는 시간 */
        const val RESULT_DELAY_SEC = 1.0
        /**
         * 캐릭터 발에서 불꽃 배지 중심까지(유닛) — 머리 옆 높이, 몸·장신구에 닿지 않게.
         * 배지 위 끝이 다음 줄 발판(답 숫자) 아래에 오게: 머리 바로 위는 다음 줄 발판이라 그 위에 두면 답을 가린다
         */
        const val COMBO_OFFSET_X = 1.0
        const val COMBO_OFFSET_Y = 1.05
        /** 불꽃 배지 크기 배율(공용 배지 대비) — 구구(1.3유닛)보다 작게. 몬스터는 원래 크기 */
        const val COMBO_SCALE = 0.6f
        /** 프레임 하나에 흘릴 최대 시간(초) — 복귀 직후·긴 프레임에 먹구름이 한 번에 튀어 오르지 않게 */
        const val MAX_FRAME_SEC = 0.1

        /** 위쪽 배지 줄: [⏸](-471) · 계단 · 먹구름 거리 · 보물 · 별 */
        val HUD_STAIRS = HudSlot(-292f, 220f)
        val HUD_CLOUD = HudSlot(-56f, 224f)
        val HUD_CHEST = HudSlot(182f, 220f)
        val HUD_COIN = HudSlot(416f, 220f)

        /** 단마다 발판 색을 바꿔 "새 단에 올라왔다"를 눈으로 알게 한다(2단부터 8색 반복) */
        val TABLE_COLORS = listOf(
            GameRGB(0.27, 0.47, 0.95), GameRGB(0.20, 0.66, 0.62), GameRGB(0.56, 0.40, 0.90), GameRGB(0.90, 0.45, 0.30),
            GameRGB(0.30, 0.62, 0.30), GameRGB(0.85, 0.35, 0.60), GameRGB(0.25, 0.55, 0.80), GameRGB(0.75, 0.55, 0.20),
        )

        fun color(table: Int): GameRGB = TABLE_COLORS[(table - Problems.MIN_TABLE) % TABLE_COLORS.size]

        /** 구간 이름(알림) */
        fun zoneName(zone: SkyZone, strings: Strings): String = strings.get(
            when (zone) {
                SkyZone.MEADOW -> R.string.stairs_zone_meadow
                SkyZone.SKY_VILLAGE -> R.string.stairs_zone_sky_village
                SkyZone.RAINBOW -> R.string.stairs_zone_rainbow
                SkyZone.SPACE -> R.string.stairs_zone_space
            },
        )
    }
}
