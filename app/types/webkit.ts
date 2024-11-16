// WebKit 메시지 핸들러 타입
export interface StoreKitMessageHandler {
  postMessage: (message: string) => void;
}

interface MessageHandler {
  postMessage: (message: string) => void;
}

export interface WebKitMessageHandlers {
  storeKit: StoreKitMessageHandler;
  hapticFeedbackHandler: MessageHandler;
  consoleLog: MessageHandler;
  showInterstitialAd: MessageHandler;
  handlePremiumPurchase: MessageHandler;
}

// Android 인터페이스 타입
export interface AndroidInterface {
  openLink: (url: string) => void;
  subscribeProduct: (productId: string) => void;
  loadAd: (adUnitId: string) => void;
  purchasePremium: () => boolean;
  getPremiumStatus: () => boolean;
  savePurchaseStatus: (status: boolean) => void;
  openLinkInNewWindow: (url: string) => void;
}

// Premium Purchase 콜백 타입
export interface PremiumPurchaseCallbacks {
  setPremiumStatus?: (isPremium: boolean, purchaseDate: string | null) => void;
  onPremiumPurchaseSuccess?: () => void;
  onPremiumPurchaseFailure?: (error: string) => void;
  closePaymentModal?: () => void;
}

// Window 인터페이스 확장
declare global {
  interface Window extends PremiumPurchaseCallbacks {
    webkit?: {
      messageHandlers: WebKitMessageHandlers;
    };
    Android?: AndroidInterface;
  }
}

// Native Handlers 인터페이스
export interface NativeHandlers {
  handleLink: (url: string) => void;
  openLinkInNewWindow: (url: string) => void;
  handleSubscription: (productId: string) => void;
  loadAd: (adUnitId: string) => void;
}

// Native Handlers Hook
export const useNativeHandlers = (): NativeHandlers => {
  const handleLink = (url: string) => {
    // iOS WKWebView
    if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
        type: 'openSafariView', // SFSafariViewController로 열기
        data: {
          url: url
        }
      }));
    } 
    // Android
    else if (window.Android?.openLink) {
      window.Android.openLink(url);
    } 
    // 일반 웹브라우저
    else {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  const openLinkInNewWindow = (url: string) => {
    // iOS WKWebView
    if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
        type: 'openSafariView',
        data: {
          url: url
        }
      }));
    } 
    // Android
    else if (window.Android?.openLinkInNewWindow) {
      window.Android.openLinkInNewWindow(url);
    } 
    // 일반 웹브라우저
    else {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  const handleSubscription = (productId: string) => {
    if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
        type: 'subscription',
        productId
      }));
    } else if (window.Android?.subscribeProduct) {
      window.Android.subscribeProduct(productId);
    }
  };

  const loadAd = (adUnitId: string) => {
    if (window.webkit?.messageHandlers?.storeKit) {
      window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
        type: 'loadAd',
        adUnitId
      }));
    } else if (window.Android?.loadAd) {
      window.Android.loadAd(adUnitId);
    }
  };

  return { handleLink, openLinkInNewWindow, handleSubscription, loadAd };
};

export {}; 