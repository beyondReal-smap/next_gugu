"use client";
import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, GraduationCap, User } from 'lucide-react';

const TABS = [
  { href: '/', label: '홈', icon: Home },
  { href: '/learn', label: '학습', icon: GraduationCap },
  { href: '/profile', label: '프로필', icon: User },
];

export function TabBar() {
  const pathname = usePathname() ?? '/';
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface/90 backdrop-blur-lg">
      <div className="mx-auto flex max-w-md items-stretch justify-around px-2 pb-[env(safe-area-inset-bottom)]">
        {TABS.map(({ href, label, icon: Icon }) => {
          const active = href === '/' ? pathname === '/' : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={`flex flex-1 flex-col items-center gap-0.5 py-2.5 text-[11px] font-bold transition-colors
                ${active ? 'text-accent' : 'text-text-muted hover:text-text'}`}
            >
              <Icon className="h-[22px] w-[22px]" strokeWidth={active ? 2.5 : 2} />
              {label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
