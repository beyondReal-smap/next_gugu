// NPC 머리 위 라벨 — 캔버스 텍스처 스프라이트 (drei 없이 자체 구현)
import * as THREE from 'three';

export interface LabelTexture {
  texture: THREE.CanvasTexture;
  aspect: number; // width / height
}

export function makeLabelTexture(text: string): LabelTexture | null {
  if (typeof document === 'undefined') return null;
  const scale = 2; // 레티나 대응
  const fontPx = 26 * scale;
  const padX = 18 * scale;
  const h = 44 * scale;

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  const font = `700 ${fontPx}px SUITE, 'Apple SD Gothic Neo', sans-serif`;
  ctx.font = font;
  const w = Math.ceil(ctx.measureText(text).width) + padX * 2;
  canvas.width = w;
  canvas.height = h;

  // 반투명 알약 배경 + 흰 글자
  const r = h / 2;
  ctx.fillStyle = 'rgba(15, 18, 25, 0.72)';
  ctx.beginPath();
  ctx.moveTo(r, 0);
  ctx.lineTo(w - r, 0);
  ctx.arc(w - r, r, r, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(r, h);
  ctx.arc(r, r, r, Math.PI / 2, (Math.PI * 3) / 2);
  ctx.closePath();
  ctx.fill();

  ctx.font = font;
  ctx.fillStyle = '#ffffff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, w / 2, h / 2 + 1 * scale);

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 4;
  return { texture, aspect: w / h };
}
