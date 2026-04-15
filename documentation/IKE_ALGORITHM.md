# IKE_ALGORITHM.md — Algorytm Indeksu Krytyczności Ewakuacji

> Dokument referencyjny dla `IkeService.java` i `ike.config.json`.
> Opisuje pełną logikę obliczania IKE, obsługę edge case'ów i przykłady.
> **Czytaj przed implementacją lub modyfikacją `IkeService.java`.**

---

## Spis treści

1. [Cel algorytmu](#1-cel-algorytmu)
2. [Formuła główna](#2-formuła-główna)
3. [Obliczanie składowych score'ów](#3-obliczanie-składowych-scoreów)
4. [Progi kategorii IKE](#4-progi-kategorii-ike)
5. [Edge case'y — tabela decyzyjna](#5-edge-casely--tabela-decyzyjna)
6. [Przykłady obliczenia IKE](#6-przykłady-obliczenia-ike)
7. [Wynik IKE — struktura DTO](#7-wynik-ike--struktura-dto)
8. [Konfiguracja wag](#8-konfiguracja-wag)
9. [Zapytania PostGIS używane przez IkeService](#9-zapytania-postgis-używane-przez-ikeservice)
10. [Kolejność kroków w IkeService.java](#10-kolejność-kroków-w-ikeservicejava)

---

## 1. Cel algorytmu

IKE przypisuje każdej placówce DPS liczbę z zakresu **[0.0 – 1.0]**, gdzie:

- `1.0` = placówka w bezpośrednim śmiertelnym zagrożeniu, bez transportu, pełna niesamodzielnych podopiecznych
- `0.0` = placówka bezpieczna, z zasobami, poza strefą zagrożenia

Wynik IKE służy do:
- kolorowania markerów na mapie (czerwony / żółty / zielony)
- sortowania listy „Top 10 do ewakuacji" w `Top10Panel.jsx`
- wyboru celu relokacji i wyznaczenia trasy ewakuacji w `EvacuationRoute.jsx`

---

## 2. Formuła główna

```
IKE = (W_zag  × score_zagrozenia)
    + (W_nies × score_niesamodzielnych)
    + (W_tran × score_braku_transportu)
    + (W_droz × score_braku_droznosci)
    + (W_odl  × score_odleglosci_relokacji)
```

Gdzie wagi domyślne (z `ike.config.json`):

| Symbol | Nazwa | Waga domyślna |
|---|---|---|
| W_zag | Zagrożenie strefą kryzysową | **0.35** |
| W_nies | Udział niesamodzielnych podopiecznych | **0.25** |
| W_tran | Brak dostępnego transportu | **0.20** |
| W_droz | Brak drożności dróg wyjazdu | **0.15** |
| W_odl | Odległość do najbliższego miejsca relokacji | **0.05** |
| | **Suma wag** | **1.00** |

> Wagi są konfigurowalne w `frontend/src/config/ike.config.json` i wczytywane
> przez `IkeService.java` przy starcie. Zmiana wag nie wymaga rekompilacji —
> tylko restartu backendu lub wywołania `POST /api/ike/recalculate`.

---

## 3. Obliczanie składowych score'ów

Każdy score mieści się w zakresie **[0.0 – 1.0]**, gdzie `1.0` = stan najbardziej krytyczny.

---

### 3.1 `score_zagrozenia` — nakładanie się ze strefą zagrożenia

**Źródło danych:** warstwa L-03 (`strefy_powodz.geojson`, `strefy_pozar.geojson`, `strefy_blackout.geojson`)

**Zapytanie PostGIS:** `ST_Intersects(placowka.geom, strefa.geom)`

**Logika:**

```
Jeśli placówka leży w strefie czerwonej (poziom = 'czerwony'):
    score_zagrozenia = 1.0

Jeśli placówka leży w strefie żółtej (poziom = 'zolty'):
    score_zagrozenia = 0.60

Jeśli placówka leży w strefie zielonej (poziom = 'zielony'):
    score_zagrozenia = 0.20

Jeśli placówka nie leży w żadnej strefie zagrożenia:
    score_zagrozenia = 0.0

Jeśli warstwa zagrożeń jest niedostępna (L-03 offline):
    score_zagrozenia = 0.0  ← wartość domyślna z ike.config.json
```

**Uwaga:** jeśli placówka nakłada się na wiele stref jednocześnie (np. powódź + blackout),
przyjmij **najwyższy** z uzyskanych score'ów (worst-case).

---

### 3.2 `score_niesamodzielnych` — ciężar ewakuacyjny podopiecznych

**Źródło danych:** pole `niesamodzielni_procent` z tabeli `placowka`

**Logika:** score jest wprost równy procentowi niesamodzielnych.

```
score_niesamodzielnych = placowka.niesamodzielni_procent
```

Przykłady:
- 90% niesamodzielnych → `score = 0.90`
- 45% niesamodzielnych → `score = 0.45`
- 0% niesamodzielnych → `score = 0.00`

**Edge case:** jeśli pole `niesamodzielni_procent` jest `NULL`:
```
score_niesamodzielnych = 0.50  ← wartość domyślna z ike.config.json
```

---

### 3.3 `score_braku_transportu` — dostępność pojazdów ewakuacyjnych

**Źródło danych:** tabela `zasob_transportu`, kolumna `dostepny = TRUE`

**Zapytanie PostGIS:** `ST_DWithin(placowka.geom, transport.geom, promien_stopnie)`

Promień z `ike.config.json`: `promienie_km.transport_dostepny = 15 km`
Konwersja stopnie: `15 km ÷ 111.32 km/° ≈ 0.13476°`

> ⚠️ Używaj `ST_DWithin` z geometrią w EPSG:4326 i promieniem w stopniach,
> LUB rzutuj do EPSG:2180 (Polska CS92) i używaj metrów.
> Zalecane: rzutowanie do EPSG:2180 dla dokładności w polskich szerokościach.

**Logika:**

```
N = liczba pojazdów z dostepny=TRUE w promieniu R km od placówki

Jeśli N = 0:
    score_braku_transportu = 1.0  ← brak transportu = krytyczne

Jeśli N = 1:
    score_braku_transportu = 0.75

Jeśli N = 2:
    score_braku_transportu = 0.50

Jeśli N = 3:
    score_braku_transportu = 0.25

Jeśli N ≥ 4:
    score_braku_transportu = 0.0  ← wystarczające zasoby

Jeśli warstwa transportu niedostępna:
    score_braku_transportu = 1.0  ← brak danych = zakładamy brak zasobów
```

**Uwaga:** liczymy tylko pojazdy `przyjmuje_niesamodzielnych = TRUE`
jeśli `niesamodzielni_procent > 0.5` — w takich placówkach zwykły bus nie wystarczy.

---

### 3.4 `score_braku_droznosci` — stan dróg wyjazdu

**Źródło danych:** warstwa L-04 (`drogi_ewakuacyjne.geojson`), atrybut `droznosc`

**Zapytanie PostGIS:** znajdź drogi w promieniu 2 km od placówki
(`ST_DWithin(placowka.geom, droga.geom, 0.018°)`) i sprawdź ich status.

**Logika:**

```
Jeśli wszystkie drogi w promieniu mają droznosc = 'zablokowana':
    score_braku_droznosci = 1.0

Jeśli część dróg zablokowana, część z utrudnieniami:
    score_braku_droznosci = 0.70

Jeśli część dróg zablokowana, część przejezdna:
    score_braku_droznosci = 0.50

Jeśli wszystkie drogi mają droznosc = 'utrudnienia' (brak zablokowanych):
    score_braku_droznosci = 0.30

Jeśli co najmniej jedna droga ma droznosc = 'przejezdna':
    score_braku_droznosci = 0.0

Jeśli brak danych o drogach w promieniu (warstwa L-04 offline lub brak dróg):
    score_braku_droznosci = 0.5  ← wartość domyślna z ike.config.json
```

---

### 3.5 `score_odleglosci_relokacji` — dystans do miejsca ewakuacji

**Źródło danych:** tabela `miejsca_relokacji`

**Zapytanie PostGIS:** `ST_Distance` (w EPSG:2180, wynik w metrach) do najbliższego
miejsca relokacji z `pojemnosc_dostepna > 0`.

Maksymalny promień z `ike.config.json`: `promienie_km.miejsca_relokacji = 50 km`

**Logika:**

```
d = ST_Distance (placówka → najbliższe dostępne miejsce relokacji) [km]

Jeśli d ≤ 10 km:
    score_odleglosci_relokacji = 0.0   ← blisko, niskie ryzyko

Jeśli 10 < d ≤ 25 km:
    score_odleglosci_relokacji = 0.25

Jeśli 25 < d ≤ 40 km:
    score_odleglosci_relokacji = 0.50

Jeśli 40 < d ≤ 50 km:
    score_odleglosci_relokacji = 0.75

Jeśli d > 50 km lub brak dostępnych miejsc relokacji:
    IKE = NULL  ← nie wyliczaj; zaloguj WARN i wyklucz z Top10
```

---

## 4. Progi kategorii IKE

| Zakres IKE | Kategoria | Kolor markera | Kolor hex | Akcja rekomendowana |
|---|---|---|---|---|
| 0.70 – 1.00 | `czerwony` | 🔴 Czerwony | `#EF4444` | Ewakuacja natychmiastowa |
| 0.40 – 0.69 | `zolty` | 🟡 Żółty | `#F59E0B` | Monitoring, przygotowanie do ewakuacji |
| 0.00 – 0.39 | `zielony` | 🟢 Zielony | `#22C55E` | Brak bezpośredniego zagrożenia |
| `null` | `nieznany` | ⚫ Szary | `#6B7280` | Brak danych — wyklucz z Top10 |

---

## 5. Edge case'y — tabela decyzyjna

Kompletna lista sytuacji, które `IkeService.java` musi obsłużyć jawnie.
Każdy nieobsłużony edge case = cicha awaria lub NullPointerException.

| # | Sytuacja | Zachowanie | Logowanie |
|---|---|---|---|
| E1 | `liczba_podopiecznych = 0` | Zwróć `IKE = 0.0`, kategoria `zielony` | INFO |
| E2 | `niesamodzielni_procent` = NULL | Użyj `0.50` (domyślna) | WARN |
| E3 | Warstwa zagrożeń (L-03) niedostępna | `score_zagrozenia = 0.0` | WARN |
| E4 | Warstwa transportu (L-05) niedostępna | `score_braku_transportu = 1.0` | WARN |
| E5 | Warstwa dróg (L-04) niedostępna | `score_braku_droznosci = 0.5` | WARN |
| E6 | Brak miejsc relokacji w promieniu 50 km | `IKE = null`, wyklucz z Top10 | WARN |
| E7 | Placówka nakłada się na wiele stref zagrożeń | Weź max `score_zagrozenia` | DEBUG |
| E8 | N = 0 pojazdów dostępnych w promieniu, ale `dostepny=FALSE` u wszystkich | `score_braku_transportu = 1.0` | INFO |
| E9 | `pojemnosc_ogolna = NULL` lub `= 0` | Pomiń placówkę, zaloguj błąd danych | ERROR |
| E10 | `geom` placówki NULL (brak współrzędnych) | Pomiń placówkę, zaloguj błąd danych | ERROR |
| E11 | Suma wag ≠ 1.0 (błąd konfiguracji) | Rzuć `IllegalStateException` przy starcie | ERROR |
| E12 | Wszystkie miejsca relokacji mają `pojemnosc_dostepna = 0` | `IKE = null` dla wszystkich, zaloguj | ERROR |
| E13 | `niesamodzielni_procent > 1.0` (błąd danych) | Ogranicz do `1.0`, zaloguj | WARN |
| E14 | Recalculate wywołany podczas trwającego obliczania | Odrzuć drugi request, zwróć 409 | WARN |

---

## 6. Przykłady obliczenia IKE

### Przykład A — DPS-CHE-002 (Sawin) — wynik czerwony

Dane placówki:
- `liczba_podopiecznych = 55`, `pojemnosc_ogolna = 60`
- `niesamodzielni_procent = 0.89`
- `generator_backup = false`
- Lokalizacja: 51.2108°N, 23.3984°E

Dane kontekstowe (symulacja kryzysu powodzi):
- Placówka leży w strefie zagrożenia powodziowego — poziom `czerwony`
- Pojazdy dostępne w promieniu 15 km: `N = 0` (strefa odcięta)
- Drogi wyjazdu: 1 droga `zablokowana`, 1 droga `utrudnienia`
- Najbliższe dostępne miejsce relokacji: 22 km

Obliczenie:

```
score_zagrozenia           = 1.00  (strefa czerwona)
score_niesamodzielnych     = 0.89  (niesamodzielni_procent)
score_braku_transportu     = 1.00  (N=0 pojazdów)
score_braku_droznosci      = 0.70  (zablokowana + utrudnienia)
score_odleglosci_relokacji = 0.25  (22 km → przedział 10–25 km)

IKE = (0.35 × 1.00)
    + (0.25 × 0.89)
    + (0.20 × 1.00)
    + (0.15 × 0.70)
    + (0.05 × 0.25)

IKE = 0.3500 + 0.2225 + 0.2000 + 0.1050 + 0.0125
IKE = 0.8900
```

**Wynik: IKE = 0.89 → kategoria `czerwony` 🔴 — ewakuacja natychmiastowa**

---

### Przykład B — DPS-LBL-001 (Niemce) — wynik żółty

Dane placówki:
- `liczba_podopiecznych = 72`, `pojemnosc_ogolna = 80`
- `niesamodzielni_procent = 0.68`
- `generator_backup = true`
- Lokalizacja: 51.3012°N, 22.5891°E

Dane kontekstowe (zagrożenie na peryferiach):
- Placówka leży w strefie zagrożenia — poziom `zolty`
- Pojazdy w promieniu 15 km: `N = 2`
- Drogi wyjazdu: 2 drogi `przejezdna`
- Najbliższe dostępne miejsce relokacji: 8 km

Obliczenie:

```
score_zagrozenia           = 0.60  (strefa żółta)
score_niesamodzielnych     = 0.68
score_braku_transportu     = 0.50  (N=2)
score_braku_droznosci      = 0.00  (co najmniej jedna droga przejezdna)
score_odleglosci_relokacji = 0.00  (8 km ≤ 10 km)

IKE = (0.35 × 0.60)
    + (0.25 × 0.68)
    + (0.20 × 0.50)
    + (0.15 × 0.00)
    + (0.05 × 0.00)

IKE = 0.2100 + 0.1700 + 0.1000 + 0.0000 + 0.0000
IKE = 0.4800
```

**Wynik: IKE = 0.48 → kategoria `zolty` 🟡 — przygotowanie do ewakuacji**

---

### Przykład C — DPS-LUK-002 (Stoczek Łukowski) — wynik zielony

Dane placówki:
- `liczba_podopiecznych = 17`, `pojemnosc_ogolna = 20`
- `niesamodzielni_procent = 0.20`
- `generator_backup = false`
- Lokalizacja: 51.9640°N, 22.0811°E

Dane kontekstowe (brak zagrożenia):
- Placówka poza strefami zagrożenia
- Pojazdy w promieniu 15 km: `N = 4`
- Drogi: wszystkie `przejezdna`
- Najbliższe dostępne miejsce relokacji: 12 km

Obliczenie:

```
score_zagrozenia           = 0.00  (poza strefą)
score_niesamodzielnych     = 0.20
score_braku_transportu     = 0.00  (N≥4)
score_braku_droznosci      = 0.00  (przejezdne)
score_odleglosci_relokacji = 0.25  (12 km → przedział 10–25 km)

IKE = (0.35 × 0.00)
    + (0.25 × 0.20)
    + (0.20 × 0.00)
    + (0.15 × 0.00)
    + (0.05 × 0.25)

IKE = 0.0000 + 0.0500 + 0.0000 + 0.0000 + 0.0125
IKE = 0.0625
```

**Wynik: IKE = 0.06 → kategoria `zielony` 🟢 — brak bezpośredniego zagrożenia**

---

### Przykład D — edge case E2 + E5 (brakujące dane)

Dane placówki:
- `niesamodzielni_procent = NULL` → użyj domyślnej `0.50`
- Warstwa dróg niedostępna → użyj domyślnej `0.50`
- Strefa zagrożenia: `czerwona`
- Pojazdy w promieniu: `N = 1`
- Odległość relokacji: 35 km

Obliczenie z wartościami domyślnymi:

```
score_zagrozenia           = 1.00  (strefa czerwona)
score_niesamodzielnych     = 0.50  ← DOMYŚLNA (NULL w bazie, log WARN)
score_braku_transportu     = 0.75  (N=1)
score_braku_droznosci      = 0.50  ← DOMYŚLNA (L-04 offline, log WARN)
score_odleglosci_relokacji = 0.50  (35 km → przedział 25–40 km)

IKE = (0.35 × 1.00)
    + (0.25 × 0.50)
    + (0.20 × 0.75)
    + (0.15 × 0.50)
    + (0.05 × 0.50)

IKE = 0.3500 + 0.1250 + 0.1500 + 0.0750 + 0.0250
IKE = 0.7250
```

**Wynik: IKE = 0.73 → kategoria `czerwony` 🔴**
W odpowiedzi API dodaj flagi `data_warnings`:
```json
"data_warnings": ["niesamodzielni_procent_brak", "warstwa_drogi_offline"]
```

---

## 7. Wynik IKE — struktura DTO

`IkeResult.java` — obiekt zwracany przez `GET /api/ike` dla każdej placówki.

```json
{
  "placowka_kod": "DPS-CHE-002",
  "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
  "powiat": "chelm",
  "gmina": "Sawin",
  "lat": 51.2108,
  "lon": 23.3984,
  "liczba_podopiecznych": 55,
  "niesamodzielni_liczba": 49,
  "ike_score": 0.8900,
  "ike_kategoria": "czerwony",
  "skladowe": {
    "score_zagrozenia": 1.00,
    "score_niesamodzielnych": 0.89,
    "score_braku_transportu": 1.00,
    "score_braku_droznosci": 0.70,
    "score_odleglosci_relokacji": 0.25
  },
  "cel_relokacji": {
    "kod": "REL-CHE-001",
    "nazwa": "Hala Sportowa MOSiR Chełm",
    "odleglosc_km": 22.4,
    "pojemnosc_dostepna": 180,
    "przyjmuje_niesamodzielnych": true
  },
  "trasa_ewakuacji_geojson": {
    "type": "LineString",
    "coordinates": [[23.3984, 51.2108], [23.3800, 51.2200], [23.4720, 51.1433]]
  },
  "czas_przejazdu_min": 31,
  "data_warnings": [],
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

### Przypadek gdy IKE = null (edge case E6 / E12)

```json
{
  "placowka_kod": "DPS-VLO-002",
  "placowka_nazwa": "Dom Pomocy Społecznej w Hannie",
  "ike_score": null,
  "ike_kategoria": "nieznany",
  "cel_relokacji": null,
  "trasa_ewakuacji_geojson": null,
  "czas_przejazdu_min": null,
  "data_warnings": ["brak_miejsca_relokacji_w_promieniu_50km"],
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

---

## 8. Konfiguracja wag

Wagi wczytywane są z pliku `frontend/src/config/ike.config.json`
przez endpoint `GET /api/ike/config` i cachowane w pamięci przez `IkeService`.

**Walidacja przy starcie (`@PostConstruct` w `IkeService`):**

```java
double sum = wagi.zagrozenie + wagi.niesamodzielni + wagi.transportBrak
           + wagi.droznoscBrak + wagi.odlegloscRelokacji;

if (Math.abs(sum - 1.0) > 0.001) {
    throw new IllegalStateException(
        "Suma wag IKE ≠ 1.0 (wartość: " + sum + "). Sprawdź ike.config.json."
    );
}
```

**Zmiana wag bez rekompilacji:**

1. Edytuj `frontend/src/config/ike.config.json`
2. Wywołaj `POST /api/ike/recalculate` — IkeService przeładuje config i przeliczy
3. Frontend automatycznie pobierze nowe wyniki przez `GET /api/ike`

---

## 9. Zapytania PostGIS używane przez IkeService

Wszystkie zapytania używają EPSG:2180 (Polska CS92) dla dokładnych pomiarów odległości.

### 9.1 Sprawdzenie strefy zagrożenia

```sql
SELECT s.poziom, s.typ_zagrozenia
FROM strefy_zagrozen s
WHERE ST_Intersects(
    ST_Transform(p.geom, 2180),
    ST_Transform(s.geom, 2180)
)
AND p.kod = :placowkaKod
ORDER BY
    CASE s.poziom
        WHEN 'czerwony' THEN 1
        WHEN 'zolty'    THEN 2
        WHEN 'zielony'  THEN 3
    END
LIMIT 1;
```

### 9.2 Liczba dostępnych pojazdów w promieniu

```sql
SELECT COUNT(*) as liczba_pojazdow
FROM zasob_transportu t
WHERE t.dostepny = TRUE
  AND ST_DWithin(
      ST_Transform(t.geom, 2180),
      ST_Transform(
          (SELECT geom FROM placowka WHERE kod = :placowkaKod),
          2180
      ),
      :promienMetry   -- np. 15000 dla 15 km
  );
```

Wariant dla placówek z >50% niesamodzielnych (tylko pojazdy przystosowane):

```sql
SELECT COUNT(*) as liczba_pojazdow
FROM zasob_transportu t
WHERE t.dostepny = TRUE
  AND t.przyjmuje_niesamodzielnych = TRUE
  AND ST_DWithin(
      ST_Transform(t.geom, 2180),
      ST_Transform(
          (SELECT geom FROM placowka WHERE kod = :placowkaKod),
          2180
      ),
      :promienMetry
  );
```

### 9.3 Stan dróg w promieniu wyjazdu

```sql
SELECT d.droznosc, COUNT(*) as liczba
FROM drogi_ewakuacyjne d
WHERE ST_DWithin(
    ST_Transform(d.geom, 2180),
    ST_Transform(
        (SELECT geom FROM placowka WHERE kod = :placowkaKod),
        2180
    ),
    2000  -- 2 km
)
GROUP BY d.droznosc;
```

### 9.4 Najbliższe miejsce relokacji z wolnymi miejscami

```sql
SELECT
    r.kod,
    r.nazwa,
    r.przyjmuje_niesamodzielnych,
    r.pojemnosc_dostepna,
    ST_Distance(
        ST_Transform(r.geom, 2180),
        ST_Transform(
            (SELECT geom FROM placowka WHERE kod = :placowkaKod),
            2180
        )
    ) / 1000.0 AS odleglosc_km
FROM miejsca_relokacji r
WHERE r.pojemnosc_dostepna > 0
ORDER BY odleglosc_km ASC
LIMIT 1;
```

---

## 10. Kolejność kroków w IkeService.java

Poniżej pseudokod opisujący wymaganą kolejność operacji przy obliczaniu IKE
dla jednej placówki. Implementuj dokładnie w tej kolejności — kolejność wpływa
na obsługę edge case'ów.

```
FUNKCJA obliczIke(placowkaKod):

  1. Pobierz placówkę z bazy. Jeśli nie istnieje → rzuć wyjątek.

  2. Sprawdź warunki dyskwalifikujące (zwróć IKE=null natychmiast):
     - geom IS NULL                     → ERROR, zwróć null
     - pojemnosc_ogolna IS NULL lub = 0 → ERROR, zwróć null

  3. Sprawdź warunek zerowania (zwróć IKE=0.0 natychmiast):
     - liczba_podopiecznych = 0         → zwróć IKE=0.0, kategoria=zielony

  4. Oblicz score_zagrozenia (zapytanie 9.1):
     - Jeśli L-03 offline → score = 0.0, dodaj warning

  5. Oblicz score_niesamodzielnych:
     - Jeśli niesamodzielni_procent IS NULL → score = 0.50, dodaj warning
     - Jeśli niesamodzielni_procent > 1.0  → score = 1.0, dodaj warning
     - W przeciwnym razie → score = niesamodzielni_procent

  6. Oblicz score_braku_transportu (zapytanie 9.2 / 9.2-wariant):
     - Użyj wariantu z przyjmuje_niesamodzielnych jeśli score_nies > 0.5
     - Jeśli L-05 offline → score = 1.0, dodaj warning
     - Mapuj N na score według tabeli z sekcji 3.3

  7. Oblicz score_braku_droznosci (zapytanie 9.3):
     - Jeśli L-04 offline lub brak dróg w promieniu → score = 0.5, dodaj warning
     - Mapuj kombinację statusów na score według tabeli z sekcji 3.4

  8. Znajdź najbliższe miejsce relokacji (zapytanie 9.4):
     - Jeśli brak wyników (odległość > 50 km lub brak wolnych miejsc)
       → zwróć IKE=null, kategoria=nieznany, dodaj warning

  9. Oblicz score_odleglosci_relokacji:
     - Mapuj odleglosc_km na score według tabeli z sekcji 3.5

  10. Oblicz IKE:
      IKE = W_zag  * score_zagrozenia
          + W_nies * score_niesamodzielnych
          + W_tran * score_braku_transportu
          + W_droz * score_braku_droznosci
          + W_odl  * score_odleglosci_relokacji
      Zaokrąglij do 4 miejsc po przecinku.

  11. Wyznacz kategorię:
      - IKE >= 0.70 → czerwony
      - IKE >= 0.40 → zolty
      - IKE <  0.40 → zielony

  12. Pobierz trasę ewakuacji z OSRM (przez routingService):
      - Punkt startowy: placowka.geom
      - Punkt docelowy: cel_relokacji.geom
      - Jeśli OSRM niedostępny → trasa=null, nie blokuj wyniku IKE

  13. Zapisz wynik do tabeli ike_results (upsert po placowka_kod).

  14. Zwróć IkeResult DTO.
```
