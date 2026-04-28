import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { type AxiosInstance } from 'axios'
import { createApiClient } from './api'

const ApiContext = createContext<AxiosInstance | null>(null)

export function ApiProvider({ baseUrl, children }: { baseUrl: string; children: ReactNode }) {
  const client = useMemo(() => createApiClient(baseUrl), [baseUrl])
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>
}

export function useApi(): AxiosInstance {
  const client = useContext(ApiContext)
  if (!client) throw new Error('useApi must be used within ApiProvider')
  return client
}
