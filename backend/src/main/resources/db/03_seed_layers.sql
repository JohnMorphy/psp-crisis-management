INSERT INTO layer_config
    (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
     endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis)
VALUES
('L-01', 'DPS i placówki opiekuńcze',
 'DPSLayer', 'Point', TRUE,
 '/api/layers/L-01', 900, '#3B82F6', 'building',
 'Lokalizacja placówek DPS i domów opieki'),

('L-02', 'Gęstość podopiecznych',
 'HeatmapLayer', 'Heatmap', FALSE,
 '/api/layers/L-02', 900, NULL, 'flame',
 'Heatmapa koncentracji podopiecznych wymagających ewakuacji'),

('L-03', 'Strefy zagrożenia',
 'ZagrozeniaLayer', 'Polygon', TRUE,
 '/api/layers/L-03', 60, '#EF4444', 'alert-triangle',
 'Aktywne strefy zagrożenia — aktualizowane przy każdym imporcie scenariusza'),

('L-04', 'Drogi ewakuacyjne',
 'DrogiLayer', 'LineString', FALSE,
 '/api/layers/L-04', 60, '#22C55E', 'route',
 'Drogi z atrybutem drożności'),

('L-05', 'Dostępność transportu',
 'TransportLayer', 'Point', FALSE,
 '/api/layers/L-05', 60, '#8B5CF6', 'truck',
 'Lokalizacja dostępnych pojazdów ewakuacyjnych'),

('L-06', 'Miejsca relokacji',
 'RelokacjaLayer', 'Point', FALSE,
 '/api/layers/L-06', 900, '#10B981', 'home',
 'Dostępne miejsca przyjęcia ewakuowanych'),

('L-07', 'Białe plamy transportowe',
 'BialePlamiLayer', 'Polygon', FALSE,
 '/api/layers/L-07', 3600, '#6B7280', 'map-off',
 'Obszary bez regularnego transportu publicznego');