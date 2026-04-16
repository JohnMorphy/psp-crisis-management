import { CircleMarker, Popup } from 'react-leaflet'
import { useLayerData } from '../../../hooks/useLayerData'
import DPSPopup from '../DPSPopup'
import type { GeoJsonCollection, FacilityProperties, IkeCategory } from '../../../types/gis'

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
  const { data } = useLayerData<GeoJsonCollection<FacilityProperties>>('L-01')

  if (!data?.features) return null

  return data.features.map((feature) => {
    const [lng, lat] = feature.geometry.coordinates as [number, number]
    const props = feature.properties
    const color = ikeColor(props.ike_kategoria)

    return (
      <CircleMarker
        key={props.kod}
        center={[lat, lng]}
        radius={8}
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
