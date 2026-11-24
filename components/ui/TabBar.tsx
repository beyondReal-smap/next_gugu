"use client";
import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, GraduationCap, User } from 'lucide-react';

const TABS = [
  { href: '/play', label: '홈', icon: Home },
  { href: '/learn', label: '학습', icon: GraduationCap },
  { href: '/profile', label: '프로필', icon: User },
];

export function TabBar() {
  const pathname = usePathname() ?? '/';
  return (
    <nav aria-label="주요 메뉴" className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface/95 backdrop-blur-lg">
      <div className="mx-auto flex max-w-5xl items-stretch justify-around gap-1 px-4 pb-[env(safe-area-inset-bottom)] sm:px-8">
        {TABS.map(({ href, label, icon: Icon }) => {
          const active = pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? 'page' : undefined}
              className={`flex min-h-16 flex-1 flex-col items-center justify-center gap-0.5 py-2 text-xs font-bold transition-colors
                ${active ? 'text-accent' : 'text-text-muted hover:text-text'}`}
            >
              <span className={`flex h-8 w-14 items-center justify-center rounded-xl transition-colors ${active ? 'bg-accent/10' : ''}`}>
                <Icon aria-hidden="true" className="h-5 w-5" strokeWidth={active ? 2.5 : 2} />
              </span>
              {label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
