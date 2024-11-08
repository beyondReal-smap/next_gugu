// WebKit 메시지 핸들러 타입
export interface StoreKitMessageHandler {
  postMessage: (message: string) => void;
}

export interface WebKitMessageHandlers {
  storeKit: StoreKitMessageHandler;
}

// Android 인터페이스 타입
export interface AndroidInterface {
  openLink: (url: string) => void;
  subscribeProduct: (productId: string) => void;
  loadAd: (adUnitId: string) => void;
}

// Window 인터페이스 확장
declare global {
  interface Window {
    webkit?: {
      messageHandlers: {
        storeKit: {
          postMessage: (message: string) => void;
        };
      };
    };
    Android?: AndroidInterface;
  }
}

export {};