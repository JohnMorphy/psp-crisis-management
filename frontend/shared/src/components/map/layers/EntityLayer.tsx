import { CircleMarker, Popup } from 'react-leaflet'
import EntityPopup from '../EntityPopup'
import { useEntityLayerData } from '../../../hooks/useEntityLayerData'
import type { EntityFeatureProperties } from '../../../types/gis'
import { useMapStore } from '../../../store/mapStore'

const CATEGORY_COLORS: Record<string, string> = {
  social_care_dps: '#2563EB',
  prm_unit: '#DC2626',
  prm_cooperating_unit: '#7C3AED',
  state_forest_unit: '#15803D',
  water_management_unit: '#0891B2',
  hospital_public: '#EA580C',
}

const FALLBACK_COLOR = '#6B7280'

function EntityLayer() {
  const isVisible = useMapStore((state) => state.activeLayers['L-01'] ?? true)
  const categoryFilters = useMapStore((state) => state.entityCategoryFilters)
  const selectedCategories = Object.entries(categoryFilters)
    .filter(([, enabled]) => enabled)
    .map(([code]) => code)
  const { data } = useEntityLayerData(selectedCategories)

  if (!isVisible || !data?.features) return null

  return (
    <>
      {data.features.map((feature) => {
        const [lng, lat] = feature.geometry.coordinates as [number, number]
        const props = feature.properties as EntityFeatureProperties
        const color = CATEGORY_COLORS[props.category_code] ?? FALLBACK_COLOR

        return (
          <CircleMarker
            key={props.id}
            center={[lat, lng]}
            radius={8}
            pane="markerPane"
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.85,
              weight: 2,
            }}
          >
            <Popup maxWidth={320} minWidth={250}>
              <EntityPopup properties={props} />
            </Popup>
          </CircleMarker>
        )
      })}
    </>
  )
}

export default EntityLayer
