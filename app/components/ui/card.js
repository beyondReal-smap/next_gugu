// components/ui/card.js
import React from 'react';

export const Card = ({ children, className, ...props }) => {
  return (
    <div className={`rounded-lg shadow-md overflow-hidden ${className}`} {...props}>
      {children}
    </div>
  );
};

export const CardContent = ({ children, className, ...props }) => {
  return (
    <div className={`p-6 ${className}`} {...props}>
      {children}
    </div>
  );
};