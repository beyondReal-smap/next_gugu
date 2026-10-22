import SwiftUI

// 온보딩 (Onboarding.tsx 이식)

struct OnboardingView: View {
    @Environment(GameStore.self) private var game

    private struct Point: Identifiable {
        let id = UUID()
        let icon: String
        let title: String
        let desc: String
    }
    private let points: [Point] = [
        Point(icon: "chart.line.uptrend.xyaxis", title: String(localized: "레벨업"), desc: String(localized: "정답마다 XP를 모아 레벨을 올려요")),
        Point(icon: "flame.fill", title: String(localized: "스트릭"), desc: String(localized: "매일 학습하면 연속 기록이 쌓여요")),
        Point(icon: "star.fill", title: String(localized: "마스터리"), desc: String(localized: "단별로 별을 모아 완전 정복해요")),
    ]

    @State private var appear = false

    /// 제목 한 문장 — 언어마다 어순이 달라 조각으로 붙이지 않고, ** 로 감싼 부분만 강조색을 입힌다
    private var headline: AttributedString {
        var text = AttributedString(localized: "구구단,\n게임처럼\n**재미있게** 배워요",
                                    comment: "온보딩 제목 — ** 사이는 강조색, 줄바꿈 위치도 언어에 맞게")
        for run in text.runs where run.inlinePresentationIntent?.contains(.stronglyEmphasized) == true {
            text[run.range].foregroundColor = Color.gg.accent
        }
        return text
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: 24)

            VStack(alignment: .leading, spacing: 12) {
                Pill(bg: Color.gg.accent.opacity(0.15), fg: .gg.accent) {
                    Image(systemName: "sparkles")
                    Text("매일 1분 구구단")
                }
                Text(headline)
                    .font(.suite(.extrabold, 38))
                    .foregroundStyle(Color.gg.text)

                Text("레벨 · 스트릭 · 마스터리를 모으며 자연스럽게 구구단을 익혀요.")
                    .font(.suite(.medium, 15))
                    .foregroundStyle(Color.gg.textMuted)
                    .padding(.top, 4)
            }
            .opacity(appear ? 1 : 0)
            .offset(y: appear ? 0 : 16)

            Spacer(minLength: 32)

            VStack(spacing: 12) {
                ForEach(Array(points.enumerated()), id: \.element.id) { i, p in
                    HStack(spacing: 16) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.gg.accent.opacity(0.15))
                                .frame(width: 44, height: 44)
                            Image(systemName: p.icon)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundStyle(Color.gg.accent)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.title).font(.suite(.bold, 16)).foregroundStyle(Color.gg.text)
                            Text(p.desc).font(.suite(.regular, 14)).foregroundStyle(Color.gg.textMuted)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
                    .opacity(appear ? 1 : 0)
                    .offset(x: appear ? 0 : -12)
                    .animation(.easeOut(duration: 0.4).delay(0.1 + Double(i) * 0.08), value: appear)
                }
            }

            Spacer(minLength: 32)

            GGButton(variant: .primary, size: .lg, action: begin) {
                Text("시작하기")
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        // 태블릿에서 카드·버튼이 화면 끝까지 늘어나지 않게
        .readableWidth(560)
        .background(Color.gg.bg.ignoresSafeArea())
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) { appear = true }
        }
    }

    private func begin() {
        Haptics.impact(.medium)
        withAnimation { game.setOnboarded(true) }
    }
}
