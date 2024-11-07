// types/banner.ts
export interface BannerItem {
    type: 'content' | 'ad' | 'image' | 'subscription';
    icon?: string;
    text?: string;
    image?: string;
    link?: string;
    backgroundColor?: string;
    textColor?: string;
    adUnitId?: string; // AdMob 광고 단위 ID
    imageUrl?: string; // 전체 이미지 URL
    description?: string;
  }
  
  export interface RollingBannerProps {
    items: BannerItem[];
    autoPlayInterval?: number;
    onSubscribe?: (product: SubscriptionProduct) => void;
    description?: string;
  }
  
  export interface SubscriptionProduct {
    id: string;
    title: string;
    description: string;
    price: string;
    currency: string;
    period: string;
}