import React from 'react';

// 구구 점프·구구 레인이 함께 쓰는 SVG 그래픽. 좌표는 지면 y=220 기준입니다.

// 반복되는 배경과 재질은 한 번 정의하고 참조해 매 프레임의 도형 갱신을 줄입니다.
export const RunnerArtwork = React.memo(function RunnerArtwork({ id }: { id: string }) {
  return (
    <defs>
      <linearGradient id={`${id}-sky`} x2="0" y2="1">
        <stop stopColor="#bde5e6" /><stop offset="0.65" stopColor="#e4f0d9" /><stop offset="1" stopColor="#f9edbc" />
      </linearGradient>
      <radialGradient id={`${id}-sun`}>
        <stop stopColor="#fff7ce" stopOpacity="0.95" /><stop offset="1" stopColor="#fff7ce" stopOpacity="0" />
      </radialGradient>
      <linearGradient id={`${id}-leaves`} x2="0.8" y2="1">
        <stop stopColor="#94c77b" /><stop offset="1" stopColor="#458965" />
      </linearGradient>
      <linearGradient id={`${id}-dino`} x2="0.8" y2="1">
        <stop stopColor="#8bce78" /><stop offset="0.5" stopColor="#4eab72" /><stop offset="1" stopColor="#237b63" />
      </linearGradient>
      <linearGradient id={`${id}-belly`} x2="0" y2="1">
        <stop stopColor="#f3f1b2" /><stop offset="1" stopColor="#bfda88" />
      </linearGradient>
      <linearGradient id={`${id}-soil`} x2="0" y2="1">
        <stop stopColor="#deb886" /><stop offset="1" stopColor="#bf9267" />
      </linearGradient>
      <linearGradient id={`${id}-rock`} x2="0.8" y2="1">
        <stop stopColor="#d5a579" /><stop offset="0.45" stopColor="#b77e58" /><stop offset="1" stopColor="#835b49" />
      </linearGradient>
      <g id={`${id}-cloud`} fill="#fffdf3">
        <path d="M-38 7a12 12 0 0 1 13-14 18 18 0 0 1 34-8A15 15 0 0 1 33 0 9 9 0 0 1 38 16h-65A12 12 0 0 1-38 7Z" />
        <path d="M-28 16H30" stroke="#d4e7dd" strokeWidth="3" strokeLinecap="round" opacity="0.6" />
      </g>
      <g id={`${id}-mountains`}>
        <path d="M0 180 91 99q14-13 28 2l71 59 104-98q13-12 27 3l96 108 72-55q16-11 32 1l110 68 51-36 38 29v56H0Z" fill="#a4cdce" />
        <path d="m78 110 13-11q14-13 28 2l22 19-23-6-11-11-12 12Zm181-15 35-33q13-12 27 3l34 39-30-14-15-16-20 17-9-3Z" fill="#e7f2e8" opacity="0.8" />
        <path d="m108 107 47 102H28Zm198-36 77 136H184Z" fill="#86b9bd" opacity="0.35" />
      </g>
      <g id={`${id}-hills`}>
        <path d="M0 194C74 164 127 153 213 185S333 201 409 166s153-9 221 20q42 20 90 8v40H0Z" fill="#9cbe98" />
        <path d="M0 209c97-27 168-13 242 5s169-25 254-19 155 38 224 14v28H0Z" fill="#70a883" />
        <path d="M51 190q67-16 116-3M434 183q36-14 70-10" fill="none" stroke="#d1e0ae" strokeWidth="3" strokeLinecap="round" opacity="0.65" />
      </g>
      <g id={`${id}-tree`}>
        <path d="M-4 0-3-45h8L5 0Z" fill="#82765c" />
        <path d="m1-18-12-13m13 6 10-11" fill="none" stroke="#82765c" strokeWidth="4" strokeLinecap="round" />
        <path d="M-23-33C-43-38-31-67-15-66c-2-29 33-36 42-12 26-1 32 35 9 39-5 17-30 21-40 10-7 9-19 7-19-4Z" fill={`url(#${id}-leaves)`} />
        <path d="M-20-56q-6-11 7-16m10-8q13-9 23 4" fill="none" stroke="#c2dfa0" strokeWidth="5" strokeLinecap="round" opacity="0.7" />
        <circle cx="-12" cy="-43" r="3" fill="#e9c372" /><circle cx="21" cy="-56" r="3" fill="#e9c372" />
      </g>
      <g id={`${id}-forest`}>
        <use href={`#${id}-tree`} transform="translate(46 217) scale(.5)" opacity="0.65" />
        <use href={`#${id}-tree`} transform="translate(239 215) scale(.82)" />
        <use href={`#${id}-tree`} transform="translate(448 217) scale(.56)" opacity="0.8" />
        <use href={`#${id}-tree`} transform="translate(641 215) scale(.9)" />
        <path d="M90 220q4-17 18-10 8-17 20-3 14-3 18 13Zm415 0q4-12 12-9 5-20 17-8 15-2 16 17Z" fill="#4e946c" />
      </g>
      <g id={`${id}-trail`}>
        <path d="M0 222q12-4 25 0t25 0 25 0 25 0 20 0v5H0Z" fill="#648c52" />
        <path d="m15 223-3-7 6 4 4-8 2 11m68 0-2-5 5 1 4-6 1 10" fill="#7ca959" />
        <path d="m15 242 7-2 5 2m43 9h8m20-18 6 2" fill="none" stroke="#a78160" strokeWidth="2" strokeLinecap="round" />
        <path d="m45 233 6-3 5 4-8 1Zm67 15 7-4 4 5Z" fill="#edd4a5" />
      </g>
      <path id={`${id}-sparkle`} d="m0-7 2 5 5 2-5 2-2 5-2-5-5-2 5-2Z" />
    </defs>
  );
});

// 하늘·해·구름과 패럴랙스 원경. travel은 달린 거리(px)이며 동작 줄이기에서는 0을 넘깁니다.
export function RunnerBackdrop({ id, travel }: { id: string; travel: number }) {
  return (
    <>
      <rect width="720" height="260" fill={`url(#${id}-sky)`} />
      <circle cx="584" cy="63" r="76" fill={`url(#${id}-sun)`} />
      <circle cx="584" cy="63" r="25" fill="#ffe9a2" />
      <circle cx="577" cy="56" r="18" fill="#fff2b9" opacity="0.45" />
      <g data-runner-layer="clouds" transform={`translate(${-((travel * 0.035) % 900)} 0)`}>
        {[0, 900].map((x) => <g key={x} transform={`translate(${x} 0)`}><use href={`#${id}-cloud`} transform="translate(82 63) scale(.85)" opacity="0.92" /><use href={`#${id}-cloud`} transform="translate(402 40) scale(.62)" opacity="0.7" /></g>)}
      </g>
      {[['mountains', 0.07], ['hills', 0.15], ['forest', 0.3]].map(([name, speed]) => (
        <g key={name} data-runner-layer={name} transform={`translate(${-((travel * Number(speed)) % 720)} 0)`}>
          <use href={`#${id}-${name}`} /><use href={`#${id}-${name}`} x="720" />
        </g>
      ))}
    </>
  );
}

export interface RunnerDinoProps {
  id: string;
  stride: number;
  airborne: boolean;
  hurt: boolean;
  tilt: number;
}

// 공룡 본체. 부모 <g>가 위치를 잡고, 발끝은 로컬 y≈63입니다.
export function RunnerDino({ id, stride, airborne, hurt, tilt }: RunnerDinoProps) {
  return (
    <g transform={`rotate(${tilt} 28 56)`} strokeLinecap="round" strokeLinejoin="round">
      <path d="M19 39Q3 43-7 30q0 18 22 23" fill="#388d65" stroke="#28634e" strokeWidth="1.5" />
      <path d="m15 28-7-4 5-8 7 4m1-8-6-5 8-6 5 5" fill="#e5c879" stroke="#9e9c56" strokeWidth="1.2" />
      <g transform={`rotate(${-stride * 24 - (airborne ? 18 : 0)} 22 47)`}>
        <path d="M18 44h10l-2 14h5q4 0 3 5H20q-3 0-3-3Z" fill="#2b795c" stroke="#28634e" strokeWidth="1.5" />
      </g>
      <path d="M15 31q0-13 12-15V11Q27 1 39 1h8q13 0 13 13v7q0 11-15 12l-3 11q-4 13-17 11-16-2-10-24Z" fill={`url(#${id}-dino)`} stroke="#28634e" strokeWidth="1.8" />
      <path d="M34 31q11-4 11 3-3 21-17 18-7-6 6-21Z" fill={`url(#${id}-belly)`} />
      <path d="M21 27q0-7 7-8m4-8q0-4 7-5" fill="none" stroke="#b2e598" strokeWidth="3" opacity="0.8" />
      <path d="M42 8q8-2 9 7v4q-1 7-8 5-7-2-6-9 0-5 5-7Z" fill="#fffdf1" />
      {hurt ? <path d="m41 13 6 6m0-6-6 6" fill="none" stroke="#294e3c" strokeWidth="2" /> : <><ellipse cx="46" cy="16" rx="3.3" ry="4.8" fill="#243f32" /><circle cx="47" cy="14" r="1.2" fill="white" /></>}
      <circle cx="56" cy="20" r="1.3" fill="#2f6d51" />
      <ellipse cx="39" cy="26" rx="4" ry="2.3" fill="#eaaa82" opacity="0.75" />
      <path d={hurt ? 'M49 29q4-3 7-1' : 'M49 28q4 3 7-1'} fill="none" stroke="#2e6449" strokeWidth="1.4" />
      <path d={`M27 32q-12 ${-7 + stride * 2}-20-3l8 5-6 5q13 0 20-4`} fill="#d56942" stroke="#a84932" strokeWidth="1.2" />
      <path d="m28 30 17 2-2 6-17-3Z" fill="#f18b52" stroke="#b95335" strokeWidth="1" />
      <circle cx="31" cy="34" r="2.5" fill="#f7d079" />
      <path d="M29 40q-2 9 6 7l4-3" fill="none" stroke="#28634e" strokeWidth="5" />
      <path d="M29 39q-2 9 6 7l4-3" fill="none" stroke="#74ba76" strokeWidth="3" />
      <g transform={`rotate(${stride * 25 + (airborne ? 14 : 0)} 30 48)`}>
        <path d="M25 46h10l-1 12h6q4 0 3 5H28q-4 0-4-4Z" fill="#4eaa70" stroke="#28634e" strokeWidth="1.5" />
        <path d="M33 60v2m4-2v2" stroke="#d7e9a2" strokeWidth="1.5" />
      </g>
    </g>
  );
}

export type RunnerObstacleKind = 'rock' | 'stump';

// 장애물 그림(그림자 포함). 부모 <g>가 x 위치를 잡습니다.
export function RunnerObstacle({ id, kind }: { id: string; kind: RunnerObstacleKind }) {
  return (
    <>
      <ellipse cx="17" cy="224" rx="24" ry="4" fill="#655845" opacity="0.2" />
      {kind === 'rock' ? (
        <>
          <path d="m0 220 3-21 7-9 2-12 11-3 8 12 5 15-1 18Z" fill={`url(#${id}-rock)`} stroke="#785444" strokeWidth="1.8" />
          <path d="m5 201 7-8 3-13 8-2 4 9-10 8-3 13Z" fill="#ebc69a" opacity="0.85" />
          <path d="m17 195 6 7-5 10 5 8m0-18 9-3" fill="none" stroke="#855e48" strokeWidth="1.5" />
          <path d="m1 219 4-8 7 4 4-3 6 7" fill="#759451" />
        </>
      ) : (
        <>
          <path d="M3 219 6 191q12-9 25 0l3 28Z" fill="#a46d47" stroke="#775137" strokeWidth="1.8" />
          <path d="m7 195 1 20m18-19 3 19m-10-20-2 22" stroke="#754f38" strokeWidth="2.5" />
          <path d="m11 198 1 9m11-11-1 16" stroke="#d4a471" strokeWidth="2" />
          <ellipse cx="18.5" cy="192" rx="13" ry="6" fill="#eac596" stroke="#9e714b" strokeWidth="1.5" />
          <ellipse cx="18.5" cy="192" rx="7" ry="3" fill="none" stroke="#bc935e" strokeWidth="1.3" />
          <path d="M29 189v-9m0 3q-9 0-7-6 7-1 7 6m0-2q8-1 7-7-8 0-7 7" fill="#84b269" stroke="#527d46" strokeWidth="1.5" />
        </>
      )}
    </>
  );
}
