import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import AdminBoundaryLayer from './layers/AdminBoundaryLayer'
import DPSLayer from './layers/DPSLayer'
import ThreatZoneLayer from './layers/ThreatZoneLayer'

const MAP_CENTER: [number, number] = [51.25, 22.57]
const INITIAL_ZOOM = 9

function MapContainer() {
  return (
    <LeafletMapContainer
      center={MAP_CENTER}
      zoom={INITIAL_ZOOM}
      style={{ height: '100%', width: '100%' }}
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      />
      <AdminBoundaryLayer />
      <ThreatZoneLayer />
      <DPSLayer />
    </LeafletMapContainer>
  )
}

export default MapContainer
