import SwiftUI

// 구구 점프 화면 (RunnerScreen.tsx 이식)
// 보기 3개 중 정답을 고르면 자동으로 점프한다. 틀리거나 시간이 지나면 하트가 하나 줄어든다.

struct RunnerView: View {
    var onExit: () -> Void

    @State private var engine = RunnerEngine()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var game: RunnerState { engine.game }
    private var ready: Bool { game.phase == .ready }
    private var over: Bool { game.phase == .over }
    private var paused: Bool { game.phase == .paused }
    private var active: Bool { game.phase == .running }

    // 무대 위 안내 문구 — 웹과 동일 카피
    private var feedback: String {
        let answer = game.question.answer
        switch game.outcome {
        case .correct: return "정답! 장애물을 넘어요."
        case .wrong:   return "아쉬워요! \(game.question.a) × \(game.question.b) = \(answer)"
        case .missed:  return "시간이 다 됐어요. \(game.question.a) × \(game.question.b) = \(answer)"
        case nil:      return "장애물이 오기 전에 정답을 골라요!"
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
                Image(systemName: "figure.run")
                Text("달리며 배우는 구구단")
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    // MARK: - 무대 (점수판 + 씬)

    private var stage: some View {
        VStack(spacing: 0) {
            scoreboard
            ZStack {
                RunnerScene(game: game, reducedMotion: reduceMotion)
                    .aspectRatio(36.0 / 13.0, contentMode: .fit)
                    .frame(maxWidth: .infinity)

                VStack {
                    HStack {
                        Text(ready ? "오늘의 작은 모험" : "\(Int(game.distance))m 달리는 중")
                        Spacer()
                        Text(game.combo >= 2 ? "\(game.combo)연속 성공! · 속도 \(Runner.level(score: game.score) + 1)단계" : "속도 \(Runner.level(score: game.score) + 1)단계")
                    }
                    .font(.suite(.extrabold, 11))
                    .foregroundStyle(Color(hex: "#486146"))
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    Spacer()
                }

                if paused || over {
                    Color(hex: "#eff3df").opacity(0.85)
                    VStack(spacing: 8) {
                        Text(paused ? "잠깐 쉬어가요" : "멋진 달리기였어요!")
                            .font(.suite(.extrabold, 22))
                            .foregroundStyle(Color(hex: "#153f35"))
                        Text(paused
                             ? "준비되면 이어서 달려요."
                             : "장애물 \(game.score)개 통과 · 최고 \(game.maxCombo)연속 성공")
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

    private var scoreboard: some View {
        HStack(spacing: 20) {
            VStack(alignment: .leading, spacing: 2) {
                Text("넘은 장애물").font(.suite(.bold, 11)).foregroundStyle(Color(hex: "#c1d4c2"))
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
                ForEach(1...Runner.maxLives, id: \.self) { heart in
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
                    Text("\(game.score)개를 넘었어요. 정답을 익히면 다음엔 더 멀리!")
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
                        Image(systemName: "figure.run")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Color.gg.emerald)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text("정답을 맞히면 자동으로 점프!")
                            .font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                        Text("보기 3개 중 정답을 골라요. 틀리거나 시간이 지나면 하트가 하나 줄어요.")
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
                    ForEach(RunnerEngine.tables, id: \.self) { tableChip($0) }
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
                Text("\(engine.table == nil ? "전체 구구단" : "\(engine.table!)단") · \(game.round + 1)번째 장애물")
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                Spacer()
                Text(timeLabel)
                    .font(.suiteNum(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            }

            // 남은 시간 게이지 — 25% 아래로 떨어지면 주의색
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.gg.surface2)
                    Capsule()
                        .fill(Runner.remaining(game) < 0.25 && game.outcome == nil ? Color.gg.warning : Color.gg.emerald)
                        .frame(width: geo.size.width * Runner.remaining(game))
                }
            }
            .frame(height: 6)

            VStack(spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(game.question.a)").foregroundStyle(Color.gg.text)
                    Text("×").foregroundStyle(Color.gg.textMuted)
                    Text("\(game.question.b)").foregroundStyle(Color.gg.text)
                    Text("=").foregroundStyle(Color.gg.textMuted)
                    Text(game.outcome != nil ? "\(game.question.answer)" : "?")
                        .foregroundStyle(game.outcome == .correct ? Color.gg.success : Color.gg.textMuted)
                }
                .font(.suiteNum(.extrabold, 40))
                .accessibilityLabel("\(game.question.a) 곱하기 \(game.question.b)는?")

                Text(feedback)
                    .font(.suite(.bold, 13))
                    .foregroundStyle(game.outcome == .wrong || game.outcome == .missed
                                     ? Color.gg.warning : Color.gg.textMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 6)

            HStack(spacing: 10) {
                ForEach(Array(game.question.choices.enumerated()), id: \.offset) { index, choice in
                    choiceButton(index: index, choice: choice)
                }
            }
        }
    }

    private var timeLabel: String {
        switch game.outcome {
        case nil: return String(format: "%.1f초", Runner.remaining(game) * Runner.answerWindowMs(score: game.score) / 1000)
        case .correct: return "점프!"
        default: return "정답을 기억해요"
        }
    }

    private func choiceButton(index: Int, choice: Int) -> some View {
        let reveal = game.outcome != nil
        let correct = reveal && choice == game.question.answer
        let wrong = reveal && choice == game.given && choice != game.question.answer
        let border: Color = correct ? .gg.success : wrong ? .gg.warning : .gg.border
        let fill: Color = correct ? Color.gg.success.opacity(0.1)
            : wrong ? Color.gg.warning.opacity(0.1) : Color.gg.surface

        return Button {
            engine.answer(index: index)
        } label: {
            ZStack(alignment: .topLeading) {
                Text("\(choice)")
                    .font(.suiteNum(.extrabold, 30))
                    .foregroundStyle(correct ? Color.gg.success : wrong ? Color.gg.warning : Color.gg.text)
                    .frame(maxWidth: .infinity)
                    .frame(height: 76)
                Text("\(index + 1)")
                    .font(.suite(.bold, 10)).foregroundStyle(Color.gg.textMuted)
                    .padding(8)
                if correct || wrong {
                    HStack {
                        Spacer()
                        Image(systemName: correct ? "checkmark" : "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(correct ? Color.gg.success : Color.gg.warning)
                            .padding(8)
                    }
                }
            }
            .background(fill, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(border, lineWidth: 2))
            .opacity(reveal && !correct && !wrong ? 0.4 : 1)
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!active || reveal)
        .accessibilityLabel("보기 \(index + 1): \(choice)")
    }

    private var footnote: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("장애물 \(Runner.scorePerLevel)개를 넘을 때마다 속도가 한 단계 빨라져요. 앱을 벗어나면 자동으로 일시정지해요.")
            Text("단별 최고 기록은 이 기기에 저장돼요.")
        }
        .font(.suite(.medium, 12))
        .foregroundStyle(Color.gg.textMuted)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }
}
