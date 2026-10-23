import SwiftUI

// 구구 팡팡 화면 — 하늘에서 떨어지는 숫자 풍선 중 미션에 맞는 것만 눌러 터뜨린다.
// 진행 중: HUD(점수·최고·콤보·하트·일시정지) + 고정 위치 미션 막대 + 놀이 영역(Canvas).
// 준비·종료: 단 칩(섞어서 + 2~9단)·최고 기록·무료 체험 안내·시작 버튼 패널.
// 실시간 게임이라 화면 낭독기로는 할 수 없다(다른 미니게임과 같음) — 미션·점수·하트·결과는 읽히게 한다.

struct PopView: View {
    var onExit: () -> Void

    @State private var engine: PopEngine
    @State private var paywallOpen = false
    /// 지금 누르고 있는 손가락의 시작점 — 한 번 누르면 한 번만 쏜다 (누르는 순간 발사 — 손을 뗄 때까지 기다리면 늦다).
    /// 불(Bool) 대신 시작점을 기억하는 이유: 시스템이 제스처를 취소하면 onEnded 가 오지 않아도 다음 터치는 시작점이 달라 다시 쏠 수 있다
    @State private var touchStart: CGPoint?
    @Environment(PremiumStore.self) private var premium
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// start: 처음 골라 둘 단 — 단 시트에서 열면 그 단(섞어서 = nil), 홈에서 열면 기본값(섞어서)
    init(start: MinigameStart = .standard, onExit: @escaping () -> Void) {
        self.onExit = onExit
        _engine = State(initialValue: PopEngine(table: start.table(defaultTable: PopEngine.defaultTable)))
    }

    private static let navy = Color(hex: "#24306b")
    private static let cream = Color(hex: "#fff5db")

    private var game: PopState { engine.game }
    private var ready: Bool { game.phase == .ready }
    private var over: Bool { game.phase == .over }
    private var paused: Bool { game.phase == .paused }
    private var playing: Bool { !ready && !over }
    /// 무료 체험 판이 끝났다 — 다시 하기 대신 결제 화면으로 안내한다(구매하면 바로 일반 종료 화면으로 바뀐다).
    /// 첫 웨이브를 깨든 그 도중 하트를 다 쓰든 같다 — 다른 미니게임과 같은 규칙 (대표님 결정 2026-10-23)
    private var trialEnded: Bool { over && game.trialLeft != nil && !premium.isPremium }

    var body: some View {
        VStack(spacing: 0) {
            header
            if playing {
                stage
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        hero
                        panel
                            .padding(16)
                            .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .strokeBorder(Color.gg.border, lineWidth: 1))
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
                .scrollIndicators(.hidden)
            }
        }
        .readableWidth(560)
        .background(Color.gg.bg.ignoresSafeArea())
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { engine.pause() }
        }
        .onDisappear { engine.teardown() }
        // 미니게임은 루트의 fullScreenCover 라 루트의 결제 시트가 위에 뜨지 못한다 — 여기서 직접 띄운다
        .sheet(isPresented: $paywallOpen) { PaywallView() }
    }

    // MARK: - 헤더

    private var header: some View {
        HStack {
            Button {
                Haptics.impact(.light)
                engine.pause()
                onExit()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left").font(.system(size: 14, weight: .bold))
                    Text("나가기").font(.suite(.bold, 14))
                }
                .foregroundStyle(Color.gg.textMuted)
                .frame(minHeight: 44)
            }
            Spacer()
            Pill(bg: Color.gg.surface, fg: .gg.textMuted) {
                Image(systemName: Minigame.pop.icon)
                Text(Minigame.pop.title)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 8)
    }

    // MARK: - 진행 중 무대

    private var stage: some View {
        VStack(spacing: 0) {
            hud
            missionBar
            GeometryReader { geo in
                let layout = PopLayout(size: geo.size)
                ZStack {
                    PopScene(game: game, effects: engine.effects, clockMs: engine.clockMs,
                             aim: engine.aim, reducedMotion: reduceMotion)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    guard touchStart != value.startLocation else { return }
                                    touchStart = value.startLocation
                                    let p = layout.normalized(value.startLocation)
                                    engine.tap(x: p.x, y: p.y, aspect: layout.aspect)
                                }
                                .onEnded { _ in touchStart = nil }
                        )
                        .accessibilityElement()
                        .accessibilityLabel("구구 팡팡 놀이 영역")
                        .accessibilityHint("실시간 게임이라 화면을 보며 풍선을 눌러요")

                    if game.introMs > 0 && !paused { introBanner.transition(.opacity) }
                    if paused { pauseOverlay }
                }
                .animation(.easeOut(duration: 0.2), value: game.introMs > 0)
            }
            .clipped()
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
            .strokeBorder(Self.navy.opacity(0.2), lineWidth: 1))
    }

    private var hud: some View {
        HStack(spacing: 12) {
            HStack(spacing: 5) {
                Image(systemName: "star.fill").font(.system(size: 14, weight: .bold))
                Text(verbatim: "\(game.score)").font(.suiteNum(.extrabold, 17)).monospacedDigit()
            }
            .foregroundStyle(Self.cream)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "점수 \(game.score)점"))

            HStack(spacing: 4) {
                Image(systemName: "trophy.fill").font(.system(size: 11, weight: .bold))
                Text(verbatim: "\(engine.displayBest)").font(.suiteNum(.bold, 13)).monospacedDigit()
            }
            .foregroundStyle(Color(hex: "#ffe1aa"))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "최고 기록 \(engine.displayBest)점"))

            if game.combo >= 2 {
                comboPill
            }

            Spacer(minLength: 0)

            HStack(spacing: 3) {
                ForEach(1...Pop.maxHearts, id: \.self) { heart in
                    Image(systemName: heart <= game.hearts ? "heart.fill" : "heart")
                        .font(.system(size: 14))
                        .foregroundStyle(heart <= game.hearts ? Color(hex: "#ff9fb3") : Color(hex: "#8a90b8"))
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "남은 하트 \(game.hearts)개"))

            Button {
                Haptics.impact(.light)
                engine.togglePause()
            } label: {
                Image(systemName: paused ? "play.fill" : "pause.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Self.cream)
                    .frame(width: 44, height: 44)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
            }
            .accessibilityLabel(paused ? "게임 계속하기" : "일시정지")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .background(Self.navy)
    }

    private var comboPill: some View {
        let multiplier = Pop.multiplier(combo: game.combo)
        return HStack(spacing: 4) {
            Text("\(game.combo)콤보").font(.suite(.extrabold, 11))
            if multiplier > 1 {
                Text(verbatim: "×\(multiplier)").font(.suiteNum(.heavy, 12))
            }
        }
        .foregroundStyle(Self.navy)
        .padding(.horizontal, 8).padding(.vertical, 3)
        .background(multiplier > 1 ? Color(hex: "#ffcf4a") : Self.cream, in: Capsule())
        .fixedSize()
    }

    /// 미션 막대 — 높이와 위치가 고정이다 (문제 위치를 움직이지 않는 원칙). 보스 웨이브에도 같은 자리에 같은 크기
    private var missionBar: some View {
        HStack(spacing: 10) {
            Image(systemName: isBoss ? "shield.lefthalf.filled" : "target")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(isBoss ? Color(hex: "#6b4fd8") : Color.gg.sky)
                .frame(width: 32, height: 32)
                .background((isBoss ? Color(hex: "#6b4fd8") : Color.gg.sky).opacity(0.14), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(game.mission.text)
                    .font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                    .lineLimit(1).minimumScaleFactor(0.7)
                HStack(spacing: 6) {
                    Text("웨이브 \(game.wave)").font(.suite(.bold, 11)).foregroundStyle(Color.gg.textMuted)
                    if let left = game.trialLeft, left > 0 {
                        Text("체험 웨이브")
                            .font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    }
                    // 황금 풍선 느려짐 — 놀이 영역 위에 띄우면 위에서 들어오는 풍선을 가려서 미션 막대 안에 둔다
                    if game.slowMs > 0 { slowChip }
                }
            }
            Spacer(minLength: 4)
            missionProgress
        }
        .padding(.horizontal, 12)
        .frame(height: 56)
        .frame(maxWidth: .infinity)
        .background(Color.gg.surface)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(missionLabel)
        .accessibilityAddTraits(.updatesFrequently)
    }

    private var isBoss: Bool {
        if case .pairBoss = game.mission { return true }
        return false
    }

    @ViewBuilder
    private var missionProgress: some View {
        if let boss = game.boss {
            HStack(spacing: 8) {
                // 먼저 터뜨린 짝꿍 한쪽 — 다음에 곱할 수를 찾는다
                Text(verbatim: game.pending.map { "\($0) × ?" } ?? "? × ?")
                    .font(.suiteNum(.extrabold, 18))
                    .foregroundStyle(game.pending == nil ? Color.gg.textMuted : Color(hex: "#6b4fd8"))
                    .monospacedDigit()
                HStack(spacing: 3) {
                    ForEach(0..<Pop.bossHits, id: \.self) { i in
                        Image(systemName: i < boss.hitsLeft ? "shield.fill" : "shield")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(i < boss.hitsLeft ? Color(hex: "#e0a800") : Color.gg.border)
                    }
                }
            }
        } else if case .multiples = game.mission {
            Text(verbatim: "\(game.handled)/\(Pop.waveGoal)")
                .font(.suiteNum(.extrabold, 18)).foregroundStyle(Color.gg.text).monospacedDigit()
        }
    }

    private var missionLabel: String {
        var parts = [game.mission.text, String(localized: "웨이브 \(game.wave)")]
        if let boss = game.boss {
            if let p = game.pending { parts.append(String(localized: "짝꿍 \(p) 고름")) }
            parts.append(String(localized: "보스 방패 \(boss.hitsLeft)개 남음"))
        } else {
            parts.append(String(localized: "\(Pop.waveGoal)개 중 \(game.handled)개"))
        }
        return parts.joined(separator: ", ")
    }

    /// 웨이브 시작 전 미션 안내 — 풍선이 멈춰 있는 동안만 보인다
    private var introBanner: some View {
        VStack(spacing: 6) {
            Text(isBoss ? String(localized: "짝꿍 보스 등장!") : String(localized: "웨이브 \(game.wave)"))
                .font(.suite(.extrabold, 14)).foregroundStyle(isBoss ? Color(hex: "#6b4fd8") : Color.gg.sky)
            Text(game.mission.text)
                .font(.suite(.heavy, 26)).foregroundStyle(Self.navy)
                .multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.7)
            if isBoss {
                Text("짝꿍 두 개를 차례로 팡!")
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
            }
        }
        .padding(.horizontal, 22).padding(.vertical, 16)
        .background(Color.white.opacity(0.94), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: Self.navy.opacity(0.18), radius: 12, y: 4)
        .padding(.horizontal, 24)
        .allowsHitTesting(false)
        .accessibilityHidden(true)   // 미션 막대가 같은 내용을 읽고, 시작 때 안내 방송을 한다
    }

    private var slowChip: some View {
        HStack(spacing: 3) {
            Image(systemName: "tortoise.fill").font(.system(size: 9, weight: .bold))
            Text("느려졌어요!").font(.suite(.extrabold, 10))
        }
        .foregroundStyle(Color(hex: "#8a5a00"))
        .padding(.horizontal, 6).padding(.vertical, 1)
        .background(Color(hex: "#ffe28a"), in: Capsule())
        .fixedSize()
    }

    private var pauseOverlay: some View {
        ZStack {
            Color(hex: "#eef8ff").opacity(0.95)
            VStack(spacing: 12) {
                Text("잠깐 쉬어가요")
                    .font(.suite(.extrabold, 20)).foregroundStyle(Self.navy)
                GGButton(variant: .primary, size: .md, action: { engine.togglePause() }) {
                    Text("이어서 팡팡")
                }
                .frame(maxWidth: 200)
            }
            .padding(.horizontal, 16)
        }
    }

    // MARK: - 준비·종료 패널

    /// 꾸밈 그림 — 숫자 풍선 세 개 (낭독하지 않는다)
    private var hero: some View {
        HStack(alignment: .bottom, spacing: 14) {
            heroBalloon("14", Color(hex: "#ff8fab"), lift: 10)
            heroBalloon("21", Color(hex: "#ffc93c"), lift: 0)
            heroBalloon("35", Color(hex: "#7cc6fe"), lift: 16)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 120)
        .background(
            LinearGradient(colors: [Color(hex: "#bfe3ff"), Color(hex: "#eef8ff")], startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityHidden(true)
    }

    private func heroBalloon(_ value: String, _ color: Color, lift: CGFloat) -> some View {
        VStack(spacing: 0) {
            Ellipse().fill(color)
                .frame(width: 56, height: 62)
                .overlay(Text(verbatim: value).font(.suiteNum(.extrabold, 22)).foregroundStyle(Color(hex: "#1f2544")))
            Rectangle().fill(Color(hex: "#1f2544").opacity(0.3)).frame(width: 1.5, height: 18)
        }
        .padding(.bottom, lift)
    }

    @ViewBuilder
    private var panel: some View {
        if trialEnded {
            TrialEndPanel(correct: game.pops, lead: String(localized: "풍선 \(game.pops)개를 팡! "),
                          onUnlock: { paywallOpen = true }, onExit: onExit)
        } else {
            setupPanel
        }
    }

    private var setupPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            if over {
                results
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text("미션에 맞는 풍선만 팡!")
                        .font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                    Text("숫자 풍선이 내려와요. 미션에 맞는 풍선만 눌러 터뜨려요. 정답 풍선이 지붕에 닿으면 하트가 하나 줄어요. 다른 풍선을 눌러도 하트는 그대로예요!")
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("연습할 단")
                        .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                    Spacer()
                    let best = engine.bests[Pop.bestKey(engine.table)] ?? 0
                    if best > 0 {
                        HStack(spacing: 4) {
                            Image(systemName: "trophy.fill").font(.system(size: 10, weight: .bold))
                            Text("최고 \(best)").font(.suite(.extrabold, 11))
                        }
                        .foregroundStyle(Color.gg.warning)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(String(localized: "최고 기록 \(best)점"))
                    }
                }
                let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)
                LazyVGrid(columns: columns, spacing: 8) {
                    tableChip(nil)
                    ForEach(PopEngine.tables, id: \.self) { tableChip($0) }
                }
            }

            if !premium.isPremium { TrialNotice(title: PopTrial.noticeTitle) }

            GGButton(variant: .primary, size: .lg, action: { engine.start(trial: PopTrial.limit(isPremium: premium.isPremium)) }) {
                Image(systemName: over ? "arrow.counterclockwise" : "play.fill")
                Text(over ? "다시 팡팡" : "팡팡 시작")
            }

            Text("하트 3개 · 3번째 웨이브마다 짝꿍 보스가 나와요. 단별 최고 기록은 이 기기에 저장돼요.")
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var results: some View {
        VStack(spacing: 12) {
            VStack(spacing: 4) {
                Text("\(game.score)점!")
                    .font(.suite(.heavy, 28)).foregroundStyle(Color.gg.text).monospacedDigit()
                Text(game.score > 0 && game.score >= engine.runBest ? "나의 최고 기록이에요!" : "토끼와 다시 팡팡 해 볼까요?")
                    .font(.suite(.bold, 13)).foregroundStyle(game.score > 0 && game.score >= engine.runBest ? Color.gg.warning : Color.gg.textMuted)
                    .multilineTextAlignment(.center)
            }
            .accessibilityElement(children: .combine)
            HStack(spacing: 8) {
                stat(String(localized: "터뜨린 풍선"), game.pops)
                stat(String(localized: "최고 콤보"), game.maxCombo)
                stat(String(localized: "웨이브"), game.wave)
                stat(String(localized: "물리친 보스"), game.bossesDefeated)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func stat(_ label: String, _ value: Int) -> some View {
        VStack(spacing: 2) {
            Text(verbatim: "\(value)").font(.suiteNum(.extrabold, 18)).foregroundStyle(Color.gg.text).monospacedDigit()
            Text(label).font(.suite(.bold, 10)).foregroundStyle(Color.gg.textMuted)
                .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, minHeight: 52)
        .padding(.vertical, 6)
        .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(label) \(value)"))
    }

    private func tableChip(_ value: Int?) -> some View {
        let selected = engine.table == value
        return Button {
            Haptics.selection()
            engine.table = value
        } label: {
            Text(value.map { String(localized: "\($0)단") } ?? String(localized: "섞어서"))
                .font(.suite(.extrabold, 13))
                .lineLimit(1).minimumScaleFactor(0.8)
                .foregroundStyle(selected ? Self.cream : Color.gg.text)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(selected ? Self.navy : Color.gg.surface2)
                )
        }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
