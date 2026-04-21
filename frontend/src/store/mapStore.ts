import { create } from 'zustand'

type ActiveLayers = Record<string, boolean>
type EntityCategoryFilters = Record<string, boolean>

export type SelectedRegion = {
  name: string
  kod_teryt?: string
  poziom?: string
  properties: Record<string, unknown>
} | null

type MapStore = {
  activeLayers: ActiveLayers
  toggleLayer: (id: string, value: boolean) => void
  entityCategoryFilters: EntityCategoryFilters
  setEntityCategoryFilter: (code: string, value: boolean) => void
  hydrateEntityCategoryFilters: (codes: string[]) => void
  resetEntityCategoryFilters: () => void
  selectedRegion: SelectedRegion
  setSelectedRegion: (region: SelectedRegion) => void
  isPanelCollapsed: boolean
  togglePanel: () => void
}

export const useMapStore = create<MapStore>()((set) => ({
  activeLayers: {
    'L-00': true,
    'L-01': true,
    'L-03': true,
    'L-08': true,
    'L-09': false,
    'L-10': false,
  },
  toggleLayer: (id, value) =>
    set((state) => ({
      activeLayers: {
        ...state.activeLayers,
        [id]: value,
      },
    })),
  entityCategoryFilters: {},
  setEntityCategoryFilter: (code, value) =>
    set((state) => ({
      entityCategoryFilters: {
        ...state.entityCategoryFilters,
        [code]: value,
      },
    })),
  hydrateEntityCategoryFilters: (codes) =>
    set((state) => {
      const next = { ...state.entityCategoryFilters }
      for (const code of codes) {
        if (!(code in next)) {
          next[code] = true
        }
      }
      return { entityCategoryFilters: next }
    }),
  resetEntityCategoryFilters: () =>
    set((state) => ({
      entityCategoryFilters: Object.fromEntries(
        Object.keys(state.entityCategoryFilters).map((code) => [code, true])
      ),
    })),
  selectedRegion: null,
  setSelectedRegion: (region) => set({ selectedRegion: region }),
  isPanelCollapsed: false,
  togglePanel: () =>
    set((state) => ({ isPanelCollapsed: !state.isPanelCollapsed })),
}))
