import SwiftUI

// 꾸미기 상점 — 별 조각으로 색상/모자 구매·장착. 구매 즉시 장착(원탭, 아이 친화)

struct ShopView: View {
    @Environment(AdventureStore.self) private var adventure
    @Environment(\.dismiss) private var dismiss
    @State private var notice: String?

    private var shards: Int { adventure.progress.starShards }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                preview
                colorSection
                hatSection
                if let notice {
                    Text(notice)
                        .font(.suite(.bold, 13)).foregroundStyle(Color.gg.danger)
                        .frame(maxWidth: .infinity)
                        .transition(.opacity)
                }
                Text("별 조각은 어드벤처 월드를 탐험하며 모을 수 있어요 ⭐")
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 32)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: notice)
    }

    private var header: some View {
        HStack {
            Text("꾸미기 상점").font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text)
            Spacer()
            Pill(bg: Color.gg.warning.opacity(0.15), fg: .gg.warning) {
                Image(systemName: "star.fill")
                Text("\(shards)").monospacedDigit()
            }
            .contentTransition(.numericText())
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: shards)
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.textMuted)
            }
            .padding(.leading, 6)
        }
    }

    // 미리보기 — 월드 플레이어와 같은 캡슐+눈 언어 + 장착 모자
    private var preview: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .top) {
                Capsule()
                    .fill(Color(hex: adventure.equippedColorHex))
                    .frame(width: 76, height: 104)
                    .overlay(alignment: .top) {
                        HStack(spacing: 8) {
                            ForEach(0..<2, id: \.self) { _ in
                                Circle().fill(.white).frame(width: 13, height: 13)
                                    .overlay(Circle().strokeBorder(Color(red: 0.13, green: 0.14, blue: 0.17), lineWidth: 4))
                            }
                        }
                        .padding(.top, 26)
                    }
                    .shadow(color: Color(hex: adventure.equippedColorHex).opacity(0.4), radius: 12, y: 6)
                if let hatID = adventure.progress.equippedHat, let hat = Shop.hat(hatID) {
                    Text(hat.emoji).font(.system(size: 34)).offset(y: -26)
                }
            }
            .padding(.top, 12)
            Text("내 캐릭터").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .ggCard(padding: 0)
    }

    // MARK: - 색상

    private var colorSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("몸 색깔").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(Shop.colors) { c in
                    colorCell(c)
                }
            }
        }
    }

    private func colorCell(_ c: ShopColor) -> some View {
        let owned = adventure.ownsColor(c.id)
        let equipped = adventure.progress.equippedColor == c.id
        return Button { tapColor(c) } label: {
            VStack(spacing: 6) {
                ZStack {
                    Circle().fill(Color(hex: c.hex)).frame(width: 44, height: 44)
                    if equipped {
                        Image(systemName: "checkmark").font(.system(size: 16, weight: .heavy)).foregroundStyle(.white)
                    }
                }
                Text(c.name).font(.suite(.bold, 12)).foregroundStyle(Color.gg.text)
                stateChip(owned: owned, equipped: equipped, price: c.price)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(equipped ? Color.gg.accent.opacity(0.1) : Color.gg.surface,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(equipped ? Color.gg.accent : Color.gg.border, lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
    }

    // MARK: - 모자

    private var hatSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("모자").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            VStack(spacing: 8) {
                hatRow(nil)
                ForEach(Shop.hats) { h in
                    hatRow(h)
                }
            }
        }
    }

    private func hatRow(_ h: ShopHat?) -> some View {
        let owned = h.map { adventure.ownsHat($0.id) } ?? true
        let equipped = adventure.progress.equippedHat == h?.id
        return Button { tapHat(h) } label: {
            HStack(spacing: 12) {
                Text(h?.emoji ?? "🚫").font(.system(size: 26)).frame(width: 40)
                Text(h?.name ?? "모자 없음").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                if equipped {
                    Text("장착중").font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color.gg.accent.opacity(0.12), in: Capsule())
                } else if owned {
                    Text("장착").font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color.gg.surface2, in: Capsule())
                } else if let h {
                    HStack(spacing: 3) {
                        Image(systemName: "star.fill").font(.system(size: 10))
                        Text("\(h.price)").monospacedDigit()
                    }
                    .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.warning)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(Color.gg.warning.opacity(0.15), in: Capsule())
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(equipped ? Color.gg.accent.opacity(0.1) : Color.gg.surface,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(equipped ? Color.gg.accent : Color.gg.border, lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
    }

    // MARK: - 공용

    @ViewBuilder
    private func stateChip(owned: Bool, equipped: Bool, price: Int) -> some View {
        if equipped {
            Text("장착중").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accent)
        } else if owned {
            Text("보유").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.textMuted)
        } else {
            HStack(spacing: 2) {
                Image(systemName: "star.fill").font(.system(size: 8))
                Text("\(price)").monospacedDigit()
            }
            .font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.warning)
        }
    }

    private func tapColor(_ c: ShopColor) {
        notice = nil
        if adventure.progress.equippedColor == c.id { return }
        if adventure.ownsColor(c.id) {
            adventure.equipColor(c.id)
            Haptics.selection()
        } else if adventure.buyColor(c.id) {
            Sound.shared.collect()
            Haptics.success()
        } else {
            Haptics.warning()
            notice = "별 조각이 부족해요 — ⭐\(c.price)개가 필요해요"
        }
    }

    private func tapHat(_ h: ShopHat?) {
        notice = nil
        guard let h else {
            adventure.equipHat(nil)
            Haptics.selection()
            return
        }
        if adventure.progress.equippedHat == h.id { return }
        if adventure.ownsHat(h.id) {
            adventure.equipHat(h.id)
            Haptics.selection()
        } else if adventure.buyHat(h.id) {
            Sound.shared.collect()
            Haptics.success()
        } else {
            Haptics.warning()
            notice = "별 조각이 부족해요 — ⭐\(h.price)개가 필요해요"
        }
    }
}
