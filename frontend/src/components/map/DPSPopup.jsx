const IKE_CONFIG = {
  czerwony: { emoji: '🔴', label: 'EWAKUACJA NATYCHMIASTOWA', color: '#EF4444' },
  zolty:    { emoji: '🟡', label: 'PRZYGOTUJ EWAKUACJĘ',      color: '#F59E0B' },
  zielony:  { emoji: '🟢', label: 'MONITORUJ',                color: '#22C55E' },
}

function DPSPopup({ properties }) {
  const {
    nazwa,
    powiat,
    gmina,
    liczba_podopiecznych,
    niesamodzielni_procent,
    pojemnosc_ogolna,
    generator_backup,
    kontakt,
    ike_score,
    ike_kategoria,
  } = properties

  const niesamodzielniCount =
    liczba_podopiecznych != null && niesamodzielni_procent != null
      ? Math.round(liczba_podopiecznych * niesamodzielni_procent)
      : null

  const ike = IKE_CONFIG[ike_kategoria] || { emoji: '⚪', label: 'BRAK DANYCH', color: '#6B7280' }

  return (
    <div style={{ minWidth: 240, fontSize: 14, lineHeight: 1.5 }}>
      <div style={{ fontWeight: 700, marginBottom: 4 }}>
        {ike.emoji} DPS &ldquo;{nazwa}&rdquo;
      </div>
      <div style={{ color: '#6B7280', marginBottom: 8, fontSize: 12 }}>
        Powiat: {powiat ?? '—'}{gmina ? ` · Gmina: ${gmina}` : ''}
      </div>
      <hr style={{ border: 'none', borderTop: '1px solid #374151', marginBottom: 8 }} />
      <div style={{ marginBottom: 4 }}>
        Podopieczni: <strong>{liczba_podopiecznych ?? '—'}</strong>
        {niesamodzielniCount != null ? ` (${niesamodzielniCount} niesamodz.)` : ''}
      </div>
      <div style={{ marginBottom: 4 }}>
        Pojemność: <strong>{pojemnosc_ogolna ?? '—'}</strong>
      </div>
      <div style={{ marginBottom: 8 }}>
        Generator: {generator_backup ? '✅' : '❌'}
        {kontakt ? `  ·  Kontakt: ${kontakt}` : ''}
      </div>
      <hr style={{ border: 'none', borderTop: '1px solid #374151', marginBottom: 8 }} />
      <div style={{ fontWeight: 600, color: ike.color, marginBottom: 8 }}>
        IKE: {ike_score != null ? Number(ike_score).toFixed(2) : '—'} {ike.emoji} {ike.label}
      </div>
      <hr style={{ border: 'none', borderTop: '1px solid #374151', marginBottom: 8 }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <button style={btnStyle}>
          📍 Pokaż trasę ewakuacji
        </button>
        <button style={btnStyle}>
          🏠 Najbliższe miejsce relokacji
        </button>
      </div>
    </div>
  )
}

const btnStyle = {
  background: '#1E3A5F',
  border: '1px solid #3B82F6',
  color: '#93C5FD',
  borderRadius: 4,
  padding: '4px 8px',
  cursor: 'pointer',
  fontSize: 13,
  textAlign: 'left',
}

export default DPSPopup
