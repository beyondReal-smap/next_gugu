"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

interface PremiumState {
  isPremium: boolean;
  showModal: boolean;
  isProcessing: boolean;
  purchaseDate?: string;
}

interface PremiumContextType extends PremiumState {
  handlePurchase: () => Promise<void>;
  handleModalOpen: () => void;
  handleModalClose: () => void;
  checkPremiumStatus: () => Promise<void>;
}

const PremiumContext = createContext<PremiumContextType | undefined>(undefined);

interface PremiumProviderProps {
  children: React.ReactNode;
  showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
}

export function PremiumProvider({ children, showAlert }: PremiumProviderProps) {
  // 상태 관리
  const [state, setState] = useState<PremiumState>({
    isPremium: false,
    showModal: false,
    isProcessing: false,
    purchaseDate: undefined
  });

  // 프리미엄 구매 처리
  const handlePurchase = useCallback(async () => {
    if (state.isProcessing) return;

    setState(prev => ({ ...prev, isProcessing: true }));

    try {
      // iOS
      if (window.webkit?.messageHandlers?.storeKit) {
        window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
          type: 'purchase'
        }));
        return; // iOS는 이벤트로 응답 처리
      }

      // Android
      if (window.Android?.purchasePremium) {
        const success = await window.Android.purchasePremium();
        if (success) {
          await window.Android.savePurchaseStatus(true);
          setState(prev => ({ 
            ...prev, 
            isPremium: true, 
            showModal: false,
            purchaseDate: new Date().toISOString()
          }));
          showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');
          return;
        }
      }

      // 웹 테스트용
      localStorage.setItem('premiumStatus', 'true');
      localStorage.setItem('purchaseDate', new Date().toISOString());
      setState(prev => ({ 
        ...prev, 
        isPremium: true, 
        showModal: false,
        purchaseDate: new Date().toISOString()
      }));
      showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');

    } catch (error) {
      console.error('Purchase failed:', error);
      showAlert('구매 중 오류가 발생했습니다', 'error');
    } finally {
      setState(prev => ({ ...prev, isProcessing: false }));
    }
  }, [state.isProcessing, showAlert]);

  // 프리미엄 상태 확인
  const checkPremiumStatus = useCallback(async () => {
    try {
      // iOS
      if (window.webkit?.messageHandlers?.storeKit) {
        window.webkit.messageHandlers.storeKit.postMessage(JSON.stringify({
          type: 'getPremiumStatus'
        }));
        return; // iOS는 이벤트로 응답 처리
      }

      // Android
      if (window.Android?.getPremiumStatus) {
        const status = await window.Android.getPremiumStatus();
        setState(prev => ({ ...prev, isPremium: status }));
        return;
      }

      // 웹 테스트용
      const savedStatus = localStorage.getItem('premiumStatus') === 'true';
      const savedDate = localStorage.getItem('purchaseDate');
      setState(prev => ({ 
        ...prev, 
        isPremium: savedStatus,
        purchaseDate: savedDate || undefined
      }));

    } catch (error) {
      console.error('Failed to check premium status:', error);
      setState(prev => ({ ...prev, isPremium: false }));
    }
  }, []);

  // 모달 컨트롤
  const handleModalOpen = useCallback(() => {
    setState(prev => ({ ...prev, showModal: true }));
  }, []);

  const handleModalClose = useCallback(() => {
    setState(prev => ({ ...prev, showModal: false }));
  }, []);

  // iOS 메시지 핸들러
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      const { type, status, purchaseDate } = event.data || {};

      switch (type) {
        case 'premiumStatus':
          setState(prev => ({ ...prev, isPremium: Boolean(status) }));
          break;

        case 'purchaseSuccess':
          setState(prev => ({ 
            ...prev, 
            isPremium: true, 
            showModal: false,
            purchaseDate: purchaseDate || new Date().toISOString()
          }));
          showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');
          break;

        default:
          break;
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [showAlert]);

  // 초기 상태 체크
  useEffect(() => {
    checkPremiumStatus();
  }, [checkPremiumStatus]);

  const value = {
    ...state,
    handlePurchase,
    handleModalOpen,
    handleModalClose,
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
  if (context === undefined) {
    throw new Error('usePremium must be used within a PremiumProvider');
  }
  return context;
}