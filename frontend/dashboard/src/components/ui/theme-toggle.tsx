'use client'

import { Sun, Moon } from 'lucide-react'
import { useThemeStore } from '@/store/theme-store'

export function ThemeToggle() {
  const { theme, toggleTheme } = useThemeStore()
  return (
    <button
      onClick={toggleTheme}
      className="flex h-8 w-8 items-center justify-center rounded-lg transition-all duration-200 hover:scale-105"
      style={{ color: 'var(--text-muted)', border: '1px solid var(--border)', background: 'var(--bg-subtle)' }}
      aria-label="Toggle theme"
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {theme === 'dark'
        ? <Sun size={14} />
        : <Moon size={14} />}
    </button>
  )
}
