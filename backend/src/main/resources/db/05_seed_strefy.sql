INSERT INTO strefy_zagrozen
    (kod, typ_zagrozenia, poziom, scenariusz, obszar, nazwa, geom,
     szybkosc_wznoszenia_m_h, czas_do_zagrozenia_h, zrodlo)
VALUES
('DEMO-POWODZ-001', 'powodz', 'czerwony', 'Q100', 'chelm',
 'Strefa zalewowa rzeki Uherka — rejon Sawina',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.36 51.18, 23.44 51.18, 23.44 51.25, 23.36 51.25, 23.36 51.18))'
 ), 4326),
 0.25, 3.0, 'syntetyczne'),

('DEMO-POWODZ-002', 'powodz', 'czerwony', 'Q100', 'wlodawski',
 'Strefa zalewowa rzeki Bug — rejon Włodawy',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.51 51.52, 23.58 51.52, 23.58 51.60, 23.51 51.60, 23.51 51.52))'
 ), 4326),
 0.25, 4.0, 'syntetyczne'),

('DEMO-BLACKOUT-001', 'blackout', 'zolty', 'blackout_powiat', 'hrubieszowski',
 'Strefa awarii sieci energetycznej — powiat hrubieszowski',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.72 50.76, 23.98 50.76, 23.98 50.87, 23.72 50.87, 23.72 50.76))'
 ), 4326),
 NULL, NULL, 'syntetyczne');