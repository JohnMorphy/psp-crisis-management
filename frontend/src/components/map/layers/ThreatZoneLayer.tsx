import { GeoJSON } from 'react-leaflet'
import type { Feature } from 'geojson'
import type { PathOptions } from 'leaflet'
import { useLayerData } from '../../../hooks/useLayerData'
import type { GeoJsonCollection, ThreatZoneProperties } from '../../../types/gis'
import { useMapStore } from '../../../store/mapStore'

const LEVEL_COLORS: Record<string, string> = {
  czerwony:     '#EF4444',
  zolty:        '#F59E0B',
  pomaranczowy: '#F97316',
}

function threatStyle(feature?: Feature): PathOptions {
  const level = (feature?.properties as ThreatZoneProperties | null)?.poziom
  const color = (level && LEVEL_COLORS[level]) ? LEVEL_COLORS[level] : '#F59E0B'
  return {
    color,
    fillColor: color,
    fillOpacity: 0.25,
    weight: 2,
  }
}

function ThreatZoneLayer() {
  const isVisible = useMapStore((state) => state.activeLayers['L-03'] ?? true)
  const { data } = useLayerData<GeoJsonCollection<ThreatZoneProperties>>('L-03')

  if (!isVisible || !data?.features?.length) return null

  return (
    <GeoJSON
      key={data.ostatnia_aktualizacja}
      data={data as unknown as GeoJSON.GeoJsonObject}
      style={threatStyle}
    />
  )
}

export default ThreatZoneLayer
