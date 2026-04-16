import { useQuery } from '@tanstack/react-query'
import api from '../services/api'

export function useLayerData(layerId, options = {}) {
  return useQuery({
    queryKey: ['layers', layerId],
    queryFn: () => api.get(`/api/layers/${layerId}`).then(r => r.data),
    staleTime: 60_000,
    ...options,
  })
}
