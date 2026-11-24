import SwiftUI

// 구구 몬스터 그림 정보 (Unity 시험판 GameArt.Monster·MonsterTint·MonsterPanelCenter·Stump·Forest 이식)
// 몬스터 그림은 8종 × (보통·기절). 파일 이름 = monster_<종>(_defeated), 종 순서 = MonsterKind(도감 칸 순서).
// 원본은 512px 높이(Unity PPU 512 → 1유닛)이고, 빨강만 날개 때문에 캔버스가 가로로 넓다(몸 배율은 같음).

extension GameArt {
    static func monsterFile(_ kind: MonsterKind, defeated: Bool = false) -> String {
        "monster_\(monsterName(kind))" + (defeated ? "_defeated" : "")
    }

    /// 몬스터 그림 원본 px 크기 — 높이 512 기준으로 폭을 늘려 그린다
    static func monsterPixels(_ kind: MonsterKind, defeated: Bool = false) -> CGSize {
        switch (kind, defeated) {
        case (.red, false): return CGSize(width: 580, height: 512)
        case (.red, true): return CGSize(width: 560, height: 512)
        default: return CGSize(width: 512, height: 512)
        }
    }

    /// 터짐 고리처럼 몬스터 색이 필요한 곳
    static func monsterTint(_ kind: MonsterKind) -> Color {
        switch kind {
        case .green: return Color(hex: "#3CC28A")
        case .orange: return Color(hex: "#FF9A3C")
        case .purple: return Color(hex: "#A77BFF")
        case .sky: return Color(hex: "#4CC3F0")
        case .pink: return Color(hex: "#FF8FC8")
        case .red: return Color(hex: "#FF6B5B")
        case .night: return Color(hex: "#4B55D6")
        case .gold: return Color(hex: "#FFC93C")
        }
    }

    /// 몬스터 배의 빈 크림색 패널 중심 — 그림 높이 대비, 중심 기준(y 위가 +). 8종 모두 같음(실측 오차 1% 이내)
    static let monsterPanelCenter = CGPoint(x: 0, y: -0.17)

    static let stumpPixels = CGSize(width: 265, height: 112)
    static let forestPixels = CGSize(width: 1024, height: 1536)

    private static func monsterName(_ kind: MonsterKind) -> String {
        switch kind {
        case .green: return "green"
        case .orange: return "orange"
        case .purple: return "purple"
        case .sky: return "sky"
        case .pink: return "pink"
        case .red: return "red"
        case .night: return "night"
        case .gold: return "gold"
        }
    }
}
