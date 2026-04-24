'use client'

import React, { useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  LayoutDashboard,
  ArrowLeftRight,
  ShieldAlert,
  ScrollText,
  Building2,
  Settings,
  ChevronRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ══════════════════════════════════════════════════════════════════════════════
// Modern Sidebar Navigation
// Clean, minimal design inspired by Linear and Vercel
// ══════════════════════════════════════════════════════════════════════════════

export interface NavItem {
  label: string
  href: string
  icon: React.ElementType
  badge?: string
  hidden?: boolean
}

const mainNavItems: NavItem[] = [
  { label: 'Overview', href: '/overview', icon: LayoutDashboard },
  { label: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { label: 'Fraud Intelligence', href: '/fraud-alerts', icon: ShieldAlert },
  { label: 'Audit Trail', href: '/audit-trail', icon: ScrollText },
]

const bottomNavItems: NavItem[] = [
  { label: 'Tenants', href: '/tenants', icon: Building2 },
  { label: 'Settings', href: '/settings', icon: Settings },
]

interface SidebarProps {
  className?: string
}

export function Sidebar({ className }: SidebarProps) {
  return (
    <aside className={cn(
      'fixed left-0 top-0 z-30 flex h-screen w-64 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900',
      className
    )}>
      {/* Logo / Brand */}
      <div className="flex h-16 items-center gap-3 border-b border-slate-200 px-6 dark:border-slate-800">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600">
          <svg className="h-5 w-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </div>
        <div>
          <h1 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Civic Savings
          </h1>
          <p className="text-[10px] text-slate-500 dark:text-slate-400">
            Security Platform
          </p>
        </div>
      </div>

      {/* Main Navigation */}
      <nav className="flex-1 space-y-1 overflow-y-auto p-4">
        <div className="space-y-0.5">
          {mainNavItems.map((item) => (
            <NavLink key={item.href} item={item} />
          ))}
        </div>

        {/* Divider */}
        <div className="my-4 border-t border-slate-200 dark:border-slate-800" />

        {/* Bottom Navigation */}
        <div className="space-y-0.5">
          {bottomNavItems.map((item) => (
            <NavLink key={item.href} item={item} />
          ))}
        </div>
      </nav>

      {/* User Section */}
      <div className="border-t border-slate-200 p-4 dark:border-slate-800">
        <div className="flex items-center gap-3 rounded-lg p-2 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-semibold text-primary-700 dark:bg-primary-950/30 dark:text-primary-400">
            AD
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
              Admin User
            </p>
            <p className="truncate text-xs text-slate-500 dark:text-slate-400">
              admin@civicsavings.io
            </p>
          </div>
        </div>
      </div>
    </aside>
  )
}

function NavLink({ item }: { item: NavItem }) {
  const pathname = usePathname()
  const isActive = item.href === '/overview'
    ? pathname === item.href
    : pathname.startsWith(item.href)
  const Icon = item.icon

  return (
    <Link
      href={item.href}
      className={cn(
        'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-150',
        isActive
          ? 'bg-primary-50 text-primary-700 dark:bg-primary-950/30 dark:text-primary-400'
          : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
      )}
    >
      <Icon className={cn(
        'h-4 w-4 transition-colors',
        isActive
          ? 'text-primary-600 dark:text-primary-400'
          : 'text-slate-400 group-hover:text-slate-600 dark:text-slate-500 dark:group-hover:text-slate-300'
      )} />
      <span className="flex-1">{item.label}</span>
      {item.badge && (
        <span className="rounded-md bg-primary-100 px-1.5 py-0.5 text-[10px] font-semibold text-primary-700 dark:bg-primary-950/50 dark:text-primary-400">
          {item.badge}
        </span>
      )}
      {isActive && (
        <ChevronRight className="h-3 w-3 text-primary-600 dark:text-primary-400" />
      )}
    </Link>
  )
}
