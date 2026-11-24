import SwiftUI

// 게임 결과 위 학습 기록 알림 — 세션 결과와 같은 공용 부품(AchievementToast·LevelUpOverlay, Feedback.swift)을 쓴다.
// 결과 창이 뜰 때 기록한 CommitResult 를 받아, 새 업적은 위쪽 토스트로, 레벨 업은 잠깐 뒤 가운데 창으로 보여 준다.
// 게임 화면은 안전 영역 밖까지 그리므로 토스트는 안전 영역 위 끝 아래에 둔다.

struct GameCommitFeedback: View {
    /// 이번 판의 학습 기록 결과(아직 없거나 답이 없어 기록하지 않았으면 nil)
    let commit: CommitResult?
    /// 판 번호 — 다시 하기로 새 판이 되면 같은 레벨이어도 다시 알린다
    let round: Int
    let viewport: GameViewport

    /// 결과 창이 먼저 보이고 나서 레벨 업 창을 띄우기까지(초) — 세션 결과(SessionView)와 같은 간격
    static let levelUpDelaySec = 0.7

    @State private var levelUp = false

    var body: some View {
        ZStack {
            if let commit, !commit.unlocked.isEmpty {
                AchievementToast(ids: commit.unlocked)
                    .padding(.horizontal, 20)
                    .padding(.top, viewport.safeArea.top + 12)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .allowsHitTesting(false)
            }
            LevelUpOverlay(show: levelUp, level: commit?.newLevel ?? 0) { levelUp = false }
                .animation(.spring(response: 0.4, dampingFraction: 0.7), value: levelUp)
        }
        .frame(width: viewport.size.width, height: viewport.size.height)
        .onChange(of: levelUpKey) { _, key in
            guard key != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.levelUpDelaySec) {
                Sound.shared.levelUp()
                levelUp = true
            }
        }
    }

    /// 레벨이 오른 판이면 (판 번호, 새 레벨) — 바뀌는 순간 한 번 알린다
    private var levelUpKey: [Int]? {
        guard let commit, commit.leveledUp else { return nil }
        return [round, commit.newLevel]
    }
}
