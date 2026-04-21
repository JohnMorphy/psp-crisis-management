import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import type { EntityFeatureProperties, GeoJsonCollection } from '../types/gis'

export function useEntityLayerData(categoryCodes: string[]) {
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
