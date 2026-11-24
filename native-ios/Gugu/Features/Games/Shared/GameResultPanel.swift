import SwiftUI

// 판이 끝난 뒤 결과 창 (Unity 시험판 ResultPanel.cs 이식): 젤리 카드 — 새 기록·오늘의 미션 줄 / 주 점수 / 보조 점수·받은 별(좌우 두 칸)·받은 XP·모은 별 /
// 다시 볼 식 하나 / [다시 하기](0.6초 뒤 활성 — 화면을 끌던 손가락이 바로 누르지 않게) / 카드 밖 아래 작은 [나가기].

struct GameResultContent: Equatable {
    /// 예: "새 기록!", "최고 기록 23" (없으면 빈 문자열)
    let note: String
    /// 예: "오른 계단"
    let mainLabel: String
    let mainValue: Int
    /// 예: "보물", "최고 연속"
    let subLabel: String
    let subValue: Int
    /// 다시 볼 식(없으면 nil)과 그 아래 아이가 낸 답 한 줄(A "고른 답 7", B "고른 카드 2 × 6 = 12")
    let miss: Problem?
    let missGiven: String?
    /// 이번 판에 받은 별
    let stars: Int
    /// 받은 별을 넣은 뒤 모은 별
    let starTotal: Int
    /// 학습 기록으로 받은 XP — 답이 없어 기록하지 않은 판은 nil(줄을 그리지 않음)
    let xpEarned: Int?
    /// 오늘의 미션 줄 — 이번 판으로 미션을 새로 끝냈을 때만(없으면 nil)
    let missionNote: String?

    /// 결과 메모: 새 기록이면 "새 기록!", 지난 기록이 있으면 "최고 기록 N"
    static func note(_ record: PlayRoundRecord) -> String {
        if record.isRecord { return String(localized: "새 기록!") }
        return record.previousBest > 0 ? String(localized: "최고 기록 \(record.previousBest)") : ""
    }

    /// 오늘의 미션 줄: 3개가 처음 다 됐으면 보물상자 안내, 하나라도 새로 끝냈으면 첫 미션, 아니면 nil
    static func missionNote(_ record: PlayRoundRecord) -> String? {
        if record.allMissionsDone { return String(localized: "오늘의 미션 완료! 홈에서 보물상자를 열어 봐요") }
        return record.missionsCompleted.first.map { String(localized: "미션 완료! \($0.title)") }
    }
}

struct GameResultPanel: View {
    let viewport: GameViewport
    let content: GameResultContent
    /// [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초)
    let restartArmed: Bool
    let onRestart: () -> Void
    let onExit: () -> Void

    /// 결과 창이 뜬 뒤 [다시 하기]가 눌리기까지(초)
    static let armDelaySec = 0.6

    var body: some View {
        let u = viewport.unit
        ZStack {
            Color.black.opacity(0.72)
                .contentShape(Rectangle())
                .onTapGesture {}   // 뒤 게임 입력 막기
            card(u)
                .frame(width: 920 * u, height: 1260 * u)
                .position(viewport.screenCenter(x: 0, y: 60))
            // 카드 밖 아래 작은 버튼 — 아이 손이 닿는 [다시 하기]와 떨어뜨려 둔다
            Button {
                Haptics.impact(.light)
                onExit()
            } label: {
                // 진한 둥근 받침 — 뒤에 깔린 답 카드 위에서도 글자가 겹쳐 보이지 않게
                Text("나가기")
                    .font(.suite(.extrabold, 46 * u))
                    .foregroundStyle(GamePalette.text)
                    .frame(width: 420 * u, height: 110 * u)
                    .background(GamePalette.background.opacity(0.95), in: Capsule())
                    .overlay(Capsule().strokeBorder(GamePalette.wall, lineWidth: 4 * u))
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .position(viewport.screenCenter(x: 0, y: -640))
        }
        .frame(width: viewport.size.width, height: viewport.size.height)
    }

    private func card(_ u: CGFloat) -> some View {
        ZStack {
            // 아래 입술(72px)이 [다시 하기]에 걸리지 않게 1260 높이
            JellyBackground(color: GamePalette.card, pointsPerPixel: u)
            if let mission = content.missionNote {
                // 미션 줄이 있으면 메모를 위로 올리고 그 아래(주 점수 이름 위)에 한 줄 — 위 테두리·이름과 간격을 둔다
                label(content.note, size: 48, color: GamePalette.gold, y: 570, u: u)
                label(mission, size: 40, color: GamePalette.correct, y: 512, u: u)
                label(content.mainLabel, size: 56, color: GamePalette.textDim, y: 445, u: u)
            } else {
                label(content.note, size: 48, color: GamePalette.gold, y: 545, u: u)
                label(content.mainLabel, size: 56, color: GamePalette.textDim, y: 450, u: u)
            }
            label("\(content.mainValue)", size: 190, color: GamePalette.text, y: 310, u: u)

            // 왼쪽 칸: 보조 점수 / 오른쪽 칸: 받은 별(코인 + "+N") 과 모은 별
            label(content.subLabel, size: 48, color: GamePalette.textDim, x: -220, y: 175, u: u)
            label("\(content.subValue)", size: 96, color: GamePalette.gold, x: -220, y: 80, u: u)
            label(String(localized: "받은 별"), size: 48, color: GamePalette.textDim, x: 220, y: 175, u: u)
            GameArt.image("icon_coin")
                .resizable()
                .scaledToFit()
                .frame(width: 88 * u, height: 88 * u)
                .offset(x: 135 * u, y: -80 * u)
            Text(verbatim: "+\(content.stars)")
                .font(.suiteNum(.extrabold, 96 * u))
                .foregroundStyle(GamePalette.gold)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(width: 260 * u, height: 120 * u, alignment: .leading)
                .offset(x: 315 * u, y: -80 * u)
            label(String(localized: "모은 별 \(content.starTotal)"), size: 38, color: GamePalette.textDim, x: 220, y: 5, u: u)
            // 왼쪽 칸 아래: 학습 기록 XP(앱 레벨) — 오른쪽 "모은 별" 과 같은 줄
            if let xp = content.xpEarned {
                label("+\(xp) XP", size: 38, color: GamePalette.textDim, x: -220, y: 5, u: u)
            }

            Rectangle()
                .fill(GamePalette.wall)
                .frame(width: 760 * u, height: 4 * u)
                .offset(y: 50 * u)

            label(String(localized: "다시 볼 식"), size: 44, color: GamePalette.textDim, y: -120, u: u)
            if let miss = content.miss {
                label("\(miss.a) × \(miss.b) = \(miss.a * miss.b)", size: 92, color: GamePalette.correct, y: -215, u: u)
                if let given = content.missGiven {
                    label(given, size: 44, color: GamePalette.wrong, y: -300, u: u)
                }
            } else {
                label(String(localized: "틀린 식 없음"), size: 92, color: GamePalette.correct, y: -215, u: u)
            }

            JellyButton(title: String(localized: "다시 하기"), color: GamePalette.correct, textColor: GamePalette.background,
                        fontSize: 84, jellyScale: 0.9, unit: u, enabled: restartArmed, action: onRestart)
                .frame(width: 760 * u, height: 200 * u)
                .offset(y: 455 * u)
        }
    }

    /// 카드 가운데 기준 설계 좌표(위가 +)에 한 줄 글자
    private func label(_ text: String, size: CGFloat, color: Color, x: CGFloat = 0, y: CGFloat, u: CGFloat) -> some View {
        Text(verbatim: text)
            .font(.suite(.extrabold, size * u))
            .foregroundStyle(color)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(width: (x == 0 ? 860 : 420) * u, height: max(120, size * 1.3) * u)
            .offset(x: x * u, y: -y * u)
    }
}
