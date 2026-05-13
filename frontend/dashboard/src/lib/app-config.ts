export const APP_NAME = process.env.NEXT_PUBLIC_APP_NAME || 'BBSS'
export const APP_SUBTITLE =
  process.env.NEXT_PUBLIC_APP_SUBTITLE || 'Civic Savings'
export const APP_DESCRIPTION =
  process.env.NEXT_PUBLIC_APP_DESCRIPTION ||
  'Enterprise-grade blockchain-backed banking security platform with real-time fraud detection and immutable audit trails.'
export const APP_VERSION = process.env.NEXT_PUBLIC_APP_VERSION || 'dev'

export function formatAppVersion() {
  return `v${APP_VERSION}`
}
