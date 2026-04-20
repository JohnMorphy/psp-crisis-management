import Header from './Header'
import MapContainer from '../map/MapContainer'
import LayerControlPanel from '../panels/LayerControlPanel'
import RegionInfoPanel from '../panels/RegionInfoPanel'
import { useMapStore } from '../../store/mapStore'

function AppShell() {
  const { isPanelCollapsed, togglePanel } = useMapStore()

  return (
    <div className="flex flex-col h-screen bg-gray-900 text-white">
      <Header />
      <div className="flex flex-1 overflow-hidden relative">
        <main className="w-full bg-gray-950">
          <MapContainer />
        </main>

        {!isPanelCollapsed && (
          <aside className="absolute right-0 top-0 h-full w-80 bg-gray-800/95 backdrop-blur-sm overflow-y-auto border-l border-gray-700 flex flex-col z-[1000]">
            <LayerControlPanel />
            <RegionInfoPanel />
            {/* ScenarioPanel, Top10Panel, DecisionPanel — kolejne zadania */}
          </aside>
        )}
      </div>

      <footer className="flex items-center gap-4 px-4 py-2 bg-gray-800 border-t border-gray-700 text-sm text-gray-400 shrink-0">
        <button
          onClick={togglePanel}
          className="hover:text-white transition-colors"
        >
          {isPanelCollapsed ? '▶ Rozwiń panel' : '◀ Zwiń panel'}
        </button>
        <button className="hover:text-white transition-colors">🗺 Reset widoku</button>
        <button className="hover:text-white transition-colors">⊕ Kalkulatory</button>
      </footer>
    </div>
  )
}

export default AppShell
