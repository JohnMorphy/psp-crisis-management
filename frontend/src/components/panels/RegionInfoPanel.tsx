import { useMapStore } from '../../store/mapStore'
import { useLayerData } from '../../hooks/useLayerData'
import type { GeoJsonCollection, FacilityProperties } from '../../types/gis'

function RegionInfoPanel() {
  const selectedRegion = useMapStore((state) => state.selectedRegion)
  const { data } = useLayerData<GeoJsonCollection<FacilityProperties>>('L-01')

  if (!selectedRegion) return null

  const { name } = selectedRegion
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

  return (
    <div className="p-4 border-t border-gray-700 space-y-3">
      <h2 className="text-base font-semibold text-white truncate">
        {name || 'Wybrany region'}
      </h2>

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
    </div>
  )
}

export default RegionInfoPanel
