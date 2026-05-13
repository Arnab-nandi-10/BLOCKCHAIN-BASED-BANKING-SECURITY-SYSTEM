import type { Metadata, Viewport } from 'next'
import './globals.css'
import { Providers } from './providers'
import { APP_DESCRIPTION, APP_NAME, APP_SUBTITLE } from '@/lib/app-config'

export const metadata: Metadata = {
  title: {
    default: `${APP_NAME} — ${APP_SUBTITLE}`,
    template: `%s | ${APP_NAME}`,
  },
  description: APP_DESCRIPTION,
  robots: { index: false, follow: false },
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
