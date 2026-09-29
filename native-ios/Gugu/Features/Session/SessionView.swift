import SwiftUI

// 세션 화면 (SessionScreen.tsx 이식) — 6개 모드 공용

struct SessionView: View {
    let mode: GameMode
    let table: Int?
    var review: Bool = false
    var onExit: () -> Void

    @Environment(GameStore.self) private var game
    @Environment(SyncStore.self) private var sync
    @Environment(AuthStore.self) private var auth
    @State private var engine: SessionEngine?

    var body: some View {
        Group {
            if let engine {
                if let done = engine.done {
                    ResultView(engine: engine, done: done, onClose: onExit)
                } else {
                    SessionPlayView(engine: engine, onExit: onExit)
                }
            } else {
                Color.gg.bg.ignoresSafeArea()
            }
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .onAppear {
            if engine == nil {
                let e = SessionEngine(mode: mode, table: table, review: review, game: game) { result in
                    // 학습 원장에 적재하고 바로 올려 본다.
                    // 보호자 검증 전이면 flush 가 요청 없이 큐에 남긴다.
                    sync.record(result)
                    sync.flush(auth: auth)
                }
                e.start()
                engine = e
            }
        }
        .onDisappear { Speech.shared.stop() }
    }
}

// MARK: - 플레이 화면

private struct SessionPlayView: View {
    @Bindable var engine: SessionEngine
    var onExit: () -> Void

    @Environment(PrefsStore.self) private var prefs
    @State private var confirmExit = false
    /// 마지막으로 소리 내어 읽은 문제 번호 — 확인 시트 동안 넘어간 문제를 재개할 때 읽기 위해서다
    @State private var readSerial = -1

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                topBar
                scoreComboBar
                problemArea
                Spacer(minLength: 8)
                if engine.mode == .truefalse {
                    OxPad { engine.handleOX($0) }
                } else {
                    Keypad(
                        onInput: { engine.handleInput($0) },
                        onDelete: { engine.handleDelete() },
                        onSubmit: { engine.handleManualSubmit() },
                        canSubmit: !engine.input.isEmpty
                    )
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .readableWidth(560)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.gg.bg.ignoresSafeArea())
            // 게임 화면은 키패드·식 배치가 고정이라 아주 큰 글자에서는 더 키우지 않는다
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)

            if confirmExit {
                ConfirmSheet(
                    title: "그만할까요?",
                    message: "지금까지 푼 \(engine.answeredCount)문제는 기록돼요.",
                    cancelLabel: "계속하기",
                    confirmLabel: "그만하기",
                    onCancel: {
                        withAnimation(.easeOut(duration: 0.2)) { confirmExit = false }
                        engine.resume()
                        // 확인하는 사이에 다음 문제로 넘어갔으면 그 문제를 이제 읽어 준다
                        if readSerial != engine.problemSerial { readQuestion() }
                    },
                    onConfirm: {
                        engine.abandon()
                        onExit()
                    }
                )
                .transition(.opacity)
            }
        }
        .onAppear(perform: readQuestion)
        .onChange(of: engine.problemSerial) { _, _ in readQuestion() }
    }

    /// 닫기 — 푼 문제가 있으면 시간을 멈추고 한 번 묻는다. 없으면 잃을 것이 없으니 바로 닫는다.
    private func requestExit() {
        guard engine.answeredCount > 0 else {
            engine.abandon()
            onExit()
            return
        }
        engine.pause()
        Speech.shared.stop()
        withAnimation(.easeOut(duration: 0.2)) { confirmExit = true }
    }

    private func readQuestion() {
        // 확인 시트가 떠 있는 동안 넘어온 문제는 시트 뒤에서 읽지 않는다 — 재개할 때 읽는다
        guard prefs.readAloud, engine.pausedAt == nil else { return }
        readSerial = engine.problemSerial
        Speech.shared.speak(VoiceLines.question(engine.problem, mode: engine.mode, statement: engine.statement))
    }

    // 상단 바 — 모드별 진행 위젯
    private var topBar: some View {
        HStack(spacing: 8) {
            Button(action: requestExit) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("그만하기")
            switch engine.modeKind {
            case .fixed:
                ProgressBar(value: Double(engine.idx) / Double(max(1, engine.total)))
                if engine.mode == .timeAttack {
                    SpeedTimer(engine: engine)
                } else {
                    Text("\(engine.idx + 1)/\(engine.total)")
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                        .frame(width: 44, alignment: .trailing)
                        .accessibilityLabel("\(engine.total)문제 중 \(engine.idx + 1)번째")
                }
            case .timed:
                if let limit = engine.timeLimitMs {
                    CountdownTimer(engine: engine, limitMs: limit)
                }
            case .lives:
                Spacer()
                HeartsView(lives: engine.lives, max: engine.maxLives)
            }
            readAloudToggle
        }
        .frame(height: 44)
        .padding(.bottom, 8)
    }

    /// 문제 읽어주기 — 켜면 지금 문제부터 바로 읽는다
    private var readAloudToggle: some View {
        Button {
            Haptics.impact(.light)
            prefs.readAloud.toggle()
            readQuestion()
        } label: {
            Image(systemName: prefs.readAloud ? "speaker.wave.2.fill" : "speaker.slash")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(prefs.readAloud ? Color.gg.accent : Color.gg.textMuted)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(prefs.readAloud ? "문제 읽어주기 끄기" : "문제 읽어주기 켜기")
    }

    // 점수(무제한) + 콤보
    private var scoreComboBar: some View {
        ZStack {
            if engine.modeKind != .fixed {
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .heavy)).foregroundStyle(Color.gg.success)
                        Text("\(engine.score)").font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text).monospacedDigit()
                    }
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color.gg.surface2, in: Capsule())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("맞힌 문제 \(engine.score)개")
                    Spacer()
                }
            }
            if engine.combo >= 2 {
                Pill(bg: Color.gg.danger.opacity(0.15), fg: .gg.danger) {
                    Image(systemName: "flame.fill")
                    Text("\(engine.combo) 콤보")
                }
                .id(engine.combo)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .frame(height: 28)
        .padding(.bottom, 8)
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: engine.combo)
    }

    // 문제 — 식은 항상 영역 한가운데 고정. 설명·힌트는 레이아웃을 밀지 않는 오버레이로 단다.
    private var problemArea: some View {
        GeometryReader { geo in
            equation
                .font(.suiteNum(.extrabold, 56))
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(KoreanReading.question(engine.problem, mode: engine.mode, statement: engine.statement))
                .accessibilityValue(accessibilityAnswerState)
                .pinnedAbove(spacing: 14) { feedbackMark }
                .pinnedBelow(width: geo.size.width) {
                    if engine.feedback == .wrong {
                        Text(wrongExplanation)
                            .font(.suite(.bold, 15)).foregroundStyle(Color.gg.textMuted)
                            .multilineTextAlignment(.center)
                            // 식의 접근성 값("오답. …")에 이미 들어 있다 — 두 번 읽히지 않게 숨긴다
                            .accessibilityHidden(true)
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .overlay(alignment: .bottom) { coachHintCard }
                .animation(.easeOut(duration: 0.15), value: engine.feedback)
        }
    }

    /// 정답/오답 표시 — 식 위쪽에 겹쳐 띄워 식을 움직이지 않는다
    @ViewBuilder
    private var feedbackMark: some View {
        if let fb = engine.feedback {
            Image(systemName: fb == .correct ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(fb == .correct ? Color.gg.success : Color.gg.danger)
                .accessibilityHidden(true)
                .transition(.opacity)
        }
    }

    /// 코치 힌트 — 같은 단을 여러 번 틀렸을 때 답을 뺀 풀이를 보여 준다
    @ViewBuilder
    private var coachHintCard: some View {
        if let hint = engine.coachHint, engine.feedback == nil {
            HStack(spacing: 10) {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 16, weight: .bold)).foregroundStyle(Color.gg.warning)
                VStack(alignment: .leading, spacing: 2) {
                    Text(hint.title).font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.text)
                    Text(hint.scaffold).font(.suite(.bold, 15)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(Color.gg.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color.gg.warning.opacity(0.25), lineWidth: 1))
            .padding(.bottom, 4)
            .transition(.opacity)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("힌트, \(hint.title), \(hint.scaffold)")
        }
    }

    /// 식 — 색이 다른 조각을 하나의 Text 로 이어 붙인다.
    /// 한 덩어리여야 큰 글자 설정에서 minimumScaleFactor 가 모든 조각을 같은 비율로 줄인다.
    private var equation: Text {
        let p = engine.problem
        let answer = p.a * p.b
        let showAnswer = engine.feedback == .wrong
        // 정답이면 입력한 수를 초록으로 — 위치는 그대로 두고 색으로만 알린다
        let inputColor: Color = engine.feedback == .correct ? .gg.success : (engine.input.isEmpty ? .gg.border : .gg.accent)
        let slot = engine.input.isEmpty ? "?" : engine.input
        if engine.mode == .truefalse, let st = engine.statement {
            let shownColor: Color = engine.feedback == .correct ? .gg.success : engine.feedback == .wrong ? .gg.danger : .gg.text
            return num("\(p.a)", .gg.text) + op(" × ") + num("\(p.b)", .gg.text) + op(" = ") + num("\(st.shown)", shownColor)
        } else if engine.mode == .missing {
            return num("\(p.a)", .gg.text) + op(" × ")
                + num(showAnswer ? "\(p.b)" : slot, showAnswer ? .gg.success : inputColor)
                + op(" = ") + num("\(answer)", .gg.text)
        }
        return num("\(p.a)", .gg.text) + op(" × ") + num("\(p.b)", .gg.text) + op(" = ")
            + num(showAnswer ? "\(answer)" : slot, showAnswer ? .gg.success : inputColor)
    }

    private func num(_ s: String, _ c: Color) -> Text {
        Text(s).foregroundStyle(c).monospacedDigit()
    }
    private func op(_ s: String) -> Text {
        Text(s).foregroundStyle(Color.gg.textMuted)
    }

    private var accessibilityAnswerState: String {
        switch engine.feedback {
        case .correct: return "정답"
        case .wrong: return "오답. \(wrongExplanation)"
        case nil: return engine.input.isEmpty ? "" : "입력 \(engine.input)"
        }
    }

    private var wrongExplanation: String {
        let p = engine.problem
        let answer = p.a * p.b
        if engine.mode == .truefalse, let st = engine.statement {
            return st.isTrue ? "맞는 식이었어요 — \(p.a) × \(p.b) = \(answer)"
                             : "\(p.a) × \(p.b) = \(answer) — 틀린 식이에요"
        }
        let expected = engine.mode == .missing ? p.b : answer
        if let given = engine.lastGiven {
            return KoreanReading.notButIs(given: given, answer: expected)
        }
        return "정답은 \(KoreanReading.withCopula(expected))"
    }
}

// MARK: - 진행 위젯

private struct ProgressBar: View {
    var value: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.gg.surface2)
                Capsule().fill(Color.gg.accent)
                    .frame(width: geo.size.width * max(0, min(1, value)))
                    .animation(.spring(response: 0.4, dampingFraction: 0.8), value: value)
            }
        }
        .frame(height: 10)
        .accessibilityHidden(true)
    }
}

private struct SpeedTimer: View {
    let engine: SessionEngine
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
            let elapsed = engine.elapsedMs(at: CACurrentMediaTime() * 1000)
            Text(String(format: "%.1fs", elapsed / 1000))
                .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                .frame(width: 56, alignment: .trailing)
        }
    }
}

private struct CountdownTimer: View {
    let engine: SessionEngine
    var limitMs: Int
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
            let left = max(0, Double(limitMs) - engine.elapsedMs(at: CACurrentMediaTime() * 1000))
            let sec = Int(ceil(left / 1000))
            let urgent = sec <= 10
            HStack(spacing: 8) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(urgent ? Color.gg.danger : Color.gg.accent)
                            .frame(width: geo.size.width * (left / Double(limitMs)))
                    }
                }
                .frame(height: 10)
                Text("\(sec)s")
                    .font(.suite(.bold, 14)).foregroundStyle(urgent ? Color.gg.danger : Color.gg.textMuted)
                    .monospacedDigit().frame(width: 40, alignment: .trailing)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("남은 시간 \(sec)초")
        }
    }
}

private struct HeartsView: View {
    var lives: Int
    var max: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<max, id: \.self) { i in
                Image(systemName: i < lives ? "heart.fill" : "heart")
                    .font(.system(size: 20))
                    .foregroundStyle(i < lives ? Color.gg.danger : Color.gg.textMuted)
                    .opacity(i < lives ? 1 : 0.3)
                    .scaleEffect(i < lives ? 1 : 0.8)
                    .animation(.spring(response: 0.3, dampingFraction: 0.6), value: lives)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("남은 하트 \(lives)개")
    }
}
