// 기본 배너 아이템 인터페이스
interface BaseBannerItem {
    type: string;
    link?: string;
    textColor?: string;
    backgroundColor?: string;
  }
  
  // 이미지 배너
  export interface ImageBannerItem extends BaseBannerItem {
    type: 'image';
    imageUrl: string;
  }
  
  // 콘텐츠 배너
  export interface ContentBannerItem extends BaseBannerItem {
    type: 'content';
    text: string;
    icon?: string;
  }
  
  // 광고 배너
  export interface AdBannerItem extends BaseBannerItem {
    type: 'ad';
    adUnitId: string;
  }
  
  // 구독 배너
  export interface SubscriptionBannerItem extends BaseBannerItem {
    type: 'subscription';
    text: string;
    description?: string;
  }
  
  // 모든 배너 타입을 하나로 통합
  export type BannerItem = 
    | ImageBannerItem 
    | ContentBannerItem 
    | AdBannerItem 
    | SubscriptionBannerItem;
  
  // 컴포넌트 props 타입
  export interface RollingBannerProps {
    items: BannerItem[];
    autoPlayInterval?: number;
    onSubscribe?: (productId: string) => void;
  }
  
  // 구독 상품 타입 (필요한 경우)
  export interface SubscriptionProduct {
    id: string;
    name: string;
    price: number;
    description?: string;
  }