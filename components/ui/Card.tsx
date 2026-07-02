import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  as?: 'div' | 'section';
}

export function Card({ className = '', children, ...props }: CardProps) {
  return (
    <div
      className={`rounded-2xl bg-surface border border-border shadow-[0_1px_3px_0_rgb(15_23_42_/_0.06)] ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
