import Foundation
import Security

// 키체인 최소 래퍼 — 인증 토큰은 자격 증명이므로 UserDefaults 가 아닌 키체인에 둔다.
// 기기 잠금 해제 후에만 읽히고(ThisDeviceOnly), 기기 백업으로 새어나가지 않는다.

enum Keychain {
    static func save(_ data: Data, key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert.merge(attributes) { current, _ in current }
            let addStatus = SecItemAdd(insert as CFDictionary, nil)
            if addStatus != errSecSuccess {
                print("[Keychain] 저장 실패(\(key)): \(addStatus)")
            }
        } else if status != errSecSuccess {
            print("[Keychain] 갱신 실패(\(key)): \(status)")
        }
    }

    static func load(key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else {
            if status != errSecItemNotFound { print("[Keychain] 로드 실패(\(key)): \(status)") }
            return nil
        }
        return item as? Data
    }

    static func remove(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
