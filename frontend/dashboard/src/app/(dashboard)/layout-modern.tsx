'use client'

import React from 'react'
import { DashboardLayout } from '@/components/layout/dashboard-layout'

// ══════════════════════════════════════════════════════════════════════════════
// Dashboard Layout Wrapper
// Modern SaaS layout with sidebar navigation and top bar
// ══════════════════════════════════════════════════════════════════════════════

export default function Layout({ children }: { children: React.ReactNode }) {
  return <DashboardLayout>{children}</DashboardLayout>
}
