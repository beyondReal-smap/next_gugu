import XCTest
import UIKit
@testable import Gugu

/// 재디자인 V1 토대(U3) — 그림 자산과 캡처용 진행 상태(GUGU_SEED)가 계획서 값과 맞는지
final class RedesignToolTests: XCTestCase {

    /// 모든 WorldArt 그림이 자산 목록에서 읽히고, 원본(2000px 안팎)이 아니라 화면 크기의 3배로 줄인 그림이다
    /// (tools/redesign/import-art-ios.py — 가장 큰 것이 몬스터 문 474px)
    func testWorldArtLoadsAtImportedSize() throws {
        for art in WorldArt.allCases {
            let image = try XCTUnwrap(UIImage(named: art.assetName), "\(art.assetName) 없음")
            let longest = max(image.size.width * image.scale, image.size.height * image.scale)
            XCTAssertLessThanOrEqual(longest, 500, "\(art.assetName) 가 너무 크다: \(longest)px")
            XCTAssertGreaterThanOrEqual(longest, 90, "\(art.assetName) 가 너무 작다: \(longest)px")
        }
    }

    #if DEBUG
    private let tuesday = "2026-11-24"

    /// v1 — V1 시제품 숫자: Lv.8 38/305, 배지 7, 헷갈린 문제 11개, 단별 정확도, 이번 주 월 32·화 45, 약한 단 7·8
    func testDebugSeedV1MatchesPrototypeNumbers() {
        let s = DebugSeed.state("v1", today: tuesday)
        let level = Level.info(totalXp: s.totalXp)
        XCTAssertEqual([level.level, level.currentLevelXp, level.xpForNextLevel], [8, 38, 305])
        XCTAssertEqual(Commit.activeStreak(s, now: localNoon(tuesday)), 3)
        XCTAssertEqual(s.unlockedAchievements.count, 7)
        XCTAssertEqual(s.wrongPool.count, 11)
        XCTAssertEqual(Problems.tablesWithWrong(s.wrongPool), [7, 8])

        let report = GuardianReport.build(s, today: tuesday)
        let percent = Dictionary(uniqueKeysWithValues: report.rows.map { ($0.table, $0.accuracy.map { Int(($0 * 100).rounded()) }) })
        XCTAssertEqual(percent, [2: 98, 3: 92, 4: 85, 5: 88, 6: 71, 7: 58, 8: 62, 9: nil])
        XCTAssertEqual(report.headline, .weak(first: 7, second: 8))
        XCTAssertEqual(report.week.days.map(\.correct), [32, 45, 0, 0, 0, 0, 0])
    }

    /// max — 10~19단 모두 기록, 약한 단 18·19, 19×19 헷갈림, 배지 전부, Lv.99
    func testDebugSeedMaxHasLongestContent() {
        let s = DebugSeed.state("max", today: tuesday)
        XCTAssertEqual(Level.info(totalXp: s.totalXp).level, 99)
        XCTAssertEqual(s.unlockedAchievements.count, Achievements.all.count)
        XCTAssertTrue(Problems.tablesWithWrong(s.wrongPool).contains(19))
        let report = GuardianReport.build(s, today: tuesday)
        XCTAssertEqual(report.rows.map(\.table), Array(2...19))
        XCTAssertEqual(report.headline, .weak(first: 19, second: 18))
    }
    #endif
}
