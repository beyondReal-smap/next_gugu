import SwiftUI

// 구구 레인 화면 (LaneRunnerScreen.tsx 이식)
// 위아래로 길을 옮겨 장애물을 피하고, 문제가 나오면 정답 숫자가 적힌 길로 들어간다.
// 웹의 키보드 조작(↑↓·1~3·Space·Esc)은 웹 전용이라 제외하고, 스와이프와 ▲▼ 버튼만 제공한다.

struct LaneRunnerView: View {
    var onExit: () -> Void

    @State private var engine = LaneRunnerEngine()
    /// 한 번의 제스처는 한 칸만 이동한다
    @State private var swipeHandled = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let laneNames = ["위", "가운데", "아래"]
    /// 스와이프로 인정하는 세로 이동 거리
    private static let swipeThreshold: CGFloat = 28

    private var game: LaneRunnerState { engine.game }
    private var ready: Bool { game.phase == .ready }
    private var over: Bool { game.phase == .over }
    private var paused: Bool { game.phase == .paused }
    private var active: Bool { game.phase == .running }
    /// 판정 뒤에도 게이트가 화면을 벗어날 때까지는 문제와 결과를 보여 준다
    private var quiz: Bool { game.question != nil && game.gate != nil }
    private var reveal: Bool { game.outcome == .correct || game.outcome == .wrong }
    private var answer: Int? { game.question?.answer }

    private var feedback: String {
        switch game.outcome {
        case .correct:
            return LaneRunner.leveledUp(game)
                ? "정답! 이제 속도 \(LaneRunner.level(score: game.score) + 1)단계로 빨라져요."
                : "정답! \(answer ?? 0) 길로 통과했어요."
        case .wrong:
            guard let question = game.question else { return "아쉬워요!" }
            return "아쉬워요! \(question.a) × \(question.b) = \(question.answer)"
        case .hit:
            return "쿵! 장애물에 부딪혔어요."
        case nil:
            return quiz ? "정답 숫자가 있는 길로 옮겨요!" : "위아래로 길을 옮겨 장애물을 피해요."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 16) {
                    stage
                    controls
                    footnote
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { engine.pause() }
        }
        .onDisappear { engine.teardown() }
    }

    // MARK: - 헤더

    private var header: some View {
        HStack {
            Button {
                engine.pause()
                onExit()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left").font(.system(size: 14, weight: .bold))
                    Text("홈으로").font(.suite(.bold, 14))
                }
                .foregroundStyle(Color.gg.textMuted)
                .frame(minHeight: 44)
            }
            Spacer()
            Pill(bg: Color.gg.surface, fg: .gg.textMuted) {
                Image(systemName: "rectangle.split.1x2")
                Text("길을 바꾸며 배우는 구구단")
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    // MARK: - 무대

    private var stage: some View {
        VStack(spacing: 0) {
            scoreboard
            ZStack {
                LaneScene(game: game, reducedMotion: reduceMotion)
                    .aspectRatio(8.0 / 5.0, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    .gesture(swipeGesture)

                VStack {
                    HStack {
                        Text(ready ? "오늘의 작은 모험" : "\(Int(game.distance))m 달리는 중")
                        Spacer()
                        Text(game.combo >= 2
                             ? "\(game.combo)연속 정답 길! · 속도 \(LaneRunner.level(score: game.score) + 1)단계"
                             : "속도 \(LaneRunner.level(score: game.score) + 1)단계")
                    }
                    .font(.suite(.extrabold, 11))
                    .foregroundStyle(Color(hex: "#486146"))
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    Spacer()
                }
                .allowsHitTesting(false)

                if paused || over {
                    Color(hex: "#eff3df").opacity(0.85)
                    VStack(spacing: 8) {
                        Text(paused ? "잠깐 쉬어가요" : "멋진 달리기였어요!")
                            .font(.suite(.extrabold, 22))
                            .foregroundStyle(Color(hex: "#153f35"))
                        Text(paused
                             ? "준비되면 이어서 달려요."
                             : "정답 길 \(game.score)개 · 피한 장애물 \(game.dodged)개 · 최고 \(game.maxCombo)연속")
                            .font(.suite(.bold, 13))
                            .foregroundStyle(Color(hex: "#486146"))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .clipped()
        }
        .background(Color(hex: "#153f35"))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(Color(hex: "#294e3d"), lineWidth: 1)
        )
    }

    /// 무대를 위아래로 밀면 한 칸 이동 (가로 이동이 더 크면 무시)
    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                guard active, !swipeHandled else { return }
                let dy = value.translation.height
                guard abs(dy) >= Self.swipeThreshold, abs(dy) > abs(value.translation.width) else { return }
                swipeHandled = true
                engine.move(dy < 0 ? -1 : 1)
            }
            .onEnded { _ in swipeHandled = false }
    }

    private var scoreboard: some View {
        HStack(spacing: 20) {
            VStack(alignment: .leading, spacing: 2) {
                Text("통과한 정답 길").font(.suite(.bold, 11)).foregroundStyle(Color(hex: "#c1d4c2"))
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text("\(game.score)").font(.suiteNum(.extrabold, 24)).foregroundStyle(Color(hex: "#f3f6e8"))
                    Text("개").font(.suite(.bold, 11)).foregroundStyle(Color(hex: "#c1d4c2"))
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: "trophy.fill").font(.system(size: 9, weight: .bold))
                    Text(engine.table == nil ? "전체 최고" : "\(engine.table!)단 최고").font(.suite(.bold, 11))
                }
                .foregroundStyle(Color(hex: "#c1d4c2"))
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text("\(engine.selectedBest)").font(.suiteNum(.extrabold, 24)).foregroundStyle(Color(hex: "#dbef9e"))
                    Text("개").font(.suite(.bold, 11)).foregroundStyle(Color(hex: "#dbef9e"))
                }
            }
            Spacer()
            HStack(spacing: 4) {
                ForEach(1...LaneRunner.maxLives, id: \.self) { heart in
                    Image(systemName: heart <= game.lives ? "heart.fill" : "heart")
                        .font(.system(size: 16))
                        .foregroundStyle(heart <= game.lives ? Color(hex: "#f3a88c") : Color(hex: "#6c897b"))
                }
            }
            .accessibilityLabel("남은 하트 \(game.lives)개")

            Button {
                engine.togglePause()
            } label: {
                Image(systemName: paused ? "play.fill" : "pause.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(hex: "#f3f6e8"))
                    .frame(width: 44, height: 44)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
            }
            .disabled(ready || over)
            .opacity(ready || over ? 0.3 : 1)
            .accessibilityLabel(paused ? "계속 달리기" : "일시정지")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    // MARK: - 하단 조작부

    @ViewBuilder
    private var controls: some View {
        VStack(spacing: 0) {
            if ready || over {
                setupPanel
            } else if paused {
                pausePanel
            } else {
                playPanel
            }
        }
        .padding(16)
        .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
            .strokeBorder(Color.gg.border, lineWidth: 1))
    }

    private var setupPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            if over {
                VStack(spacing: 6) {
                    Text(game.score > 0 && game.score >= engine.runBest
                         ? "나의 최고 기록을 달성했어요!"
                         : "한 번 더, 더 멀리 가볼까요?")
                        .font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                        .multilineTextAlignment(.center)
                    Text("정답 길을 \(game.score)개 지났어요. 정답을 익히면 다음엔 더 멀리!")
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            } else {
                HStack(alignment: .top, spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.gg.emerald.opacity(0.12))
                            .frame(width: 40, height: 40)
                        Image(systemName: "rectangle.split.1x2.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Color.gg.emerald)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text("길을 직접 바꾸며 달려요!")
                            .font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                        Text("위아래로 길을 옮겨 장애물을 피하고, 문제가 나오면 정답 숫자가 적힌 길로 들어가요. 부딪히거나 틀리면 하트가 하나 줄어요.")
                            .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("달리면서 연습할 단")
                    .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)
                LazyVGrid(columns: columns, spacing: 8) {
                    tableChip(nil)
                    ForEach(LaneRunnerEngine.tables, id: \.self) { tableChip($0) }
                }
            }

            GGButton(variant: .primary, size: .lg, action: { engine.start() }) {
                Image(systemName: over ? "arrow.counterclockwise" : "play.fill")
                Text(over ? "다시 달리기" : "달리기 시작")
            }
        }
    }

    private func tableChip(_ value: Int?) -> some View {
        let selected = engine.table == value
        return Button {
            Haptics.selection()
            engine.table = value
        } label: {
            Text(value == nil ? "전체" : "\(value!)단")
                .font(.suite(.extrabold, 13))
                .foregroundStyle(selected ? Color(hex: "#e6f5c1") : Color.gg.text)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(selected ? Color(hex: "#153f35") : Color.gg.surface2)
                )
        }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private var pausePanel: some View {
        VStack(spacing: 14) {
            Text("장애물과 남은 시간이 멈춰 있어요.")
                .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
            GGButton(variant: .primary, size: .lg, action: { engine.togglePause() }) {
                Image(systemName: "play.fill")
                Text("이어서 달리기")
            }
            Button {
                engine.pause()
                onExit()
            } label: {
                HStack(spacing: 4) {
                    Text("학습 모드로 돌아가기").font(.suite(.bold, 13))
                    Image(systemName: "arrow.up.right").font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(Color.gg.textMuted)
                .frame(minHeight: 44)
            }
        }
    }

    private var playPanel: some View {
        VStack(spacing: 12) {
            HStack {
                Text("\(engine.table == nil ? "전체 구구단" : "\(engine.table!)단") · \(quiz ? "정답 길 찾기" : "장애물 피하기")")
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                Spacer()
                Text(statusLabel)
                    .font(.suiteNum(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            }

            // 게이트가 판정선에 닿기까지 남은 비율
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.gg.surface2)
                    Capsule()
                        .fill(remaining < 0.25 && !reveal ? Color.gg.warning : Color.gg.emerald)
                        .frame(width: geo.size.width * remaining)
                }
            }
            .frame(height: 6)

            VStack(spacing: 8) {
                if quiz, let question = game.question {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(question.a)").foregroundStyle(Color.gg.text)
                        Text("×").foregroundStyle(Color.gg.textMuted)
                        Text("\(question.b)").foregroundStyle(Color.gg.text)
                        Text("=").foregroundStyle(Color.gg.textMuted)
                        Text(reveal ? "\(question.answer)" : "?")
                            .foregroundStyle(game.outcome == .correct ? Color.gg.success : Color.gg.textMuted)
                    }
                    .font(.suiteNum(.extrabold, 38))
                    .accessibilityLabel("\(question.a) 곱하기 \(question.b)는? \(laneSummary)")
                } else {
                    Text("장애물을 피해요!")
                        .font(.suite(.extrabold, 20))
                        .foregroundStyle(Color.gg.text)
                        .frame(height: 44)
                }

                Text(feedback)
                    .font(.suite(.bold, 13))
                    .foregroundStyle(game.outcome == .wrong || game.outcome == .hit
                                     ? Color.gg.warning : Color.gg.textMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 2)

            HStack(alignment: .top, spacing: 10) {
                VStack(spacing: 6) {
                    ForEach(0..<3, id: \.self) { laneRow($0) }
                }
                VStack(spacing: 6) {
                    moveButton(delta: -1)
                    moveButton(delta: 1)
                }
                .frame(width: 80)
            }
        }
    }

    private var remaining: Double {
        guard let gate = game.gate, gate.startX > LaneRunner.judgeX else { return 0 }
        return max(0, min(1, (gate.x - LaneRunner.judgeX) / (gate.startX - LaneRunner.judgeX)))
    }

    private var statusLabel: String {
        guard quiz, let gate = game.gate else { return "피한 장애물 \(game.dodged)개" }
        if reveal { return game.outcome == .correct ? "통과!" : "정답을 기억해요" }
        let seconds = max(0, gate.x - LaneRunner.judgeX) / LaneRunner.speed(score: game.score) / 1000
        return String(format: "%.1f초", seconds)
    }

    private var laneSummary: String {
        guard let gate = game.gate else { return "" }
        return zip(Self.laneNames, gate.values).map { "\($0) \($1)" }.joined(separator: ", ")
    }

    private func laneRow(_ lane: Int) -> some View {
        let value = game.gate?.values[lane]
        let current = lane == game.lane
        let correct = reveal && value != nil && value == answer
        let wrong = reveal && value != nil && value == game.given && value != answer
        let border: Color = correct ? .gg.success : wrong ? .gg.warning : current ? Color(hex: "#153f35") : .gg.border
        let fill: Color = correct ? Color.gg.success.opacity(0.1)
            : wrong ? Color.gg.warning.opacity(0.1)
            : current ? Color(hex: "#dbef9e").opacity(0.4) : Color.gg.surface

        return HStack(spacing: 12) {
            Text(Self.laneNames[lane])
                .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                .frame(width: 40, alignment: .leading)
            if let value {
                Text("\(value)").font(.suiteNum(.extrabold, 24)).foregroundStyle(Color.gg.text)
            } else {
                Text("·").font(.suite(.bold, 16)).foregroundStyle(Color.gg.textMuted)
            }
            Spacer()
            if current {
                Text("공룡").font(.suite(.extrabold, 11)).foregroundStyle(Color(hex: "#2b6a4f"))
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 48)
        .background(fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(border, lineWidth: 2))
        .opacity(reveal && !correct && !wrong ? 0.4 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(current ? [.isSelected] : [])
    }

    private func moveButton(delta: Int) -> some View {
        let disabled = !active || (delta < 0 ? game.lane == 0 : game.lane == 2)
        return Button {
            engine.move(delta)
        } label: {
            Image(systemName: delta < 0 ? "chevron.up" : "chevron.down")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Color.gg.text)
                .frame(maxWidth: .infinity)
                .frame(height: 51)
                .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(Color.gg.border, lineWidth: 2))
        }
        .buttonStyle(PressScaleStyle())
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
        .accessibilityLabel(delta < 0 ? "위 길로 이동" : "아래 길로 이동")
    }

    private var footnote: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("화면을 위아래로 밀거나 ▲▼ 버튼으로 길을 옮겨요.")
            Text("정답 길 \(LaneRunner.scorePerLevel)개를 지날 때마다 속도가 한 단계 빨라져요. 앱을 벗어나면 자동으로 일시정지해요.")
            Text("단별 최고 기록은 이 기기에 저장돼요.")
        }
        .font(.suite(.medium, 12))
        .foregroundStyle(Color.gg.textMuted)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }
}
