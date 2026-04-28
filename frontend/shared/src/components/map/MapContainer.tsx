import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import AdminBoundaryLayer from './layers/AdminBoundaryLayer'
import EntityLayer from './layers/EntityLayer'

const MAP_CENTER: [number, number] = [52.1, 19.4]
const INITIAL_ZOOM = 6
const MAP_MIN_ZOOM = 5
const MAP_MAX_BOUNDS: [[number, number], [number, number]] = [[48.0, 13.0], [55.5, 26.5]]

function MapContainer() {
  return (
    <LeafletMapContainer
      center={MAP_CENTER}
      zoom={INITIAL_ZOOM}
      minZoom={MAP_MIN_ZOOM}
      maxBounds={MAP_MAX_BOUNDS}
      maxBoundsViscosity={1.0}
      style={{ height: '100%', width: '100%' }}
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      />
      <AdminBoundaryLayer />
      <EntityLayer />
    </LeafletMapContainer>
  )
}

export default MapContainer
