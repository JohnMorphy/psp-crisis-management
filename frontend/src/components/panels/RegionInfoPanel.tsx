import { useMapStore } from '../../store/mapStore'
import { useLayerData } from '../../hooks/useLayerData'
import type { GeoJsonCollection, FacilityProperties } from '../../types/gis'

const POZIOM_LABELS: Record<string, string> = {
  wojewodztwo: 'Województwo',
  powiat: 'Powiat',
  gmina: 'Gmina',
}

function RegionInfoPanel() {
  const selectedRegion = useMapStore((state) => state.selectedRegion)
  const { data } = useLayerData<GeoJsonCollection<FacilityProperties>>('L-01')

  if (!selectedRegion) return null

  const { name, kod_teryt, poziom, properties } = selectedRegion
  const isLubelskie = kod_teryt ? kod_teryt.startsWith('06') : false

  const allFacilities = data?.features ?? []
  const regionFacilities = allFacilities.filter(
    (f) => f.properties.powiat === name
  )
  const totalPodopieczni = regionFacilities.reduce(
    (sum, f) => sum + (f.properties.liczba_podopiecznych ?? 0),
    0
  )
  const czerwonych = regionFacilities.filter(
    (f) => f.properties.ike_kategoria === 'czerwony'
  ).length

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

      {poziom && !isLubelskie ? (
        <p className="text-xs text-gray-500">
          Brak danych placówek dla tego regionu
        </p>
      ) : (
        <div className="space-y-1.5 text-sm">
          <div className="flex justify-between text-gray-300">
            <span>Placówki:</span>
            <span className="font-medium text-white">{regionFacilities.length}</span>
          </div>
          <div className="flex justify-between text-gray-300">
            <span>Podopieczni:</span>
            <span className="font-medium text-white">{totalPodopieczni}</span>
          </div>
          {czerwonych > 0 && (
            <div className="flex justify-between text-gray-300">
              <span>IKE czerwony:</span>
              <span className="font-medium text-red-400">{czerwonych}</span>
            </div>
          )}
          {regionFacilities.length === 0 && (
            <p className="text-xs text-gray-500">
              Brak danych dla wybranego regionu
            </p>
          )}
        </div>
      )}

      {!!properties['kod_nadrzedny'] && (
        <div className="text-xs text-gray-500">
          Jednostka nadrzędna: <span className="font-mono">{String(properties['kod_nadrzedny'])}</span>
        </div>
      )}
    </div>
  )
}

export default RegionInfoPanel
