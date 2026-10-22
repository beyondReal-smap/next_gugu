import SwiftUI

// 계정 — 보호자 이메일로 로그인해 이용권과 학습 기록을 지킨다 (ProfileView 에서 설정 화면으로 이동).
// 처음 쓰는 주소는 지금 계정에 연결하고, 이미 있는 주소는 그 계정으로 로그인한다.
// 로그아웃·계정 삭제(App Store 5.1.1(v))도 여기서 제공한다.

struct AccountSettingsCard: View {
    @Environment(AuthStore.self) private var auth
    @Environment(SyncStore.self) private var sync
    @Environment(PremiumStore.self) private var premium
    @Environment(PushStore.self) private var push

    /// 로그아웃·계정 삭제까지 보일지 — 결제 직후 로그인 권유(페이월)에서는 로그인만 보여 준다
    var showsAccountManagement = true

    @State private var email = ""
    @State private var code = ""
    @State private var confirmSignOut = false
    @State private var signingOut = false
    @State private var confirmDeleteAccount = false
    @State private var deletingAccount = false
    @State private var deleteNotice: String?

    var body: some View {
        if auth.state != .disabled {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "person.badge.shield.checkmark")
                        .font(.system(size: 14, weight: .bold)).foregroundStyle(Color.gg.accent)
                    Text("기록 지키기").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                }

                switch auth.promotion {
                case let .codeSent(email, purpose, _), let .verifying(email, purpose):
                    codeEntry(email: email, purpose: purpose)
                default:
                    if case let .needsReauth(rememberedEmail) = auth.state {
                        reauthSection(rememberedEmail: rememberedEmail)
                    } else if auth.isPermanent {
                        signedInSection
                    } else {
                        emailEntry(
                            intro: String(localized: "보호자 이메일로 이용권과 기록을 지켜요. 앱을 다시 설치하거나 기기를 바꿔도 같은 이메일로 로그인하면 이어서 쓸 수 있어요. 비밀번호는 필요 없어요."),
                            hint: String(localized: "이미 쓰던 이메일이 있으면 그 주소를 넣어 주세요.")
                        )
                    }
                }

                // 귀속 후보가 여럿이면 어느 기록에 이어 붙일지 고르게 한다
                if case let .needsLearnerChoice(candidates) = sync.state, !candidates.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("이어서 쓸 기록을 골라 주세요")
                            .font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.text)
                        ForEach(candidates) { candidate in
                            Button {
                                Haptics.impact(.light)
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
                    warningText(message)
                }

                if sync.restoreFailed {
                    warningText(String(localized: "학습 기록을 불러오지 못했어요. 다음에 다시 시도할게요."))
                }

                if showsAccountManagement { deleteAccountSection }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .ggCard()
        }
    }

    // MARK: - 상태별 화면

    /// 이메일 입력 — 익명 계정(연결 또는 로그인)과 재로그인에서 함께 쓴다
    private func emailEntry(intro: String, hint: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(intro)
                .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            if let hint {
                Text(hint)
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            TextField("", text: $email, prompt: Text("보호자 이메일").foregroundStyle(Color.gg.textMuted))
                .textFieldStyle(.plain)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.suite(.medium, 15))
                .foregroundStyle(Color.gg.text)
                .tint(Color.gg.accent)
                .padding(.horizontal, 14).frame(height: 48)
                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            GGButton(variant: .primary, size: .md, action: {
                Task { await auth.requestEmailCode(email: email) }
            }) {
                Text(auth.promotion == .sending ? "보내는 중…" : "확인 코드 받기")
            }
        }
    }

    private func codeEntry(email: String, purpose: AuthStore.CodePurpose) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            switch purpose {
            case .link:
                Text("\(email) 로 6자리 확인 코드를 보냈어요.")
                    .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            case .signIn:
                Text("\(email) 은 이미 쓰던 계정이에요. 메일로 받은 6자리 코드를 넣으면 그 계정으로 로그인해요.")
                    .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                Text("이 기기의 별·레벨·코인은 그대로 두고, 계정의 이용권과 학습 기록을 불러와요.")
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                if sync.pendingCount > 0 {
                    warningText(String(localized: "다른 계정으로 로그인하면 아직 서버에 올리지 못한 학습 기록 \(sync.pendingCount)개는 서버에 남지 않아요."))
                }
            }
            TextField("", text: $code, prompt: Text("확인 코드").foregroundStyle(Color.gg.textMuted))
                .textFieldStyle(.plain)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .font(.suiteNum(.extrabold, 20))
                .foregroundStyle(Color.gg.text)
                .tint(Color.gg.accent)
                .padding(.horizontal, 14).frame(height: 48)
                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            if case let .codeSent(_, _, error?) = auth.promotion {
                warningText(error)
            }
            GGButton(variant: .primary, size: .md, action: {
                Task {
                    await auth.confirmEmailCode(code: code, onAccountSwitch: {
                        sync.resetForAccountChange()
                        premium.clearServerGrant()
                        // 광고 동의는 계정에 묶인다 — 새 계정으로 다시 등록하면서 끈다
                        push.resetForAccountSwitch()
                    })
                    if case .done = auth.promotion { code = "" }
                }
            }) {
                Text(auth.promotion == .verifying(email: email, purpose: purpose)
                     ? "확인 중…"
                     : (purpose == .signIn ? "로그인" : "연결 완료하기"))
            }
            HStack(spacing: 16) {
                Button("주소 다시 입력") { Haptics.impact(.light); auth.resetPromotion(); code = "" }
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                    .frame(minHeight: 44)
                resendButton
            }
        }
    }

    /// 코드 재발송 — 서버 제한(같은 주소 60초)에 맞춰 남은 시간을 보여 준다
    private var resendButton: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let elapsed = auth.lastCodeSentAt.map { context.date.timeIntervalSince($0) } ?? .infinity
            let remaining = Int((AuthStore.resendInterval - elapsed).rounded(.up))
            Button(remaining > 0 ? "코드 다시 보내기 (\(remaining)초)" : "코드 다시 보내기") {
                Haptics.impact(.light)
                Task { await auth.resendCode() }
            }
            .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
            .frame(minHeight: 44)
            .disabled(remaining > 0)
        }
    }

    private var signedInSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            accountRow(icon: "checkmark.seal.fill", tint: .gg.success,
                       title: auth.email.map { String(localized: "\($0) 로 로그인돼 있어요") } ?? String(localized: "계정이 연결돼 있어요"),
                       desc: premium.isPremium
                           ? String(localized: "다른 기기에서도 이 이메일로 로그인하면 이용권과 학습 기록을 이어서 쓸 수 있어요.")
                           : String(localized: "다른 기기에서도 이 이메일로 로그인할 수 있어요. 학습 기록 보관은 이용권 사용자에게 켜져요."))
            Text("별·레벨·코인은 이 기기에만 저장돼요.")
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            if showsAccountManagement { signOutControl(label: String(localized: "로그아웃")) }
        }
    }

    private func reauthSection(rememberedEmail: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            accountRow(icon: "exclamationmark.circle.fill", tint: .gg.warning,
                       title: String(localized: "다시 로그인해 주세요"),
                       desc: String(localized: "로그인이 만료됐어요. 그동안 공부한 기록은 기기에 쌓아 두었다가 다시 로그인하면 올려요."))
            emailEntry(intro: String(localized: "보호자 이메일로 받은 6자리 코드를 넣으면 다시 로그인돼요."), hint: nil)
                .onAppear {
                    if email.isEmpty, let rememberedEmail { email = rememberedEmail }
                }
            if showsAccountManagement { signOutControl(label: String(localized: "이 기기에서 로그아웃")) }
        }
    }

    /// 로그아웃 — 되돌리기 쉬운 동작이지만 서버 사본이 사라질 수 있어 한 번 확인한다
    @ViewBuilder
    private func signOutControl(label: String) -> some View {
        if confirmSignOut {
            VStack(alignment: .leading, spacing: 8) {
                Text("로그아웃할까요?")
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.text)
                Text("이 기기의 별·레벨·코인과 이 기기에서 산 이용권은 그대로예요. 계정으로 받은 이용권과 학습 기록 보관은 다시 로그인하면 돌아와요.")
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                if sync.pendingCount > 0 {
                    warningText(String(localized: "아직 서버에 올리지 못한 학습 기록 \(sync.pendingCount)개는 로그아웃하면 서버에 남지 않아요."))
                }
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md, action: { confirmSignOut = false }) { Text("취소") }
                    GGButton(variant: .danger, size: .md, action: signOut) {
                        Text(signingOut ? "로그아웃 중…" : "로그아웃")
                    }
                }
            }
        } else {
            Button {
                Haptics.impact(.light)
                confirmSignOut = true
                // 확인하는 동안 남은 기록을 올려 본다 — 다 올라가면 경고가 사라진다
                Task { await sync.flushNow(auth: auth) }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 14, weight: .bold))
                    Text(label).font(.suite(.bold, 13))
                    Spacer(minLength: 0)
                }
                .foregroundStyle(Color.gg.textMuted)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func signOut() {
        guard !signingOut else { return }
        signingOut = true
        Task {
            // 세션을 지우기 전에 지금 계정 토큰으로 푸시 기기 등록을 해제한다 (최대 5초, 실패해도 로그아웃은 계속)
            await push.unregisterBeforeSignOut(accessToken: { await auth.accessToken() })
            await auth.signOut(onLocalSessionCleared: {
                sync.resetForAccountChange()
                premium.clearServerGrant()
                push.resetForAccountSwitch()
            })
            signingOut = false
            confirmSignOut = false
            email = ""
            code = ""
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

        if auth.needsReauth {
            // 삭제 요청엔 유효한 로그인이 필요하다
            Text("계정 삭제는 다시 로그인한 뒤에 할 수 있어요.")
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        } else if confirmDeleteAccount {
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
            // 기기에 남은 신원·아웃박스·서버 부여 이용권은 서버 삭제 직후(익명 재로그인을 기다리기 전)에 정리한다
            let ok = await auth.deleteAccount(onLocalSessionCleared: {
                sync.resetForAccountChange()
                premium.clearServerGrant()
                // 서버 purge 가 기기 행·동의 이력을 지웠다 — 광고 동의 초기화, 공지 토글은 유지
                push.resetForAccountDeletion()
            })
            deletingAccount = false
            confirmDeleteAccount = false
            if ok { email = "" }
            deleteNotice = ok
                ? String(localized: "계정과 서버에 보관된 학습 기록을 지웠어요.")
                : String(localized: "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }

    private func warningText(_ message: String) -> some View {
        Text(message)
            .font(.suite(.bold, 12)).foregroundStyle(Color.gg.warning)
            .fixedSize(horizontal: false, vertical: true)
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
}
