import { useEffect, useRef } from 'react'
import { GeoJSON } from 'react-leaflet'
import type { Layer } from 'leaflet'
import { useAdminBoundaries } from '../../../hooks/useAdminBoundaries'
import { useMapStore } from '../../../store/mapStore'

const SELECTED_STYLE = { color: '#60A5FA', weight: 2.5, fillOpacity: 0.15 }

const LAYER_STYLES = {
  'L-08': { color: '#6366F1', weight: 2, fillOpacity: 0.04 },
  'L-09': { color: '#4B5563', weight: 1, fillOpacity: 0.03 },
  'L-10': { color: '#374151', weight: 0.5, fillOpacity: 0.02 },
}

type BoundaryLayerId = 'L-08' | 'L-09' | 'L-10'

interface SingleBoundaryLayerProps {
  layerId: BoundaryLayerId
  kodWoj?: string
}

function SingleBoundaryLayer({ layerId, kodWoj }: SingleBoundaryLayerProps) {
  const isVisible = useMapStore((state) => state.activeLayers[layerId] ?? false)
  const setSelectedRegion = useMapStore((state) => state.setSelectedRegion)
  const setSelectedFeatureForLayer = useMapStore((state) => state.setSelectedFeatureForLayer)
  const selectedFeatureId = useMapStore((state) => state.selectedFeatureByLayer[layerId])
  const { data } = useAdminBoundaries(layerId, kodWoj, isVisible)
  const selectedLayerRef = useRef<Layer | null>(null)
  const defaultStyle = LAYER_STYLES[layerId]

  useEffect(() => {
    if (selectedFeatureId === null && selectedLayerRef.current) {
      ;(selectedLayerRef.current as unknown as { setStyle: (s: object) => void }).setStyle(defaultStyle)
      selectedLayerRef.current = null
    }
  }, [selectedFeatureId, defaultStyle])

  if (!isVisible || !data?.features?.length) return null

  const onEachFeature = (feature: unknown, layer: Layer) => {
    layer.on('click', () => {
      if (selectedLayerRef.current && selectedLayerRef.current !== layer) {
        ;(selectedLayerRef.current as unknown as { setStyle: (s: object) => void }).setStyle(defaultStyle)
      }
      ;(layer as unknown as { setStyle: (s: object) => void }).setStyle(SELECTED_STYLE)
      selectedLayerRef.current = layer

      const props = (feature as { properties?: Record<string, unknown> })?.properties ?? {}
      const name = String(props['nazwa'] ?? props['NAME_2'] ?? props['NAZWA'] ?? '')
      const kod_teryt = String(props['kod_teryt'] ?? '')
      const poziom = String(props['poziom'] ?? '')

      setSelectedFeatureForLayer(layerId, kod_teryt)
      setSelectedRegion({ name, kod_teryt, poziom, properties: props })
    })
  }

  return (
    <GeoJSON
      key={`${layerId}-${data.feature_count}`}
      data={data as unknown as GeoJSON.GeoJsonObject}
      style={defaultStyle}
      onEachFeature={onEachFeature as never}
    />
  )
}

function AdminBoundaryLayer() {
  const { selectedRegion } = useMapStore()

  return (
    <>
      <SingleBoundaryLayer layerId="L-08" />
      <SingleBoundaryLayer layerId="L-09" />
      <SingleBoundaryLayer
        layerId="L-10"
        kodWoj={selectedRegion?.kod_teryt?.substring(0, 2)}
      />
    </>
  )
}

export default AdminBoundaryLayer
