INSERT INTO layer_config (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona, endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis, aktywna)
VALUES
  ('L-01', 'Podmioty ochrony ludnosci', 'EntityLayer',        'Point',        TRUE,  '/api/layers/L-01', 900,  '#2563EB', 'building', 'Krajowa warstwa podmiotow ochrony ludnosci z rejestru aplikacyjnego', TRUE),
  ('L-08', 'Wojewodztwa',               'AdminBoundaryLayer', 'MultiPolygon', TRUE,  '/api/layers/L-08', 3600, '#6366F1', NULL,       'Granice wojewodztw z PRG GUGiK',                                       TRUE),
  ('L-09', 'Powiaty',                   'AdminBoundaryLayer', 'MultiPolygon', FALSE, '/api/layers/L-09', 3600, '#4B5563', NULL,       'Granice powiatow z PRG GUGiK',                                         TRUE),
  ('L-10', 'Gminy',                     'AdminBoundaryLayer', 'MultiPolygon', FALSE, '/api/layers/L-10', 3600, '#374151', NULL,       'Granice gmin z PRG GUGiK (filtrowane po wojewodztwie)',                TRUE)
ON CONFLICT (id) DO UPDATE
  SET nazwa                  = EXCLUDED.nazwa,
      komponent              = EXCLUDED.komponent,
      typ_geometrii          = EXCLUDED.typ_geometrii,
      domyslnie_wlaczona     = EXCLUDED.domyslnie_wlaczona,
      endpoint               = EXCLUDED.endpoint,
      interval_odswiezania_s = EXCLUDED.interval_odswiezania_s,
      kolor_domyslny         = EXCLUDED.kolor_domyslny,
      ikona                  = EXCLUDED.ikona,
      opis                   = EXCLUDED.opis,
      aktywna                = EXCLUDED.aktywna;
