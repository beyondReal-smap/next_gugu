import SwiftUI

// 페이월 (Paywall.tsx 이식) — StoreKit 2 단일 비소모성 상품

struct PaywallView: View {
    @Environment(PremiumStore.self) private var premium
    @Environment(AuthStore.self) private var auth
    @Environment(SyncStore.self) private var sync
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var busy = false
    @State private var notice: String?
    /// 결제·복원은 됐지만 아직 이메일 로그인 전 — 닫기 전에 로그인을 권한다
    @State private var showLinkPrompt = false

    private struct Benefit: Identifiable {
        let id = UUID(); let icon: String; let title: String; let desc: String
    }
    private let benefits: [Benefit] = [
        Benefit(icon: "map.fill", title: String(localized: "3D 어드벤처 전 지역"), desc: String(localized: "8개 지역 탐험 · 주민/보스 구구단 대결")),
        Benefit(icon: "bolt.fill", title: String(localized: "게임 모드 전부 해제"), desc: String(localized: "60초 챌린지 · 서바이벌 · 빈칸 추리 · OX 퀴즈")),
        Benefit(icon: "figure.run", title: String(localized: "움직이며 놀기 무제한"), desc: String(localized: "구구 팡팡 · 바구니 · 점프 · 레인 (무료는 팡팡 첫 웨이브, 나머지 \(PremiumConfig.minigameTrialQuestions)문제 체험)")),
        Benefit(icon: "infinity", title: String(localized: "한 번 결제, 평생 소장"), desc: String(localized: "추가 결제 없음 · 업데이트 콘텐츠도 전부 포함")),
    ]

    var body: some View {
        if showLinkPrompt {
            linkPrompt
        } else {
            offer
        }
    }

    /// 결제 직후 로그인 권유.
    /// 스토어 구매는 Apple 계정에 묶이고 앱 계정은 그 이메일을 알 수 없다 — 익명인 채로 두면
    /// 구매가 서버 계정에 등록되지 않아 기기를 바꿀 때 「구매 복원」 말고는 되살릴 길이 없다.
    /// 강제하지 않는다(App Store 5.1.1) — 언제든 "나중에 할게요"로 닫을 수 있다.
    private var linkPrompt: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 40, weight: .bold)).foregroundStyle(Color.gg.success)
                    Text("평생 이용권이 열렸어요 🎉")
                        .font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text)
                        .multilineTextAlignment(.center)
                    Text("보호자 이메일로 로그인해 두면 앱을 다시 설치하거나 기기를 바꿔도 이용권이 따라와요.")
                        .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("로그인하지 않아도 같은 Apple 계정에서는 「구매 복원」으로 되살릴 수 있어요.")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 24)

                AccountSettingsCard(showsAccountManagement: false)

                if auth.isPermanent {
                    GGButton(variant: .primary, size: .lg, action: { dismiss() }) { Text("시작하기") }
                } else {
                    GGButton(variant: .surface, size: .md, action: { dismiss() }) { Text("나중에 할게요") }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Color.gg.bg.ignoresSafeArea())
    }

    private var offer: some View {
        ScrollView {
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button {
                        Haptics.impact(.light)
                        dismiss()
                    } label: {
                        Image(systemName: "xmark").font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("닫기")
                }
                .padding(.bottom, 8)

                hero
                benefitList
                priceCard
                purchaseButton

                if !premium.storeReady {
                    Text("스토어 연결 준비 중 — 가격은 결제 화면에서 최종 확인돼요.")
                        .font(.suite(.bold, 11)).foregroundStyle(Color.gg.warning)
                        .multilineTextAlignment(.center).padding(.top, 8)
                }
                if let notice {
                    Text(notice).font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                        .multilineTextAlignment(.center).padding(.top, 8)
                }

                footerLinks
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        // 오프라인으로 시작했거나 조회가 실패했어도, 페이월을 열 때 다시 스토어에 묻는다 (이미 받았으면 건너뜀)
        .task { await premium.loadProducts() }
        .onChange(of: premium.storeReady) { _, ready in
            // 재조회가 성공하면 "준비 중" 안내는 더 이상 맞지 않는다
            if ready && notice == Self.storeNotReadyNotice { notice = nil }
        }
        .onChange(of: premium.isPremium) { _, isPrem in
            guard isPrem else { return }
            // 이미 이메일 계정이거나 인증이 꺼진 빌드면 권할 것이 없다 — 바로 닫는다
            if auth.isPermanent || auth.state == .disabled {
                dismiss()
            } else {
                showLinkPrompt = true
            }
        }
    }

    private var hero: some View {
        VStack(spacing: 12) {
            Image(systemName: "cup.and.saucer.fill")
                .font(.system(size: 32, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(
                    LinearGradient(colors: [Color(hex: "#fbbf24"), Color(hex: "#f97316")],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .shadow(color: Color(hex: "#f97316").opacity(0.3), radius: 12, y: 6)
            Text("커피 한 잔 값으로,\n아이의 구구단 실력을")
                .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
                .multilineTextAlignment(.center)
            Group {
                if let price = premium.price {
                    Text("딱 한 번 \(price) — 모든 기능을 평생 이용해요")
                } else {
                    Text("딱 한 번 결제로 모든 기능을 평생 이용해요")
                }
            }
                .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                .multilineTextAlignment(.center)
        }
        .padding(.bottom, 20)
    }

    private var benefitList: some View {
        VStack(spacing: 10) {
            ForEach(benefits) { b in
                HStack(spacing: 12) {
                    Image(systemName: b.icon).font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.accent)
                        .frame(width: 40, height: 40)
                        .background(Color.gg.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(b.title).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                        Text(b.desc).font(.suite(.regular, 12)).foregroundStyle(Color.gg.textMuted)
                    }
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .ggCard(padding: 0)
            }
        }
        .padding(.bottom, 20)
    }

    private var priceCard: some View {
        VStack(spacing: 4) {
            HStack(alignment: .lastTextBaseline, spacing: 6) {
                if let price = premium.price {
                    Text(price).font(.suite(.extrabold, 36)).foregroundStyle(Color.gg.text).monospacedDigit()
                } else {
                    // 스토어 가격을 받기 전 — 금액 자리를 같은 높이로 비워 둔다
                    ProgressView().frame(height: 44)
                }
                Text("· 딱 한 번").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            }
            Text("정기 결제 아니에요 — 한 번 사면 끝!").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Color.gg.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color.gg.accent, lineWidth: 2))
        .overlay(alignment: .top) {
            Text("☕ 커피 한 잔 값").font(.suite(.extrabold, 11)).foregroundStyle(Color(hex: "#78350f"))
                .padding(.horizontal, 12).padding(.vertical, 5)
                .background(Color(hex: "#fbbf24"), in: Capsule())
                .offset(y: -12)
        }
        .padding(.top, 12)
        .padding(.bottom, 16)
    }

    private var purchaseButton: some View {
        GGButton(variant: .primary, size: .lg, action: onPurchase) {
            Image(systemName: "sparkles")
            Text(busy ? "처리 중…" : "평생 이용권 시작하기")
        }
        .disabled(busy)
    }

    private var footerLinks: some View {
        HStack(spacing: 16) {
            Button("구매 복원", action: { Haptics.impact(.light); onRestore() }).disabled(busy)
            Button("이용약관") { Haptics.impact(.light); open(PremiumConfig.Legal.terms) }
            Button("개인정보처리방침") { Haptics.impact(.light); open(PremiumConfig.Legal.privacy) }
        }
        .font(.suite(.bold, 11))
        .foregroundStyle(Color.gg.textMuted)
        .underline()
        .padding(.top, 16)
    }

    private static let storeNotReadyNotice = String(localized: "스토어 연결 준비 중이에요. 잠시 후 다시 시도해주세요.")

    private func onPurchase() {
        guard premium.storeReady else {
            notice = Self.storeNotReadyNotice
            Task { await premium.loadProducts() }   // 안내한 대로 실제로 다시 시도한다
            return
        }
        busy = true; notice = nil
        Task {
            let result = await premium.purchase()
            busy = false
            switch result {
            case .ok: break // isPremium 변경 → onChange가 닫음
            case .cancelled: break
            case .pending: notice = String(localized: "구매가 대기 중이에요 (승인 필요).")
            case .error: notice = String(localized: "구매에 실패했어요. 잠시 후 다시 시도해주세요.")
            }
        }
    }

    private func onRestore() {
        busy = true; notice = nil
        Task {
            let ok = await premium.restore()
            busy = false
            notice = ok ? String(localized: "구매가 복원되었어요! 🎉") : String(localized: "복원할 구매 내역이 없어요.")
            // 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
            // 서버 구매 등록과 보호자 권한을 시도한다.
            if ok { await sync.enableGuardianSync(auth: auth) }
        }
    }

    private func open(_ url: String) {
        if let u = URL(string: url) { openURL(u) }
    }
}
