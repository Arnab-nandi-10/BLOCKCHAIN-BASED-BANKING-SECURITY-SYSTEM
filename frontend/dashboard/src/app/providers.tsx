'use client'

import React, { useState, useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import * as Toast from '@radix-ui/react-toast'
import { useThemeStore } from '@/store/theme-store'

function ThemeApplier() {
  const theme = useThemeStore((s) => s.theme)
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])
  return null
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () => new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          retry: 1,
          refetchOnWindowFocus: false,
        },
        mutations: { retry: 0 },
      },
    })
  )

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeApplier />
      <Toast.Provider swipeDirection="right" duration={5000}>
        {children}
        <Toast.Viewport
          className="fixed bottom-5 right-5 z-[100] flex flex-col gap-2 w-80 max-w-full outline-none"
        />
      </Toast.Provider>
    </QueryClientProvider>
  )
}
