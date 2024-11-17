export interface PremiumState {
    isPremium: boolean;
    showModal: boolean;
    isProcessing: boolean;
    purchaseDate?: string;
    transactionId?: string;
  }
  
  export interface PremiumContext {
    state: PremiumState;
    setState: (state: PremiumState | ((prev: PremiumState) => PremiumState)) => void;
  }