import SwiftUI

// 하루 목표 고르기 — 홈의 "오늘의 목표" 카드를 누르면 뜬다 (설정 > 학습 > 하루 목표와 같은 값)

struct DailyGoalSheet: View {
    @Environment(GameStore.self) private var game
    @Environment(ReminderStore.self) private var reminder
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("하루 목표").font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text)
                Text("하루에 맞힐 정답 수를 골라요. 목표를 채우면 그날 학습 알림은 쉬어요.")
                    .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            ChoiceChips(
                options: SettingsView.dailyGoalOptions,
                selected: game.state.dailyGoal,
                label: { "\($0)개" }
            ) { goal in
                game.setDailyGoal(goal)
                Task { await reminder.reschedule(state: game.state) }
            }
            Text(hint(for: game.state.dailyGoal))
                .font(.suite(.bold, 13)).foregroundStyle(Color.gg.accent)
            GGButton(variant: .primary, size: .lg, action: { dismiss() }) { Text("좋아요") }
        }
        .padding(24)
        .presentationDetents([.height(330)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.gg.bg)
    }

    private func hint(for goal: Int) -> String {
        switch goal {
        case ..<15: return "가볍게 — 한 판(10문제)이면 거의 채워요"
        case ..<25: return "보통 — 두 판쯤이면 채워요"
        case ..<40: return "열심히 — 세 판쯤이면 채워요"
        default: return "도전 — 다섯 판쯤이면 채워요"
        }
    }
}
