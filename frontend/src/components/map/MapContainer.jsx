import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import AdminBoundaries from './AdminBoundaries'
import DPSLayer from './layers/DPSLayer'
import ZagrozeniaLayer from './layers/ZagrozeniaLayer'

const LUBLIN_CENTER = [51.25, 22.57]
const INITIAL_ZOOM = 9

function MapContainer() {
  return (
    <LeafletMapContainer
      center={LUBLIN_CENTER}
      zoom={INITIAL_ZOOM}
      style={{ height: '100%', width: '100%' }}
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      />
      <AdminBoundaries />
      <ZagrozeniaLayer />
      <DPSLayer />
    </LeafletMapContainer>
  )
}

export default MapContainer
