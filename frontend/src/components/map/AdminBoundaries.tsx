import { useRef } from 'react'
import { GeoJSON } from 'react-leaflet'
import type { Layer } from 'leaflet'
import { useLayerData } from '../../hooks/useLayerData'
import type { GeoJsonCollection } from '../../types/gis'
import { useMapStore } from '../../store/mapStore'

const DEFAULT_STYLE = {
  color: '#4B5563',
  weight: 1,
  fillOpacity: 0.04,
  fillColor: '#6B7280',
}

const SELECTED_STYLE = {
  color: '#60A5FA',
  weight: 2,
  fillOpacity: 0.15,
  fillColor: '#3B82F6',
}

function AdminBoundaries() {
  const isVisible = useMapStore((state) => state.activeLayers['L-00'] ?? true)
  const setSelectedRegion = useMapStore((state) => state.setSelectedRegion)
  const { data } = useLayerData<GeoJsonCollection>('L-00')
  const selectedLayerRef = useRef<Layer | null>(null)

  if (!isVisible || !data?.features) return null

  const onEachFeature = (feature: unknown, layer: Layer) => {
    layer.on('click', () => {
      if (selectedLayerRef.current) {
        (selectedLayerRef.current as unknown as { setStyle: (s: object) => void }).setStyle(DEFAULT_STYLE)
      }
      (layer as unknown as { setStyle: (s: object) => void }).setStyle(SELECTED_STYLE)
      selectedLayerRef.current = layer

      const props = (feature as { properties?: Record<string, unknown> })?.properties ?? {}
      const name =
        String(props['NAME_2'] ?? props['name'] ?? props['NAZWA'] ?? props['JPT_NAZWA_'] ?? '')
      setSelectedRegion({ name, properties: props })
    })
  }

  return (
    <GeoJSON
      data={data as unknown as GeoJSON.GeoJsonObject}
      style={DEFAULT_STYLE}
      onEachFeature={onEachFeature as never}
    />
  )
}

export default AdminBoundaries
