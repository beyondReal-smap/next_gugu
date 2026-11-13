import SwiftUI
import StoreKit

// 세션 결과 화면 (ResultScreen.tsx 이식)

struct ResultView: View {
    @Bindable var engine: SessionEngine
    let done: SessionDone
    var onClose: () -> Void

    @Environment(GameStore.self) private var game
    @Environment(ReminderStore.self) private var reminder
    @Environment(PushStore.self) private var push
    @Environment(AuthStore.self) private var auth
    @Environment(PremiumStore.self) private var premium
    @Environment(\.requestReview) private var requestReview
    @State private var confetti = 0
    @State private var levelUp = false
    @State private var reminderBusy = false
    @State private var reminderNotice: String?

    /// 권유 시각 = 지금 시각. "이 시간에" 공부한 아이는 내일도 이 시간이 편하다 (야간은 8~20시로 당긴다)
    private var offerHour: Int {
        ReminderPlanner.clampHour(Calendar.current.component(.hour, from: Date()))
    }

    private var result: SessionResult { done.result }
    private var commit: CommitResult { done.commit }
    private var def: ModeDef { Modes.def(result.mode) }

    private var total: Int { result.answers.count }
    private var correct: Int { total - done.wrongCount }
    private var accuracy: Int { total > 0 ? Int((Double(correct) / Double(total) * 100).rounded()) : 0 }
    private var avgMs: Int { total > 0 ? Int((Double(result.answers.reduce(0) { $0 + $1.ms }) / Double(total)).rounded()) : 0 }
    private var best: Int? {
        guard let score = commit.score else { return nil }
        return max(game.state.bestScores[result.mode] ?? 0, score)
    }

    private var headline: String {
        if def.scored { return commit.isNewBest ? String(localized: "신기록 달성! 🏆") : String(localized: "수고했어요!") }
        if accuracy >= 95 { return String(localized: "완벽해요! 🎯") }
        if accuracy >= 70 { return String(localized: "잘했어요!") }
        return String(localized: "좋은 시도예요!")
    }

    @ViewBuilder
    private var reminderOffer: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let reminderNotice {
                Label(reminderNotice, systemImage: "bell")
                    .font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "bell.badge").font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.gg.accent)
                    Text("내일도 이 시간에 알려드릴까요?")
                        .font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                }
                Text("매일 \(ReminderPlanner.hourLabel(offerHour))에 오늘의 구구단을 알려드려요. 밤에는 보내지 않아요.")
                    .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                // 허용하면 공지 알림도 켠다 — 원격 푸시를 쓸 수 있는 빌드에서만 알린다
                if push.isAvailable {
                    Text(PushCopy.reminderOfferNoticeLine)
                        .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md, action: { reminder.declineOffer() }) {
                        Text("괜찮아요")
                    }
                    GGButton(variant: .primary, size: .md, action: acceptReminder) {
                        Text(reminderBusy ? "설정 중…" : "알림 받기")
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .ggCard(padding: 16)
    }

    private func acceptReminder() {
        guard !reminderBusy else { return }
        reminderBusy = true
        Task {
            let hour = offerHour
            let granted = await reminder.enable(hour: hour, state: game.state)
            if granted {
                // 권한을 방금 받았으므로 시스템 팝업 없이 공지 알림을 켠다 (기존 사용자는 이 동의 전까지 꺼짐).
                // 토큰·서버 등록은 시간이 걸릴 수 있어 카드를 기다리게 하지 않는다
                let ctx = PushStore.Context.current(auth: auth, premium: premium, reminder: reminder)
                Task { _ = await push.setNotice(true, ctx: ctx) }
            }
            reminderBusy = false
            reminderNotice = granted
                ? String(localized: "매일 \(ReminderPlanner.hourLabel(hour))에 알려드릴게요. 프로필 > 설정에서 바꿀 수 있어요.")
                : String(localized: "알림이 꺼져 있어요. 설정 앱에서 구구 어드벤처 알림을 켤 수 있어요.")
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(engine.modeName) 완료").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                        Text(headline).font(.suite(.extrabold, 28)).foregroundStyle(Color.gg.text)
                    }
                    .padding(.top, 24)
                    .accessibilityElement(children: .combine)

                    if def.scored, let score = commit.score {
                        scoreHero(score)
                    }
                    statGrid
                    if let table = commit.table {
                        masteryRow(table)
                    }
                    if !missedFacts.isEmpty {
                        missedList
                    }
                    if commit.goalReached {
                        Text("🎉 오늘의 목표를 달성했어요!")
                            .font(.suite(.bold, 14)).foregroundStyle(Color.gg.success)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.gg.success.opacity(0.15), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }

                    // 첫 실행이 아니라 한 판을 끝낸 뒤에 묻는다 — iOS 는 권한을 한 번만 물을 수 있다
                    if (reminder.shouldOfferAfterSession && !result.partial) || reminderNotice != nil {
                        reminderOffer
                    }

                    Spacer(minLength: 8)
                    actions
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }

            ConfettiView(trigger: confetti)
            AchievementToast(ids: commit.unlocked)
                .padding(.horizontal, 20).padding(.top, 12)
            LevelUpOverlay(show: levelUp, level: commit.newLevel) { levelUp = false }
                .animation(.spring(response: 0.4, dampingFraction: 0.7), value: levelUp)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gg.bg.ignoresSafeArea())
        .onAppear {
            if accuracy >= 80 || commit.isNewBest { confetti += 1 }
            if commit.leveledUp {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { levelUp = true }
            }
            askReviewIfEarned()
        }
    }

    /// 잘 해낸 판이 끝났을 때 앱 평가를 요청한다 (조건은 ReviewPolicy).
    /// 알림 권유 카드·레벨업 연출과 겹치면 이번 판은 건너뛴다 — 한 화면에 요청이 둘 이상 뜨지 않게
    private func askReviewIfEarned() {
        if reminder.shouldOfferAfterSession || commit.leveledUp { return }
        let moment = ReviewMoment(partial: result.partial, accuracy: accuracy, isNewBest: commit.isNewBest,
                                  improvedStars: commit.improvedStars, goalReached: commit.goalReached)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let log = Persistence.load(ReviewPromptLog.self, key: ReviewPolicy.storageKey) ?? ReviewPromptLog()
        let now = Date()
        guard ReviewPolicy.shouldAsk(moment: moment, state: game.state, log: log, appVersion: version, now: now) else { return }
        // 시스템이 실제로 띄웠는지는 알 수 없다(연간 3회 제한 등) — 요청한 사실을 먼저 남겨 같은 버전에서 반복하지 않는다
        Persistence.save(ReviewPolicy.recordAsk(log, appVersion: version, now: now), key: ReviewPolicy.storageKey)
        // 결과·축하 효과를 먼저 보여 준 뒤에 띄운다
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { requestReview() }
    }

    private func scoreHero(_ score: Int) -> some View {
        VStack(spacing: 8) {
            if commit.isNewBest {
                Pill(bg: Color.gg.warning.opacity(0.15), fg: .gg.warning) {
                    Image(systemName: "trophy.fill"); Text("신기록!")
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("\(score)").font(.suite(.extrabold, 56)).foregroundStyle(Color.gg.text).monospacedDigit()
                Text("점").font(.suite(.bold, 20)).foregroundStyle(Color.gg.textMuted)
            }
            if let best { Text("최고 기록 \(best)점").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit() }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .ggCard()
    }

    private var statGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            stat(icon: "checkmark", tint: .gg.success, label: String(localized: "정확도"), value: "\(accuracy)%")
            stat(icon: "gauge.medium", tint: .gg.accent, label: String(localized: "평균 속도"), value: Seconds.text(Double(avgMs) / 1000))
            stat(icon: "sparkles", tint: .gg.warning, label: String(localized: "획득 XP"), value: "+\(commit.xpEarned)")
            stat(icon: "flame.fill", tint: .gg.danger, label: String(localized: "최고 콤보"), value: "\(result.maxCombo)")
        }
    }

    private func stat(icon: String, tint: Color, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 14, weight: .bold)).foregroundStyle(tint)
                Text(label).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            }
            Text(value).font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
    }

    /// 틀린 문제 (같은 식은 한 번만, 처음 틀린 순서대로) — 아이가 쓴 답과 정답을 나란히 보여 준다
    private var missedFacts: [AnswerRecord] {
        var seen = Set<String>()
        return result.answers.filter { a in
            guard !a.correct else { return false }
            return seen.insert(Problems.key(a.a, a.b)).inserted
        }
    }

    private var missedList: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "arrow.uturn.backward.circle.fill")
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(Color.gg.danger)
                Text("다시 볼 문제").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                Text("\(missedFacts.count)").font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.danger).monospacedDigit()
            }
            ForEach(Array(missedFacts.enumerated()), id: \.offset) { _, a in
                HStack(alignment: .firstTextBaseline) {
                    Text("\(a.a) × \(a.b) = \(a.a * a.b)")
                        .font(.suite(.extrabold, 18)).foregroundStyle(Color.gg.text).monospacedDigit()
                    Spacer(minLength: 8)
                    if let mine = givenLabel(a) {
                        Text(mine)
                            .font(.suite(.bold, 13)).foregroundStyle(Color.gg.danger).monospacedDigit()
                            .strikethrough(true, color: Color.gg.danger.opacity(0.6))
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(SpokenMath.equationLabel(a.a, a.b)). \(givenLabel(a) ?? "")")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.vertical, 16)
        .ggCard(padding: 0)
    }

    /// 아이가 낸 답 — 빈칸 추리는 빈칸에 쓴 수, OX 는 고른 쪽
    private func givenLabel(_ a: AnswerRecord) -> String? {
        switch a.given {
        case let .number(n): return String(localized: "내 답 \(n)")
        case let .boolean(b): return String(localized: "내 선택 \(OxPad.mark(b))", comment: "OX 퀴즈에서 아이가 고른 기호")
        case .text, .none: return nil
        }
    }

    private func masteryRow(_ table: Int) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(table)단 마스터리").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                if commit.improvedStars {
                    Text("새 기록 달성!").font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                }
            }
            Spacer()
            StarsView(count: commit.newStars, size: 24)
        }
        .padding(.horizontal, 20).padding(.vertical, 16)
        .ggCard(padding: 0)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(commit.improvedStars ? "\(table)단 마스터리 별 \(commit.newStars)개, 새 기록"
                                                  : "\(table)단 마스터리 별 \(commit.newStars)개")
    }

    private var actions: some View {
        VStack(spacing: 10) {
            if def.kind == .fixed && done.wrongCount > 0 {
                GGButton(variant: .surface, size: .lg, action: { engine.restart(retryWrong: true) }) {
                    Image(systemName: "arrow.counterclockwise")
                    Text("틀린 문제만 다시 (\(done.wrongCount))")
                }
            }
            GGButton(variant: .primary, size: .lg, action: { engine.restart(retryWrong: false) }) {
                Text("한 판 더")
            }
            GGButton(variant: .ghost, size: .md, action: onClose) {
                Image(systemName: "xmark"); Text("닫기")
            }
        }
    }
}
