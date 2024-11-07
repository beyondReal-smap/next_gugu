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
  
  export interface AndroidInterface {
    openLink: (url: string) => void;
    subscribeProduct: (productId: string) => void;
    loadAd: (adUnitId: string) => void;
  }
  
  declare global {
    interface Window {
      webkit?: {
        messageHandlers: WebKitMessageHandlers;
      };
      Android?: AndroidInterface;
    }
  }