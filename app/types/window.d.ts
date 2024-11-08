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
}

interface AndroidInterface {
  openLink: (url: string) => void;
  subscribeProduct: (productId: string) => void;
  loadAd: (adUnitId: string) => void;
}

declare global {
  interface Window {
    Android?: AndroidInterface;
    webkit?: {
      messageHandlers: WebKitInterface;
    };
  }
}

export {};