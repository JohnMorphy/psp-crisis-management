import type { CSSProperties } from 'react'
import type { EntityFeatureProperties } from '../../types/gis'

interface EntityPopupProps {
  properties: EntityFeatureProperties
}

function EntityPopup({ properties }: EntityPopupProps) {
  const residents = asNumber(properties.attributes['liczba_podopiecznych'])
  const capacity = asNumber(properties.attributes['pojemnosc_ogolna'])
  const dependence = asNumber(properties.attributes['niesamodzielni_procent'])
  const dependencePct = dependence != null ? Math.round(dependence * 100) : null

  return (
    <div style={{ minWidth: 260, fontSize: 14, lineHeight: 1.5 }}>
      <div style={{ fontWeight: 700, marginBottom: 4 }}>{properties.name}</div>
      <div style={{ color: '#6B7280', marginBottom: 8, fontSize: 12 }}>
        {properties.category_name}
        {properties.subtitle ? ` · ${properties.subtitle}` : ''}
      </div>

      <hr style={dividerStyle} />

      <div style={rowStyle}>
        <span>Zrodlo</span>
        <strong>{properties.source_name}</strong>
      </div>
      <div style={rowStyle}>
        <span>Status</span>
        <strong>{properties.status ?? 'brak'}</strong>
      </div>
      {properties.owner_name && (
        <div style={rowStyle}>
          <span>Operator</span>
          <strong>{properties.owner_name}</strong>
        </div>
      )}
      {properties.address_raw && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ color: '#6B7280', fontSize: 12 }}>Adres</div>
          <div>{properties.address_raw}</div>
        </div>
      )}

      {(residents != null || capacity != null || dependencePct != null) && (
        <>
          <hr style={dividerStyle} />
          {residents != null && (
            <div style={rowStyle}>
              <span>Osoby / podopieczni</span>
              <strong>{residents}</strong>
            </div>
          )}
          {capacity != null && (
            <div style={rowStyle}>
              <span>Pojemnosc</span>
              <strong>{capacity}</strong>
            </div>
          )}
          {dependencePct != null && (
            <div style={rowStyle}>
              <span>Niesamodzielni</span>
              <strong>{dependencePct}%</strong>
            </div>
          )}
        </>
      )}

      {(properties.contact_phone || properties.contact_email || properties.www) && (
        <>
          <hr style={dividerStyle} />
          {properties.contact_phone && <div style={rowStyle}><span>Telefon</span><strong>{properties.contact_phone}</strong></div>}
          {properties.contact_email && <div style={rowStyle}><span>Email</span><strong>{properties.contact_email}</strong></div>}
          {properties.www && (
            <div style={{ marginBottom: 8 }}>
              <a href={properties.www} target="_blank" rel="noreferrer" style={linkStyle}>
                Otworz strone
              </a>
            </div>
          )}
        </>
      )}

      <hr style={dividerStyle} />

      <div style={{ color: '#6B7280', fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span>Ostatni import: {properties.last_seen_at ? new Date(properties.last_seen_at).toLocaleString('pl-PL') : 'brak'}</span>
        {properties.match_confidence != null && <span>Pewnosc dopasowania: {Math.round(properties.match_confidence * 100)}%</span>}
        {properties.source_url && (
          <a href={properties.source_url} target="_blank" rel="noreferrer" style={linkStyle}>
            Rekord zrodlowy
          </a>
        )}
      </div>
    </div>
  )
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' ? value : null
}

const dividerStyle: CSSProperties = { border: 'none', borderTop: '1px solid #374151', margin: '8px 0' }
const rowStyle: CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 4 }
const linkStyle: CSSProperties = { color: '#60A5FA', textDecoration: 'none' }

export default EntityPopup
