import SwiftUI

// 홈 「오늘의 미션」 카드 (2.9.0) — 날마다 바뀌는 미션 3개, 다 하면 보물상자(별 +10), 이번 주 도장(월~일).
// 진행도·상자·도장 규칙은 PlayRewardsStore(DailyMissions) 가 정하고, 이 카드는 보여 주고 [보물상자 열기!]만 넘긴다.
// 상자를 열면 별이 바로 저장되므로 아래 구구 꾸미기 카드의 모은 별도 함께 늘어난다.

struct DailyMissionsCard: View {
    @Environment(PlayRewardsStore.self) private var playRewards
    /// 앱으로 돌아올 때마다 카드를 다시 그리게 한다 — 그사이 날짜가 바뀌었으면 새 미션을 읽는다(missionBoard 는 그리는 순간의 오늘 기준)
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 방금 연 상자에서 받은 별 — "별 10개를 받았어요!" (카드를 다시 열면 "내일 새 미션이 와요!"만)
    @State private var receivedStars: Int?
    @State private var confetti = 0
    /// 준비된 상자가 통통 튀는 위상(0 ↔ 1 반복)
    @State private var bouncing = false
    /// 상자가 열리는 순간 커졌다 돌아오는 크기
    @State private var chestScale: CGFloat = 1

    var body: some View {
        let board = playRewards.missionBoard(now: Date())
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 12) {
                header(board)
                ForEach(Array(board.items.enumerated()), id: \.offset) { _, item in
                    missionRow(item)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "오늘의 미션, \(board.items.count)개 중 \(board.doneCount)개 완료"))
            .accessibilityValue(board.items.map { "\($0.mission.title) \($0.progress)/\($0.mission.target)" }.joined(separator: ", "))

            footer(board)
            Rectangle().fill(Color.gg.border).frame(height: 1)
            weekStamps(board.week)
        }
        .ggCard(padding: 16)
        .overlay(ConfettiView(trigger: confetti))
        .onChange(of: board.chestReady && !reduceMotion, initial: true) { _, bounce in
            bouncing = bounce
        }
    }

    // MARK: 제목 줄 + 보물상자

    private func header(_ board: MissionBoard) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "flag.checkered").font(.system(size: 14, weight: .bold)).foregroundStyle(Color.gg.accent)
            Text("오늘의 미션").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
            Spacer(minLength: 8)
            GameArt.image(board.chestOpened ? "chest_open" : "chest_closed")
                .resizable()
                .scaledToFit()
                .frame(width: 56, height: 56)
                .scaleEffect(chestScale)
                .offset(y: bouncing ? -6 : 0)
                .animation(bouncing ? .easeInOut(duration: 0.42).repeatForever(autoreverses: true) : .default, value: bouncing)
                .frame(height: 40)   // 그림은 크게, 줄 높이는 제목에 맞춘다
        }
    }

    // MARK: 미션 한 줄 — 아이콘 · 문구 · 진행 막대 · "7/15" · 다 하면 초록 도장

    private func missionRow(_ item: MissionBoard.Item) -> some View {
        HStack(spacing: 12) {
            missionIcon(item.mission.kind)
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(item.mission.title)
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                        .lineLimit(1).minimumScaleFactor(0.75)
                    Spacer(minLength: 4)
                    Text(verbatim: "\(item.progress)/\(item.mission.target)")
                        .font(.suite(.extrabold, 13)).monospacedDigit()
                        .foregroundStyle(item.done ? Color.gg.success : Color.gg.textMuted)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(item.done ? Color.gg.success : Color.gg.accent)
                            .frame(width: geo.size.width * CGFloat(item.progress) / CGFloat(item.mission.target))
                    }
                }
                .frame(height: 8)
            }
            Image(systemName: item.done ? "checkmark.seal.fill" : "circle.dashed")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(item.done ? Color.gg.success : Color.gg.border)
                .frame(width: 26)
        }
    }

    private func missionIcon(_ kind: MissionKind) -> some View {
        Group {
            switch kind {
            case .stairsSteps: gameThumb(.stairs)
            case .monstersDefeated: gameThumb(.monster)
            case .bestStreak:
                Image(systemName: "flame.fill")
                    .font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.danger)
                    .frame(width: 40, height: 40)
                    .background(Color.gg.danger.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            case .starsEarned:
                GameArt.image("icon_coin").resizable().scaledToFit()
                    .padding(8)
                    .frame(width: 40, height: 40)
                    .background(Color.gg.warning.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .accessibilityHidden(true)
    }

    private func gameThumb(_ game: Minigame) -> some View {
        GameArt.image(game.thumbnail).resizable().scaledToFit()
            .padding(4)
            .frame(width: 40, height: 40)
            .background(game.iconBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: 아래 — 상자 열기 / 안내

    @ViewBuilder
    private func footer(_ board: MissionBoard) -> some View {
        if board.chestReady {
            GGButton(variant: .primary, size: .lg, action: openChest) {
                Image(systemName: "gift.fill")
                Text("보물상자 열기!")
            }
        } else if board.chestOpened {
            VStack(alignment: .leading, spacing: 4) {
                if let receivedStars {
                    HStack(spacing: 6) {
                        GameArt.image("icon_coin").resizable().scaledToFit().frame(width: 20, height: 20)
                            .accessibilityHidden(true)
                        Text("별 \(receivedStars)개를 받았어요!").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.warning)
                    }
                    .transition(.scale.combined(with: .opacity))
                }
                Text("내일 새 미션이 와요!").font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text("미션 3개를 다 하면 보물상자가 열려요")
                .font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 버튼은 상자가 준비됐을 때만 보이지만, 연타로 두 번째 탭이 화면이 바뀌기 전에 오면 무시한다
    private func openChest() {
        guard playRewards.missionBoard(now: Date()).chestReady else { return }
        let earned: Int
        do {
            earned = try playRewards.openChest()
        } catch {
            // 바로 위에서 준비된 상자만 통과시켰으므로 여기 오면 규칙 버그
            preconditionFailure("화면이 막았어야 하는 보물상자 열기: \(error)")
        }
        Sound.shared.complete()
        Haptics.success()
        confetti += 1
        withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { receivedStars = earned }
        if reduceMotion { return }
        chestScale = 1.35
        withAnimation(.spring(response: 0.45, dampingFraction: 0.45)) { chestScale = 1 }
    }

    // MARK: 이번 주 도장 — 월~일, 상자를 연 날은 별, 오늘은 테두리

    private func weekStamps(_ week: [WeekStamp]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("이번 주 도장").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            HStack(spacing: 0) {
                ForEach(week, id: \.date) { day in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle().fill(day.stamped ? Color.gg.warning.opacity(0.18) : Color.gg.surface2)
                            if day.stamped {
                                GameArt.image("star").resizable().scaledToFit().padding(6)
                            }
                        }
                        .frame(width: 34, height: 34)
                        .overlay(Circle().strokeBorder(day.isToday ? Color.gg.accent : .clear, lineWidth: 2.5))
                        Text(WeeklyReport.weekdayLabel(day.date))
                            .font(.suite(day.isToday ? .extrabold : .bold, 11))
                            .foregroundStyle(day.isToday ? Color.gg.accent : Color.gg.textMuted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(String(localized: "이번 주 도장"))
        .accessibilityValue(week.filter(\.stamped).map { WeeklyReport.weekdayName($0.date) }.joined(separator: ", "))
    }
}
