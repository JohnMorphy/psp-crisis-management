import { create } from "zustand";

type ActiveLayers = Record<string, boolean>;

export type SelectedRegion = {
  name: string;
  kod_teryt?: string;
  poziom?: string;
  properties: Record<string, unknown>;
} | null;

type MapStore = {
  activeLayers: ActiveLayers;
  toggleLayer: (id: string, value: boolean) => void;
  selectedRegion: SelectedRegion;
  setSelectedRegion: (region: SelectedRegion) => void;
  isPanelCollapsed: boolean;
  togglePanel: () => void;
};

export const useMapStore = create<MapStore>()((set) => ({
  activeLayers: {
    "L-00": true,
    "L-01": true,
    "L-03": true,
    "L-08": true,
    "L-09": false,
    "L-10": false,
  },
  toggleLayer: (id, value) =>
    set((state) => ({
      activeLayers: {
        ...state.activeLayers,
        [id]: value,
      },
    })),
  selectedRegion: null,
  setSelectedRegion: (region) => set({ selectedRegion: region }),
  isPanelCollapsed: false,
  togglePanel: () =>
    set((state) => ({ isPanelCollapsed: !state.isPanelCollapsed })),
}));
