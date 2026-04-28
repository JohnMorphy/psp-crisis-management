import { useQuery } from '@tanstack/react-query'
import { useMapStore } from '../../store/mapStore'
import { useApi } from '../../services/ApiContext'
import type { EntitySummary } from '../../types/gis'

const POZIOM_LABELS: Record<string, string> = {
  wojewodztwo: 'Wojewodztwo',
  powiat: 'Powiat',
  gmina: 'Gmina',
}

function RegionInfoPanel() {
  const api = useApi()
  const selectedRegion = useMapStore((state) => state.selectedRegion)

  const { data } = useQuery<EntitySummary>({
    queryKey: ['entity-summary', selectedRegion?.kod_teryt],
    queryFn: () =>
      api.get<EntitySummary>('/api/entities/summary', {
        params: { kod_teryt: selectedRegion?.kod_teryt },
      }).then((response) => response.data),
    enabled: Boolean(selectedRegion?.kod_teryt),
    staleTime: 30_000,
  })

  if (!selectedRegion) return null

  const { name, kod_teryt, poziom, properties } = selectedRegion
  const poziomLabel = poziom ? (POZIOM_LABELS[poziom] ?? poziom) : null

  return (
    <div className="p-4 border-t border-gray-700 space-y-3">
      <h2 className="text-base font-semibold text-white truncate">
        {name || 'Wybrany region'}
      </h2>

      {(kod_teryt || poziomLabel) && (
        <div className="space-y-1 text-xs text-gray-400">
          {poziomLabel && (
            <div className="flex justify-between">
              <span>Poziom:</span>
              <span className="text-gray-300">{poziomLabel}</span>
            </div>
          )}
          {kod_teryt && (
            <div className="flex justify-between">
              <span>TERYT:</span>
              <span className="font-mono text-gray-300">{kod_teryt}</span>
            </div>
          )}
        </div>
      )}

      {data ? (
        <div className="space-y-1.5 text-sm">
          <div className="flex justify-between text-gray-300">
            <span>Podmioty:</span>
            <span className="font-medium text-white">{data.total_entities}</span>
          </div>
          <div className="flex justify-between text-gray-300">
            <span>Zweryfikowane:</span>
            <span className="font-medium text-emerald-400">{data.verified_entities}</span>
          </div>
          <div className="flex justify-between text-gray-300">
            <span>Do weryfikacji:</span>
            <span className="font-medium text-amber-400">{data.needs_review_entities}</span>
          </div>
          {data.categories.slice(0, 4).map((category) => (
            <div key={category.code} className="flex justify-between text-gray-300">
              <span>{category.name}:</span>
              <span className="font-medium text-white">{category.count}</span>
            </div>
          ))}
          {data.total_entities === 0 && (
            <p className="text-xs text-gray-500">Brak podmiotow dla wybranego regionu</p>
          )}
        </div>
      ) : (
        <p className="text-xs text-gray-500">Brak danych podsumowujacych dla wybranego regionu</p>
      )}

      {!!properties['kod_nadrzedny'] && (
        <div className="text-xs text-gray-500">
          Jednostka nadrzedna: <span className="font-mono">{String(properties['kod_nadrzedny'])}</span>
        </div>
      )}
    </div>
  )
}

export default RegionInfoPanel
