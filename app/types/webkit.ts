// types/webkit.ts
export interface WebKitMessageHandlers {
    openLink: {
      postMessage: (url: string) => void;
    };
    subscribeProduct: {
      postMessage: (productId: string) => void;
    };
    loadAd: {
      postMessage: (adUnitId: string) => void;
    };
  }
 