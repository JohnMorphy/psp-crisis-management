export interface GeoJsonGeometry {
  type: string
  coordinates: number[] | number[][] | number[][][]
}

export interface GeoJsonFeature<P = Record<string, unknown>> {
  type: 'Feature'
  geometry: GeoJsonGeometry
  properties: P
}

export interface GeoJsonCollection<P = Record<string, unknown>> {
  type: 'FeatureCollection'
  features: GeoJsonFeature<P>[]
  layer_id?: string
  ostatnia_aktualizacja?: string
  feature_count?: number
}

export type IkeCategory = 'czerwony' | 'zolty' | 'zielony'

export interface FacilityProperties {
  kod: string
  nazwa: string
  typ: string | null
  powiat: string
  gmina: string
  liczba_podopiecznych: number | null
  pojemnosc_ogolna: number | null
  niesamodzielni_procent: number | null
  generator_backup: boolean | null
  kontakt: string | null
  ike_score: number | null
  ike_kategoria: IkeCategory | null
}

export interface ThreatZoneProperties {
  id: string
  typ_zagrozenia: string
  poziom: string
  scenariusz: string | null
  nazwa: string | null
  szybkosc_wznoszenia_m_h: number | null
  czas_do_zagrozenia_h: number | null
  zrodlo: string | null
}
