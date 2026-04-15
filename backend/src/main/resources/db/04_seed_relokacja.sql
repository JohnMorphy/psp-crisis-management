INSERT INTO miejsca_relokacji
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, pojemnosc_dostepna, przyjmuje_niesamodzielnych, kontakt, zrodlo)
VALUES
('REL-LBL-001', 'Hala Sportowa MOSiR Lublin',
 'hala_sportowa', 'lublin', 'Lublin', 'ul. Filaretów 44, 20-609 Lublin',
 ST_SetSRID(ST_MakePoint(22.5372, 51.2401), 4326),
 350, 350, TRUE, '81-466-25-00', 'syntetyczne'),

('REL-ZAM-001', 'Hala Widowiskowo-Sportowa w Zamościu',
 'hala_sportowa', 'zamosc', 'Zamość', 'ul. Królowej Jadwigi 8, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2480, 50.7190), 4326),
 400, 400, TRUE, '84-639-30-80', 'syntetyczne'),

('REL-CHE-001', 'Hala Sportowa MOSiR Chełm',
 'hala_sportowa', 'chelm', 'Chełm', 'ul. Sienkiewicza 27, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4690, 51.1380), 4326),
 300, 300, TRUE, '82-565-35-00', 'syntetyczne'),

('REL-BIA-001', 'Hala Sportowa w Białej Podlaskiej',
 'hala_sportowa', 'biala_podlaska', 'Biała Podlaska', 'ul. Kolejowa 14, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1220, 52.0290), 4326),
 250, 250, TRUE, '83-342-81-30', 'syntetyczne'),

('REL-PUL-001', 'Centrum Sportu i Rekreacji w Puławach',
 'hala_sportowa', 'pulaski', 'Puławy', 'ul. Lubelska 5, 24-100 Puławy',
 ST_SetSRID(ST_MakePoint(21.9720, 51.4210), 4326),
 200, 200, FALSE, '81-880-04-00', 'syntetyczne'),

('REL-HRU-001', 'Dom Kultury w Hrubieszowie',
 'centrum_kultury', 'hrubieszowski', 'Hrubieszów', 'ul. Ciesielczuka 7, 22-500 Hrubieszów',
 ST_SetSRID(ST_MakePoint(23.8870, 50.8120), 4326),
 180, 180, TRUE, '84-696-28-95', 'syntetyczne'),

('REL-BIL-001', 'Centrum Kultury w Biłgoraju',
 'centrum_kultury', 'bilgorajski', 'Biłgoraj', 'ul. Kościuszki 16, 23-400 Biłgoraj',
 ST_SetSRID(ST_MakePoint(22.7190, 50.5380), 4326),
 200, 200, FALSE, '84-686-61-40', 'syntetyczne'),

('REL-KRA-001', 'Szkoła Podstawowa w Kraśniku',
 'szkola', 'krasnicki', 'Kraśnik', 'ul. Urzędowska 54, 23-200 Kraśnik',
 ST_SetSRID(ST_MakePoint(22.2280, 50.9190), 4326),
 100, 100, FALSE, '81-825-26-20', 'syntetyczne');