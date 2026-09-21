import SwiftUI

// 미구현 화면 자리표시자 — 각 Phase에서 실제 구현으로 교체
struct PhasePlaceholder<Extra: View>: View {
    let title: String
    let phase: String
    let icon: String
    let note: String
    @ViewBuilder var extra: () -> Extra

    init(title: String, phase: String, icon: String, note: String,
         @ViewBuilder extra: @escaping () -> Extra = { EmptyView() }) {
        self.title = title
        self.phase = phase
        self.icon = icon
        self.note = note
        self.extra = extra
    }

    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: icon).font(.system(size: 44)).foregroundStyle(Color.gg.accent)
            Text(title).font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
            Text(note).font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
            Text("\(phase)에서 구현 예정").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Color.gg.surface2, in: Capsule())
            extra().padding(.top, 8)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gg.bg.ignoresSafeArea())
    }
}
