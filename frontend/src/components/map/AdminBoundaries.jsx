import { useRef } from 'react'
import { GeoJSON } from 'react-leaflet'
import { useLayerData } from '../../hooks/useLayerData'

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
  const { data } = useLayerData('L-00')
  const selectedLayerRef = useRef(null)

  if (!data?.features) return null

  const onEachFeature = (_feature, layer) => {
    layer.on('click', () => {
      if (selectedLayerRef.current) {
        selectedLayerRef.current.setStyle(DEFAULT_STYLE)
      }
      layer.setStyle(SELECTED_STYLE)
      selectedLayerRef.current = layer
    })
  }

  return (
    <GeoJSON
      data={data}
      style={DEFAULT_STYLE}
      onEachFeature={onEachFeature}
    />
  )
}

export default AdminBoundaries
