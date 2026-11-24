import SwiftUI

// A·B 게임 그림과 색 (Unity 시험판 GameArt.cs·Palette.cs 이식)
// 그림은 Assets.xcassets/Games(이름공간 폴더, universal 1배)에 원본 PNG 그대로 들어 있다 — 이름은 "Games/<파일 이름>".
// 크기 계산에 쓰는 원본 px 크기도 여기 둔다(그림을 바꾸면 함께 고칠 것).

enum GameArt {
    /// 캐릭터 그림(512px 정사각형) 아래쪽 투명 여백 비율 — 발을 발판 위에 붙일 때 뺀다
    static let feetPad: CGFloat = 0.04

    /// 그림 이름 — Assets.xcassets/Games 의 이미지 세트
    static func name(_ file: String) -> String { "Games/\(file)" }

    static func image(_ file: String) -> Image { Image(name(file)) }

    // MARK: - 구구(주인공)

    enum GuguPose: Equatable {
        case idle, jump, happy, oops

        var file: String {
            switch self {
            case .idle: return "gugu_idle"
            case .jump: return "gugu_jump"
            case .happy: return "gugu_happy"
            case .oops: return "gugu_oops"
            }
        }

        /// 머리 윗면 가운데 — 그림 크기 대비, 그림 중심 기준(y 위가 +). 자세마다 실측(점프는 웅크려 낮음)
        var headTop: CGPoint {
            switch self {
            case .idle: return CGPoint(x: 0.001, y: 0.299)
            case .jump: return CGPoint(x: -0.007, y: 0.230)
            case .happy: return CGPoint(x: -0.02, y: 0.309)
            case .oops: return CGPoint(x: 0.006, y: 0.307)
            }
        }
    }

    // MARK: - 장신구

    static func file(_ item: Accessory) -> String {
        switch item {
        case .ribbon: return "acc_ribbon"
        case .flowerCrown: return "acc_flower_crown"
        case .partyHat: return "acc_party_hat"
        case .pirateHat: return "acc_pirate_hat"
        case .wizardHat: return "acc_wizard_hat"
        case .crown: return "acc_crown"
        }
    }

    /// 장신구 원본 px 크기 — 머리에 얹을 때 가로세로 비율
    static func pixelSize(_ item: Accessory) -> CGSize {
        switch item {
        case .ribbon: return CGSize(width: 243, height: 150)
        case .flowerCrown: return CGSize(width: 252, height: 138)
        case .partyHat: return CGSize(width: 199, height: 235)
        case .pirateHat: return CGSize(width: 253, height: 146)
        case .wizardHat: return CGSize(width: 248, height: 199)
        case .crown: return CGSize(width: 244, height: 170)
        }
    }

    /// 머리 맞춤: width = 구구 그림 폭 대비 장신구 폭(머리 폭 ≈ 0.48), sink = 장신구 높이 중 머리 윗면 아래로 덮는 비율
    static func fit(_ item: Accessory) -> (width: CGFloat, sink: CGFloat) {
        switch item {
        case .ribbon: return (0.34, 0.30)
        case .flowerCrown: return (0.50, 0.45)
        case .partyHat: return (0.30, 0.12)
        case .pirateHat: return (0.56, 0.35)
        case .wizardHat: return (0.56, 0.22)
        case .crown: return (0.44, 0.25)
        }
    }

    /// 장신구 자리 — 구구 그림 크기 대비, 그림 중심 기준(y 위가 +)의 중심과 크기.
    /// 게임 속 구구·고도계 아이콘·옷장 미리 보기가 모두 이 계산을 써서 같은 자리에 얹힌다
    static func accessoryPlacement(_ item: Accessory, pose: GuguPose) -> (center: CGPoint, size: CGSize) {
        let px = pixelSize(item)
        let f = fit(item)
        let height = f.width * px.height / px.width
        let top = pose.headTop
        return (CGPoint(x: top.x, y: top.y + height * (0.5 - f.sink)), CGSize(width: f.width, height: height))
    }

    // MARK: - 원본 px 크기 (크기·비율 계산용)

    static let platformPixels = CGSize(width: 512, height: 200)
    static let stormCloudPixels = CGSize(width: 1200, height: 398)
    static let grassHillPixels = CGSize(width: 290, height: 71)
    static let flagPixels = CGSize(width: 212, height: 283)
    static let boardPixels = CGSize(width: 800, height: 240)
    static let comboFlamePixels = CGSize(width: 230, height: 266)
    static let sparklePixels = CGSize(width: 161, height: 171)
    static let starPixels = CGSize(width: 176, height: 168)
}

/// 게임 화면 색 (Unity Palette)
enum GamePalette {
    /// 결과·쉬는 창 버튼 글자 등 가장 어두운 남색
    static let background = Color(hex: "#141A33")
    static let wall = Color(hex: "#47538A")
    static let correct = Color(hex: "#3DDC84")
    static let wrong = Color(hex: "#FF5A5F")
    static let text = Color(hex: "#F4F6FF")
    static let textDim = Color(hex: "#9AA3C7")
    /// 젤리 창(쉬는 창·결과 창) 몸 색
    static let card = Color(hex: "#232C57")
    static let gold = Color(hex: "#FFD45C")
    /// 그림 외곽선 색 — 밝은 배지·카드 위 글자, 글자 테두리
    static let ink = Color(hex: "#2B2350")
    /// 쉬는 창 [메뉴로 나가기] 회색 젤리
    static let quit = Color(red: 0.72, green: 0.74, blue: 0.80)
}
