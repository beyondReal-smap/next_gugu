import SwiftUI

// 월드 상단 HUD (WorldHud.tsx 이식) + 별 조각 카운터
struct WorldHud: View {
    let region: RegionDef
    let stats: AdvProgress.RegionStats
    let shards: Int   // 누적 별 조각 — 수집 시 팝 애니메이션
    var onBack: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button { onBack() } label: {
                Image(systemName: "arrow.left").font(.system(size: 18, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(.black.opacity(0.35), in: Circle())
            }
            HStack(spacing: 0) {
                Text(region.name).font(.suite(.extrabold, 14))
                Text(" · \(region.table)단").font(.suite(.extrabold, 14))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(.black.opacity(0.35), in: Capsule())
            Spacer()
            HStack(spacing: 5) {
                Image(systemName: "star.fill").font(.system(size: 12)).foregroundStyle(Color(hex: "#ffd34d"))
                Text("\(shards)").monospacedDigit()
            }
            .font(.suite(.extrabold, 13)).foregroundStyle(.white)
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(.black.opacity(0.35), in: Capsule())
            .contentTransition(.numericText())
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: shards)
            HStack(spacing: 6) {
                Image(systemName: "shield.lefthalf.filled").font(.system(size: 14))
                Text("\(stats.defeated)/\(stats.total)").monospacedDigit()
            }
            .font(.suite(.bold, 14)).foregroundStyle(.white)
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(.black.opacity(0.35), in: Capsule())
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

// 지역 클리어 연출 — 보스 첫 격파 후 월드 복귀 시 1회 (부모가 3.4초 뒤 해제)
struct ClearCelebration: View {
    let region: RegionDef
    let next: RegionDef?
    @State private var visible = false

    var body: some View {
        VStack(spacing: 10) {
            Text("🎉").font(.system(size: 44))
            Text("\(region.name) 클리어!").font(.suite(.heavy, 30)).foregroundStyle(.white)
            Text(next != nil ? "북쪽 끝에 \(next!.name) 포털이 열렸어요!" : "모든 지역을 정복했어요! 👑")
                .font(.suite(.bold, 14)).foregroundStyle(.white.opacity(0.9))
        }
        .padding(.horizontal, 32).padding(.vertical, 24)
        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .opacity(visible ? 1 : 0)
        .scaleEffect(visible ? 1 : 0.85)
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) { visible = true }
        }
    }
}

// 지역 입장 배너 — 진입 순간 지역명 스플래시 후 사라짐
struct RegionBanner: View {
    let region: RegionDef
    @State private var visible = false

    var body: some View {
        VStack(spacing: 6) {
            Text(region.name).font(.suite(.heavy, 34)).foregroundStyle(.white)
            Text("\(region.table)단의 세계").font(.suite(.bold, 15)).foregroundStyle(.white.opacity(0.85))
        }
        .padding(.horizontal, 32).padding(.vertical, 20)
        .background(.black.opacity(0.35), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .opacity(visible ? 1 : 0)
        .scaleEffect(visible ? 1 : 0.9)
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) { visible = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                withAnimation(.easeOut(duration: 0.5)) { visible = false }
            }
        }
    }
}

// NPC 근접 대결 프롬프트 (EncounterPrompt.tsx 이식)
struct EncounterPrompt: View {
    let npc: NpcDef
    let defeated: Bool
    let bossLocked: Bool
    var onBattle: () -> Void

    private var isBoss: Bool { npc.kind == .boss }

    private var styleIcon: String {
        switch npc.battle {
        case .hp: return "heart.fill"
        case .speed: return "gauge.medium"
        case .counter: return "timer"
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color(hex: npc.color)).frame(width: 48, height: 48)
                    HStack(spacing: 6) {
                        ForEach(0..<2, id: \.self) { _ in
                            Circle().fill(.white).frame(width: 8, height: 8)
                                .overlay(Circle().strokeBorder(Color(red: 0.13, green: 0.14, blue: 0.17), lineWidth: 2.5))
                        }
                    }
                    if isBoss {
                        Image(systemName: "crown.fill").font(.system(size: 16)).foregroundStyle(Color.gg.warning)
                            .offset(y: -30)
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(npc.name).font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text).lineLimit(1)
                        if isBoss {
                            Text("보스").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.danger)
                                .padding(.horizontal, 8).padding(.vertical, 2).background(Color.gg.danger.opacity(0.15), in: Capsule())
                        }
                        Text("\(npc.table)단").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(Color.gg.accent.opacity(0.12), in: Capsule())
                        if !isBoss {
                            HStack(spacing: 3) {
                                Image(systemName: styleIcon).font(.system(size: 9))
                                Text(Battle.styleName(npc.battle)).font(.suite(.extrabold, 10))
                            }
                            .foregroundStyle(Color.gg.textMuted)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(Color.gg.surface2, in: Capsule())
                        }
                    }
                    Text(bossLocked ? "부하들을 모두 이기면 도전할 수 있어요"
                         : defeated ? "이미 격파한 상대 — 연습 대결로 XP를 벌 수 있어요"
                         : "\u{201C}\(npc.greeting)\u{201D}")
                        .font(.suite(.regular, 13)).foregroundStyle(Color.gg.textMuted).lineLimit(1)
                }
                Spacer()
            }
            GGButton(variant: isBoss ? .danger : .primary, size: .md, action: onBattle) {
                Image(systemName: bossLocked ? "lock.fill" : "shield.lefthalf.filled")
                Text(bossLocked ? "잠겨 있어요" : defeated ? "다시 대결하기" : "대결하기")
            }
            .disabled(bossLocked)
        }
        .padding(16)
        .background(Color.gg.surface.opacity(0.96), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
        .shadow(color: .black.opacity(0.2), radius: 12, y: 6)
        .padding(.horizontal, 16)
    }
}
