// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처
import { GameMode } from './types';

/** 스토어 상품 ID — 비소모성(iOS)/1회성 인앱 상품(Android)으로 동일하게 등록 */
export const PRODUCT_ID = 'site.smap.next.premium';

/** 자체 영수증 검증 서버 (aws ~/smap/smap_gugu/api, cordova-plugin-purchase validator 프로토콜) */
export const VALIDATOR_URL = 'https://gugu.aiagnt.kr/api/iap/cdv-validate';

/** 오퍼링 로드 전 표시용 가격 (스토어 연결되면 실제 가격으로 대체) */
export const FALLBACK_PRICE = '₩4,400';

/** 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지 */
export const PREMIUM_MODES: GameMode[] = ['challenge', 'survival', 'missing', 'truefalse'];

export const isPremiumMode = (mode: GameMode) => PREMIUM_MODES.includes(mode);

/** 스토어 링크 — 출시 후 채우면 웹 데모 CTA가 활성화됨 */
export const STORE_URLS = {
  appStore: '',   // 예: https://apps.apple.com/kr/app/idXXXXXXXXX
  playStore: '',  // 예: https://play.google.com/store/apps/details?id=site.smap.gugudan
};

/** 법적 고지 링크 (페이월·프로필에서 사용, Apple 심사 필수) */
export const LEGAL_URLS = {
  terms: 'https://gugu.aiagnt.kr/terms',
  privacy: 'https://gugu.aiagnt.kr/privacy',
  deleteAccount: 'https://gugu.aiagnt.kr/delete-account',
};
