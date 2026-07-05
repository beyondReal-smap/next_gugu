import type { WebKitMessageHandlers, AndroidInterface } from './webkit';

// Premium 상태 인터페이스
export interface PremiumState {
  isPremium: boolean;
  showModal: boolean;
  isProcessing: boolean;
  purchaseDate?: string;
  transactionId?: string;
}

// Premium Context 인터페이스
export interface PremiumContext {
  state: PremiumState;
  setState: (state: PremiumState | ((prev: PremiumState) => PremiumState)) => void;
}

// Premium 핸들러 인터페이스
export interface PremiumHandlers {
  setPremiumStatus: (
    isPremium: boolean, 
    purchaseDate: string | undefined, 
    transactionId: string | undefined
  ) => void;
  restorePurchases?: () => Promise<void>; // 추가
  getState: () => PremiumState;
}

// Context 타입
export interface PremiumContextType extends PremiumState {
  handlePurchase: () => Promise<void>;
  handleRestore: () => Promise<void>; // 반환 타입을 Promise로 변경
  handleModalOpen: () => void;
  handleModalClose: () => void;
  checkPremiumStatus: () => Promise<void>;
}

// Provider Props
export interface PremiumProviderProps {
  children: React.ReactNode;
  showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
}

// 메시지 핸들러 기본 인터페이스
export interface MessageHandler {
  postMessage: (message: string) => void;
}

// WebKit 메시지 핸들러 타입 확장 업데이트
export interface WebKitMessageHandlersWithPremium extends WebKitMessageHandlers {
  checkPremiumStatus: MessageHandler;
  handlePremiumPurchase: MessageHandler;
  restorePurchases: MessageHandler; // 추가
}


// Window 인터페이스 확장
declare global {
  interface Window {
    __PREMIUM_CONTEXT__?: PremiumContext;
    webkit?: {
      messageHandlers: WebKitMessageHandlersWithPremium;
    };
    premiumHandlers?: PremiumHandlers;
    setPremiumStatus?: (
      isPremium: boolean, 
      purchaseDate: string | undefined, 
      transactionId: string | undefined
    ) => void;
    onPremiumPurchaseSuccess?: () => void;
    onPremiumPurchaseFailure?: (error: string) => void;
    onPremiumRestoreSuccess?: () => void; // 추가
    onPremiumRestoreFailure?: (error: string) => void; // 추가
    closePaymentModal?: () => void;
    Android?: AndroidInterface;
  }
}