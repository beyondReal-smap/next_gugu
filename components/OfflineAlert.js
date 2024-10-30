// components/OfflineAlert.js
import React from 'react';
import { Alert, AlertDescription } from '../app/components/ui/alert';
import { WifiOff } from 'lucide-react';

export function OfflineAlert({ isOffline }) {
  if (!isOffline) return null;

  return (
    <Alert className="fixed bottom-4 left-4 right-4 bg-yellow-50 border-yellow-200 z-40">
      <WifiOff className="w-4 h-4 text-yellow-600" />
      <AlertDescription className="text-yellow-700">
        You are currently offline. Some features may be limited.
      </AlertDescription>
    </Alert>
  );
}