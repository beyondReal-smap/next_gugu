# App Store 등록정보 2.6.0 (다국어 출시)

`listing.json` — en-US · en-GB · ja 등록정보 초안(이름·부제·키워드·홍보 문구·설명·새로운 기능·인앱 구매 표시명).
한국어(ko)는 기존 ASC 내용을 그대로 쓰고, 2.6.0 에서 ASC 기본 언어를 ko → en-US 로 바꾼다(계획 §7).

- 용어는 앱 번역(Localizable.xcstrings)·`tools/i18n/glossary.csv` 와 같게 썼다 (모드명·Lifetime Pass/買い切りパス 등).
- 가격은 적지 않는다 — 원화 기준 자동 환산이라 나라마다 다르다. (한국어 설명의 ₩4,400 표기는 ko 전용)
- 해외에서는 광고성 알림이 없으므로 "광고 없음"만 쓰고 알림 혜택 문구는 넣지 않는다.
- 원어민 검수 대상. ASC 반영은 대표님 확인 후.

## Google Play (Android 2.6.0)

- 문구는 `native-android/app/src/main/play/listings/{en-US,en-GB,ja-JP}/` (gradle-play-publisher 디렉터리 규약:
  title ≤30자, short-description ≤80자, full-description ≤4000자). 제목·자세한 설명은 `listing.json` 의 name·description 을
  그대로 옮겼고, 간단한 설명만 Play 용으로 새로 썼다.
- 출시 노트 `native-android/app/src/main/play/release-notes/<언어>/production.txt`, 피처 그래픽 `creative-2.6.0/feature-graphic-1024x500-*.png`,
  스크린샷 `screenshots-2.6.0/<언어>/play-{phone,tablet-7,tablet-10}/`.
