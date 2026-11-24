import SwiftUI

// 구구 꾸미기(옷장) 화면 (Unity 시험판 WardrobeScreen.cs 의 화면 구성 이식)
// 위 [메뉴로]·모은 별 배지, 제목, 가운데 둥근 받침 위 큰 구구(써 보기), 장신구 카드 3×2(값·"가짐"·"쓰는 중"·빛 테두리),
// 아래 젤리 버튼(사기 / 별 N개 더 모아요 / 쓰기 / 벗기). 규칙·상태는 WardrobeModel, 여기서는 그리기와 누름 연결만.
// 좌표는 게임 화면과 같은 설계 px(1080 폭, GameViewport) — 바탕은 화면 전체, 내용은 안전 영역 안.

struct WardrobeView: View {
    let onClose: () -> Void

    @State private var model: WardrobeModel?
    @Environment(PlayRewardsStore.self) private var rewards

    /// 하늘빛 바탕
    static let backdrop = Color(hex: "#CFE9FF")

    var body: some View {
        // 안전 영역을 읽는 바깥 GeometryReader + 전체 화면 안쪽(게임 화면과 같은 방식)
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let full = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let viewport = GameViewport(size: full, safeArea: insets)
            ZStack {
                Self.backdrop
                if let model {
                    WardrobeContent(model: model, viewport: viewport, onClose: close)
                }
            }
            .frame(width: full.width, height: full.height)
            .ignoresSafeArea()
        }
        .background(Self.backdrop.ignoresSafeArea())
        .onAppear {
            // 모델은 환경의 저장소가 필요해 화면이 뜰 때 만든다 — 열 때마다 쓰고 있는 것이 골라진 채로
            if model == nil { model = WardrobeModel(rewards: rewards) }
        }
        .onDisappear { model?.teardown() }
    }

    private func close() {
        model?.teardown()
        onClose()
    }
}

private struct WardrobeContent: View {
    let model: WardrobeModel
    let viewport: GameViewport
    let onClose: () -> Void

    private static let cardSize = CGSize(width: 300, height: 250)
    private static let cardGapX: CGFloat = 330
    /// 윗줄·아랫줄 가운데 높이(안전 영역 아래 끝에서 위로 — 엄지 영역)
    private static let rowY: [CGFloat] = [690, 410]
    private static let previewSize: CGFloat = 440
    /// 미리 보기 칸: 위 끝(안전 영역 위에서)~아래 끝(안전 영역 아래에서). 키 큰 화면에서 가운데가 비지 않게
    /// 제목 아래~카드 위 사이를 늘어나는 칸으로 잡고 그 가운데에 둔다(1920 높이에서는 위에서 650)
    private static let stageTop: CGFloat = 230
    private static let stageBottom: CGFloat = 850
    /// 카드 색(무채색 젤리에 곱함) — 장신구 순서
    private static let cardColors: [Color] = ["#FFC2CC", "#D9F5C5", "#CDEBFF", "#D6D3E6", "#E3D6FF", "#FFF0B3"].map { Color(hex: $0) }

    var body: some View {
        let u = viewport.unit
        ZStack {
            topRow(u)
            Text("구구 꾸미기")
                .font(.suite(.extrabold, 84 * u))
                .foregroundStyle(GamePalette.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .placed(at: viewport.safeTop(x: 0, y: -210), size: viewport.px(CGSize(width: 1000, height: 120)))
                .accessibilityAddTraits(.isHeader)
            preview(u)
                .position(stageCenter)
            // 카드 모습은 body 에서 값으로 만들어 넘긴다(ForEach 안에서 모델을 읽으면 갱신이 빠질 수 있음 — MonsterView 참고)
            ForEach(cardFaces) { face in
                card(face, u)
                    .position(viewport.safeBottom(x: CGFloat(face.id.rawValue % 3 - 1) * Self.cardGapX,
                                                  y: Self.rowY[face.id.rawValue / 3]))
            }
            actionButton(u)
        }
        .frame(width: viewport.size.width, height: viewport.size.height)
    }

    /// 미리 보기 칸 가운데(화면 pt)
    private var stageCenter: CGPoint {
        let safe = viewport.safeRect
        let top = safe.minY + Self.stageTop * viewport.unit
        let bottom = safe.maxY - Self.stageBottom * viewport.unit
        return CGPoint(x: safe.midX, y: (top + bottom) / 2)
    }

    // MARK: - 위 줄: 돌아가기 · 모은 별

    private func topRow(_ u: CGFloat) -> some View {
        ZStack {
            Button {
                Haptics.impact(.light)
                onClose()
            } label: {
                ZStack {
                    PillBackground()
                    // 글꼴(SUITE)에 화살표 기호가 없을 수 있어 글자만
                    Text("메뉴로")
                        .font(.suite(.extrabold, 50 * u))
                        .foregroundStyle(GamePalette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
                .frame(width: 260 * u, height: HudBadgeState.height * u)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .position(viewport.safeTop(x: -370, y: PauseLayout.hudRowY))
            HudBadgeView(state: model.starsBadge, icon: "icon_coin", width: 280, unit: u)
                .position(viewport.safeTop(x: 370, y: PauseLayout.hudRowY))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("모은 별 \(model.stars)개"))
        }
    }

    // MARK: - 큰 구구(써 보기)

    private func preview(_ u: CGFloat) -> some View {
        let size = Self.previewSize * u
        return ZStack {
            Circle()
                .fill(Color.white.opacity(0.6))
                .frame(width: size + 60 * u, height: size + 60 * u)
                .offset(y: -10 * u)
            ZStack {
                GameArt.image(GameArt.GuguPose.happy.file)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
                if let item = model.previewWearing {
                    let place = GameArt.accessoryPlacement(item, pose: .happy)
                    GameArt.image(GameArt.file(item))
                        .resizable()
                        .scaledToFit()
                        .frame(width: place.size.width * size, height: place.size.height * size)
                        .offset(x: place.center.x * size, y: -place.center.y * size)
                }
            }
            // 홈 게임 카드와 같은 숨쉬기(장신구도 함께) — 고를 때 통통은 그 위에 곱해진다
            .cardBreathing(phase: 0)
            .scaleEffect(model.previewScale)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(previewLabel))
        .accessibilityIdentifier("wardrobe.preview")
    }

    private var previewLabel: String {
        guard let item = model.previewWearing else { return String(localized: "구구 — 맨머리") }
        return String(localized: "구구 — \(WardrobeContent.name(item))")
    }

    // MARK: - 장신구 카드

    /// 카드 한 장의 그릴 값
    private struct CardFace: Identifiable, Equatable {
        let id: Accessory
        let owned: Bool
        let selected: Bool
        let cannotAfford: Bool
        /// 쓰는 중 / 가짐 / 값
        let priceText: String
    }

    private var cardFaces: [CardFace] {
        Accessory.allCases.map { item in
            CardFace(id: item, owned: model.owns(item), selected: model.selected == item,
                     cannotAfford: model.cannotAfford(item), priceText: priceText(item))
        }
    }

    private func card(_ face: CardFace, _ u: CGFloat) -> some View {
        let item = face.id
        let w = Self.cardSize.width * u
        let h = Self.cardSize.height * u
        let owned = face.owned
        let selected = face.selected
        return Button {
            model.select(item)
        } label: {
            ZStack {
                // 고른 카드: 같은 젤리 모양을 조금 크게 흰색으로 뒤에 깔아 빛 테두리
                if selected {
                    GameArt.image("card_jelly")
                        .resizable()
                        .frame(width: w + 34 * u, height: h + 34 * u)
                }
                GameArt.image("card_jelly")
                    .resizable()
                    .colorMultiply(Self.cardColors[item.rawValue])
                    .frame(width: w, height: h)
                GameArt.image(GameArt.file(item))
                    .resizable()
                    .scaledToFit()
                    .frame(width: 190 * u, height: 120 * u)
                    .offset(y: -34 * u)
                if owned {
                    Text(face.priceText)
                        .font(.suite(.extrabold, 50 * u))
                        .foregroundStyle(GamePalette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .frame(width: 280 * u, height: 70 * u)
                        .offset(y: 64 * u)
                } else {
                    GameArt.image("icon_coin")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 52 * u, height: 52 * u)
                        .offset(x: -50 * u, y: 66 * u)
                    Text(face.priceText)
                        .font(.suiteNum(.extrabold, 50 * u))
                        .foregroundStyle(face.cannotAfford ? GamePalette.wrong : GamePalette.ink)
                        .lineLimit(1)
                        .frame(width: 140 * u, height: 70 * u, alignment: .leading)
                        .offset(x: 50 * u, y: 64 * u)
                }
            }
            .frame(width: w + 34 * u, height: h + 34 * u)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(WardrobeContent.name(item)), \(face.priceText)"))
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("wardrobe.card.\(String(describing: item))")
    }

    /// 쓰는 중 / 가짐 / 값
    private func priceText(_ item: Accessory) -> String {
        if model.isEquipped(item) { return String(localized: "쓰는 중") }
        if model.owns(item) { return String(localized: "가짐") }
        return "\(item.price)"
    }

    // MARK: - 아래 큰 버튼

    private func actionButton(_ u: CGFloat) -> some View {
        JellyButton(title: model.actionTitle, color: actionColor, textColor: GamePalette.ink,
                    fontSize: 60, jellyScale: 0.75, unit: u, action: model.pressAction)
            .frame(width: 860 * u, height: 160 * u)
            .offset(x: CGFloat(model.actionShakeX) * u)
            .position(viewport.safeBottom(x: 0, y: 140))
            .accessibilityIdentifier("wardrobe.action")
    }

    /// 사기·쓰기 초록, 벗기 금색, 고르기 전·별 부족 회색
    private var actionColor: Color {
        switch model.action {
        case .buy, .wear: return GamePalette.correct
        case .takeOff: return GamePalette.gold
        case .none, .needMore: return GamePalette.quit
        }
    }

    /// 장신구 표시 이름(읽어 주기용)
    static func name(_ item: Accessory) -> String {
        switch item {
        case .ribbon: return String(localized: "빨간 리본")
        case .flowerCrown: return String(localized: "꽃 화관")
        case .partyHat: return String(localized: "파티 모자")
        case .pirateHat: return String(localized: "해적 모자")
        case .wizardHat: return String(localized: "마법사 모자")
        case .crown: return String(localized: "황금 왕관")
        }
    }
}
