import SwiftUI

// 이번 주 학습 리포트 — 보호자가 한눈에 보는 최근 7일 요약 (웹 ParentReport 대응, 기기 기록만 사용)

struct WeeklyReportCard: View {
    let report: WeeklyReport
    var onReview: (() -> Void)? = nil

    private var maxSolved: Int { max(1, report.days.map(\.solved).max() ?? 1) }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            bars
            summary
            if !report.topMisses.isEmpty { misses }
            tip
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            HStack(spacing: 6) {
                Image(systemName: "chart.bar.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(Color.gg.accent)
                Text("이번 주 학습").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
            }
            .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            if let delta = deltaLabel {
                Text(delta.text).font(.suite(.bold, 12)).foregroundStyle(delta.color).monospacedDigit()
            }
        }
    }

    private var deltaLabel: (text: String, color: Color)? {
        guard report.correct > 0 || report.prevCorrect > 0 else { return nil }
        let d = report.correctDelta
        if d > 0 { return ("지난주보다 정답 +\(d)", .gg.success) }
        if d < 0 { return ("지난주보다 정답 −\(-d)", .gg.textMuted) }
        return ("지난주와 같아요", .gg.textMuted)
    }

    // 7일 막대 — 아래는 정답, 위는 오답. 문제를 안 푼 날은 짧은 회색 막대
    private var bars: some View {
        HStack(alignment: .bottom, spacing: 8) {
            ForEach(Array(report.days.enumerated()), id: \.offset) { i, day in
                let isToday = i == report.days.count - 1
                VStack(spacing: 6) {
                    VStack(spacing: 0) {
                        if day.solved == 0 {
                            Capsule().fill(Color.gg.surface2).frame(height: 4)
                        } else {
                            let h = 72 * CGFloat(day.solved) / CGFloat(maxSolved)
                            let wrongH = h * CGFloat(day.wrong) / CGFloat(day.solved)
                            Rectangle().fill(Color.gg.danger.opacity(0.55)).frame(height: wrongH)
                            Rectangle().fill(isToday ? Color.gg.accent : Color.gg.accent.opacity(0.65))
                                .frame(height: h - wrongH)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
                    .frame(height: 72, alignment: .bottom)
                    Text(WeeklyReport.weekdayLabel(day.date))
                        .font(.suite(isToday ? .extrabold : .bold, 11))
                        .foregroundStyle(isToday ? Color.gg.accent : Color.gg.textMuted)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(WeeklyReport.weekdayLabel(day.date))요일\(isToday ? ", 오늘" : ""): 정답 \(day.correct)개, 오답 \(day.wrong)개")
            }
        }
    }

    private var summary: some View {
        HStack(spacing: 0) {
            stat("\(report.activeDays)일", "학습한 날")
            stat("\(report.correct)개", "정답")
            stat(report.accuracy.map { "\($0)%" } ?? "—", "정확도")
            stat(minutesLabel, "푼 시간")
        }
    }

    private var minutesLabel: String {
        let minutes = Int((Double(report.studyMs) / 60_000).rounded())
        if report.studyMs > 0 && minutes == 0 { return "1분 미만" }
        return "\(minutes)분"
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text).monospacedDigit()
                .lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.suite(.bold, 11)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private var misses: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("자주 틀린 문제").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                Spacer()
                if let onReview {
                    Button("복습하기", action: onReview)
                        .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent)
                        .frame(minHeight: 32)
                }
            }
            HStack(spacing: 8) {
                ForEach(report.topMisses, id: \.key) { miss in
                    HStack(spacing: 4) {
                        Text(miss.key.replacingOccurrences(of: "x", with: " × "))
                            .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.danger).monospacedDigit()
                        Text("\(miss.count)번").font(.suite(.bold, 11)).foregroundStyle(Color.gg.danger.opacity(0.8))
                    }
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Color.gg.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(miss.key.replacingOccurrences(of: "x", with: " 곱하기 ")), \(miss.count)번 틀림")
                }
            }
        }
    }

    private var tip: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "quote.bubble.fill").font(.system(size: 13)).foregroundStyle(Color.gg.accent)
            Text(report.parentTip)
                .font(.suite(.medium, 13)).foregroundStyle(Color.gg.text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.gg.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("보호자 한마디. \(report.parentTip)")
    }
}
