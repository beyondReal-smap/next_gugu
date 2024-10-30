// components/ui/button.js
import React from 'react';

export const Button = ({ children, onClick, variant, className, ...props }) => {
  const buttonVariants = {
    default: 'bg-blue-500 hover:bg-blue-600 text-white',
    outline: 'border border-gray-300 hover:bg-gray-100 text-gray-900',
  };

  const buttonVariantClass = buttonVariants[variant] || buttonVariants.default;

  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 rounded-md font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 ${buttonVariantClass} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
};