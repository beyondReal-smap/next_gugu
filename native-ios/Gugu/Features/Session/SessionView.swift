import SwiftUI

// 세션 화면 (SessionScreen.tsx 이식) — 6개 모드 공용

struct SessionView: View {
    let mode: GameMode
    let table: Int?
    var onExit: () -> Void

    @Environment(GameStore.self) private var game
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
                let e = SessionEngine(mode: mode, table: table, game: game)
                e.start()
                engine = e
            }
        }
    }
}

// MARK: - 플레이 화면

private struct SessionPlayView: View {
    @Bindable var engine: SessionEngine
    var onExit: () -> Void

    var body: some View {
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
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gg.bg.ignoresSafeArea())
    }

    // 상단 바 — 모드별 진행 위젯
    private var topBar: some View {
        HStack(spacing: 12) {
            Button { onExit() } label: {
                Image(systemName: "xmark").font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.textMuted)
            }
            switch engine.modeKind {
            case .fixed:
                ProgressBar(value: Double(engine.idx) / Double(max(1, engine.total)))
                if engine.mode == .timeAttack {
                    SpeedTimer(startClock: engine.sessionStartClock)
                } else {
                    Text("\(engine.idx + 1)/\(engine.total)")
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                        .frame(width: 44, alignment: .trailing)
                }
            case .timed:
                if let limit = engine.timeLimitMs {
                    CountdownTimer(startClock: engine.sessionStartClock, limitMs: limit) { engine.expire() }
                }
            case .lives:
                Spacer()
                HeartsView(lives: engine.lives, max: engine.maxLives)
            }
        }
        .frame(height: 28)
        .padding(.bottom, 16)
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

    // 문제
    private var problemArea: some View {
        VStack(spacing: 12) {
            Spacer()
            equation
                .font(.suiteNum(.extrabold, 56))
            if engine.feedback == .wrong {
                Text(wrongExplanation)
                    .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                    .multilineTextAlignment(.center)
                    .transition(.opacity)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var equation: some View {
        let p = engine.problem
        let answer = p.a * p.b
        let showAnswer = engine.feedback == .wrong
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            if engine.mode == .truefalse, let st = engine.statement {
                num("\(p.a)", .gg.text); op("×"); num("\(p.b)", .gg.text); op("=")
                num("\(st.shown)", engine.feedback == .correct ? .gg.success : engine.feedback == .wrong ? .gg.danger : .gg.text)
            } else if engine.mode == .missing {
                num("\(p.a)", .gg.text); op("×")
                num(showAnswer ? "\(p.b)" : (engine.input.isEmpty ? "?" : engine.input),
                    showAnswer ? .gg.success : (engine.input.isEmpty ? .gg.border : .gg.accent))
                op("="); num("\(answer)", .gg.text)
            } else {
                num("\(p.a)", .gg.text); op("×"); num("\(p.b)", .gg.text); op("=")
                num(showAnswer ? "\(answer)" : (engine.input.isEmpty ? "?" : engine.input),
                    showAnswer ? .gg.success : (engine.input.isEmpty ? .gg.border : .gg.accent))
            }
        }
    }

    private func num(_ s: String, _ c: Color) -> some View {
        Text(s).foregroundStyle(c).monospacedDigit()
    }
    private func op(_ s: String) -> some View {
        Text(s).foregroundStyle(Color.gg.textMuted)
    }

    private var wrongExplanation: String {
        let p = engine.problem
        let answer = p.a * p.b
        if engine.mode == .truefalse, let st = engine.statement {
            return st.isTrue ? "맞는 식이었어요 — \(p.a) × \(p.b) = \(answer)"
                             : "\(p.a) × \(p.b) = \(answer) — 틀린 식이에요"
        } else if engine.mode == .missing {
            return "빈칸은 \(p.b) — \(p.a) × \(p.b) = \(answer)"
        }
        return "정답은 \(answer) 이에요"
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
    }
}

private struct SpeedTimer: View {
    var startClock: Double
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
            let elapsed = max(0, CACurrentMediaTime() * 1000 - startClock)
            Text(String(format: "%.1fs", elapsed / 1000))
                .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                .frame(width: 56, alignment: .trailing)
        }
    }
}

private struct CountdownTimer: View {
    var startClock: Double
    var limitMs: Int
    var onExpire: () -> Void
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
            let left = max(0, Double(limitMs) - (CACurrentMediaTime() * 1000 - startClock))
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
    }
}
