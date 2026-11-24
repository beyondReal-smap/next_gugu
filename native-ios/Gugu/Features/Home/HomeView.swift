import SwiftUI

// 홈 (Home.tsx 이식)

struct HomeView: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session
    @Environment(PlayRewardsStore.self) private var playRewards
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router
    @State private var showGoalSheet = false

    // 놀이 방식별 묶음 — 기록 도전 모드는 전체 랜덤으로 바로 시작한다.
    // 단을 고르는 모드(차근차근·빈칸·OX)는 학습 탭 단 시트로 모으고, 홈에는 오늘 추천 단 카드만 둔다 (2.7.0)
    private let recordModes: [GameMode] = [.timeAttack, .challenge, .survival]

    private var goalPct: Double {
        game.state.dailyGoal > 0 ? Double(game.state.dailyCorrect) / Double(game.state.dailyGoal) : 0
    }
    private var weakCount: Int { game.state.wrongPool.count }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                // 날마다 바뀌는 미션 3개 → 보물상자 → 이번 주 도장: 매일 앱을 열 이유 (2.9.0)
                DailyMissionsCard()
                // 게임은 아이가 첫 화면에서 바로 누를 수 있게 맨 위에 둔다 (2.9.0 — 예전엔 목록 맨 아래라 스크롤해야 보였다)
                gamesGroup
                dailyGoalCard
                levelCard
                quickStartButton
                if weakCount > 0 { weakReview }
                modesHeader
                recommendGroup
                recordGroup
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
            .readableWidth()
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
        .statusBarBackdrop()
        .sheet(isPresented: $showGoalSheet) { DailyGoalSheet() }
    }

    // MARK: 헤더
    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("안녕하세요 👋").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                Text("오늘도 구구단 한 판!").font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
            }
            Spacer()
            // 끊긴 연속 기록은 0으로 — 불이 꺼진 모습으로 오늘 다시 시작하게 한다
            let streak = Commit.activeStreak(game.state)
            Pill(bg: (streak > 0 ? Color.gg.danger : Color.gg.textMuted).opacity(0.12),
                 fg: streak > 0 ? .gg.danger : .gg.textMuted) {
                Image(systemName: streak > 0 ? "flame.fill" : "flame")
                Text("\(streak)일").monospacedDigit()
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(streak > 0 ? "\(streak)일 연속 학습 중" : "연속 학습 기록 없음, 오늘 시작해요")
        }
    }

    // MARK: 데일리 골 — 누르면 목표를 바꾼다
    private var dailyGoalCard: some View {
        Button {
            Haptics.impact(.light)
            showGoalSheet = true
        } label: {
            dailyGoalContent
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("오늘의 목표, 정답 \(game.state.dailyGoal)개 중 \(game.state.dailyCorrect)개")
        .accessibilityHint("눌러서 하루 목표 바꾸기")
    }

    private var dailyGoalContent: some View {
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
                    .fixedSize(horizontal: false, vertical: true)
                Text("목표 바꾸기").font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                    .padding(.top, 2)
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
            Text("배우기부터 게임까지, 모두 구구단 연습이 돼요.")
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

    // 오늘 추천 단 — 학습 탭 맨 위와 같은 카드 (누르면 그 단 차근차근 바로 시작)
    private var recommendGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("star.fill", String(localized: "오늘 추천 단"), String(localized: "로드맵 순서대로 한 단씩"), .gg.accent)
            RecommendCard()
        }
    }

    // 기록 도전 — 속도와 집중력
    private var recordGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("trophy.fill", String(localized: "기록 도전"), String(localized: "속도와 집중력으로 최고 기록"), .gg.warning)
            ForEach(recordModes, id: \.self) { sessionModeRow($0) }
        }
    }

    // 게임 — 구구 계단·구구 몬스터 + 구구 꾸미기(옷장). 순서·문구는 Minigame 정의 하나를 학습 탭 단 시트와 같이 쓴다
    private var gamesGroup: some View {
        VStack(alignment: .leading, spacing: 8) {
            groupHeader("gamecontroller.fill", String(localized: "게임"), String(localized: "오르고, 물리치며 구구단 연습"), .gg.emerald)
            HStack(spacing: 12) {
                ForEach(Minigame.allCases) { g in
                    gameCard(g) { router.open(g) }
                }
            }
            wardrobeCard
        }
    }

    /// 게임 진입 카드 — 두 장을 나란히 놓는 큰 카드. 글을 못 읽어도 고를 수 있게 게임 그림을 크게 두고,
    /// 이름·배지(무료 "N문제 체험", 이용권 "새 게임")만 얹는다. 한 줄 소개는 화면 읽기 도움말로 남긴다.
    /// 그림은 천천히 숨쉰다(두 카드 위상 반대) — 게임·세션·옷장이 홈을 덮는 동안은 멈춤
    private func gameCard(_ g: Minigame, action: @escaping () -> Void) -> some View {
        let badge = g.badge(isPremium: premium.isPremium)
        let phase = Double(Minigame.allCases.firstIndex(of: g)!) * Motion.cardBreathPhaseStep
        let covered = router.minigame != nil || router.wardrobeOpen || session.active != nil
        return Button {
            Haptics.impact(.light)
            action()
        } label: {
            VStack(spacing: 8) {
                GameArt.image(g.thumbnail)
                    .resizable()
                    .scaledToFit()
                    .cardBreathing(phase: phase, paused: covered)
                    .padding(8)
                    .frame(width: 96, height: 96)
                    .background(g.iconBg, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .accessibilityHidden(true)
                Text(g.title)
                    .font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                    .lineLimit(1).minimumScaleFactor(0.6)
                Text(badge)
                    .font(.suite(.extrabold, 11)).foregroundStyle(g.tint)
                    .lineLimit(1).minimumScaleFactor(0.7)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(g.tint.opacity(0.14), in: Capsule())
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(
                LinearGradient(colors: [g.tint.opacity(0.16), g.tint.opacity(0.05)], startPoint: .top, endPoint: .bottom),
                in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(g.tint.opacity(0.35), lineWidth: 1.5))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel("\(g.title), \(badge)")
        .accessibilityHint(g.desc)
    }

    /// 구구 꾸미기 — 게임에서 모은 별로 장신구를 사서 씌운다(무료 사용자에게도 열림, 대표님 결정 Q5)
    private var wardrobeCard: some View {
        let stars = playRewards.stars
        let tint = Color.gg.warning
        return Button {
            Haptics.impact(.light)
            router.openWardrobe()
        } label: {
            HStack(spacing: 14) {
                gameThumbnail("icon_hanger", bg: tint.opacity(0.15), inset: 10)
                VStack(alignment: .leading, spacing: 4) {
                    Text("구구 꾸미기").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                    HStack(spacing: 4) {
                        GameArt.image("icon_coin").resizable().scaledToFit().frame(width: 16, height: 16)
                        Text("모은 별 \(stars)개").monospacedDigit()
                    }
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer(minLength: 4)
                HStack(spacing: 4) {
                    Text("옷장 열기").font(.suite(.extrabold, 13)).lineLimit(1).fixedSize()
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(tint)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(String(localized: "구구 꾸미기, 모은 별 \(stars)개"))
        .accessibilityHint(String(localized: "눌러서 옷장 열기"))
    }

    /// 게임 그림 썸네일 — 둥근 받침 위에 그림을 맞춰 얹는다
    private func gameThumbnail(_ file: String, bg: Color, inset: CGFloat = 4) -> some View {
        GameArt.image(file)
            .resizable()
            .scaledToFit()
            .padding(inset)
            .frame(width: 60, height: 60)
            .background(bg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .accessibilityHidden(true)
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
                meta: best > 0 ? String(localized: "최고 \(best)점 · \(ModeStyle.meta(id))") : String(localized: "\(ModeStyle.meta(id)) · 전체 랜덤"),
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

    // MARK: 취약 문제 복습
    private var weakReview: some View {
        Button {
            Haptics.impact(.light)
            session.startReview()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "book.closed.fill")
                    .foregroundStyle(Color.gg.danger)
                    .frame(width: 40, height: 40)
                    .background(Color.gg.danger.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("취약 문제 복습").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                    Text("헷갈렸던 문제 \(weakCount)개를 모아 다시 풀어요").font(.suite(.regular, 13)).foregroundStyle(Color.gg.textMuted)
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
}
