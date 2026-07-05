// purchaseManager.ts
import type { WebKitMessageHandlers, AndroidInterface } from '../types/webkit';

interface PurchaseStatus {
  isPremium: boolean;
  purchaseDate?: Date;
}

declare global {
  interface Window {
      BillingManager?: {
          initializePurchase: () => Promise<boolean>;
          purchasePremium: () => Promise<boolean>;
          checkPurchaseStatus: () => Promise<boolean>;
          logPurchaseStatus: () => void;
      };
  }
}

class PurchaseManager {
  private static readonly STORAGE_KEY = 'PREMIUM_STATUS';
  private static readonly MOCK_STORAGE_KEY = 'MOCK_PREMIUM_STATUS';

  // 초기화
  public static async initialize(): Promise<boolean> {
      try {
          if (window.webkit?.messageHandlers.storeKit) {
              // iOS
              window.webkit.messageHandlers.storeKit.postMessage('initialize');
              return true;
          } else if (window.BillingManager) {
              // Android
              return await window.BillingManager.initializePurchase();
          } else {
              // 웹 환경 (개발용)
              this.loadMockPurchaseStatus();
              return true;
          }
      } catch (error) {
          console.error('Failed to initialize purchase system:', error);
          return false;
      }
  }

  // 프리미엄 구매
  public static async purchasePremium(): Promise<boolean> {
      try {
          if (window.webkit?.messageHandlers.storeKit) {
              // iOS
              window.webkit.messageHandlers.storeKit.postMessage('purchase');
              return true;
          } else if (window.BillingManager) {
              // Android
              return await window.BillingManager.purchasePremium();
          } else {
              // 웹 환경 (개발용)
              return await this.mockPurchase();
          }
      } catch (error) {
          console.error('Purchase failed:', error);
          return false;
      }
  }

  // 구매 상태 저장
  public static async savePurchaseStatus(isPurchased: boolean): Promise<boolean> {
      try {
          const purchaseStatus: PurchaseStatus = {
              isPremium: isPurchased,
              purchaseDate: isPurchased ? new Date() : undefined
          };

          if (window.webkit?.messageHandlers.storeKit || window.BillingManager) {
              // 네이티브 환경에서는 로컬 저장소에 저장
              localStorage.setItem(
                  this.STORAGE_KEY,
                  JSON.stringify(purchaseStatus)
              );
          } else {
              // 웹 환경 (개발용)
              localStorage.setItem(
                  this.MOCK_STORAGE_KEY,
                  JSON.stringify(purchaseStatus)
              );
          }

          console.log('Purchase status saved:', purchaseStatus);
          return true;
      } catch (error) {
          console.error('Failed to save purchase status:', error);
          return false;
      }
  }

  // 구매 상태 확인
  public static getPurchaseStatus(): PurchaseStatus {
      try {
          if (window.webkit?.messageHandlers.storeKit) {
              // iOS
              const status = localStorage.getItem(this.STORAGE_KEY);
              return this.parsePurchaseStatus(status);
          } else if (window.BillingManager) {
              // Android
              const status = localStorage.getItem(this.STORAGE_KEY);
              return this.parsePurchaseStatus(status);
          } else {
              // 웹 환경 (개발용)
              const status = localStorage.getItem(this.MOCK_STORAGE_KEY);
              return this.parsePurchaseStatus(status);
          }
      } catch (error) {
          console.error('Failed to get purchase status:', error);
          return { isPremium: false };
      }
  }

  // 구매 상태 로그
  public static logPurchaseStatus(): void {
      const status = this.getPurchaseStatus();
      console.group('Premium Status Log');
      console.log('Is Premium:', status.isPremium);
      console.log('Purchase Date:', status.purchaseDate?.toLocaleString());
      console.log('Environment:', this.getEnvironment());
      console.groupEnd();
  }

  // 구매 상태 초기화
  public static clearPurchaseStatus(): void {
      if (window.webkit?.messageHandlers.storeKit || window.BillingManager) {
          localStorage.removeItem(this.STORAGE_KEY);
      } else {
          localStorage.removeItem(this.MOCK_STORAGE_KEY);
      }
      console.log('Purchase status cleared');
  }

  // Private helper methods
  private static parsePurchaseStatus(status: string | null): PurchaseStatus {
      if (!status) return { isPremium: false };
      
      try {
          const parsed = JSON.parse(status);
          return {
              isPremium: parsed.isPremium,
              purchaseDate: parsed.purchaseDate ? new Date(parsed.purchaseDate) : undefined
          };
      } catch {
          return { isPremium: false };
      }
  }

  private static getEnvironment(): string {
      if (window.webkit?.messageHandlers.storeKit) {
          return 'iOS';
      } else if (window.BillingManager) {
          return 'Android';
      } else {
          return 'Web (Development)';
      }
  }

  // 웹 환경에서 테스트용 mock purchase
  private static async mockPurchase(): Promise<boolean> {
      return new Promise((resolve) => {
          setTimeout(() => {
              const success = Math.random() > 0.1; // 90% 성공률
              if (success) {
                  this.savePurchaseStatus(true);
              }
              resolve(success);
          }, 1000); // 1초 지연
      });
  }

  private static loadMockPurchaseStatus(): void {
      const status = localStorage.getItem(this.MOCK_STORAGE_KEY);
      if (!status) {
          this.savePurchaseStatus(false);
      }
  }
}

export default PurchaseManager;