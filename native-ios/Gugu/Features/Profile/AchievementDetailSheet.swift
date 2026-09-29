import SwiftUI

// 업적 상세 — 무엇을 하면 얻는지, 지금 어디까지 왔는지 보여 준다.
// 잠긴 업적도 목표가 보여야 아이가 다음 목표로 삼을 수 있다.

struct AchievementDetailSheet: View {
    let def: AchievementDef
    @Environment(GameStore.self) private var game

    var body: some View {
        let unlocked = game.state.unlockedAchievements.contains(def.id)
        let progress = Achievements.progress(def, game.state)
        VStack(spacing: 18) {
            Image(systemName: IconMap.sf(def.icon))
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(unlocked ? Color.gg.accent : Color.gg.textMuted)
                .frame(width: 72, height: 72)
                .background((unlocked ? Color.gg.accent : Color.gg.textMuted).opacity(0.14), in: Circle())
            VStack(spacing: 6) {
                Text(def.name).font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text)
                Text(def.description).font(.suite(.medium, 15)).foregroundStyle(Color.gg.textMuted)
                    .multilineTextAlignment(.center)
            }
            if unlocked {
                Pill(bg: Color.gg.success.opacity(0.15), fg: .gg.success) {
                    Image(systemName: "checkmark.seal.fill")
                    Text("달성했어요!")
                }
            } else {
                VStack(spacing: 8) {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.gg.surface2)
                            Capsule().fill(Color.gg.accent)
                                .frame(width: geo.size.width * CGFloat(progress.fraction))
                        }
                    }
                    .frame(height: 10)
                    Text("\(progress.current) / \(progress.target)")
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                }
                .padding(.horizontal, 12)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("진행도 \(progress.target) 중 \(progress.current)")
            }
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 32)
        .frame(maxWidth: .infinity)
        .presentationDetents([.height(330)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.gg.bg)
    }
}
