import { CircleMarker, Popup } from 'react-leaflet'
import { useLayerData } from '../../../hooks/useLayerData'
import DPSPopup from '../DPSPopup'
import type { GeoJsonCollection, FacilityProperties, IkeCategory } from '../../../types/gis'
import { useMapStore } from '../../../store/mapStore'

const IKE_COLORS: Record<IkeCategory, string> = {
  czerwony: '#EF4444',
  zolty:    '#F59E0B',
  zielony:  '#22C55E',
}

const FALLBACK_COLOR = '#6B7280'

function ikeColor(category: IkeCategory | null): string {
  return category ? (IKE_COLORS[category] ?? FALLBACK_COLOR) : FALLBACK_COLOR
}

function DPSLayer() {
  const isVisible = useMapStore((state) => state.activeLayers['L-01'] ?? true)
  const { data } = useLayerData<GeoJsonCollection<FacilityProperties>>('L-01')

  if (!isVisible || !data?.features) return null

  return data.features.map((feature) => {
    const [lng, lat] = feature.geometry.coordinates as [number, number]
    const props = feature.properties
    const color = ikeColor(props.ike_kategoria)

    return (
      <CircleMarker
        key={props.kod}
        center={[lat, lng]}
        radius={8}
        pane="markerPane"
        pathOptions={{
          color: color,
          fillColor: color,
          fillOpacity: 0.85,
          weight: 2,
        }}
      >
        <Popup maxWidth={300} minWidth={240}>
          <DPSPopup properties={props} />
        </Popup>
      </CircleMarker>
    )
  })
}

export default DPSLayer
