INSERT INTO placowka
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, liczba_podopiecznych, niesamodzielni_procent,
     generator_backup, personel_dyzurny, kontakt, zrodlo)
VALUES
-- POWIAT LUBELSKI
('DPS-LBL-001', 'Dom Pomocy Społecznej im. Jana Pawła II w Niemcach',
 'DPS_dorosli', 'lubelski', 'Niemce', 'ul. Różana 14, 21-025 Niemce',
 ST_SetSRID(ST_MakePoint(22.5891, 51.3012), 4326),
 80, 72, 0.68, TRUE, 8, '81-756-32-10', 'syntetyczne'),

('DPS-LBL-002', 'Dom Pomocy Społecznej w Albertowie',
 'DPS_dorosli', 'lubelski', 'Bełżyce', 'Albertów 12, 24-200 Bełżyce',
 ST_SetSRID(ST_MakePoint(22.2841, 51.1723), 4326),
 100, 94, 0.82, FALSE, 10, '81-517-22-34', 'syntetyczne'),

-- POWIAT ZAMOJSKI
('DPS-ZAM-001', 'Dom Pomocy Społecznej w Zamościu',
 'DPS_dorosli', 'zamojski', 'Zamość', 'ul. Poprzeczna 6, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2517, 50.7230), 4326),
 120, 108, 0.74, TRUE, 14, '84-639-21-80', 'syntetyczne'),

('DPS-ZAM-002', 'Dom Pomocy Społecznej w Bodaczowie',
 'DPS_dzieci', 'zamojski', 'Szczebrzeszyn', 'Bodaczów 3, 22-460 Szczebrzeszyn',
 ST_SetSRID(ST_MakePoint(22.9822, 50.6961), 4326),
 45, 38, 0.45, FALSE, 5, '84-682-11-20', 'syntetyczne'),

-- POWIAT CHEŁM
('DPS-CHE-001', 'Dom Pomocy Społecznej przy ul. Polnej w Chełmie',
 'DPS_dorosli', 'chelm', 'Chełm', 'ul. Polna 15, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4720, 51.1433), 4326),
 90, 83, 0.71, TRUE, 9, '82-565-13-40', 'syntetyczne'),

('DPS-CHE-002', 'Dom Pomocy Społecznej w Sawinie',
 'DPS_dorosli', 'chelm', 'Sawin', 'ul. Chełmska 2, 22-107 Sawin',
 ST_SetSRID(ST_MakePoint(23.3984, 51.2108), 4326),
 60, 55, 0.89, FALSE, 6, '82-568-60-17', 'syntetyczne'),

-- POWIAT BIALSKI
('DPS-BIA-001', 'Dom Pomocy Społecznej w Białej Podlaskiej',
 'DPS_dorosli', 'bialski', 'Biała Podlaska', 'ul. Warszawska 18, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1167, 52.0333), 4326),
 110, 99, 0.65, TRUE, 12, '83-344-51-82', 'syntetyczne'),

('DPS-BIA-002', 'Dom Opieki Podlasie w Terespolu',
 'hostel_wspomagany', 'bialski', 'Terespol', 'ul. Kościuszki 7, 21-550 Terespol',
 ST_SetSRID(ST_MakePoint(23.6140, 52.0793), 4326),
 30, 27, 0.50, FALSE, 3, '83-375-20-15', 'syntetyczne'),

-- POWIAT PUŁAWSKI
('DPS-PUL-001', 'Dom Pomocy Społecznej w Puławach',
 'DPS_dorosli', 'pulaski', 'Puławy', 'ul. Piaskowa 21, 24-100 Puławy',
 ST_SetSRID(ST_MakePoint(21.9693, 51.4158), 4326),
 95, 88, 0.77, TRUE, 11, '81-886-32-50', 'syntetyczne'),

('DPS-PUL-002', 'Dom Pomocy Społecznej w Końskowoli',
 'DPS_dorosli', 'pulaski', 'Końskowola', 'ul. Ogrodowa 4, 24-130 Końskowola',
 ST_SetSRID(ST_MakePoint(22.0732, 51.3481), 4326),
 50, 47, 0.91, FALSE, 5, '81-881-64-15', 'syntetyczne'),

-- POWIAT HRUBIESZOWSKI
('DPS-HRU-001', 'Dom Pomocy Społecznej w Hrubieszowie',
 'DPS_dorosli', 'hrubieszowski', 'Hrubieszów', 'ul. B. Prusa 8, 22-500 Hrubieszów',
 ST_SetSRID(ST_MakePoint(23.8911, 50.8099), 4326),
 75, 70, 0.73, TRUE, 8, '84-696-22-80', 'syntetyczne'),

('DPS-HRU-002', 'Dom Pomocy Społecznej w Uchaniach',
 'DPS_dorosli', 'hrubieszowski', 'Uchanie', 'Uchanie 45, 22-510 Uchanie',
 ST_SetSRID(ST_MakePoint(23.6892, 50.8420), 4326),
 40, 36, 0.64, FALSE, 4, '84-696-10-03', 'syntetyczne'),

-- POWIAT KRASNOSTAWSKI
('DPS-KRA-S-001', 'Dom Pomocy Społecznej w Krasnymstawie',
 'DPS_dorosli', 'krasnostawski', 'Krasnystaw', 'ul. Poniatowskiego 25, 22-300 Krasnystaw',
 ST_SetSRID(ST_MakePoint(23.1716, 50.9860), 4326),
 90, 84, 0.73, TRUE, 10, '82-576-20-40', 'syntetyczne'),

('DPS-KRA-S-002', 'Dom Pomocy Społecznej w Zakrzówku',
 'DPS_dorosli', 'krasnostawski', 'Zakrzówek', 'Zakrzówek Wieś 10, 23-213 Zakrzówek',
 ST_SetSRID(ST_MakePoint(22.9480, 50.9122), 4326),
 50, 44, 0.68, FALSE, 5, '82-576-44-18', 'syntetyczne'),

-- POWIAT KRAŚNICKI
('DPS-KRA-001', 'Dom Pomocy Społecznej w Kraśniku',
 'DPS_dorosli', 'krasnicki', 'Kraśnik', 'ul. Słoneczna 9, 23-200 Kraśnik',
 ST_SetSRID(ST_MakePoint(22.2200, 50.9228), 4326),
 85, 79, 0.69, TRUE, 9, '81-884-20-20', 'syntetyczne'),

('DPS-KRA-002', 'Dom Dziecka w Annopolu',
 'dom_dziecka', 'krasnicki', 'Annopol', 'ul. Rynek 11, 23-235 Annopol',
 ST_SetSRID(ST_MakePoint(21.8583, 50.8888), 4326),
 25, 21, 0.24, FALSE, 3, '81-861-31-02', 'syntetyczne'),

-- POWIAT BIŁGORAJSKI
('DPS-BIL-001', 'Dom Pomocy Społecznej w Biłgoraju',
 'DPS_dorosli', 'bilgorajski', 'Biłgoraj', 'ul. Nadrzeczna 10, 23-400 Biłgoraj',
 ST_SetSRID(ST_MakePoint(22.7227, 50.5411), 4326),
 100, 91, 0.78, TRUE, 11, '84-686-51-30', 'syntetyczne'),

('DPS-BIL-002', 'Dom Pomocy Społecznej w Tarnogrodzie',
 'DPS_dorosli', 'bilgorajski', 'Tarnogród', 'ul. Leśna 3, 23-420 Tarnogród',
 ST_SetSRID(ST_MakePoint(22.7374, 50.3615), 4326),
 65, 60, 0.62, FALSE, 7, '84-689-71-03', 'syntetyczne'),

-- POWIAT ŁĘCZYŃSKI
('DPS-LEC-001', 'Dom Pomocy Społecznej w Łęcznej',
 'DPS_dorosli', 'leczynski', 'Łęczna', 'ul. Krasnystawska 52, 21-010 Łęczna',
 ST_SetSRID(ST_MakePoint(22.8862, 51.3022), 4326),
 70, 65, 0.58, TRUE, 7, '81-752-29-11', 'syntetyczne'),

('DPS-LEC-002', 'Dom Pomocy Społecznej w Milejowie',
 'DPS_dorosli', 'leczynski', 'Milejów', 'ul. Wesoła 8, 21-020 Milejów',
 ST_SetSRID(ST_MakePoint(22.9374, 51.2581), 4326),
 45, 41, 0.71, FALSE, 5, '81-757-60-44', 'syntetyczne'),

-- POWIAT ŚWIDNICKI
('DPS-SWI-001', 'Dom Pomocy Społecznej w Świdniku',
 'centrum_opiekuncze', 'swidnicki', 'Świdnik', 'ul. Powstańców 7, 21-040 Świdnik',
 ST_SetSRID(ST_MakePoint(22.6950, 51.2231), 4326),
 55, 51, 0.55, FALSE, 6, '81-751-43-20', 'syntetyczne'),

('DPS-SWI-002', 'Dom Pomocy Społecznej w Piaskach',
 'DPS_dorosli', 'swidnicki', 'Piaski', 'ul. Lubelska 22, 21-050 Piaski',
 ST_SetSRID(ST_MakePoint(22.8391, 51.1480), 4326),
 40, 37, 0.60, TRUE, 4, '81-757-51-30', 'syntetyczne'),

-- POWIAT OPOLSKI
('DPS-OPO-001', 'Dom Pomocy Społecznej w Opolu Lubelskim',
 'DPS_dorosli', 'opolski', 'Opole Lubelskie', 'ul. Ogrodowa 5, 24-300 Opole Lubelskie',
 ST_SetSRID(ST_MakePoint(21.9648, 51.1482), 4326),
 90, 82, 0.76, TRUE, 10, '81-827-60-70', 'syntetyczne'),

('DPS-OPO-002', 'Dom Pomocy Społecznej w Poniatowej',
 'DPS_dorosli', 'opolski', 'Poniatowa', 'ul. Fabryczna 14, 24-320 Poniatowa',
 ST_SetSRID(ST_MakePoint(22.0643, 51.1817), 4326),
 55, 49, 0.67, FALSE, 6, '81-820-30-25', 'syntetyczne'),

-- POWIAT RYCKI
('DPS-RYK-001', 'Dom Pomocy Społecznej w Rykach',
 'DPS_dorosli', 'rycki', 'Ryki', 'ul. Warszawska 50, 08-500 Ryki',
 ST_SetSRID(ST_MakePoint(21.9321, 51.6228), 4326),
 75, 68, 0.60, FALSE, 8, '81-865-32-10', 'syntetyczne'),

('DPS-RYK-002', 'Dom Pomocy Społecznej w Dęblinie',
 'DPS_dorosli', 'rycki', 'Dęblin', 'ul. Różana 3, 08-530 Dęblin',
 ST_SetSRID(ST_MakePoint(21.8484, 51.5612), 4326),
 50, 45, 0.72, TRUE, 5, '81-883-20-18', 'syntetyczne'),

-- POWIAT ŁUKOWSKI
('DPS-LUK-001', 'Dom Pomocy Społecznej w Łukowie',
 'DPS_dorosli', 'lukowski', 'Łuków', 'ul. Browarna 14, 21-400 Łuków',
 ST_SetSRID(ST_MakePoint(22.3831, 51.9308), 4326),
 80, 76, 0.72, TRUE, 9, '25-798-42-60', 'syntetyczne'),

('DPS-LUK-002', 'Dom Dziecka w Stoczku Łukowskim',
 'dom_dziecka', 'lukowski', 'Stoczek Łukowski', 'ul. Szkolna 5, 21-450 Stoczek Łukowski',
 ST_SetSRID(ST_MakePoint(22.0811, 51.9640), 4326),
 20, 17, 0.20, FALSE, 2, '25-797-50-10', 'syntetyczne'),

-- POWIAT RADZYŃSKI
('DPS-RAD-001', 'Dom Pomocy Społecznej w Radzyniu Podlaskim',
 'DPS_dorosli', 'radzynski', 'Radzyń Podlaski', 'ul. Sitkowskiego 3, 21-300 Radzyń Podlaski',
 ST_SetSRID(ST_MakePoint(22.6212, 51.7842), 4326),
 85, 79, 0.66, TRUE, 9, '83-352-71-77', 'syntetyczne'),

('DPS-RAD-002', 'Dom Pomocy Społecznej w Kąkolewnicy',
 'DPS_dorosli', 'radzynski', 'Kąkolewnica', 'Kąkolewnica 80, 21-302 Kąkolewnica',
 ST_SetSRID(ST_MakePoint(22.5041, 51.8220), 4326),
 40, 35, 0.74, FALSE, 4, '83-352-80-02', 'syntetyczne'),

-- POWIAT PARCZEWSKI
('DPS-PAR-001', 'Dom Pomocy Społecznej w Parczewie',
 'DPS_dorosli', 'parczewski', 'Parczew', 'ul. Szpitalna 2, 21-200 Parczew',
 ST_SetSRID(ST_MakePoint(22.9016, 51.6398), 4326),
 55, 50, 0.70, TRUE, 6, '83-355-10-33', 'syntetyczne'),

('DPS-PAR-002', 'Dom Pomocy Społecznej w Jabłoniu',
 'DPS_dorosli', 'parczewski', 'Jabłoń', 'Jabłoń 120, 21-205 Jabłoń',
 ST_SetSRID(ST_MakePoint(22.7340, 51.6811), 4326),
 35, 31, 0.65, FALSE, 4, '83-355-20-15', 'syntetyczne'),

-- POWIAT WŁODAWSKI
('DPS-VLO-001', 'Dom Pomocy Społecznej we Włodawie',
 'DPS_dorosli', 'wlodawski', 'Włodawa', 'ul. Czerwonego Krzyża 6, 22-200 Włodawa',
 ST_SetSRID(ST_MakePoint(23.5411, 51.5512), 4326),
 70, 63, 0.80, FALSE, 7, '82-572-20-12', 'syntetyczne'),

('DPS-VLO-002', 'Dom Pomocy Społecznej w Hannie',
 'DPS_dorosli', 'wlodawski', 'Hanna', 'Hanna 55, 22-220 Hanna',
 ST_SetSRID(ST_MakePoint(23.6530, 51.6801), 4326),
 30, 28, 0.86, FALSE, 3, '82-572-61-05', 'syntetyczne'),

-- POWIAT JANOWSKI
('DPS-JAN-001', 'Dom Pomocy Społecznej w Janowie Lubelskim',
 'DPS_dorosli', 'janowski', 'Janów Lubelski', 'ul. Zamoyskiego 3, 23-300 Janów Lubelski',
 ST_SetSRID(ST_MakePoint(22.4107, 50.7082), 4326),
 60, 54, 0.67, TRUE, 6, '15-872-38-19', 'syntetyczne'),

('DPS-JAN-002', 'Dom Pomocy Społecznej w Modliborzycach',
 'DPS_dorosli', 'janowski', 'Modliborzyce', 'ul. Szkolna 4, 23-310 Modliborzyce',
 ST_SetSRID(ST_MakePoint(22.3290, 50.7572), 4326),
 35, 31, 0.57, FALSE, 4, '15-871-20-08', 'syntetyczne'),

-- POWIAT TOMASZOWSKI
('DPS-TOM-001', 'Dom Pomocy Społecznej w Tomaszowie Lubelskim',
 'DPS_dorosli', 'tomaszowski', 'Tomaszów Lubelski', 'ul. Lwowska 18, 22-600 Tomaszów Lubelski',
 ST_SetSRID(ST_MakePoint(23.4209, 50.4482), 4326),
 90, 85, 0.75, TRUE, 10, '84-664-21-30', 'syntetyczne'),

('DPS-TOM-002', 'Dom Pomocy Społecznej w Tyszowcach',
 'DPS_dorosli', 'tomaszowski', 'Tyszowce', 'ul. Kościelna 12, 22-630 Tyszowce',
 ST_SetSRID(ST_MakePoint(23.7140, 50.6008), 4326),
 45, 40, 0.80, FALSE, 5, '84-661-30-20', 'syntetyczne'),

-- MIASTO ZAMOŚĆ (prawa powiatu)
('DPS-ZAM-M-001', 'Dom Pomocy Społecznej Caritas w Zamościu',
 'DPS_dorosli', 'zamosc', 'Zamość', 'ul. Okrzei 5, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2520, 50.7162), 4326),
 130, 117, 0.79, TRUE, 15, '84-638-60-90', 'syntetyczne'),

('DPS-ZAM-M-002', 'Centrum Opieki Senioralnej w Zamościu',
 'centrum_opiekuncze', 'zamosc', 'Zamość', 'ul. Wyszyńskiego 9, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2610, 50.7280), 4326),
 60, 55, 0.62, FALSE, 7, '84-639-44-11', 'syntetyczne'),

-- MIASTO LUBLIN (prawa powiatu)
('DPS-LBL-M-001', 'Dom Pomocy Społecznej przy ul. Głębokiej w Lublinie',
 'DPS_dorosli', 'lublin', 'Lublin', 'ul. Głęboka 11, 20-612 Lublin',
 ST_SetSRID(ST_MakePoint(22.5231, 51.2465), 4326),
 150, 138, 0.84, TRUE, 18, '81-466-51-00', 'syntetyczne'),

('DPS-LBL-M-002', 'Dom Pomocy Społecznej przy ul. Sławinkowskiej w Lublinie',
 'DPS_dorosli', 'lublin', 'Lublin', 'ul. Sławinkowska 37, 20-810 Lublin',
 ST_SetSRID(ST_MakePoint(22.5080, 51.2860), 4326),
 120, 109, 0.76, TRUE, 14, '81-744-17-22', 'syntetyczne'),

-- MIASTO CHEŁM (prawa powiatu)
('DPS-CHE-M-001', 'Dom Pomocy Społecznej przy ul. Ceramicznej w Chełmie',
 'DPS_dorosli', 'chelm', 'Chełm', 'ul. Ceramiczna 1, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4580, 51.1320), 4326),
 100, 92, 0.77, TRUE, 11, '82-562-78-30', 'syntetyczne'),

('DPS-CHE-M-002', 'Centrum Opiekuńczo-Mieszkalne w Chełmie',
 'centrum_opiekuncze', 'chelm', 'Chełm', 'ul. Połaniecka 4, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4810, 51.1488), 4326),
 40, 36, 0.50, FALSE, 4, '82-563-10-55', 'syntetyczne'),

-- MIASTO BIAŁA PODLASKA (prawa powiatu)
('DPS-BIA-M-001', 'Dom Pomocy Społecznej przy ul. Terebelskiej w Białej Podlaskiej',
 'DPS_dorosli', 'biala_podlaska', 'Biała Podlaska', 'ul. Terebelska 57, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1290, 52.0410), 4326),
 90, 83, 0.71, TRUE, 10, '83-343-30-30', 'syntetyczne'),

('DPS-BIA-M-002', 'Centrum Opieki w Białej Podlaskiej',
 'centrum_opiekuncze', 'biala_podlaska', 'Biała Podlaska', 'ul. Młodości 12, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1350, 52.0370), 4326),
 45, 40, 0.58, FALSE, 5, '83-343-40-40', 'syntetyczne');