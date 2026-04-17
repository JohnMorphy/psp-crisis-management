import api from '../services/api'
import { useQuery } from '@tanstack/react-query'
import type { GeoJsonCollection } from '../types/gis'

export function useAdminBoundaries(
  layerId: 'L-08' | 'L-09' | 'L-10',
  kodWoj?: string,
  enabled = true
) {
  const params: Record<string, string> = {}
  if (kodWoj) params['kod_woj'] = kodWoj

  const queryKey = kodWoj
    ? ['layers', layerId, kodWoj]
    : ['layers', layerId]

  return useQuery<GeoJsonCollection>({
    queryKey,
    queryFn: () =>
      api
        .get<GeoJsonCollection>(`/api/layers/${layerId}`, { params })
        .then((r) => r.data),
    staleTime: 60_000,
    enabled,
  })
}
