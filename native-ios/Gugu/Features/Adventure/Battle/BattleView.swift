import SwiftUI

// 배틀 화면 (BattleScreen.tsx 이식) — hp/speed/counter 3종 + 보스 분노 + 인트로/결과 오버레이

struct BattleView: View {
    let npc: NpcDef
    var onWorld: () -> Void
    var onRetry: () -> Void
    var onFlee: () -> Void

    @Environment(GameStore.self) private var game
    @Environment(AdventureStore.self) private var adventure
    @State private var engine: BattleEngine?
    @State private var confetti = 0
    @State private var levelUp = false

    var body: some View {
        ZStack {
            Color.gg.bg.ignoresSafeArea()
            if let engine {
                battle(engine)
                if engine.phase == .intro { intro(engine) }
                if engine.phase == .end, let end = engine.end { result(engine, end) }
            }
            ConfettiView(trigger: confetti)
            if let e = engine?.end {
                AchievementToast(ids: e.commit.unlocked + e.advUnlocked)
                    .padding(.horizontal, 20).padding(.top, 12)
                    .frame(maxHeight: .infinity, alignment: .top)
            }
            LevelUpOverlay(show: levelUp, level: engine?.end?.commit.newLevel ?? 0) { levelUp = false }
                .animation(.spring(response: 0.4, dampingFraction: 0.7), value: levelUp)
        }
        .onAppear {
            if engine == nil {
                let e = BattleEngine(npc: npc, game: game, adventure: adventure)
                e.start()
                engine = e
            }
        }
        .onDisappear { engine?.teardown() }
        .onChange(of: engine?.end?.won) { _, won in
            guard let won else { return }
            if won { confetti += 1 }
            if engine?.end?.commit.leveledUp == true {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { levelUp = true }
            }
        }
    }

    // MARK: - 전투 본 화면

    @ViewBuilder
    private func battle(_ engine: BattleEngine) -> some View {
        @Bindable var engine = engine
        VStack(spacing: 0) {
            topBar(engine)
            npcPanel(engine)
            if engine.battleStyle == .counter && engine.phase == .play && engine.feedback == nil {
                CounterTimer(startClock: engine.qStartClock, limitMs: engine.counterLimit)
                    .padding(.top, 8)
            }
            problemArea(engine)
            playerPanel(engine)
            Keypad(
                onInput: { engine.handleInput($0) },
                onDelete: { engine.handleDelete() },
                onSubmit: { engine.handleManualSubmit() },
                canSubmit: !engine.input.isEmpty
            )
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 16)
    }

    private func topBar(_ engine: BattleEngine) -> some View {
        HStack(spacing: 12) {
            Button { onFlee() } label: {
                Image(systemName: "xmark").font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.textMuted)
            }
            Text("\(engine.isBossBattle ? "보스 대결" : Battle.styleName(engine.battleStyle)) · \(npc.table)단")
                .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            Spacer()
            if engine.combo >= 2 {
                Pill(bg: Color.gg.danger.opacity(0.15), fg: .gg.danger) {
                    Image(systemName: "flame.fill"); Text("\(engine.combo) 콤보")
                }
                .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: engine.combo)
        .padding(.bottom, 12)
    }

    private func npcPanel(_ engine: BattleEngine) -> some View {
        HStack(spacing: 12) {
            BattleAvatar(color: npc.color, size: 52, boss: engine.isBossBattle, hit: engine.npcHit, enraged: engine.enraged)
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(npc.name).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text).lineLimit(1)
                    if engine.isBossBattle {
                        Text("보스").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.danger)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(Color.gg.danger.opacity(0.15), in: Capsule())
                    }
                    if engine.enraged {
                        HStack(spacing: 2) { Image(systemName: "flame.fill").font(.system(size: 9)); Text("분노").font(.suite(.extrabold, 10)) }
                            .foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 2).background(Color.gg.danger, in: Capsule())
                    }
                }
                if engine.battleStyle == .speed {
                    statBar(value: Double(engine.npcScore) / Double(Battle.raceTarget), label: "\(engine.npcScore)/\(Battle.raceTarget)", color: .gg.warning)
                } else {
                    statBar(value: Double(engine.npcHp) / Double(engine.npcMaxHp),
                            label: "\(max(0, engine.npcHp))/\(engine.npcMaxHp)",
                            color: Double(engine.npcHp) / Double(engine.npcMaxHp) > 0.4 ? .gg.danger : .gg.warning)
                }
            }
        }
        .overlay(alignment: .topTrailing) {
            if let f = engine.npcFloat { FloatText(f).id(f.id) }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .ggCard(padding: 0)
    }

    private func playerPanel(_ engine: BattleEngine) -> some View {
        HStack(spacing: 12) {
            BattleAvatar(color: adventure.equippedColorHex, size: 44, boss: false, hit: engine.playerHit, enraged: false)
            VStack(alignment: .leading, spacing: 6) {
                Text("나").font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                if engine.battleStyle == .speed {
                    statBar(value: Double(engine.playerScore) / Double(Battle.raceTarget), label: "\(engine.playerScore)/\(Battle.raceTarget)", color: .gg.accent)
                } else {
                    statBar(value: Double(engine.playerHp) / Double(Battle.playerMaxHp), label: "\(max(0, engine.playerHp))/\(Battle.playerMaxHp)", color: .gg.success)
                }
            }
        }
        .overlay(alignment: .topTrailing) {
            if let f = engine.playerFloat { FloatText(f).id(f.id) }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .ggCard(padding: 0)
        .padding(.vertical, 12)
    }

    private func problemArea(_ engine: BattleEngine) -> some View {
        let answer = engine.problem.a * engine.problem.b
        let showAnswer = engine.feedback == .wrong
        return VStack(spacing: 12) {
            Spacer()
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("\(engine.problem.a)").foregroundStyle(Color.gg.text)
                Text("×").foregroundStyle(Color.gg.textMuted)
                Text("\(engine.problem.b)").foregroundStyle(Color.gg.text)
                Text("=").foregroundStyle(Color.gg.textMuted)
                Text(showAnswer ? "\(answer)" : (engine.input.isEmpty ? "?" : engine.input))
                    .foregroundStyle(showAnswer ? Color.gg.success : (engine.input.isEmpty ? Color.gg.border : Color.gg.accent))
            }
            .font(.suiteNum(.extrabold, 52)).monospacedDigit()
            if showAnswer {
                Text(engine.timedOut ? "시간 초과! 정답은 \(answer) — \(npc.name)의 반격!"
                     : engine.battleStyle == .speed ? "정답은 \(answer) 이에요"
                     : "정답은 \(answer) — \(npc.name)의 반격!")
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted).multilineTextAlignment(.center)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func statBar(value: Double, label: String, color: Color) -> some View {
        HStack(spacing: 8) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.gg.surface2)
                    Capsule().fill(color).frame(width: geo.size.width * max(0, min(1, value)))
                        .animation(.easeOut(duration: 0.3), value: value)
                }
            }
            .frame(height: 12)
            Text(label).font(.suite(.extrabold, 11)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                .frame(width: 60, alignment: .trailing)
        }
    }

    // MARK: - 인트로

    private func intro(_ engine: BattleEngine) -> some View {
        let rule: String = {
            switch engine.battleStyle {
            case .speed: return "먼저 \(Battle.raceTarget)문제를 맞히면 승리!"
            case .counter: return "\(engine.counterLimit / 1000)초 안에 못 풀면 반격당해요!"
            case .hp: return engine.isBossBattle ? "HP가 절반이 되면 분노해요 — 조심!" : "정답이면 공격, 오답이면 반격!"
            }
        }()
        return ZStack {
            Color.gg.bg.opacity(0.95).ignoresSafeArea()
            VStack(spacing: 16) {
                BattleAvatar(color: npc.color, size: 88, boss: engine.isBossBattle, hit: 0, enraged: false)
                VStack(spacing: 4) {
                    Text(npc.name).font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
                    Text("\u{201C}\(npc.greeting)\u{201D}").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).multilineTextAlignment(.center)
                }
                Text("\(Battle.styleName(engine.battleStyle)) — \(rule)")
                    .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.textMuted)
                    .padding(.horizontal, 16).padding(.vertical, 6).background(Color.gg.surface2, in: Capsule())
                HStack(spacing: 8) {
                    Image(systemName: "shield.lefthalf.filled"); Text("대결 시작!")
                }
                .font(.suite(.extrabold, 18)).foregroundStyle(.white)
                .padding(.horizontal, 20).padding(.vertical, 10).background(Color.gg.danger, in: Capsule())
            }
            .padding(32)
        }
        .transition(.opacity)
    }

    // MARK: - 결과

    private func result(_ engine: BattleEngine, _ end: BattleEnd) -> some View {
        let worldCleared = engine.isBossBattle && npc.table == 9
        let nextRegion = engine.isBossBattle ? World.region(for: npc.table + 1) : nil
        return ZStack {
            Color.gg.bg.opacity(0.95).ignoresSafeArea()
            VStack(spacing: 16) {
                BattleAvatar(color: end.won ? adventure.equippedColorHex : npc.color, size: 72, boss: engine.isBossBattle && !end.won, hit: 0, enraged: false)
                VStack(spacing: 4) {
                    Text(end.won ? (worldCleared ? "월드 클리어! 👑" : "승리! 🎉") : "아쉬운 패배…")
                        .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
                    Text(end.won ? (worldCleared ? "모든 지역을 정복했어요 — 진정한 구구단 정복자!" : "\(npc.name)을(를) 물리쳤어요!")
                         : "괜찮아요, 답을 익히고 다시 도전해요")
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).multilineTextAlignment(.center)
                }
                if end.won, engine.isBossBattle, !worldCleared, let next = nextRegion {
                    HStack(spacing: 6) {
                        Image(systemName: "lock.open.fill").font(.system(size: 13))
                        Text("\(next.name)(\(next.table)단)이 열렸어요!")
                    }
                    .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.accent)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Color.gg.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                HStack(spacing: 10) {
                    resultStat(icon: "sparkles", tint: .gg.warning, label: "획득 XP", value: "+\(end.commit.xpEarned)")
                    resultStat(icon: "flame.fill", tint: .gg.danger, label: "최고 콤보", value: "\(engine.maxComboReached)")
                }
                if let table = end.commit.table {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(table)단 마스터리").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                            if end.commit.improvedStars { Text("새 기록!").font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent) }
                        }
                        Spacer()
                        StarsView(count: end.commit.newStars, size: 22)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                VStack(spacing: 10) {
                    if !end.won {
                        GGButton(variant: .primary, size: .lg, action: onRetry) {
                            Image(systemName: "arrow.counterclockwise"); Text("다시 도전")
                        }
                    }
                    GGButton(variant: end.won ? .primary : .surface, size: .lg, action: onWorld) {
                        Text("월드로 돌아가기")
                    }
                }
            }
            .padding(24)
            .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding(.horizontal, 24)
        }
        .transition(.opacity)
    }

    private func resultStat(icon: String, tint: Color, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 12)).foregroundStyle(tint)
                Text(label).font(.suite(.bold, 11)).foregroundStyle(Color.gg.textMuted)
            }
            Text(value).font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - 배틀 아바타 (월드 캐릭터와 같은 색/눈)

struct BattleAvatar: View {
    let color: String
    let size: CGFloat
    var boss: Bool
    var hit: Int
    var enraged: Bool

    @State private var knock: CGFloat = 0

    var body: some View {
        ZStack {
            Circle().fill(Color(hex: color)).frame(width: size, height: size)
                .overlay(enraged ? Circle().strokeBorder(Color.gg.danger, lineWidth: 2) : nil)
                .scaleEffect(enraged ? 1.08 : 1)
            HStack(spacing: size * 0.14) {
                ForEach(0..<2, id: \.self) { _ in
                    Circle().fill(.white).frame(width: size * 0.18, height: size * 0.18)
                        .overlay(Circle().strokeBorder(Color(red: 0.13, green: 0.14, blue: 0.17), lineWidth: max(2, size * 0.03)))
                }
            }
            if boss {
                Image(systemName: "crown.fill").font(.system(size: size * 0.28)).foregroundStyle(Color.gg.warning)
                    .offset(y: -size * 0.62)
            }
        }
        .frame(width: size, height: size)
        .rotationEffect(.degrees(knock))
        .offset(x: knock)
        .onChange(of: hit) { _, newVal in
            guard newVal > 0 else { return }
            withAnimation(.spring(response: 0.15, dampingFraction: 0.4)) { knock = -8 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) { knock = 0 }
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: enraged)
    }
}

// MARK: - 뜨는 데미지 숫자

struct FloatText: View {
    let floater: Floater
    @State private var up = false
    init(_ f: Floater) { floater = f }
    var body: some View {
        Text(floater.text)
            .font(.suite(.extrabold, 18)).foregroundStyle(Color.gg.danger).monospacedDigit()
            .offset(y: up ? -26 : 0)
            .opacity(up ? 0 : 1)
            .onAppear { withAnimation(.easeOut(duration: 0.7)) { up = true } }
    }
}

// MARK: - 반격전 카운트다운

struct CounterTimer: View {
    let startClock: Double
    let limitMs: Int
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
            let left = max(0, Double(limitMs) - (CACurrentMediaTime() * 1000 - startClock))
            let urgent = left <= 2000
            HStack(spacing: 8) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(urgent ? Color.gg.danger : Color.gg.accent)
                            .frame(width: geo.size.width * (left / Double(limitMs)))
                    }
                }
                .frame(height: 8)
                Text(String(format: "%.1fs", left / 1000))
                    .font(.suite(.extrabold, 11)).foregroundStyle(urgent ? Color.gg.danger : Color.gg.textMuted)
                    .monospacedDigit().frame(width: 44, alignment: .trailing)
            }
        }
    }
}
