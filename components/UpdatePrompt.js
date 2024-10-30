// components/UpdatePrompt.js
import React, { useState, useEffect } from 'react';
import { Button } from '../app/components/ui/button';
import { Card, CardContent } from '../app/components/ui/card';
import { RefreshCw } from 'lucide-react';

export function UpdatePrompt() {
  const [showUpdatePrompt, setShowUpdatePrompt] = useState(false);

  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        setShowUpdatePrompt(true);
      });
    }
  }, []);

  const handleUpdate = () => {
    setShowUpdatePrompt(false);
    window.location.reload();
  };

  if (!showUpdatePrompt) return null;

  return (
    <Card className="fixed top-4 left-4 right-4 bg-white/90 backdrop-blur z-50">
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex-1 mr-4">
            <h3 className="font-medium text-gray-900">Update Available</h3>
            <p className="text-sm text-gray-500">A new version is available. Please refresh.</p>
          </div>
          <Button onClick={handleUpdate} className="flex items-center gap-2">
            <RefreshCw className="w-4 h-4" />
            Update
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}