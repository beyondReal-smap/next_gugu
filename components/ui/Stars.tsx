import React from 'react';
import { Star } from 'lucide-react';

interface StarsProps {
  value: number; // 0~max
  max?: number;
  size?: number;
  className?: string;
}

export function Stars({ value, max = 3, size = 16, className = '' }: StarsProps) {
  return (
    <div className={`flex items-center gap-0.5 ${className}`}>
      {Array.from({ length: max }, (_, i) => (
        <Star
          key={i}
          width={size}
          height={size}
          className={i < value ? 'text-warning' : 'text-border'}
          fill="currentColor"
        />
      ))}
    </div>
  );
}
