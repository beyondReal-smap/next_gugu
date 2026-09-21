import SwiftUI

// 지역 선택 맵 (RegionMap.tsx 이식) — 8지역, 직전 보스 격파 시 해금
struct RegionMap: View {
    @Environment(AdventureStore.self) private var adventure
    @Environment(GameStore.self) private var game
    var onSelect: (RegionDef) -> Void
    var onExit: () -> Void

    @State private var showShop = false

    private var total: (defeated: Int, total: Int) { AdvProgress.totalStats(adventure.progress) }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(World.regions, id: \.table) { region in
                        regionRow(region)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gg.bg.ignoresSafeArea())
        .sheet(isPresented: $showShop) { ShopView() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("어드벤처").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
                Spacer()
                Button { onExit() } label: {
                    Image(systemName: "xmark").font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                }
            }
            Text("지역을 탐험하고 주민들과 구구단 대결을 펼쳐요")
                .font(.suite(.regular, 14)).foregroundStyle(Color.gg.textMuted)
            HStack(spacing: 12) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(Color.gg.accent)
                            .frame(width: geo.size.width * (total.total > 0 ? Double(total.defeated) / Double(total.total) : 0))
                    }
                }
                .frame(height: 10)
                HStack(spacing: 4) {
                    Image(systemName: "shield.lefthalf.filled").font(.system(size: 12))
                    Text("\(total.defeated)/\(total.total)").monospacedDigit()
                }
                .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
            }
            // 꾸미기 상점 입구 — 별 조각 잔액 + CTA
            Button {
                Haptics.impact(.light)
                showShop = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "star.fill").font(.system(size: 14)).foregroundStyle(Color.gg.warning)
                    Text("\(adventure.progress.starShards)").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text).monospacedDigit()
                    Text("꾸미기 상점").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                    Spacer()
                    Text("색상 · 모자").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Color.gg.warning.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.warning.opacity(0.25), lineWidth: 1))
            }
            .buttonStyle(PressScaleStyle())

            // 어드벤처 업적 칩
            FlowRow(spacing: 6) {
                ForEach(AdvAchievements.all) { a in
                    let on = adventure.progress.achievements.contains(a.id)
                    HStack(spacing: 4) {
                        Image(systemName: IconMap.sf(a.icon)).font(.system(size: 9, weight: .bold))
                        Text(a.name).font(.suite(.extrabold, 10))
                    }
                    .foregroundStyle(on ? Color.gg.warning : Color.gg.textMuted)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(on ? Color.gg.warning.opacity(0.15) : Color.gg.surface2, in: Capsule())
                    .opacity(on ? 1 : 0.6)
                }
            }
            .padding(.top, 2)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
    }

    private func regionRow(_ region: RegionDef) -> some View {
        let unlocked = AdvProgress.isRegionUnlocked(
            adventure.progress,
            table: region.table,
            tableStars: game.state.tableMastery[region.table]?.stars ?? 0
        )
        let stats = AdvProgress.regionStats(adventure.progress, region)
        return Button {
            if unlocked { onSelect(region) }
        } label: {
            HStack(spacing: 14) {
                swatch(region, unlocked: unlocked)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(region.name).font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text).lineLimit(1)
                        Text("\(region.table)단").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(Color.gg.accent.opacity(0.12), in: Capsule())
                        if stats.bossDefeated {
                            Image(systemName: "crown.fill").font(.system(size: 13)).foregroundStyle(Color.gg.warning)
                        }
                    }
                    Text(unlocked
                         ? "주민 격파 \(stats.defeated)/\(stats.total)\(stats.bossDefeated ? " · 클리어!" : "")"
                         : "이전 지역 보스를 이기면 열려요")
                        .font(.suite(.regular, 13)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer()
                if unlocked {
                    Image(systemName: "chevron.right").foregroundStyle(Color.gg.textMuted)
                }
            }
            .padding(16)
            .ggCard(padding: 0)
            .opacity(unlocked ? 1 : 0.55)
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!unlocked)
    }

    private func swatch(_ region: RegionDef, unlocked: Bool) -> some View {
        ZStack(alignment: .bottom) {
            Color(hex: region.theme.sky)
            VStack(spacing: 0) {
                Spacer()
                Color(hex: region.theme.ground).frame(height: 20)
            }
            Circle().fill(Color(hex: region.theme.accent)).frame(width: 16, height: 16).padding(.bottom, 12)
            if !unlocked {
                Color.black.opacity(0.35)
                Image(systemName: "lock.fill").foregroundStyle(.white)
            }
        }
        .frame(width: 56, height: 56)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
