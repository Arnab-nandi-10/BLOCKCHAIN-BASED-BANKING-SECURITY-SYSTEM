'use client'

import React from 'react'
import { Bell, Search, HelpCircle, Menu } from 'lucide-react'
import { cn } from '@/lib/utils'
import { ThemeToggle } from '@/components/ui/theme-toggle'

// ══════════════════════════════════════════════════════════════════════════════
// Modern Top Navigation
// Clean, minimal header inspired by Stripe and Vercel
// ══════════════════════════════════════════════════════════════════════════════

interface TopNavProps {
  className?: string
  onMenuClick?: () => void
}

export function TopNav({ className, onMenuClick }: TopNavProps) {
  return (
    <header className={cn(
      'sticky top-0 z-20 flex h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white/80 px-6 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-900/80',
      className
    )}>
      {/* Left: Mobile menu + Search */}
      <div className="flex flex-1 items-center gap-4">
        {/* Mobile menu button */}
        <button
          onClick={onMenuClick}
          className="flex items-center justify-center rounded-lg p-2 text-slate-600 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800 lg:hidden"
        >
          <Menu className="h-5 w-5" />
        </button>

        {/* Search bar */}
        <div className="relative hidden w-full max-w-md md:flex">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search transactions, alerts..."
            className={cn(
              'h-9 w-full rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-4 text-sm text-slate-900 placeholder-slate-400',
              'transition-all duration-200',
              'focus:border-primary-300 focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary-100',
              'dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:placeholder-slate-500',
              'dark:focus:border-primary-700 dark:focus:bg-slate-800 dark:focus:ring-primary-950/30'
            )}
          />
        </div>
      </div>

      {/* Right: Actions */}
      <div className="flex items-center gap-2">
        {/* Help */}
        <button className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800">
          <HelpCircle className="h-4 w-4" />
        </button>

        {/* Notifications */}
        <button className="relative flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800">
          <Bell className="h-4 w-4" />
          <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white dark:ring-slate-900" />
        </button>

        {/* Theme toggle */}
        <ThemeToggle />

        {/* Divider */}
        <div className="mx-2 h-6 w-px bg-slate-200 dark:bg-slate-700" />

        {/* User avatar */}
        <button className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700 ring-2 ring-white transition-all hover:ring-4 hover:ring-primary-50 dark:bg-primary-950/30 dark:text-primary-400 dark:ring-slate-900 dark:hover:ring-primary-950/50">
          AD
        </button>
      </div>
    </header>
  )
}
