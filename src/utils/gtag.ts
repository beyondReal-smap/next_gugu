export const GA_TRACKING_ID = 'G-PQH2R3CR6S'

// gtag 함수의 타입 선언
declare global {
  interface Window {
    gtag: (
      command: string,
      target: string,
      config?: {
        page_path?: string;
        event_category?: string;
        event_label?: string;
        value?: number;
      }
    ) => void;
  }
}

// 페이지뷰 추적
export const pageview = (url: string) => {
  window.gtag('config', GA_TRACKING_ID, {
    page_path: url,
  })
}

// 이벤트 추적
export const event = ({ action, category, label, value }: {
  action: string
  category: string
  label: string
  value?: number
}) => {
  window.gtag('event', action, {
    event_category: category,
    event_label: label,
    value: value,
  })
}