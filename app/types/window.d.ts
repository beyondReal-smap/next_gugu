// types/window.d.ts
interface PostMessageHandler {
  postMessage: (message: any) => void;
}

interface WebKitInterface {
  openLink: PostMessageHandler;
  subscribeProduct: PostMessageHandler;
  loadAd: PostMessageHandler;
  setPurchaseStatus?: PostMessageHandler;
  getPurchaseStatus?: PostMessageHandler;
  showInterstitialAd: PostMessageHandler;  // 추가
  hapticFeedbackHandler: PostMessageHandler;  // 추가
  consoleLog: PostMessageHandler;  // 추가
  storeKit: {
    postMessage: (message: string) => void;
  };
}

interface AndroidInterface {
  openLink: (url: string) => void;
  subscribeProduct: (productId: string) => void;
  loadAd: (adUnitId: string) => void;
  purchasePremium: () => void;
  getPremiumStatus: () => boolean;
  savePurchaseStatus: (status: boolean) => void;
  showInterstitialAd: () => void;  // 추가
}

declare global {
  interface Window {
    Android?: AndroidInterface;
    webkit?: {
      messageHandlers: WebKitInterface;
    };
    triggerHapticFeedback?: (type: string) => void;
  }
}

export {};