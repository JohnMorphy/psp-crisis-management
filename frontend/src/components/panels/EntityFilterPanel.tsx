import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../../services/api'
import { useMapStore } from '../../store/mapStore'
import type { EntityCategory } from '../../types/gis'

function EntityFilterPanel() {
  const filters = useMapStore((state) => state.entityCategoryFilters)
  const setEntityCategoryFilter = useMapStore((state) => state.setEntityCategoryFilter)
  const hydrateEntityCategoryFilters = useMapStore((state) => state.hydrateEntityCategoryFilters)
  const resetEntityCategoryFilters = useMapStore((state) => state.resetEntityCategoryFilters)

  const { data: categories = [] } = useQuery<EntityCategory[]>({
    queryKey: ['entity-categories'],
    queryFn: () => api.get<EntityCategory[]>('/api/entity-categories').then((response) => response.data),
    staleTime: 60_000,
  })

  useEffect(() => {
    if (categories.length > 0) {
      hydrateEntityCategoryFilters(categories.map((category) => category.code))
    }
  }, [categories, hydrateEntityCategoryFilters])

  if (categories.length === 0) return null

  return (
    <div className="p-4 border-t border-gray-700 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold text-white">Kategorie podmiotow</h2>
        <button
          onClick={resetEntityCategoryFilters}
          className="text-xs text-blue-300 hover:text-blue-200"
        >
          Reset
        </button>
      </div>

      <div className="space-y-2">
        {categories.map((category) => {
          const enabled = filters[category.code] ?? true

          return (
            <label key={category.code} className="flex items-start gap-2 text-sm text-gray-200">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(event) => setEntityCategoryFilter(category.code, event.target.checked)}
                className="mt-1"
              />
              <span className="flex-1">
                <span className="block">{category.name}</span>
                <span className="text-xs text-gray-500">{category.entity_count} rekordow</span>
              </span>
            </label>
          )
        })}
      </div>
    </div>
  )
}

export default EntityFilterPanel
