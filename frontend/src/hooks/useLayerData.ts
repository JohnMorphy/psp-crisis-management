import { useQuery } from '@tanstack/react-query'
import type { UseQueryOptions } from '@tanstack/react-query'
import api from '../services/api'

export function useLayerData<T = unknown>(
  layerId: string,
  options?: Omit<UseQueryOptions<T>, 'queryKey' | 'queryFn'>
) {
  return useQuery<T>({
    queryKey: ['layers', layerId],
    queryFn: () => api.get<T>(`/api/layers/${layerId}`).then(r => r.data),
    staleTime: 60_000,
    ...options,
  })
}
