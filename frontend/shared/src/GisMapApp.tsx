import { useMemo } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiProvider } from './services/ApiContext'
import AppShell from './components/layout/AppShell'

export interface GisMapAppProps {
  apiBaseUrl: string
  initialZoom?: number
}

export function GisMapApp({ apiBaseUrl }: GisMapAppProps) {
  const queryClient = useMemo(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 60_000, retry: 1 } }
  }), [])

  return (
    <QueryClientProvider client={queryClient}>
      <ApiProvider baseUrl={apiBaseUrl}>
        <AppShell />
      </ApiProvider>
    </QueryClientProvider>
  )
}
