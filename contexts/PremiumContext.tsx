import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { 
  PremiumState, 
  PremiumContextType, 
  PremiumProviderProps,
  PremiumHandlers 
} from '../app/types/premium-types';

const PremiumContext = createContext<PremiumContextType | undefined>(undefined);

export function PremiumProvider({ children, showAlert }: PremiumProviderProps) {
  const [state, setState] = useState<PremiumState>({
    isPremium: false,
    showModal: false,
    isProcessing: false,
    purchaseDate: undefined,
    transactionId: undefined
  });

  // Context element 업데이트 함수
  const updateContextElement = useCallback((data: Partial<PremiumState>) => {
    try {
      let contextElement = document.querySelector('[data-premium-context]') as HTMLDivElement;
      if (!contextElement) {
        contextElement = document.createElement('div');
        contextElement.setAttribute('data-premium-context', 'true');
        contextElement.style.display = 'none';
        document.body.appendChild(contextElement);
      }

      const contextData = {
        isPremium: data.isPremium ?? false,
        purchaseDate: data.purchaseDate,
        transactionId: data.transactionId,
        lastUpdated: new Date().toISOString()
      };

      const newContent = JSON.stringify(contextData);
      if (contextElement.textContent !== newContent) {
        contextElement.textContent = newContent;
        console.log('[Premium] Context element updated:', contextData);
      }
    } catch (error) {
      console.error('[Premium] Context update error:', error);
    }
  }, []);

  // 네이티브 핸들러 초기화
  const initializeNativeHandlers = useCallback(() => {
    if (window.premiumHandlers) {
      console.log('[Premium] Handlers already initialized');
      return;
    }

    console.log('[Premium] Initializing native handlers');
    const handlers: PremiumHandlers = {
      setPremiumStatus: (isPremium: boolean, purchaseDate: string | undefined, transactionId: string | undefined) => {
        console.log('[Premium] Setting status from native:', { isPremium, purchaseDate, transactionId });
        
        const newState: PremiumState = {
          isPremium,
          purchaseDate,
          transactionId,
          isProcessing: false,
          showModal: false
        };

        setState(prev => ({
          ...prev,
          ...newState
        }));
        updateContextElement(newState);
      },

      getState: () => state
    };

    window.premiumHandlers = handlers;
    window.setPremiumStatus = handlers.setPremiumStatus;
    
    console.log('[Premium] Native handlers initialized');
  }, [state, updateContextElement]);

  // React Context를 전역으로 노출
  useEffect(() => {
    window.__PREMIUM_CONTEXT__ = { state, setState };
    console.log('[Premium] Context state updated:', state);
    return () => {
      delete window.__PREMIUM_CONTEXT__;
    };
  }, [state]);

  // Premium 상태 변경 이벤트 처리
  const handleStatusUpdate = useCallback((event: CustomEvent<Partial<PremiumState>>) => {
    console.log('[Premium] Status update event received:', event.detail);
    
    if (!event.detail) return;

    setState(prev => ({
      ...prev,
      ...event.detail,
      showModal: false,
      isProcessing: false
    }));

    updateContextElement(event.detail);
  }, [updateContextElement]);

  // 이벤트 리스너 및 핸들러 초기화
  useEffect(() => {
    window.addEventListener('updatePremiumStatus', handleStatusUpdate as EventListener);
    initializeNativeHandlers();

    return () => {
      window.removeEventListener('updatePremiumStatus', handleStatusUpdate as EventListener);
      if (window.premiumHandlers) {
        delete window.premiumHandlers;
      }
      if (window.setPremiumStatus) {
        delete window.setPremiumStatus;
      }
      const contextElement = document.querySelector('[data-premium-context]');
      if (contextElement?.parentNode) {
        contextElement.parentNode.removeChild(contextElement);
      }
    };
  }, [handleStatusUpdate, initializeNativeHandlers]);

  // 구매 처리
  const handlePurchase = useCallback(async () => {
    if (state.isProcessing) {
      console.log('[Premium] Purchase already in progress');
      return;
    }

    setState(prev => ({ ...prev, isProcessing: true }));
    console.log('[Premium] Starting purchase process');

    try {
      if (window.webkit?.messageHandlers.handlePremiumPurchase) {
        window.webkit.messageHandlers.handlePremiumPurchase.postMessage('');
      }
    } catch (error) {
      console.error('[Premium] Purchase error:', error);
      setState(prev => ({ ...prev, isProcessing: false }));
      showAlert('구매 중 오류가 발생했습니다', 'error');
    }
  }, [state.isProcessing, showAlert]);

  const checkPremiumStatus = useCallback(async () => {
    console.log('[Premium] Checking premium status');
    if (window.webkit?.messageHandlers.checkPremiumStatus) {
      window.webkit.messageHandlers.checkPremiumStatus.postMessage('');
    }
  }, []);

  const value: PremiumContextType = {
    ...state,
    handlePurchase,
    handleModalOpen: useCallback(() => setState(prev => ({ ...prev, showModal: true })), []),
    handleModalClose: useCallback(() => setState(prev => ({ ...prev, showModal: false })), []),
    checkPremiumStatus
  };

  return (
    <PremiumContext.Provider value={value}>
      {children}
    </PremiumContext.Provider>
  );
}

export function usePremium() {
  const context = useContext(PremiumContext);
  if (!context) {
    throw new Error('usePremium must be used within a PremiumProvider');
  }
  return context;
}