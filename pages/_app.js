// pages/_app.js
import { InstallPrompt } from '../components/InstallPrompt';
import { UpdatePrompt } from '../components/UpdatePrompt';
import { OfflineAlert } from '../components/OfflineAlert';
import { useState, useEffect } from 'react';

function MyApp({ Component, pageProps }) {
  const [isOffline, setIsOffline] = useState(false);

  useEffect(() => {
    setIsOffline(!navigator.onLine);
    
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return (
    <>
      <Component {...pageProps} />
      <InstallPrompt />
      <UpdatePrompt />
      <OfflineAlert isOffline={isOffline} />
    </>
  );
}

export default MyApp;