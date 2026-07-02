import type { Config } from "tailwindcss";

// 시맨틱 토큰: globals.css의 CSS 변수(RGB)와 연결 → 라이트/다크 자동 전환 + alpha 지원
const withVar = (v: string) => `rgb(var(${v}) / <alpha-value>)`;

const config: Config = {
  darkMode: "class",
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./features/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: withVar("--bg"),
        surface: withVar("--surface"),
        "surface-2": withVar("--surface-2"),
        text: withVar("--text"),
        "text-muted": withVar("--text-muted"),
        border: withVar("--border"),
        accent: withVar("--accent"),
        "accent-strong": withVar("--accent-strong"),
        "accent-fg": withVar("--accent-fg"),
        success: withVar("--success"),
        warning: withVar("--warning"),
        danger: withVar("--danger"),
        ring: withVar("--ring"),
      },
      fontFamily: {
        suite: ["SUITE", "sans-serif"],
        num: ["var(--font-geist-sans)", "SUITE", "ui-sans-serif", "sans-serif"],
      },
      borderRadius: {
        "2xl": "1rem",
        "3xl": "1.5rem",
      },
      keyframes: {
        "fade-in": { "0%": { opacity: "0" }, "100%": { opacity: "1" } },
        "slide-up": {
          "0%": { opacity: "0", transform: "translateY(16px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "scale-in": {
          "0%": { opacity: "0", transform: "scale(0.92)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        pop: {
          "0%": { transform: "scale(1)" },
          "45%": { transform: "scale(1.12)" },
          "100%": { transform: "scale(1)" },
        },
        shake: {
          "0%,100%": { transform: "translateX(0)" },
          "25%": { transform: "translateX(-6px)" },
          "75%": { transform: "translateX(6px)" },
        },
        "count-up": {
          "0%": { transform: "translateY(0.4em)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.3s ease-out",
        "slide-up": "slide-up 0.35s cubic-bezier(0.22,1,0.36,1) both",
        "scale-in": "scale-in 0.25s cubic-bezier(0.22,1,0.36,1) both",
        pop: "pop 0.28s ease-out",
        shake: "shake 0.4s ease-in-out",
        "count-up": "count-up 0.3s ease-out both",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
