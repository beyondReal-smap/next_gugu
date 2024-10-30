// components/ui/alert.js
import React from 'react';

export const Alert = ({ children, className, ...props }) => {
  return (
    <div className={`rounded-md p-4 ${className}`} {...props}>
      {children}
    </div>
  );
};

// components/ui/alert.js
export const AlertDescription = ({ children, className, ...props }) => {
  return (
    <div className={`font-medium ${className}`} {...props}> 
      {children}
    </div> 
  );
};