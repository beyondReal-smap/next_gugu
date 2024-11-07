// lib/purchaseManager.ts
class PurchaseManager {
    private static isAndroid: boolean = 
      typeof window !== 'undefined' && /android/i.test(window.navigator.userAgent);
    
    private static isIOS: boolean = 
      typeof window !== 'undefined' && /iphone|ipad|ipod/i.test(window.navigator.userAgent);
  
    private static readonly STORAGE_KEY = 'purchaseStatus';
  
    public static async savePurchaseStatus(isPremium: boolean): Promise<boolean> {
      try {
        if (typeof window === 'undefined') return false;
        
        if (this.isAndroid && window.Android) {
          // Android에서는 구독 처리
          window.Android.subscribeProduct('premium_no_ads');
          localStorage.setItem(this.STORAGE_KEY, 'true');
          return true;
        } 
        
        if (this.isIOS && window.webkit?.messageHandlers) {
          // iOS에서는 구독 처리
          window.webkit.messageHandlers.subscribeProduct.postMessage('premium_no_ads');
          localStorage.setItem(this.STORAGE_KEY, 'true');
          return true;
        }
        
        // 웹 테스트용
        localStorage.setItem(this.STORAGE_KEY, isPremium.toString());
        return true;
      } catch (error) {
        console.error('Failed to save purchase status:', error);
        return false;
      }
    }
  
    public static async getPurchaseStatus(): Promise<boolean> {
      try {
        if (typeof window === 'undefined') return false;
        
        // localStorage에서 구매 상태 확인
        return localStorage.getItem(this.STORAGE_KEY) === 'true';
        
      } catch (error) {
        console.error('Failed to get purchase status:', error);
        return false;
      }
    }
  
    /**
     * 현재 환경이 모바일 앱인지 확인
     */
    public static isApp(): boolean {
      return this.isAndroid || this.isIOS;
    }
  
    /**
     * 결제 처리
     */
    public static async processPurchase(): Promise<boolean> {
      try {
        if (this.isAndroid && window.Android) {
          window.Android.subscribeProduct('premium_no_ads');
          return true;
        }
        
        if (this.isIOS && window.webkit?.messageHandlers) {
          window.webkit.messageHandlers.subscribeProduct.postMessage('premium_no_ads');
          return true;
        }
  
        return false;
      } catch (error) {
        console.error('Purchase process failed:', error);
        return false;
      }
    }
  }
  
  export default PurchaseManager;