package site.smap.gugudan.features.games.monster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import site.smap.gugudan.R
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.CommitResult
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Strings
import site.smap.gugudan.core.games.ActionReject
import site.smap.gugudan.core.games.AttemptRecord
import site.smap.gugudan.core.games.CardTapResult
import site.smap.gugudan.core.games.MissionText
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.RoundPhase
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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// 구구 몬스터 진행 (Unity 시험판 MonsterGame.cs 이식, iOS MonsterEngine.swift 대응) — 규칙(MonsterSession)·카드 탭·몬스터 그림 상태·도감·보상 저장을 잇는다.
// 몬스터 체력(곱)과 같아지는 카드 2장을 고르면 처치 — 처치마다 별 코인이 배지로 날아가고, 연속 처치는 불꽃 "×N".
// 어떤 몬스터가 나올지는 MonsterDex(8종, 누적 처치로 해금), 처음 물리친 종은 위 도감 띠 칸이 채워진다.
// 한 프레임 순서: ① 입력 → 카드 고르기·채점  ② 시간 진행(60초)  ③ 그림 상태
// 쉬는 창이 열린 동안에는 프레임 루프를 멈춰 입력·시계·그림이 모두 멈춘다.
// 저장 시점(시험판 그대로): 도감은 처치하는 순간, 별·최고 기록은 결과(또는 체험 끝)를 띄울 때 한 번.
// 무료 체험(trial)은 처치 수 — 그만큼 물리치면 판을 끝내고 체험 종료 패널로(받은 별·기록은 저장).
// 프레임 루프는 화면(MonsterScreen)이 wantsFrames 동안 돌리며 advance 를 부른다. 테스트는 advance 를 직접 부른다.
// 화면 구성이 바뀌는 값(단계·쉬는 창·결과 등)은 Compose 상태로, 프레임마다 바뀌는 그림 값은 frame 하나로 다시 그리게 한다.
// 학습 기록(XP·오늘 목표·틀린 문제·서버 원장): 결과(또는 체험 끝)를 띄울 때 완료로, 판 중간에 나가면(abandon) 부분으로 한 번.
// 기록의 단: 순서대로 + 단 지정 판만 그 단(서버 tableNo, 마스터리 별은 주지 않음), 그 밖(단 없는 순서대로·무작위)은 단 없이 넘긴다.
// 문제 순서(순서대로·무작위)는 시작 전 패널에서 바꾸며, 바꾸면 뒤에 비치는 첫 몬스터도 새 순서로 다시 만든다. [다시 하기]는 같은 순서.
// 무작위는 단 시트에서 고른 단을 쓰지 않는다.
// 무료 체험 판이라도 체험 수를 채우기 전에 60초가 끝나면 체험 끝(trialFinished)이 아니다 — 화면은 일반 결과 창([다시 하기])을 띄운다.

class MonsterEngine(
    /** 홈(Default)·섞어서(Table(null)) = 단 없음, Table(n) = n단(n 은 2~9 — 10단 이상은 진입 화면이 막는다) */
    start: MinigameStart,
    /**
     * 처음 보여 줄 문제 순서(화면은 GameOrderStore 의 마지막 선택) — 순서대로는 n단 × 1 → × 9(단 없으면 단을 바꿔 가며),
     * 무작위는 고른 단과 상관없이 2~9단 섞어서
     */
    order: ProblemOrder,
    private val rewards: PlayRewardsStore,
    private val strings: Strings,
    /** 학습 기록 — 화면은 GameStore.commitSession + 서버 적재, 테스트는 메모리 상태에 적용 */
    private val commit: GameCommit,
    private val random: () -> Double = { Random.nextDouble() },
    /** DEBUG debug.gugu.monster_autoplay=N — N마리를 정답 카드 탭으로 자동으로 물리친다(스크린숏용, 시작도 자동) */
    autoplay: Int = 0,
    /** 동작 줄이기(애니메이터 길이 배율 0) — 그림의 반복·장식 움직임을 끈다(깡총·처치 화면 흔들림) */
    reduceMotion: Boolean = false,
) {
    enum class Phase {
        /** 시작 전 패널 */
        READY,
        /** 판 진행(끝난 뒤 결과 창까지 포함) */
        PLAYING,
    }

    /** 카드 색 — 평소 카드 색 / 공개 중 정답 두 장 초록 / 공개 중 고른 두 장 빨강 */
    enum class CardLook { NORMAL, CORRECT, WRONG }

    /** 위쪽 배지 줄 칸(설계 px, 가운데 x·폭) */
    data class HudSlot(val x: Float, val width: Float)

    /** 젤리 카드 6장(3 × 2) — 안전 영역 아래 끝 기준(엄지 영역) */
    object Cards {
        val SIZE = Size(300f, 230f)
        const val GAP_X = 330f
        /** 윗줄(0~2)·아랫줄(3~5) 가운데 높이(안전 영역 아래 끝에서 위로) */
        val ROW_Y = listOf(600f, 330f)

        fun center(index: Int, viewport: GameViewport): Offset {
            require(index in 0 until MonsterSession.CARD_COUNT) { "없는 카드: $index" }
            return viewport.safeBottom((index % 3 - 1) * GAP_X, ROW_Y[index / 3])
        }

        fun frame(index: Int, viewport: GameViewport): Rect = viewport.rect(center(index, viewport), SIZE.width, SIZE.height)

        /** 카드 칸 안을 눌렀을 때만 카드(null = 카드 밖). 카드 사이 틈도 가까운 카드로 보지 않는다 */
        fun index(point: Offset, viewport: GameViewport): Int? =
            (0 until MonsterSession.CARD_COUNT).firstOrNull { frame(it, viewport).contains(point) }
    }

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
    /** 이번 판의 체험 처치 수(null = 제한 없음) */
    var trial by mutableStateOf<Int?>(null)
        private set
    /** 판이 끝났는지(60초·체험 끝) */
    var roundOver by mutableStateOf(false)
        private set
    /** 결과 창 내용 — 결과(또는 체험 종료 패널)를 띄우는 순간 한 번 만들고 그때 별·기록을 저장한다 */
    var result by mutableStateOf<GameResultContent?>(null)
        private set
    /** [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초) */
    var restartArmed by mutableStateOf(false)
        private set
    /** 남은 체험 처치 수(지금 몬스터 포함, 체험이 아니면 null) */
    var trialLeft by mutableStateOf<Int?>(null)
        private set
    var viewport by mutableStateOf<GameViewport?>(null)
        private set
    /** 프레임 번호 — 그림(Canvas)이 이 값을 읽어 프레임마다 다시 그린다 */
    var frame by mutableLongStateOf(0L)
        private set

    // MARK: 그림 상태 (frame 으로 다시 그림)

    var session: MonsterSession
        private set
    var round = 0
        private set
    /** 체험 처치 수만큼 물리쳐 판이 끝났는지 */
    var trialFinished = false
        private set
    val stage = MonsterStage(reduceMotion)
    val sparkles = SparklesState()
    val reward = RewardHudState()
    val monsterBadge = HudBadgeState()
    val timeBadge = HudBadgeState()
    val dex = DexStripState()
    val banner = BannerState()
    /** 흐른 시간(초) — 통통·반딧불·안내 깜빡임용, 쉬는 동안 멈춤 */
    var time = 0.0
        private set
    /** 정답 순간 문제 글자가 통통(1 → 0) */
    var problemPulse = 0.0
        private set
    var lastWrong: AttemptRecord? = null
        private set
    /** 방금 푼 식("3 × 4 = 12")과 남은 표시 시간 */
    var solvedText = ""
        private set
    var solvedLeft = 0.0
        private set
    /** 판이 끝난 뒤 흐른 시간 */
    var endedFor = 0.0
        private set
    var resultShownFor = 0.0
        private set

    /** 단 시트에서 고른 단(null = 고르지 않음) — 순서대로일 때만 쓴다 */
    private val table: Int? = when (start) {
        MinigameStart.Default -> null
        is MinigameStart.Table -> start.table
    }
    /** 이번 판의 학습 기록 — 결과 창의 "+N XP"·레벨 업·업적 알림이 읽는다. 순서를 바꾸면 기록 단이 달라져 새로 만든다 */
    var learning by mutableStateOf(RoundLearningLog(GameMode.MONSTER, playedTable(order, table)))
        private set
    private val pendingTaps = mutableListOf<Offset>()
    /** 공개 중 빨강으로 보일 고른 두 장 */
    private var wrongFirst: Int? = null
    private var wrongSecond: Int? = null
    private var currentKind: MonsterKind? = null
    private var shownQuestionId = -1
    private var shownMonster = -1
    private var autoplayLeft = autoplay
    private var autoplayWait = 0.6

    init {
        require(autoplay >= 0) { "autoplay 는 0 이상: $autoplay" }
        if (table != null) {
            require(table in MonsterSession.TABLES) { "구구 몬스터 단 지정은 ${MonsterSession.TABLES} 만: $table" }
        }
        session = MonsterSession(order, playedTable(order, table), random)
        resetRound()
    }

    // MARK: 화면에서 읽는 값

    val pauseButtonVisible: Boolean get() = phase == Phase.PLAYING && !roundOver
    /** 프레임 루프를 돌릴지 — 시작 전·쉬는 동안·백그라운드에는 멈춤 */
    val wantsFrames: Boolean get() = phase == Phase.PLAYING && !paused && sceneActive
    /** "타임업!" — 시간이 다 된 뒤 결과가 뜨기 전까지 */
    val timeUpVisible: Boolean get() = session.clock.phase == RoundPhase.OVER && result == null
    /** 첫 카드 전 안내 — 첫 탭 전에는 시계도 멈춰 있음 */
    val hintVisible: Boolean get() = phase == Phase.PLAYING && session.clock.phase == RoundPhase.WAITING
    /** 단 시트에서 고른 단(2~9, null = 고르지 않음) — 순서와 상관없이 그대로 */
    val chosenTable: Int? get() = table
    /** 이번 판에 쓰는 단 — 순서대로 + 단 지정일 때만(시작 창 제목 "· N단"·학습 기록 단), 그 밖은 null */
    val playedTable: Int? get() = playedTable(order, table)

    val problemRevealing: Boolean get() = session.clock.isRevealing && lastWrong != null

    /** 문제 판 글자 — 공개 중이면 정답 식, 아니면 "고른 카드 × ? = 체력" */
    val problemText: String
        get() {
            val wrong = lastWrong
            if (problemRevealing && wrong != null) {
                return "${wrong.problem.a} × ${wrong.problem.b} = ${wrong.problem.a * wrong.problem.b}"
            }
            val q = session.current
            val first = session.selected?.let { q.cards[it].toString() } ?: "?"
            return "$first × ? = ${q.target.a * q.target.b}"
        }

    /** 문제 아래 줄 — 공개 중이면 고른 식(빨강), 방금 처치했으면 푼 식(초록) 0.8초 */
    val subText: String
        get() {
            val wrong = lastWrong
            if (problemRevealing && wrong != null) {
                val a = wrong.pickA
                val b = wrong.pickB
                if (a != null && b != null) return "$a × $b = ${wrong.given}"
            }
            return if (solvedLeft > 0) solvedText else ""
        }

    fun cardValue(index: Int): Int = session.current.cards[index]

    fun cardLook(index: Int): CardLook {
        if (!problemRevealing) return CardLook.NORMAL
        val q = session.current
        if (index == q.pairA || index == q.pairB) return CardLook.CORRECT
        if (index == wrongFirst || index == wrongSecond) return CardLook.WRONG
        return CardLook.NORMAL
    }

    /** 먼저 고른 카드 — 흰 테두리 + 살짝 커짐(공개 중에는 없음) */
    fun cardSelected(index: Int): Boolean = !problemRevealing && session.selected == index

    /** 학습 기록(commitSession) 연결용 — 채점 기록(문제 = 몬스터 목표 식, given = 고른 두 카드의 곱) */
    val answerRecords: List<AnswerRecord> get() = session.answerRecords
    /** 판 길이(ms) — 첫 카드부터 시간이 다 될(또는 체험이 끝날) 때까지 */
    val roundDurationMs: Int get() = (session.clock.elapsed * 1000).roundToInt()

    // MARK: 조작

    /** 화면 크기·안전 영역 — 카드 자리·코인 출발점·배지 위치 계산에 쓴다 */
    fun updateViewport(next: GameViewport) {
        if (viewport == next) return
        viewport = next
        frame += 1
    }

    /** 자동 처치가 켜져 있으면 시작 패널을 건너뛴다 — 화면이 이용권 여부에 맞춘 체험 수를 넘긴다(무료면 MinigameTrial.questions 마리에서 체험 끝) */
    fun debugAutoStart(trial: Int?) {
        if (autoplayLeft <= 0 || phase != Phase.READY || viewport == null) return
        start(trial)
    }

    /** 시작 전 패널의 [시작]. trial: 무료 체험 처치 수(이용권이 있으면 null) */
    fun start(trial: Int?) {
        check(phase == Phase.READY) { "이미 시작한 판: $phase" }
        checkNotNull(viewport) { "화면 크기를 먼저 알려야 함(updateViewport)" }
        applyTrial(trial)
        phase = Phase.PLAYING
        Sound.tap()
    }

    /** 시작 전 패널의 [순서대로]·[무작위] — 뒤에 비치는 첫 몬스터도 새 순서로 다시 만든다(새 세션·새 학습 기록 단). 같은 순서면 그대로 */
    fun setOrder(next: ProblemOrder) {
        check(phase == Phase.READY) { "문제 순서는 시작 전에만 바꿈: $phase" }
        if (next == order) return
        orderState = next
        learning = RoundLearningLog(GameMode.MONSTER, playedTable)
        session = MonsterSession(next, playedTable, random)
        resetRound()
    }

    /** 결과 창 [다시 하기] — 같은 순서·같은 단 지정으로 새 판. 쌓인 탭은 버린다(다시 하기를 누른 탭이 새 판 첫 카드로 새지 않게) */
    fun restart(trial: Int?) {
        // 결과 창이 없으면(이미 새 판을 연 뒤 연타가 한 번 더 온 것) 무시
        if (!restartArmed) return
        session = MonsterSession(order, playedTable, random)
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

    /** [계속 하기] — 쌓인 탭은 버린다([계속 하기]를 누른 탭이 같은 프레임에 카드로 새지 않게) */
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
     * 판이 끝났으면(타임업·체험 끝 뒤 결과를 띄우기 전) 완료로, 아니면 부분으로. 결과를 띄웠으면 이미 기록함(두 번 불러도 한 번)
     */
    fun abandon() {
        if (phase != Phase.PLAYING) return
        closeLearning(partial = !isRoundOver())
    }

    /**
     * Android 뒤로 가기. true = 게임이 받았음(화면에 남음), false = 화면이 나가야 함.
     * 시작 전 패널·결과 창 → 나가기 / 진행 중 → 쉬는 창 / 쉬는 창 → 그대로(나가기는 창의 [메뉴로 나가기]로만 —
     * 연타로 판을 잃지 않게) / 타임업·체험 끝 뒤 결과를 기다리는 1초 → 그대로 (구구 계단과 같은 규칙)
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

        autoplay(dt, viewport)

        // ① 입력 — [⏸] 위 탭은 빼기(카드 칸 밖이라 지금도 무시되지만 구구 계단과 같은 규칙으로).
        // 60초를 넘는 그 프레임에 누른 탭도 시간보다 먼저 채점한다
        val taps = pendingTaps.toList()
        pendingTaps.clear()
        if (!isRoundOver()) {
            val pauseFrame = PauseLayout.buttonFrame(viewport)
            for (point in taps) {
                if (isRoundOver()) break
                if (pauseFrame.contains(point)) continue
                val card = Cards.index(point, viewport) ?: continue
                tap(card, viewport)
            }
        }

        // ② 시간
        if (!isRoundOver()) {
            session.tick(dt)
            syncMonster(drop = true)   // 오답 공개가 끝나 카드만 다시 냈으면 고른 카드 표시를 지움
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
        trialLeft = trial?.let { max(0, it - session.defeated) }
        frame += 1
    }

    private fun isRoundOver(): Boolean = session.clock.phase == RoundPhase.OVER || trialFinished

    // MARK: 카드

    private fun tap(card: Int, viewport: GameViewport) {
        val first = session.selected
        val target = session.current.target
        val starsBefore = session.stars
        when (val tapped = session.tap(card)) {
            is CardTapResult.Rejected -> {
                if (tapped.reason == ActionReject.REVEALING) Sound.deny()   // 정답 공개 중 — "아직이야"
            }
            CardTapResult.Selected, CardTapResult.Deselected -> {
                Sound.tap()
                Haptics.selection()
            }
            is CardTapResult.Judged -> {
                val attempt = tapped.attempt
                if (attempt.correct) {
                    // 몬스터 자리에서 별이 터지고, 받은 별 코인이 위쪽 별 배지로 날아감
                    val camera = MonsterCamera(viewport.size)
                    sparkles.burst(stage.x, stage.y, big = StarRules.isComboMilestone(session.streak))
                    reward.earn(
                        camera.point(stage.x, stage.y), coinBadgeIcon(viewport),
                        earned = session.stars - starsBefore, total = session.stars, unit = viewport.unit,
                    )
                    recordDefeat()
                    stage.defeat()
                    val a = checkNotNull(attempt.pickA) { "B 채점에는 고른 카드가 있어야 함" }
                    val b = checkNotNull(attempt.pickB) { "B 채점에는 고른 카드가 있어야 함" }
                    solvedText = "$a × $b = ${target.a * target.b}"
                    solvedLeft = SOLVED_ECHO_SEC
                    Sound.correct()
                    Haptics.impactLight()
                    problemPulse = 1.0
                    val limit = trial
                    if (limit != null && session.defeated >= limit) trialFinished = true
                } else {
                    lastWrong = attempt
                    wrongFirst = first
                    wrongSecond = card
                    stage.hit()
                    Sound.wrong()
                    Haptics.error()
                }
                reward.setStreak(session.streak)
            }
        }
        syncMonster(drop = true)
    }

    /**
     * 새 문제가 나왔으면 고른 카드 표시를 지우고, 새 몬스터면 몬스터를 바꾼다.
     * 오답 뒤 같은 몬스터 재출제(카드만 섞임)는 몬스터를 그대로 둔다
     */
    private fun syncMonster(drop: Boolean) {
        val q = session.current
        if (q.id == shownQuestionId) return
        shownQuestionId = q.id
        wrongFirst = null
        wrongSecond = null
        if (session.monstersIssued == shownMonster) return
        shownMonster = session.monstersIssued
        // 순환 위치도 누적 처치 수로: 판마다 0번부터 다시 돌면 한 판에 3~5마리 잡는 아이는 뒤 종(4~7)을 거의 다시 못 만남
        val seen = rewards.dexSeenMask
        val total = rewards.dexTotal
        val kind = MonsterDex.nextKind(total, seen, spawnIndex = total, previous = currentKind)
        currentKind = kind
        stage.spawn(q.target.a * q.target.b, kind, drop)
        // 뒤 4종(누적 처치로 해금)을 처음 만나면 알림 — 처음 4종은 도감 띠의 실루엣으로 충분
        if (MonsterDex.unlockAt(kind) > 0 && !MonsterDex.isSeen(kind, seen)) {
            banner.show(strings.get(R.string.monster_new_appeared))
        }
    }

    /** 방금 물리친 몬스터를 도감에 기록(바로 저장). 처음 물리친 종이면 띠 칸이 채워지며 통통 튐 */
    private fun recordDefeat() {
        val kind = checkNotNull(currentKind) { "처치한 몬스터 종이 없음" }
        val record = rewards.recordDefeat(kind)
        if (!record.newlySeen && record.unlocked == null) return
        dex.show(rewards.dexSeenMask, rewards.dexTotal)   // 해금되면 자물쇠도 풀림
        if (!record.newlySeen) return
        dex.reveal(kind)
        banner.show(strings.get(R.string.monster_dex_registered))
    }

    // MARK: 판 시작·끝

    private fun applyTrial(limit: Int?) {
        if (limit != null) require(limit > 0) { "체험 처치 수는 1 이상: $limit" }
        trial = limit
        trialLeft = limit?.let { max(0, it - session.defeated) }
    }

    private fun resetRound() {
        round += 1
        pendingTaps.clear()
        stage.reset()
        sparkles.reset()
        reward.reset()
        monsterBadge.reset(0)
        timeBadge.reset(RoundClock.DURATION_SEC.toInt())
        dex.resetPop()
        dex.show(rewards.dexSeenMask, rewards.dexTotal)
        banner.clear()
        lastWrong = null
        wrongFirst = null
        wrongSecond = null
        solvedText = ""
        solvedLeft = 0.0
        problemPulse = 0.0
        endedFor = 0.0
        result = null
        resultShownFor = 0.0
        restartArmed = false
        trialFinished = false
        roundOver = false
        currentKind = null
        shownQuestionId = -1
        shownMonster = -1
        learning.reset()
        syncMonster(drop = false)
        frame += 1
    }

    /** 결과를 띄우는 순간 한 번: 받은 별을 쌓고 최고 기록·오늘의 미션을 갱신(체험 끝도 같음) */
    private fun showResult() {
        val record = rewards.recordRound(PlayGame.MONSTER, score = session.defeated, stars = session.stars, bestStreak = session.bestStreak)
        val done = closeLearning(partial = false)
        val miss = session.missToShow
        result = GameResultContent(
            note = GameResultContent.note(record, strings),
            missionNote = MissionText.resultNote(strings, record.missionsCompleted, record.allMissionsDone),
            mainLabel = strings.get(R.string.monster_result_defeated),
            mainValue = session.defeated,
            subLabel = strings.get(R.string.game_result_best_streak),
            subValue = session.bestStreak,
            miss = miss?.problem,
            missGiven = miss?.let { m ->
                val a = m.pickA
                val b = m.pickB
                if (a != null && b != null) strings.get(R.string.monster_result_picked_cards, a, b, m.given) else null
            },
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
        val clock = session.clock
        if (clock.phase == RoundPhase.OVER) banner.clear()   // 타임업 뒤에는 알림을 지움("타임업!" 만)

        stage.update(dt, time)
        sparkles.update(dt)
        banner.update(dt)
        dex.update(dt)
        problemPulse = max(0.0, problemPulse - dt * 5)
        if (!problemRevealing) solvedLeft = max(0.0, solvedLeft - dt)

        // 배지 줄: 물리친 몬스터 · 남은 초(10초 이하 빨강·두근) · 별(코인이 닿을 때마다)
        monsterBadge.setValue(session.defeated)
        timeBadge.setValue(ceil(clock.remaining).toInt())
        timeBadge.setDanger(clock.remaining <= DANGER_SEC && clock.phase != RoundPhase.OVER)
        monsterBadge.update(dt)
        timeBadge.update(dt)
        for (index in reward.update(dt, coinBadgeIcon(viewport))) Sound.coin(index)
        val camera = MonsterCamera(viewport.size)
        reward.followCombo(camera.point(COMBO_WORLD_X, COMBO_WORLD_Y))
    }

    /** 별 배지 아이콘 가운데(화면 px) — 코인이 날아가 닿는 곳 */
    private fun coinBadgeIcon(viewport: GameViewport): Offset =
        viewport.safeTop(HUD_COIN.x - HUD_COIN.width / 2 + HudBadgeState.HEIGHT / 2, PauseLayout.HUD_ROW_Y)

    // MARK: DEBUG 자동 정답

    /** 정답 두 장(pairA → pairB)을 실제 탭 경로(큐)로 누른다. 고른 상태가 잠깐 보이게 사이를 둔다 */
    private fun autoplay(dt: Double, viewport: GameViewport) {
        if (autoplayLeft <= 0 || isRoundOver() || session.clock.isRevealing) return
        autoplayWait -= dt
        if (autoplayWait > 0) return
        val q = session.current
        if (session.selected == null) {
            pendingTaps.add(Cards.center(q.pairA, viewport))
            autoplayWait = 0.35
        } else {
            pendingTaps.add(Cards.center(q.pairB, viewport))
            autoplayWait = 0.6
            autoplayLeft -= 1
        }
    }

    companion object {
        /** 판이 끝나고 결과(또는 체험 종료 패널)를 띄우기까지(초) — "타임업!" 을 보여 주는 시간 */
        const val RESULT_DELAY_SEC = 1.0
        /** 프레임 하나에 흘릴 최대 시간(초) — 복귀 직후·긴 프레임에 시계가 한 번에 튀지 않게 */
        const val MAX_FRAME_SEC = 0.1
        /** 방금 푼 식을 문제 아래에 잠깐 남겨 "왜 이겼는지"를 보여 줌(초) */
        const val SOLVED_ECHO_SEC = 0.8
        /** 남은 시간이 이만큼 이하면 시계 배지가 빨갛게 두근거림(초) */
        const val DANGER_SEC = 10.0
        /** 불꽃 배지 자리(월드) — 몬스터 왼쪽 위 */
        const val COMBO_WORLD_X = -2.7
        val COMBO_WORLD_Y: Double get() = MonsterStage.MONSTER_Y + 0.9

        /** 이 순서로 실제로 쓰는 단 — 순서대로일 때만 고른 단, 무작위는 null */
        private fun playedTable(order: ProblemOrder, chosen: Int?): Int? =
            if (order == ProblemOrder.SEQUENTIAL) chosen else null

        /** 위쪽 배지 줄(설계 px, 가운데 x·폭): [⏸](-471) · 몬스터 · 남은 시간 · 별. 몬스터 아이콘(1.2배)이 왼쪽으로 20px 나오므로 [⏸]와 26px 띄움 */
        val HUD_MONSTER = HudSlot(-250f, 280f)
        val HUD_TIME = HudSlot(68f, 280f)
        val HUD_COIN = HudSlot(386f, 280f)
        /** 배지 줄(-80) 아래 도감 띠 — 띠 위 끝 -144 가 배지 아래 끝(-135) 바로 밑 */
        const val DEX_Y = -190f
    }
}
