'use client'

import React, { useEffect, useState } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import Link from 'next/link'
import {
  LayoutDashboard,
  ArrowLeftRight,
  ShieldAlert,
  BookOpen,
  Building2,
  Settings,
  LogOut,
  Menu,
  X,
  ShieldCheck,
  Bell,
  ChevronDown,
} from 'lucide-react'
import { useAuthStore, selectIsSuperAdmin } from '@/store/auth-store'
import { api } from '@/lib/api-client'
import { cn } from '@/lib/utils'
import { ThemeToggle } from '@/components/ui/theme-toggle'

// â”€â”€â”€ Nav items â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

interface NavItem {
  label: string
  href:  string
  icon:  React.ElementType
  superAdminOnly?: boolean
}

const navItems: NavItem[] = [
  { label: 'Overview',        href: '/overview',    icon: LayoutDashboard },
  { label: 'Transactions',    href: '/transactions', icon: ArrowLeftRight  },
  { label: 'Fraud Detection', href: '/fraud-alerts', icon: ShieldAlert     },
  { label: 'Audit Logs',      href: '/audit-trail',  icon: BookOpen        },
  { label: 'Tenants',         href: '/tenants',      icon: Building2,      superAdminOnly: true },
  { label: 'Settings',        href: '/settings',     icon: Settings        },
]

// â”€â”€â”€ Sidebar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function Sidebar({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const pathname    = usePathname()
  const isSuperAdmin = useAuthStore(selectIsSuperAdmin)
  const visible     = navItems.filter((item) => !item.superAdminOnly || isSuperAdmin)

  const mainNav     = visible.filter((i) => !['Settings'].includes(i.label))
  const bottomNav   = visible.filter((i) => ['Settings'].includes(i.label))

  const NavLink = ({ item }: { item: NavItem }) => {
    const Icon     = item.icon
    const isActive =
      item.href === '/overview'
        ? pathname === '/overview'
        : pathname.startsWith(item.href)

    return (
      <Link
        href={item.href}
        onClick={onClose}
        className={cn(
          'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-[13px] font-medium transition-all duration-200',
          isActive 
            ? 'text-blue-600 bg-blue-50' 
            : 'hover:bg-gray-50'
        )}
        style={{ color: isActive ? 'var(--color-primary)' : 'var(--text-secondary)' }}
      >
        <span
          className={cn(
            "flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg transition-all duration-200",
            isActive ? "bg-blue-600 text-white" : "bg-gray-100"
          )}
          style={{ color: isActive ? 'white' : 'var(--text-muted)' }}
        >
          <Icon size={14} />
        </span>
        <span>{item.label}</span>
      </Link>
    )
  }

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/20 lg:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar panel */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex w-60 flex-col transition-transform duration-200',
          'lg:translate-x-0 lg:static lg:z-auto',
          isOpen ? 'translate-x-0' : '-translate-x-full'
        )}
        style={{
          background: 'var(--sidebar-bg)',
          borderRight: '1px solid var(--sidebar-border)',
        }}
      >
        {/* Logo */}
        <div className="flex h-[60px] items-center justify-between px-5"
          style={{ borderBottom: '1px solid var(--sidebar-border)' }}>
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600">
              <ShieldCheck size={17} className="text-white" />
            </div>
            <div>
              <p className="text-sm font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>Civic Savings</p>
              <p className="text-[10px] leading-tight" style={{ color: 'var(--text-muted)' }}>Security Platform</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 transition-colors hover:bg-gray-100 lg:hidden"
            style={{ color: 'var(--text-muted)' }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Nav */}
        <div className="flex flex-1 flex-col overflow-y-auto px-3 py-4">
          {/* Main navigation */}
          <div className="space-y-0.5">
            <p className="mb-2 px-3 text-[10px] font-semibold uppercase tracking-widest" style={{ color: 'var(--text-muted)' }}>
              Main
            </p>
            {mainNav.map((item) => <NavLink key={item.href} item={item} />)}
          </div>

          {/* Bottom navigation */}
          <div className="mt-auto space-y-0.5 pt-4" style={{ borderTop: '1px solid var(--sidebar-border)' }}>
            {bottomNav.map((item) => <NavLink key={item.href} item={item} />)}
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3" style={{ borderTop: '1px solid var(--sidebar-border)' }}>
          <div className="flex items-center gap-2">
            <div className="dot-live" />
            <p className="text-[10px]" style={{ color: 'var(--text-muted)' }}>v1.0.0 · Blockchain Secured</p>
          </div>
        </div>
      </aside>
    </>
  )
}

// â”€â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function Header({ onMenuOpen }: { onMenuOpen: () => void }) {
  const router    = useRouter()
  const pathname  = usePathname()
  const user      = useAuthStore((s) => s.user)
  const tenantId  = useAuthStore((s) => s.tenantId)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const [userMenuOpen, setUserMenuOpen] = useState(false)

  const currentPage =
    navItems.find((n) =>
      n.href === '/overview' ? pathname === '/overview' : pathname.startsWith(n.href)
    )?.label ?? 'Dashboard'

  const handleLogout = async () => {
    try { await api.auth.logout() } catch { /* ignore */ }
    clearAuth()
    router.push('/login')
  }

  const initials = user
    ? `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase()
    : 'U'

  return (
    <header
      className="flex h-[60px] flex-shrink-0 items-center justify-between gap-4 px-5 backdrop-blur-sm"
      style={{ borderBottom: '1px solid var(--header-border)', background: 'var(--header-bg)' }}
    >
      {/* Left */}
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuOpen}
          className="rounded-md p-1.5 transition-colors hover:bg-[var(--bg-subtle)] lg:hidden"
          style={{ color: 'var(--text-muted)' }}
          aria-label="Open navigation"
        >
          <Menu size={18} />
        </button>
        <div>
          <h1 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{currentPage}</h1>
          {tenantId && (
            <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{tenantId}</p>
          )}
        </div>
      </div>

      {/* Right */}
      <div className="flex items-center gap-1.5">
        {/* Theme toggle */}
        <ThemeToggle />

        {/* Tenant badge */}
        {tenantId && (
          <span className="hidden sm:inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-medium"
            style={{ background: 'var(--color-primary-light)', border: '1px solid var(--color-primary-border)', color: 'var(--color-primary)' }}>
            <Building2 size={10} />
            {tenantId}
          </span>
        )}

        {/* Bell */}
        <button
          className="relative rounded-lg p-2 transition-colors hover:bg-[var(--bg-subtle)]"
          style={{ color: 'var(--text-muted)' }}
          aria-label="Notifications"
        >
          <Bell size={16} />
        </button>

        {/* User menu */}
        <div className="relative">
          <button
            onClick={() => setUserMenuOpen((v) => !v)}
            className={cn(
              'flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors',
              userMenuOpen ? 'bg-[var(--bg-subtle)]' : 'hover:bg-[var(--bg-subtle)]'
            )}
          >
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-blue-600 text-xs font-semibold text-white">
              {initials}
            </div>
            <span className="hidden sm:block max-w-[100px] truncate text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
              {user ? `${user.firstName} ${user.lastName}` : 'User'}
            </span>
            <ChevronDown size={13} style={{ color: 'var(--text-muted)' }} />
          </button>

          {userMenuOpen && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setUserMenuOpen(false)} />
              <div
                className="absolute right-0 top-full z-20 mt-1.5 w-56 rounded-xl py-1 shadow-xl animate-scale-in"
                style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)' }}
              >
                <div className="px-4 py-3" style={{ borderBottom: '1px solid var(--border)' }}>
                  <p className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {user ? `${user.firstName} ${user.lastName}` : '—'}
                  </p>
                  <p className="truncate text-xs" style={{ color: 'var(--text-muted)' }}>{user?.email ?? '—'}</p>
                  <div className="mt-2 flex flex-wrap gap-1">
                    {user?.roles.map((r) => (
                      <span key={r}
                        className="rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide"
                        style={{ background: 'var(--color-primary-light)', color: 'var(--color-primary)', border: '1px solid var(--color-primary-border)' }}>
                        {r.replace('ROLE_', '')}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="p-1">
                  <button
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-red-600 transition-colors hover:bg-red-50"
                  >
                    <LogOut size={14} />
                    Sign out
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

// â”€â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router          = useRouter()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isLoading       = useAuthStore((s) => s.isLoading)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login')
    }
  }, [isAuthenticated, isLoading, router])

  if (!isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center" style={{ background: 'var(--bg-body)' }}>
        <div className="h-7 w-7 animate-spin rounded-full border-2 border-[var(--border)] border-t-blue-600" />
      </div>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: 'var(--bg-body)' }}>
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="flex flex-1 flex-col overflow-hidden">
        <Header onMenuOpen={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-y-auto p-6">
          {children}
        </main>
      </div>
    </div>
  )
}
