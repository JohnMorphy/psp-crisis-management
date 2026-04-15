INSERT INTO zasob_transportu
    (kod, typ, oznaczenie, operator, powiat, geom,
     pojemnosc_osob, przyjmuje_niesamodzielnych, dostepny, zrodlo)
VALUES
('TRP-001', 'bus_sanitarny', 'Bus San. LUB-1', 'Pogotowie Ratunkowe Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5684, 51.2502), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-002', 'bus_sanitarny', 'Bus San. LUB-2', 'Pogotowie Ratunkowe Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5410, 51.2610), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-003', 'bus_zwykly', 'Bus PKS LUB-5', 'PKS Lublin',
 'lubelski', ST_SetSRID(ST_MakePoint(22.5200, 51.2380), 4326),
 45, FALSE, TRUE, 'syntetyczne'),

('TRP-004', 'karetka', 'Karetka LUB-K1', 'SPZOZ Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5750, 51.2480), 4326),
 2, TRUE, TRUE, 'syntetyczne'),

('TRP-005', 'bus_sanitarny', 'Bus San. CHE-1', 'Pogotowie Ratunkowe Chełm',
 'zamosc', ST_SetSRID(ST_MakePoint(23.4700, 51.1400), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-006', 'bus_sanitarny', 'Bus San. CHE-2', 'Pogotowie Ratunkowe Chełm',
 'chelm', ST_SetSRID(ST_MakePoint(23.3990, 51.2090), 4326),
 8, TRUE, FALSE, 'syntetyczne'),

('TRP-007', 'bus_zwykly', 'Bus PKS ZAM-3', 'PKS Zamość',
 'zamojski', ST_SetSRID(ST_MakePoint(23.2500, 50.7200), 4326),
 45, FALSE, TRUE, 'syntetyczne'),

('TRP-008', 'pojazd_specjalny', 'Pojazd Spec. STR-1', 'Straż Pożarna Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5600, 51.2550), 4326),
 12, TRUE, TRUE, 'syntetyczne'),

('TRP-009', 'bus_sanitarny', 'Bus San. BIA-1', 'Pogotowie Biała Podlaska',
 'bialski', ST_SetSRID(ST_MakePoint(23.1150, 52.0320), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-010', 'bus_zwykly', 'Bus PKS PUL-2', 'PKS Puławy',
 'pulaski', ST_SetSRID(ST_MakePoint(21.9680, 51.4140), 4326),
 45, FALSE, TRUE, 'syntetyczne');