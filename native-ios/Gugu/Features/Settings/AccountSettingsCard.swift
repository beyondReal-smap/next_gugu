import SwiftUI

// 계정 — 익명 계정에 이메일을 붙여 기기를 바꿔도 기록이 남게 한다 (ProfileView 에서 설정 화면으로 이동).
// 계정 삭제(App Store 5.1.1(v))도 여기서 제공한다.

struct AccountSettingsCard: View {
    @Environment(AuthStore.self) private var auth
    @Environment(SyncStore.self) private var sync

    @State private var email = ""
    @State private var code = ""
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
                                .textContentType(.emailAddress)
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
}
