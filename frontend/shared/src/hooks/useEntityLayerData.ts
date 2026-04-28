import { useQuery } from '@tanstack/react-query'
import { useApi } from '../services/ApiContext'
import type { EntityFeatureProperties, GeoJsonCollection } from '../types/gis'

export function useEntityLayerData(categoryCodes: string[]) {
  const api = useApi()
  const categoryParam = categoryCodes.join(',')

  return useQuery<GeoJsonCollection<EntityFeatureProperties>>({
    queryKey: ['layers', 'L-01', categoryParam],
    queryFn: async () => {
      const response = await api.get<GeoJsonCollection<EntityFeatureProperties>>('/api/layers/L-01', {
        params: categoryParam ? { category: categoryParam } : {},
      })
      return response.data
    },
    staleTime: 60_000,
  })
}
