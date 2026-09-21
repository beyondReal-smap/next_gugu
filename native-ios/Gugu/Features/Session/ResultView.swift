import SwiftUI

// 세션 결과 화면 (ResultScreen.tsx 이식)

struct ResultView: View {
    @Bindable var engine: SessionEngine
    let done: SessionDone
    var onClose: () -> Void

    @Environment(GameStore.self) private var game
    @State private var confetti = 0
    @State private var levelUp = false

    private var result: SessionResult { done.result }
    private var commit: CommitResult { done.commit }
    private var def: ModeDef { Modes.def(result.mode) }

    private var total: Int { result.answers.count }
    private var correct: Int { total - done.wrongCount }
    private var accuracy: Int { total > 0 ? Int((Double(correct) / Double(total) * 100).rounded()) : 0 }
    private var avgMs: Int { total > 0 ? Int((Double(result.answers.reduce(0) { $0 + $1.ms }) / Double(total)).rounded()) : 0 }
    private var best: Int? {
        guard let score = commit.score else { return nil }
        return max(game.state.bestScores[result.mode] ?? 0, score)
    }

    private var headline: String {
        if def.scored { return commit.isNewBest ? "신기록 달성! 🏆" : "수고했어요!" }
        if accuracy >= 95 { return "완벽해요! 🎯" }
        if accuracy >= 70 { return "잘했어요!" }
        return "좋은 시도예요!"
    }

    var body: some View {
        ZStack(alignment: .top) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(def.name) 완료").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                        Text(headline).font(.suite(.extrabold, 28)).foregroundStyle(Color.gg.text)
                    }
                    .padding(.top, 24)

                    if def.scored, let score = commit.score {
                        scoreHero(score)
                    }
                    statGrid
                    if let table = commit.table {
                        masteryRow(table)
                    }
                    if commit.goalReached {
                        Text("🎉 오늘의 목표를 달성했어요!")
                            .font(.suite(.bold, 14)).foregroundStyle(Color.gg.success)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.gg.success.opacity(0.15), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }

                    Spacer(minLength: 8)
                    actions
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }

            ConfettiView(trigger: confetti)
            AchievementToast(ids: commit.unlocked)
                .padding(.horizontal, 20).padding(.top, 12)
            LevelUpOverlay(show: levelUp, level: commit.newLevel) { levelUp = false }
                .animation(.spring(response: 0.4, dampingFraction: 0.7), value: levelUp)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gg.bg.ignoresSafeArea())
        .onAppear {
            if accuracy >= 80 || commit.isNewBest { confetti += 1 }
            if commit.leveledUp {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { levelUp = true }
            }
        }
    }

    private func scoreHero(_ score: Int) -> some View {
        VStack(spacing: 8) {
            if commit.isNewBest {
                Pill(bg: Color.gg.warning.opacity(0.15), fg: .gg.warning) {
                    Image(systemName: "trophy.fill"); Text("신기록!")
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("\(score)").font(.suite(.extrabold, 56)).foregroundStyle(Color.gg.text).monospacedDigit()
                Text("점").font(.suite(.bold, 20)).foregroundStyle(Color.gg.textMuted)
            }
            if let best { Text("최고 기록 \(best)점").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit() }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .ggCard()
    }

    private var statGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            stat(icon: "checkmark", tint: .gg.success, label: "정확도", value: "\(accuracy)%")
            stat(icon: "gauge.medium", tint: .gg.accent, label: "평균 속도", value: String(format: "%.1f초", Double(avgMs) / 1000))
            stat(icon: "sparkles", tint: .gg.warning, label: "획득 XP", value: "+\(commit.xpEarned)")
            stat(icon: "flame.fill", tint: .gg.danger, label: "최고 콤보", value: "\(result.maxCombo)")
        }
    }

    private func stat(icon: String, tint: Color, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 14, weight: .bold)).foregroundStyle(tint)
                Text(label).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            }
            Text(value).font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
    }

    private func masteryRow(_ table: Int) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(table)단 마스터리").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                if commit.improvedStars {
                    Text("새 기록 달성!").font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                }
            }
            Spacer()
            StarsView(count: commit.newStars, size: 24)
        }
        .padding(.horizontal, 20).padding(.vertical, 16)
        .ggCard()
    }

    private var actions: some View {
        VStack(spacing: 10) {
            if def.kind == .fixed && done.wrongCount > 0 {
                GGButton(variant: .surface, size: .lg, action: { engine.restart(retryWrong: true) }) {
                    Image(systemName: "arrow.counterclockwise")
                    Text("틀린 문제만 다시 (\(done.wrongCount))")
                }
            }
            GGButton(variant: .primary, size: .lg, action: { engine.restart(retryWrong: false) }) {
                Text("한 판 더")
            }
            GGButton(variant: .ghost, size: .md, action: onClose) {
                Image(systemName: "xmark"); Text("닫기")
            }
        }
    }
}
