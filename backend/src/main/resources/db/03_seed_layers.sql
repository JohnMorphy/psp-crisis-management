INSERT INTO layer_config
    (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
     endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis)
VALUES
('L-01', 'Podmioty ochrony ludnosci',
 'EntityLayer', 'Point', TRUE,
 '/api/layers/L-01', 900, '#3B82F6', 'building',
 'Krajowa warstwa podmiotow ochrony ludnosci z jednego rejestru aplikacyjnego'),

('L-02', 'Gestosc podopiecznych',
 'HeatmapLayer', 'Heatmap', FALSE,
 '/api/layers/L-02', 900, NULL, 'flame',
 'Heatmapa koncentracji podopiecznych wymagajacych ewakuacji'),

('L-03', 'Strefy zagrozenia',
 'ZagrozeniaLayer', 'Polygon', TRUE,
 '/api/layers/L-03', 60, '#EF4444', 'alert-triangle',
 'Aktywne strefy zagrozenia aktualizowane przy kazdym imporcie scenariusza'),

('L-04', 'Drogi ewakuacyjne',
 'DrogiLayer', 'LineString', FALSE,
 '/api/layers/L-04', 60, '#22C55E', 'route',
 'Drogi z atrybutem droznosci'),

('L-05', 'Dostepnosc transportu',
 'TransportLayer', 'Point', FALSE,
 '/api/layers/L-05', 60, '#8B5CF6', 'truck',
 'Lokalizacja dostepnych pojazdow ewakuacyjnych'),

('L-06', 'Miejsca relokacji',
 'RelokacjaLayer', 'Point', FALSE,
 '/api/layers/L-06', 900, '#10B981', 'home',
 'Dostepne miejsca przyjecia ewakuowanych'),

('L-07', 'Biale plamy transportowe',
 'BialePlamiLayer', 'Polygon', FALSE,
 '/api/layers/L-07', 3600, '#6B7280', 'map-off',
 'Obszary bez regularnego transportu publicznego'),

('L-08', 'Granice wojewodztw',
 'AdminBoundaryLayer', 'MultiPolygon', TRUE,
 '/api/layers/L-08', 86400, '#6366F1', 'map',
 '16 granic wojewodztw z PRG GUGiK'),

('L-09', 'Granice powiatow',
 'AdminBoundaryLayer', 'MultiPolygon', FALSE,
 '/api/layers/L-09', 86400, '#4B5563', 'map',
 'Granice powiatow z PRG GUGiK'),

('L-10', 'Granice gmin',
 'AdminBoundaryLayer', 'MultiPolygon', FALSE,
 '/api/layers/L-10', 86400, '#374151', 'map',
 'Granice gmin wymagaja filtra kod_woj lub bbox z PRG GUGiK');
