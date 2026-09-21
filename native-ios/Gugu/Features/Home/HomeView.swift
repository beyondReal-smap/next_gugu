import SwiftUI

// 홈 (Home.tsx 이식)

struct HomeView: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session
    @Environment(AdventureStore.self) private var adventure
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router

    // 놀이 방식별 묶음 (웹 /play 구성과 동일) — 세션 모드는 전체 랜덤으로 바로 시작한다
    private let learnModes: [GameMode] = [.missing, .truefalse]
    private let recordModes: [GameMode] = [.timeAttack, .challenge, .survival]

    private var goalPct: Double {
        game.state.dailyGoal > 0 ? Double(game.state.dailyCorrect) / Double(game.state.dailyGoal) : 0
    }
    private var weakCount: Int { game.state.wrongPool.count }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                dailyGoalCard
                levelCard
                quickStartButton
                if weakCount > 0 { weakReview }
                modesHeader
                learnGroup
                recordGroup
                playGroup
                adventureGroup
                learnLink
                    .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
    }

    // MARK: 헤더
    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("안녕하세요 👋").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                Text("오늘도 구구단 한 판!").font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
            }
            Spacer()
            Pill(bg: Color.gg.danger.opacity(0.12), fg: .gg.danger) {
                Image(systemName: "flame.fill")
                Text("\(game.state.streak)일").monospacedDigit()
            }
        }
    }

    // MARK: 데일리 골
    private var dailyGoalCard: some View {
        HStack(spacing: 20) {
            ProgressRing(value: goalPct, size: 104, stroke: 11) {
                VStack(spacing: 0) {
                    Text("\(game.state.dailyCorrect)")
                        .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text).monospacedDigit()
                    Text("/ \(game.state.dailyGoal)")
                        .font(.suite(.bold, 11)).foregroundStyle(Color.gg.textMuted)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "target").foregroundStyle(Color.gg.accent)
                    Text("오늘의 목표").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                }
                Text(goalPct >= 1 ? "목표 달성! 멋져요 🎉" : "정답 \(max(0, game.state.dailyGoal - game.state.dailyCorrect))개 더 풀면 달성!")
                    .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
            }
            Spacer()
        }
        .ggCard()
    }

    // MARK: 레벨/XP
    private var levelCard: some View {
        VStack(spacing: 8) {
            HStack {
                HStack(spacing: 8) {
                    Text("\(game.levelInfo.level)")
                        .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.accentFg)
                        .frame(width: 36, height: 36)
                        .background(Color.gg.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .monospacedDigit()
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Lv.\(game.levelInfo.level)").font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                        Text(Level.title(game.levelInfo.level)).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                    }
                }
                Spacer()
                Text("\(game.levelInfo.currentLevelXp)/\(game.levelInfo.xpForNextLevel) XP")
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.gg.surface2)
                    Capsule().fill(Color.gg.accent)
                        .frame(width: geo.size.width * game.levelInfo.progress)
                        .animation(.spring(response: 0.5, dampingFraction: 0.8), value: game.levelInfo.progress)
                }
            }
            .frame(height: 10)
        }
        .ggCard()
    }

    // MARK: 빠른 시작
    private var quickStartButton: some View {
        GGButton(variant: .primary, size: .lg, action: { session.start(.practice, table: nil) }) {
            Image(systemName: "play.fill")
            Text("빠른 학습 시작")
        }
    }

    // MARK: 놀이 방식별 묶음

    private var modesHeader: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("어떻게 놀아볼까요?").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
            Text("배우기부터 달리기까지, 모두 구구단 연습이 돼요.")
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    private func groupHeader(_ icon: String, _ title: String, _ desc: String, _ tint: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Image(systemName: icon).font(.system(size: 12, weight: .bold)).foregroundStyle(tint)
            Text(title).font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
            Text(desc).font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted.opacity(0.75)).lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    // 차근차근 배우기 — 시간 제한 없이 원리부터
    private var learnGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("book.fill", "차근차근 배우기", "시간 제한 없이 원리부터", .gg.accent)
            Button { router.tab = .learn } label: {
                modeRowLabel(icon: ModeStyle.icon(.practice), tint: ModeStyle.tint(.practice),
                             name: "학습", tagline: "원하는 단을 골라 또박또박",
                             meta: "\(Modes.def(.practice).total)문제 · 단 선택", locked: false)
            }
            .buttonStyle(PressScaleStyle())
            ForEach(learnModes, id: \.self) { sessionModeRow($0) }
        }
    }

    // 기록 도전 — 속도와 집중력
    private var recordGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("trophy.fill", "기록 도전", "속도와 집중력으로 최고 기록", .gg.warning)
            ForEach(recordModes, id: \.self) { sessionModeRow($0) }
        }
    }

    // 직접 움직이며 놀기 — 달리기·받기 미니게임
    private var playGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("figure.run", "직접 움직이며 놀기", "달리고, 피하고, 정답을 받아요", .gg.emerald)
            gameCard(
                title: "구구 바구니", badge: "새 모드",
                desc: "좌우로 움직여 정답 열매를 쏙 받아요!",
                icon: "basket.fill", iconBg: Color(hex: "#794124"), iconFg: Color(hex: "#ffe7a3"),
                tint: Color.gg.warning
            ) { router.basketOpen = true }
            gameCard(
                title: "구구 점프", badge: nil,
                desc: "정답을 고르면 폴짝! 장애물을 넘어요.",
                icon: "figure.run", iconBg: Color(hex: "#153f35"), iconFg: Color(hex: "#dbef9e"),
                tint: Color.gg.emerald
            ) { router.runnerOpen = true }
            gameCard(
                title: "구구 레인", badge: "새 모드",
                desc: "길을 바꿔 피하고, 정답 길로 쏙!",
                icon: "rectangle.split.1x2.fill", iconBg: Color(hex: "#153f35"), iconFg: Color(hex: "#dbef9e"),
                tint: Color.gg.emerald
            ) { router.laneOpen = true }
        }
    }

    private var adventureGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("map.fill", "모험", "3D 월드를 탐험하며 대결", .gg.indigo)
            adventureHero
        }
    }

    /// 러너·레인·바구니 진입 카드
    private func gameCard(
        title: String, badge: String?, desc: String,
        icon: String, iconBg: Color, iconFg: Color, tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            Haptics.impact(.light)
            action()
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous).fill(iconBg).frame(width: 48, height: 48)
                    Image(systemName: icon).font(.system(size: 22, weight: .bold)).foregroundStyle(iconFg)
                }
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title).font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                        if let badge {
                            Text(badge)
                                .font(.suite(.extrabold, 10)).foregroundStyle(tint)
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(tint.opacity(0.12), in: Capsule())
                        }
                    }
                    Text(desc)
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                Image(systemName: "arrow.up.right").font(.system(size: 16, weight: .bold)).foregroundStyle(tint)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(tint.opacity(0.06), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(tint.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
    }

    /// 세션 모드 한 줄 카드 (웹 모바일의 목록형과 동일)
    private func sessionModeRow(_ id: GameMode) -> some View {
        let m = Modes.def(id)
        let best = m.scored ? (game.state.bestScores[id] ?? 0) : 0
        let locked = !premium.isPremium && premium.isPremiumMode(id)
        return Button {
            Haptics.impact(.light)
            if premium.gate(id) { session.start(id, table: nil) }
        } label: {
            modeRowLabel(
                icon: ModeStyle.icon(id), tint: ModeStyle.tint(id), name: m.name, tagline: m.tagline,
                meta: best > 0 ? "최고 \(best)점 · \(modeMeta(id))" : "\(modeMeta(id)) · 전체 랜덤",
                locked: locked
            )
        }
        .buttonStyle(PressScaleStyle())
    }

    private func modeRowLabel(icon: String, tint: Color, name: String, tagline: String, meta: String, locked: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .bold)).foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .background(tint.opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                Text(tagline)
                    .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    .lineLimit(1)
                Text(meta)
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted.opacity(0.8))
                    .monospacedDigit()
            }
            Spacer(minLength: 4)
            if locked {
                Image(systemName: "lock.fill").font(.system(size: 11, weight: .bold)).foregroundStyle(Color.gg.accent)
            }
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.gg.textMuted)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
    }

    /// 카드에 적는 규칙 요약 (문제 수 / 제한 시간 / 하트)
    private func modeMeta(_ id: GameMode) -> String {
        let m = Modes.def(id)
        if m.kind == .timed, let limit = m.timeLimitMs { return "\(limit / 1000)초 제한" }
        if m.kind == .lives, let lives = m.lives { return "하트 \(lives)개" }
        return "\(m.total)문제"
    }

    // MARK: 어드벤처 히어로
    private var adventureHero: some View {
        let adv = AdvProgress.totalStats(adventure.progress)
        let nextRegion = World.regions.first { !adventure.progress.defeatedNpcs.contains(World.bossId(for: $0.table)) }
        return Button {
            Haptics.impact(.light)
            if premium.isPremium { adventure.openAdventure() } else { premium.openPaywall() }
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: "map.fill").font(.system(size: 12))
                        Text("어드벤처").font(.suite(.extrabold, 11))
                        if !premium.isPremium { Image(systemName: "lock.fill").font(.system(size: 10)) }
                    }
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color.white.opacity(0.2), in: Capsule())

                    Text("3D 월드를 탐험하며\n구구단 대결!")
                        .font(.suite(.extrabold, 20)).foregroundStyle(.white)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: 6) {
                        Image(systemName: "flame.fill").font(.system(size: 12))
                        Text("격파 \(adv.defeated)/\(adv.total)").monospacedDigit()
                        if let n = nextRegion { Text("· 다음 모험 \(n.name)").lineLimit(1) }
                    }
                    .font(.suite(.bold, 12)).foregroundStyle(.white.opacity(0.9))

                    HStack(spacing: 6) {
                        ForEach(World.regions, id: \.table) { r in
                            let cleared = adventure.progress.defeatedNpcs.contains(World.bossId(for: r.table))
                            Capsule()
                                .fill(cleared ? Color.white : Color.white.opacity(0.35))
                                .frame(width: cleared ? 16 : 6, height: 6)
                        }
                    }
                    .padding(.top, 2)
                }
                Spacer()
                mascot
                Image(systemName: "chevron.right").foregroundStyle(.white.opacity(0.9))
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(
                LinearGradient(colors: [Color.gg.indigo, Color.gg.violet, Color.gg.purple],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            )
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.gg.indigo.opacity(0.3), radius: 12, y: 6)
        }
        .buttonStyle(PressScaleStyle())
    }

    // 마스코트 — 월드 플레이어와 같은 캡슐+눈 언어
    private var mascot: some View {
        ZStack(alignment: .top) {
            Capsule().fill(Color.white).frame(width: 52, height: 74).shadow(radius: 6)
            HStack(spacing: 6) {
                ForEach(0..<2, id: \.self) { _ in
                    Circle().fill(Color.white)
                        .frame(width: 10, height: 10)
                        .overlay(Circle().strokeBorder(Color(red: 0.13, green: 0.14, blue: 0.17), lineWidth: 3))
                }
            }
            .padding(.top, 18)
        }
        .frame(width: 64, height: 90, alignment: .bottom)
    }

    // MARK: 취약 문제 복습
    private var weakReview: some View {
        Button { session.start(.practice, table: nil) } label: {
            HStack(spacing: 12) {
                Image(systemName: "book.closed.fill")
                    .foregroundStyle(Color.gg.danger)
                    .frame(width: 40, height: 40)
                    .background(Color.gg.danger.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("취약 문제 복습").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                    Text("헷갈렸던 문제 \(weakCount)개가 우선 출제돼요").font(.suite(.regular, 13)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(Color.gg.textMuted)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .background(Color.gg.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.danger.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
    }

    private var learnLink: some View {
        Button { router.tab = .learn } label: {
            HStack {
                Text("단 선택해서 학습하기").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(Color.gg.textMuted)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .ggCard(padding: 0)
        }
        .buttonStyle(PressScaleStyle())
    }
}
