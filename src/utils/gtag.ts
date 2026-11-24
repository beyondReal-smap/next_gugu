export const GA_TRACKING_ID = 'G-PQH2R3CR6S'

const SCRIPT_ID = 'google-analytics-src';
const disableKey = `ga-disable-${GA_TRACKING_ID}`;

type GtagConfig = {
  page_path?: string;
  event_category?: string;
  event_label?: string;
  value?: number;
  anonymize_ip?: boolean;
};

declare global {
  interface Window {
    dataLayer: unknown[];
    gtag: (command: string, target: string | Date, config?: GtagConfig) => void;
  }
}

function setDisabled(disabled: boolean) {
  (window as unknown as Record<string, unknown>)[disableKey] = disabled;
}

// 보호자 동의 후에만 호출. 스크립트가 없으면 삽입하고 anonymize_ip를 켠다.
export function enableAnalytics(): void {
  if (typeof window === 'undefined') return;
  setDisabled(false);

  window.dataLayer = window.dataLayer || [];
  if (typeof window.gtag !== 'function') {
    window.gtag = function gtag() {
      window.dataLayer.push(arguments);
    };
  }
  window.gtag('js', new Date());
  window.gtag('config', GA_TRACKING_ID, { anonymize_ip: true });

  if (document.getElementById(SCRIPT_ID)) return;

  const s = document.createElement('script');
  s.id = SCRIPT_ID;
  s.async = true;
  s.src = `https://www.googletagmanager.com/gtag/js?id=${GA_TRACKING_ID}`;
  s.onerror = () => {
    console.error('Google Analytics 스크립트 로드 실패');
  };
  document.head.appendChild(s);
}

export function disableAnalytics(): void {
  if (typeof window === 'undefined') return;
  setDisabled(true);
}

export const pageview = (url: string) => {
  if (typeof window.gtag !== 'function') {
    console.error('pageview: gtag가 로드되지 않았습니다');
    return;
  }
  window.gtag('config', GA_TRACKING_ID, {
    page_path: url,
    anonymize_ip: true,
  });
};

export const event = ({ action, category, label, value }: {
  action: string
  category: string
  label: string
  value?: number
}) => {
  if (typeof window.gtag !== 'function') {
    console.error('event: gtag가 로드되지 않았습니다');
    return;
  }
  window.gtag('event', action, {
    event_category: category,
    event_label: label,
    value: value,
  });
}