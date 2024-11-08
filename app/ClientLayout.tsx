"use client";

import React, { useState, useCallback } from 'react';
import { PremiumProvider } from '@/contexts/PremiumContext';
import { motion, AnimatePresence } from "framer-motion";

interface AlertState {
  show: boolean;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
}

interface ClientLayoutProps {
  children: React.ReactNode;
}

export default function ClientLayout({ children }: ClientLayoutProps) {
  const [alert, setAlert] = useState<AlertState>({
    show: false,
    message: '',
    type: 'info'
  });

  const showAlert = useCallback((message: string, type: 'success' | 'error' | 'info' | 'warning') => {
    setAlert({ show: true, message, type });
    setTimeout(() => {
      setAlert(prev => ({ ...prev, show: false }));
    }, 3000);
  }, []);

  return (
    <PremiumProvider showAlert={showAlert}>
      {children}
      
      <AnimatePresence>
        {alert.show && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            className={`fixed bottom-4 left-1/2 transform -translate-x-1/2 
              px-4 py-2 rounded-lg shadow-lg z-50
              ${alert.type === 'success' ? 'bg-green-500 text-white' :
                alert.type === 'error' ? 'bg-red-500 text-white' :
                alert.type === 'warning' ? 'bg-yellow-500 text-white' :
                'bg-blue-500 text-white'}`}
          >
            {alert.message}
          </motion.div>
        )}
      </AnimatePresence>
    </PremiumProvider>
  );
}