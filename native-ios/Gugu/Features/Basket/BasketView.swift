import SwiftUI

// 구구 바구니 화면 (BasketScreen.tsx 이식)
// 무대를 좌우로 끌거나 방향 버튼을 눌러 바구니를 옮겨 정답 열매를 받는다.
// 웹의 키보드 조작(← →)은 웹 전용이라 제외했다.

struct BasketView: View {
    var onExit: () -> Void

    @State private var engine = BasketEngine()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var game: BasketState { engine.game }
    private var ready: Bool { game.phase == .ready }
    private var over: Bool { game.phase == .over }
    private var paused: Bool { game.phase == .paused }
    private var playing: Bool { !ready && !over }

    private var feedback: String {
        let question = game.question
        switch game.outcome {
        case .correct:
            return game.combo >= 3 ? "\(game.combo)연속! 별이 반짝반짝!" : "쏙! 정답 열매를 받았어요!"
        case .wrong:
            return "괜찮아요! \(question.a) × \(question.b) = \(question.answer)"
        case .missed:
            return "다음엔 받아봐요! 정답은 \(question.answer)"
        case nil:
            return "정답 열매 아래로 바구니를 옮겨요!"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 16) {
                    stage
                    controls
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
                Image(systemName: "basket.fill")
                Text("토끼의 작은 과수원")
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
            if playing { questionBar }
            ZStack {
                GeometryReader { geo in
                    OrchardScene(game: game, reducedMotion: reduceMotion)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    guard game.phase == .running, geo.size.width > 0 else { return }
                                    engine.drag(ratio: value.location.x / geo.size.width)
                                }
                        )
                }
                .aspectRatio(6.0 / 5.0, contentMode: .fit)
                .frame(maxWidth: .infinity)

                if paused {
                    Color(hex: "#fff8e9").opacity(0.95)
                    VStack(spacing: 12) {
                        Text("잠깐 쉬어가요")
                            .font(.suite(.extrabold, 20)).foregroundStyle(Color(hex: "#794124"))
                        GGButton(variant: .primary, size: .md, action: { engine.togglePause() }) {
                            Text("이어서 받기")
                        }
                        .frame(maxWidth: 200)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .clipped()
        }
        .background(Color.gg.surface)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
            .strokeBorder(Color(hex: "#794124").opacity(0.2), lineWidth: 1))
    }

    private var scoreboard: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "basket.fill").font(.system(size: 15, weight: .bold))
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text("\(game.score)").font(.suiteNum(.extrabold, 16))
                    Text("개").font(.suite(.bold, 12))
                }
            }
            .foregroundStyle(Color(hex: "#fff5db"))

            HStack(spacing: 4) {
                Image(systemName: "trophy.fill").font(.system(size: 11, weight: .bold))
                Text("\(engine.displayBest)").font(.suiteNum(.bold, 13))
            }
            .foregroundStyle(Color(hex: "#ffe1aa"))

            Spacer()

            HStack(spacing: 4) {
                ForEach(1...Basket.maxLives, id: \.self) { heart in
                    Image(systemName: heart <= game.lives ? "heart.fill" : "heart")
                        .font(.system(size: 14))
                        .foregroundStyle(heart <= game.lives ? Color(hex: "#ffbd9b") : Color(hex: "#b0866c"))
                }
            }
            .accessibilityLabel("남은 하트 \(game.lives)개")

            if playing {
                Button {
                    engine.togglePause()
                } label: {
                    Image(systemName: paused ? "play.fill" : "pause.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(hex: "#fff5db"))
                        .frame(width: 44, height: 44)
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
                }
                .accessibilityLabel(paused ? "게임 계속하기" : "일시정지")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(hex: "#794124"))
    }

    private var questionBar: some View {
        VStack(spacing: 4) {
            Text("\(game.table == nil ? "전체 구구단" : "\(game.table!)단") · \(Basket.level(score: game.score))단계")
                .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(game.question.a)").foregroundStyle(Color.gg.text)
                Text("×").foregroundStyle(Color.gg.textMuted)
                Text("\(game.question.b)").foregroundStyle(Color.gg.text)
                Text("=").foregroundStyle(Color.gg.textMuted)
                Text(game.outcome != nil ? "\(game.question.answer)" : "?")
                    .foregroundStyle(game.outcome == .correct ? Color.gg.success : Color.gg.textMuted)
            }
            .font(.suiteNum(.extrabold, 30))
            .accessibilityLabel("\(game.question.a) 곱하기 \(game.question.b)는?")
            Text(feedback)
                .font(.suite(.bold, 12))
                .foregroundStyle(game.outcome == .wrong || game.outcome == .missed ? Color.gg.warning : Color.gg.textMuted)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color.gg.surface)
    }

    // MARK: - 조작부

    @ViewBuilder
    private var controls: some View {
        if playing {
            VStack(spacing: 6) {
                HStack(spacing: 12) {
                    moveButton(direction: -1)
                    moveButton(direction: 1)
                }
                Text("화면을 좌우로 끌거나 방향 버튼을 꾹 눌러요")
                    .font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted)
            }
        } else {
            setupPanel
                .padding(16)
                .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color.gg.border, lineWidth: 1))
        }
    }

    /// 누르고 있는 동안 계속 움직이고, 손을 떼면 멈춘다
    private func moveButton(direction: Int) -> some View {
        let label = direction < 0 ? "왼쪽" : "오른쪽"
        return HStack(spacing: 8) {
            Image(systemName: direction < 0 ? "arrow.left" : "arrow.right")
                .font(.system(size: 20, weight: .bold))
            Text(label).font(.suite(.extrabold, 15))
        }
        .foregroundStyle(Color(hex: "#794124"))
        .frame(maxWidth: .infinity)
        .frame(height: 56)
        .background(Color(hex: "#f6e8c9"), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .opacity(paused ? 0.4 : 1)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !paused, engine.direction != direction else { return }
                    engine.direction = direction
                    engine.nudge(direction)
                    Haptics.selection()
                }
                .onEnded { _ in engine.direction = 0 }
        )
        .accessibilityLabel(direction < 0 ? "바구니 왼쪽으로" : "바구니 오른쪽으로")
        .accessibilityAction { engine.nudge(direction * 2) }
    }

    private var setupPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            if over {
                VStack(spacing: 6) {
                    Text("열매 \(game.score)개를 모았어요!")
                        .font(.suite(.extrabold, 19)).foregroundStyle(Color.gg.text)
                    Text("최고 \(game.maxCombo)연속 정답 · \(game.score > 0 && game.score >= engine.runBest ? "나의 최고 기록이에요!" : "토끼와 다시 놀아볼까요?")")
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text("정답 열매 아래로, 쏙!")
                        .font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                    Text("바구니를 직접 움직여 정답 열매를 받아요. 다른 열매를 받거나 놓치면 하트가 하나 줄어요. 처음에는 2단부터 천천히!")
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("연습할 단")
                    .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 5)
                LazyVGrid(columns: columns, spacing: 8) {
                    tableChip(nil)
                    ForEach(BasketEngine.tables, id: \.self) { tableChip($0) }
                }
            }

            GGButton(variant: .primary, size: .lg, action: { engine.start() }) {
                Image(systemName: over ? "arrow.counterclockwise" : "play.fill")
                Text(over ? "다시 받기" : "열매 받기 시작")
            }

            Text("하트 3개 · 정답 3개마다 조금씩 빨라져요. 단별 최고 기록은 이 기기에 저장돼요.")
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
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
                .foregroundStyle(selected ? Color(hex: "#fff5db") : Color.gg.text)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(selected ? Color(hex: "#794124") : Color.gg.surface2)
                )
        }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
