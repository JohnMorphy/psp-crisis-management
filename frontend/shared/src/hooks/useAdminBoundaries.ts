import { useApi } from '../services/ApiContext'
import { useQuery } from '@tanstack/react-query'
import type { GeoJsonCollection } from '../types/gis'

type LayerId = 'L-08' | 'L-09' | 'L-10'

export function useAdminBoundaries(
  layerId: LayerId,
  kodWoj?: string,
  enabled = true
) {
  const api = useApi()
  // enforce backend constraint
  const isAllowed =
    enabled && (layerId !== 'L-10' || Boolean(kodWoj))

  const params: Record<string, string> = {}
  if (layerId === 'L-10' && kodWoj) {
    params['kod_woj'] = kodWoj
  }

  const queryKey =
    layerId === 'L-10'
      ? ['layers', layerId, kodWoj]
      : ['layers', layerId]

  return useQuery<GeoJsonCollection>({
    queryKey,

    queryFn: async () => {
      // safety guard (extra protection)
      if (layerId === 'L-10' && !kodWoj) {
        throw new Error('kodWoj is required for L-10')
      }

      const res = await api.get<GeoJsonCollection>(
        `/api/layers/${layerId}`,
        { params }
      )

      return res.data
    },

    enabled: isAllowed,

    staleTime: 60_000,
  })
}
