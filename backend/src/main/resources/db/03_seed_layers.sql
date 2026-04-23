INSERT INTO layer_config (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona, endpoint, opis)
VALUES
  ('L-01', 'Jednostki ochrony ludności', 'EntityLayer', 'Point', true,  '/api/layers/L-01', 'Wszystkie jednostki z entity_registry'),
  ('L-02', 'Alerty zagrożeń',            'ThreatAlertLayer', 'Point', true, '/api/threats/active', 'Aktywne alerty z IMGW i manualnych triggerów')
ON CONFLICT (id) DO UPDATE SET nazwa = EXCLUDED.nazwa, endpoint = EXCLUDED.endpoint, komponent = EXCLUDED.komponent, opis = EXCLUDED.opis;
