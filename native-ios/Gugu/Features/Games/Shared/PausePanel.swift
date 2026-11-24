import SwiftUI

// 게임 중 나가기 (Unity 시험판 PausePanel.cs 이식): 위 배지 줄 왼쪽 끝 [⏸] → "잠깐 쉬는 중" 젤리 창([계속 하기] / [메뉴로 나가기]).
// 바로 나가는 버튼이 아니라 창을 한 번 거치는 이유: 아이가 연타·실수로 판을 잃지 않게.
// 창이 열려 있는 동안 게임은 입력·시간(A 먹구름, B 시계)을 멈춘다(엔진 몫). 별·기록은 결과 화면 때만 저장되므로
// 나가면 이번 판 별·기록이 사라진다고 알려 준다.

enum PauseLayout {
    /// [⏸] 크기 = 배지 높이(설계 px)
    static let buttonSize = HudBadgeState.height
    /// 배지 줄 왼쪽 끝(-526 ~ -416) — 각 게임의 첫 배지는 이보다 오른쪽에서 시작
    static let buttonX: CGFloat = -471
    /// 안전 영역 위 끝에서 배지 줄 중심까지(설계 px, 아래가 -). 아이콘이 통통 튀어도(1.18배) 안전 영역 위로 안 나감
    static let hudRowY: CGFloat = -80

    /// [⏸] 의 화면 사각형 — 이 위 탭은 게임 입력에서 뺀다(A 는 화면 x 로 칸을 고르므로 왼쪽 칸 점프로 샘)
    static func buttonFrame(_ viewport: GameViewport) -> CGRect {
        viewport.rect(center: viewport.safeTop(x: buttonX, y: hudRowY), designSize: CGSize(width: buttonSize, height: buttonSize))
    }

    /// 창 문구 — 이번 판에 받은 별이 있으면 별 수도
    static func message(starsThisRound stars: Int) -> String {
        stars > 0
            ? String(localized: "지금 나가면 이번 판 별 \(stars)개와\n기록이 사라져요")
            : String(localized: "지금 나가면 이번 판 기록이 사라져요")
    }
}

/// 배지와 같은 크림 알약(정사각 → 동그라미) + 남색 막대 2개. 글꼴에 ⏸ 기호가 없을 수 있어 도형으로 그림
struct PauseButton: View {
    let unit: CGFloat
    let action: () -> Void

    var body: some View {
        let size = PauseLayout.buttonSize * unit
        Button {
            Haptics.impact(.light)
            action()
        } label: {
            ZStack {
                PillBackground()
                HStack(spacing: 12 * unit) {
                    ForEach(0..<2, id: \.self) { _ in
                        Rectangle()
                            .fill(GamePalette.ink)
                            .frame(width: 18 * unit, height: 48 * unit)
                    }
                }
            }
            .frame(width: size, height: size)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("잠깐 쉬기"))
    }
}

/// "잠깐 쉬는 중" 창 — 어두운 막이 화면 전체(시스템 영역 포함)를 덮어 뒤 게임이 눌리지 않게 한다
struct PausePanelView: View {
    let viewport: GameViewport
    /// 이번 판에 받은 별(창 문구용)
    let starsThisRound: Int
    let onResume: () -> Void
    let onQuit: () -> Void

    var body: some View {
        let u = viewport.unit
        let cardCenter = viewport.screenCenter(x: 0, y: 60)
        ZStack {
            Color.black.opacity(0.6)
                .contentShape(Rectangle())
                .onTapGesture {}   // 뒤 게임 입력 막기
            ZStack {
                // 아래 입술(72px)에 [메뉴로 나가기]가 걸리지 않게 860 높이
                JellyBackground(color: GamePalette.card, pointsPerPixel: u)
                Text("잠깐 쉬는 중")
                    .font(.suite(.extrabold, 88 * u))
                    .foregroundStyle(GamePalette.text)
                    .offset(y: -300 * u)
                Text(PauseLayout.message(starsThisRound: starsThisRound))
                    .font(.suite(.extrabold, 46 * u))
                    .foregroundStyle(GamePalette.gold)
                    .multilineTextAlignment(.center)
                    .frame(width: 820 * u, height: 140 * u)
                    .offset(y: -155 * u)
                JellyButton(title: String(localized: "계속 하기"), color: GamePalette.correct, textColor: GamePalette.background,
                            fontSize: 84, jellyScale: 0.9, unit: u, action: onResume)
                    .frame(width: 760 * u, height: 200 * u)
                    .offset(y: 50 * u)
                JellyButton(title: String(localized: "메뉴로 나가기"), color: GamePalette.quit, textColor: GamePalette.ink,
                            fontSize: 56, jellyScale: 0.7, unit: u, action: onQuit)
                    .frame(width: 760 * u, height: 150 * u)
                    .offset(y: 280 * u)
            }
            .frame(width: 880 * u, height: 860 * u)
            .position(cardCenter)
        }
        .frame(width: viewport.size.width, height: viewport.size.height)
    }
}

/// 둥근 젤리 버튼(Unity JellyUi.MakeButton): 젤리 판 + 가운데 글자(입술만큼 위로)
struct JellyButton: View {
    let title: String
    let color: Color
    let textColor: Color
    /// 글자 크기(설계 px)
    let fontSize: CGFloat
    /// 모서리·외곽선·입술 배율(Unity JellyUi scale)
    let jellyScale: CGFloat
    let unit: CGFloat
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.impact(.light)
            action()
        } label: {
            ZStack {
                JellyBackground(color: color, pointsPerPixel: jellyScale * unit)
                Text(title)
                    .font(.suite(.extrabold, fontSize * unit))
                    .foregroundStyle(textColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .offset(y: -JellyBackground.labelLift * jellyScale * unit)
            }
            .contentShape(Rectangle())
            // 아직 누를 수 없을 때(결과 창 0.6초) Unity 버튼처럼 흐리게
            .opacity(enabled ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
