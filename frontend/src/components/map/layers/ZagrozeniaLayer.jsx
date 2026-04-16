import { GeoJSON } from 'react-leaflet'
import { useLayerData } from '../../../hooks/useLayerData'

const LEVEL_COLORS = {
  czerwony:     '#EF4444',
  zolty:        '#F59E0B',
  pomaranczowy: '#F97316',
}

function zagrozenieStyle(feature) {
  const color = LEVEL_COLORS[feature.properties?.poziom] || '#F59E0B'
  return {
    color,
    fillColor: color,
    fillOpacity: 0.25,
    weight: 2,
  }
}

function ZagrozeniaLayer() {
  const { data } = useLayerData('L-03')

  if (!data?.features?.length) return null

  return (
    <GeoJSON
      key={data.ostatnia_aktualizacja}
      data={data}
      style={zagrozenieStyle}
    />
  )
}

export default ZagrozeniaLayer
