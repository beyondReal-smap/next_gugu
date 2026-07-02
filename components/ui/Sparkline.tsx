import React from 'react';

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  className?: string; // text-* 로 선 색 지정 (currentColor)
  fill?: boolean;
}

// 경량 SVG 스파크라인 (currentColor)
export function Sparkline({ data, width = 120, height = 36, className = 'text-accent', fill = true }: SparklineProps) {
  if (!data || data.length < 2) {
    return <div className="text-xs text-text-muted">데이터 부족</div>;
  }
  const max = Math.max(...data);
  const min = Math.min(...data);
  const range = max - min || 1;
  const stepX = width / (data.length - 1);
  const pts = data.map((v, i) => {
    const x = i * stepX;
    const y = height - ((v - min) / range) * (height - 4) - 2;
    return [x, y] as const;
  });
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${width},${height} L0,${height} Z`;
  return (
    <svg width={width} height={height} className={className}>
      {fill && <path d={area} fill="currentColor" opacity={0.12} />}
      <path d={line} fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={pts[pts.length - 1][0]} cy={pts[pts.length - 1][1]} r={2.5} fill="currentColor" />
    </svg>
  );
}
