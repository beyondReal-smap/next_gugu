import Foundation

// A 구구 계단의 높이 구간 (Unity 시험판 Core/SkyZones.cs 이식)
// 오를수록 배경이 바뀌어 "높이 올라왔다"를 눈으로 보여 주고, 구간에 들어서면 알림을 띄운다.
// 높이 단위는 오른 계단 수(출발 = 0). 표시 이름은 UI 층에서 현지화한다(Core 에는 문자열을 두지 않음).

enum SkyZone: Int, CaseIterable {
    case meadow      // 풀밭 (출발)
    case skyVillage  // 하늘 마을
    case rainbow     // 무지개 언덕
    case space       // 달나라

    /// 이 구간이 시작되는 높이(오른 계단 수)
    var startHeight: Int {
        switch self {
        case .meadow: return 0
        case .skyVillage: return 10
        case .rainbow: return 25
        case .space: return 40
        }
    }

    /// 높이(오른 계단 수)가 속한 구간. 출발 아래(음수)는 풀밭
    static func at(height: Int) -> SkyZone {
        for zone in allCases.reversed() where zone != .meadow && height >= zone.startHeight {
            return zone
        }
        return .meadow
    }
}
