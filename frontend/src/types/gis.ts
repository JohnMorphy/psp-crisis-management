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

export interface EntityFeatureProperties {
  id: number
  source_record_id: string
  name: string
  subtitle: string | null
  category_code: string
  category_name: string
  category_icon: string | null
  source_code: string
  source_name: string
  status: string | null
  owner_name: string | null
  address_raw: string | null
  teryt_woj: string | null
  teryt_pow: string | null
  teryt_gmina: string | null
  lat: number | null
  lon: number | null
  contact_phone: string | null
  contact_email: string | null
  www: string | null
  source_url: string | null
  last_seen_at: string | null
  last_import_batch_id: number | null
  source_priority: number | null
  match_confidence: number | null
  attributes: Record<string, unknown>
  ike_score: number | null
  ike_kategoria: IkeCategory | null
}

export interface EntityCategory {
  code: string
  name: string
  act_ref: string | null
  icon: string | null
  default_layer_group: string | null
  geometry_mode: string | null
  entity_count: number
}

export interface EntitySummaryBucket {
  code: string
  name: string
  count: number
}

export interface EntitySummary {
  kod_teryt: string
  total_entities: number
  verified_entities: number
  needs_review_entities: number
  categories: EntitySummaryBucket[]
  sources: EntitySummaryBucket[]
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
