import { useQuery } from '@tanstack/react-query'
import { useApi } from '../../services/ApiContext'
import { useMapStore } from '../../store/mapStore'

interface LayerConfig {
  id: string
  nazwa: string
  domyslnie_wlaczona: boolean
  ostatnia_aktualizacja: string | null
  status: string
}

function LayerControlPanel() {
  const api = useApi()
  const activeLayers = useMapStore().activeLayers;
  const toggleLayer = useMapStore().toggleLayer;

  // const { activeLayers, toggleLayer } = useMapStore()

  const { data: layers = [], isLoading } = useQuery<LayerConfig[]>({
    queryKey: ['layers'],
    queryFn: () => api.get<LayerConfig[]>('/api/layers').then((r) => r.data),
    staleTime: 60_000,
  })

  return (
    <div className="p-4 space-y-3">
      <h2 className="text-base font-semibold text-white">Warstwy</h2>

      {isLoading && (
        <p className="text-xs text-gray-500">Ładowanie warstw…</p>
      )}

      {layers.map((layer) => {
        const isActive = activeLayers[layer.id] ?? layer.domyslnie_wlaczona

        return (
          <div key={layer.id} className="space-y-0.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm text-gray-200 truncate">{layer.nazwa}</span>

              <button
                onClick={() => toggleLayer(layer.id, !isActive)}
                aria-label={`Przełącz warstwę ${layer.nazwa}`}
                className={`shrink-0 w-11 h-6 flex items-center rounded-full p-1 transition-colors ${
                  isActive ? 'bg-blue-500' : 'bg-gray-600'
                }`}
              >
                <div
                  className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform ${
                    isActive ? 'translate-x-5' : ''
                  }`}
                />
              </button>
            </div>

            {layer.ostatnia_aktualizacja && (
              <p className="text-xs text-gray-500">
                {new Date(layer.ostatnia_aktualizacja).toLocaleString('pl-PL')}
              </p>
            )}
          </div>
        )
      })}
    </div>
  )
}

export default LayerControlPanel
