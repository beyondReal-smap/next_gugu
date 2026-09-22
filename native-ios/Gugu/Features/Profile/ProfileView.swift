import SwiftUI

// 프로필 탭 (Profile.tsx 이식) — 레벨/통계/업적/이용권/설정

struct ProfileView: View {
    @Environment(GameStore.self) private var game
    @Environment(ThemeStore.self) private var theme
    @Environment(PremiumStore.self) private var premium
    @Environment(AuthStore.self) private var auth
    @Environment(SyncStore.self) private var sync
    @Environment(\.openURL) private var openURL

    @State private var email = ""
    @State private var code = ""

    @State private var soundOn = Sound.shared.enabled
    @State private var confirmReset = false
    @State private var restoring = false
    @State private var restoreNotice: String?
    @State private var confirmDeleteAccount = false
    @State private var deletingAccount = false
    @State private var deleteNotice: String?

    private let scoredModes: [GameMode] = [.challenge, .survival]

    private var accuracyTotal: Int { game.state.totalCorrect + game.state.totalWrong }
    private var accuracy: Int { accuracyTotal > 0 ? Int((Double(game.state.totalCorrect) / Double(accuracyTotal) * 100).rounded()) : 0 }
    private var unlocked: Set<String> { Set(game.state.unlockedAchievements) }
    private var weakProblems: [String] {
        game.state.wrongPool.sorted { $0.value > $1.value }.prefix(6).map { $0.key }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                levelHeader
                statsRow
                bestRecords
                if game.state.recentAccuracy.count >= 2 { accuracyTrend }
                if !weakProblems.isEmpty { weakSection }
                achievements
                accountSection
                premiumSection
                settings
                resetSection
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
    }

    private var header: some View {
        Text("프로필").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
    }

    private var levelHeader: some View {
        HStack(spacing: 16) {
            Text("\(game.levelInfo.level)")
                .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.accentFg)
                .frame(width: 64, height: 64)
                .background(Color.gg.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .monospacedDigit()
            VStack(alignment: .leading, spacing: 6) {
                Text("Lv.\(game.levelInfo.level) · \(Level.title(game.levelInfo.level))")
                    .font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(Color.gg.accent).frame(width: geo.size.width * game.levelInfo.progress)
                    }
                }
                .frame(height: 8)
                Text("\(game.levelInfo.totalXp) XP").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
            }
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var statsRow: some View {
        HStack(spacing: 12) {
            miniStat("정답", "\(game.state.totalCorrect)")
            miniStat("정확도", "\(accuracy)%")
            miniStat("최고 콤보", "\(game.state.maxCombo)")
        }
    }

    private func miniStat(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text).monospacedDigit()
            Text(label).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .ggCard(padding: 0)
    }

    private var bestRecords: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("최고 기록").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            HStack(spacing: 12) {
                ForEach(scoredModes, id: \.self) { id in
                    let best = game.state.bestScores[id] ?? 0
                    HStack(spacing: 12) {
                        Image(systemName: ModeStyle.icon(id)).font(.system(size: 18, weight: .bold)).foregroundStyle(ModeStyle.tint(id))
                            .frame(width: 40, height: 40)
                            .background(ModeStyle.tint(id).opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(Modes.def(id).name).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                            Text(best > 0 ? "\(best)점" : "—").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text).monospacedDigit()
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .ggCard(padding: 0)
                }
            }
        }
    }

    private var accuracyTrend: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("최근 정확도 추이").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                    Text("최근 \(game.state.recentAccuracy.count)판 · 점선은 50%")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer(minLength: 8)
                // 그래프만으로는 현재 수준을 읽을 수 없어 최신값을 숫자로 집어 준다
                Text("\(game.state.recentAccuracy.last ?? 0)%")
                    .font(.suiteNum(.extrabold, 24)).foregroundStyle(Color.gg.accent)
            }
            HStack(spacing: 10) {
                // y축이 0~100 고정임을 눈으로 확인할 수 있게 눈금을 적는다
                ZStack {
                    Text("100").frame(maxHeight: .infinity, alignment: .top)
                    Text("50")
                    Text("0").frame(maxHeight: .infinity, alignment: .bottom)
                }
                .font(.suiteNum(.bold, 9)).foregroundStyle(Color.gg.textMuted)
                .frame(width: 24, height: 64)

                Sparkline(data: game.state.recentAccuracy, color: .gg.accent,
                          lowerBound: 0, upperBound: 100, baseline: 50, markLast: true)
                    .frame(height: 64)
            }
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var weakSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("집중 공략 문제").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            FlowRow(spacing: 8) {
                ForEach(weakProblems, id: \.self) { key in
                    Text(key.replacingOccurrences(of: "x", with: " × "))
                        .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.danger).monospacedDigit()
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Color.gg.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color.gg.danger.opacity(0.2), lineWidth: 1))
                }
            }
        }
    }

    private var achievements: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("업적 (\(unlocked.count)/\(Achievements.all.count))").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            // 3열 — 4열에서는 "오백 문제 돌파" 같은 이름이 한 줄로 잘렸다.
            // 정사각 비율을 버리고 라벨 2줄을 항상 확보해 타일 높이를 맞춘다.
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(Achievements.all) { a in
                    let on = unlocked.contains(a.id)
                    VStack(spacing: 8) {
                        Image(systemName: IconMap.sf(a.icon)).font(.system(size: 22, weight: .semibold))
                        Text(a.name)
                            .font(.suite(.bold, 11))
                            .multilineTextAlignment(.center)
                            .lineLimit(2, reservesSpace: true)
                            .minimumScaleFactor(0.85)
                    }
                    .foregroundStyle(on ? Color.gg.accent : Color.gg.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .padding(.horizontal, 8)
                    .background(on ? Color.gg.accent.opacity(0.1) : Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? Color.gg.accent.opacity(0.3) : Color.gg.border, lineWidth: 1))
                    .opacity(on ? 1 : 0.55)
                }
            }
        }
    }

    private var premiumSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("이용권").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            VStack(spacing: 0) {
                if premium.isPremium {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles").foregroundStyle(Color.gg.accent)
                        Text("평생 이용권").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                        Spacer()
                        Text("이용 중").font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    }
                    .padding(.horizontal, 20).padding(.vertical, 16)
                } else {
                    row(icon: "sparkles", label: "평생 이용권 · 커피 한 잔 값", action: premium.price) { premium.openPaywall() }
                }
                divider
                row(icon: "arrow.clockwise", label: "구매 복원",
                    action: restoring ? "확인 중…" : "", onTap: restorePurchase)
                divider
                row(icon: "doc.text", label: "이용약관", action: "") { open(PremiumConfig.Legal.terms) }
                divider
                row(icon: "checkmark.shield", label: "개인정보처리방침", action: "") { open(PremiumConfig.Legal.privacy) }
            }
            .ggCard(padding: 0)

            if let restoreNotice {
                Text(restoreNotice)
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
    /// 서버 구매 등록과 보호자 권한을 함께 시도한다 (PaywallView.onRestore 와 동일).
    private func restorePurchase() {
        guard !restoring else { return }
        restoring = true
        restoreNotice = nil
        Task {
            let ok = await premium.restore()
            restoring = false
            restoreNotice = ok ? "구매를 확인했어요. 기록 보관을 켜는 중이에요." : "복원할 구매 내역이 없어요."
            if ok { await sync.enableGuardianSync(auth: auth) }
        }
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("설정").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            VStack(spacing: 0) {
                row(icon: theme.theme == .dark ? "moon.fill" : "sun.max.fill", label: "다크 모드",
                    action: theme.theme == .dark ? "켜짐" : "꺼짐") { theme.toggle() }
                divider
                row(icon: soundOn ? "speaker.wave.2.fill" : "speaker.slash.fill", label: "효과음",
                    action: soundOn ? "켜짐" : "꺼짐") {
                    soundOn.toggle(); Sound.shared.setEnabled(soundOn)
                }
            }
            .ggCard(padding: 0)
        }
    }

    private var resetSection: some View {
        Group {
            if !confirmReset {
                GGButton(variant: .ghost, size: .md, action: { confirmReset = true }) {
                    Image(systemName: "arrow.counterclockwise")
                    Text("기록 초기화").foregroundStyle(Color.gg.danger)
                }
            } else {
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md, action: { confirmReset = false }) { Text("취소") }
                    GGButton(variant: .danger, size: .md, action: { game.resetProgress(); confirmReset = false }) { Text("초기화 확인") }
                }
            }
        }
        .padding(.top, 4)
    }

    // MARK: 계정 — 익명 계정에 이메일을 붙여 기기를 바꿔도 기록이 남게 한다
    @ViewBuilder
    private var accountSection: some View {
        if auth.state != .disabled {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "person.badge.shield.checkmark")
                        .font(.system(size: 14, weight: .bold)).foregroundStyle(Color.gg.accent)
                    Text("기록 지키기").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                }

                switch auth.promotion {
                case let .done(email):
                    accountRow(icon: "checkmark.seal.fill", tint: .gg.success,
                               title: "\(email) 에 연결됐어요",
                               desc: "기기를 바꿔도 이 주소로 기록을 찾을 수 있어요.")
                case let .codeSent(email), let .verifying(email):
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(email) 로 6자리 확인 코드를 보냈어요.")
                            .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                        TextField("확인 코드", text: $code)
                            .textFieldStyle(.plain)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .font(.suiteNum(.extrabold, 20))
                            .padding(.horizontal, 14).frame(height: 48)
                            .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        GGButton(variant: .primary, size: .md, action: {
                            Task { await auth.confirmEmailPromotion(code: code) }
                        }) {
                            Text(auth.promotion == .verifying(email: email) ? "확인 중…" : "연결 완료하기")
                        }
                        Button("주소 다시 입력") { auth.resetPromotion(); code = "" }
                            .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                            .frame(minHeight: 44)
                    }
                default:
                    if auth.isPermanent {
                        accountRow(icon: "checkmark.seal.fill", tint: .gg.success,
                                   title: "계정이 연결돼 있어요",
                                   desc: "기기를 바꿔도 기록을 찾을 수 있어요.")
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("보호자 이메일을 넣으면 기기를 바꿔도 기록이 남아요. 비밀번호는 필요 없어요.")
                                .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                                .fixedSize(horizontal: false, vertical: true)
                            TextField("보호자 이메일", text: $email)
                                .textFieldStyle(.plain)
                                .keyboardType(.emailAddress)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .font(.suite(.medium, 15))
                                .padding(.horizontal, 14).frame(height: 48)
                                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            GGButton(variant: .primary, size: .md, action: {
                                Task { await auth.startEmailPromotion(email: email) }
                            }) {
                                Text(auth.promotion == .sending ? "보내는 중…" : "확인 코드 받기")
                            }
                        }
                    }
                }

                // 귀속 후보가 여럿이면 어느 기록에 이어 붙일지 고르게 한다
                if case let .needsLearnerChoice(candidates) = sync.state, !candidates.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("이어서 쓸 기록을 골라 주세요")
                            .font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.text)
                        ForEach(candidates) { candidate in
                            Button {
                                Task { await sync.chooseLearner(candidate, auth: auth) }
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: "clock.arrow.circlepath")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(Color.gg.accent)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(candidate.displayName)
                                            .font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.text)
                                        Text("마지막 학습 \(candidate.updatedAt.prefix(10))")
                                            .font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(Color.gg.textMuted)
                                }
                                .padding(.horizontal, 12).frame(minHeight: 48)
                                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }
                        }
                    }
                }

                // 보호자 검증 전이면 왜 보관이 꺼져 있는지 알려 준다
                if case let .waitingForGuardian(message) = sync.state {
                    Text(message)
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if case let .failed(message) = auth.promotion {
                    Text(message)
                        .font(.suite(.bold, 12)).foregroundStyle(Color.gg.warning)
                        .fixedSize(horizontal: false, vertical: true)
                }

                deleteAccountSection
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .ggCard()
        }
    }

    /// 계정 삭제 (App Store 5.1.1(v)) — 계정 생성을 지원하는 앱은 앱 안에서 삭제도 제공해야 한다.
    /// 되돌릴 수 없으므로 「기록 초기화」와 같은 2단 확인을 둔다.
    @ViewBuilder
    private var deleteAccountSection: some View {
        Divider().overlay(Color.gg.border)

        if let deleteNotice {
            Text(deleteNotice)
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }

        if confirmDeleteAccount {
            VStack(alignment: .leading, spacing: 8) {
                Text("계정과 서버에 보관된 학습 기록을 지웁니다. 되돌릴 수 없어요.")
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.text)
                    .fixedSize(horizontal: false, vertical: true)
                Text("기기에 있는 기록과 이용권은 그대로예요. 이용권은 「구매 복원」으로 다시 쓸 수 있어요.")
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md,
                             action: { confirmDeleteAccount = false }) { Text("취소") }
                    GGButton(variant: .danger, size: .md, action: deleteAccount) {
                        Text(deletingAccount ? "삭제 중…" : "삭제 확인")
                    }
                }
            }
        } else {
            Button {
                Haptics.impact(.light)
                deleteNotice = nil
                confirmDeleteAccount = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "person.crop.circle.badge.xmark")
                        .font(.system(size: 14, weight: .bold))
                    Text("계정 삭제").font(.suite(.bold, 13))
                    Spacer(minLength: 0)
                }
                .foregroundStyle(Color.gg.danger)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func deleteAccount() {
        guard !deletingAccount else { return }
        deletingAccount = true
        deleteNotice = nil
        Task {
            let ok = await auth.deleteAccount()
            if ok { sync.resetAfterAccountDeletion() }
            deletingAccount = false
            confirmDeleteAccount = false
            deleteNotice = ok
                ? "계정과 서버에 보관된 학습 기록을 지웠어요."
                : "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }

    private func accountRow(icon: String, tint: Color, title: String, desc: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).font(.system(size: 16, weight: .bold)).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                Text(desc).font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var divider: some View { Rectangle().fill(Color.gg.border).frame(height: 1) }

    private func row(icon: String, label: String, action: String, onTap: @escaping () -> Void) -> some View {
        Button { Haptics.impact(.light); onTap() } label: {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(Color.gg.textMuted).frame(width: 24)
                Text(label).font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                Text(action).font(.suite(.bold, 14)).foregroundStyle(Color.gg.accent)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func open(_ url: String) {
        if let u = URL(string: url) { openURL(u) }
    }
}

// 간단한 플로우 레이아웃 (칩 줄바꿈)
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX; y += rowHeight + spacing; rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
