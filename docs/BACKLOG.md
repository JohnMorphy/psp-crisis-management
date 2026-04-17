# BACKLOG.md — Plan implementacji

> Jedyne źródło prawdy dla zadań agentowych.
> `docs/ARCHITEKTURA_PLAN.md` opisuje *co* i *dlaczego*. Ten plik opisuje *jak* i *kiedy*.
>
> **Przed każdą sesją:** przeczytaj sekcję "Aktywne zadanie", zaimplementuj, zweryfikuj,
> zrób commit, zmień status na ✅, zakończ sesję.

---

## Statusy

| Symbol | Znaczenie |
|---|---|
| ⬜ | Nie rozpoczęte |
| 🔄 | W toku (aktywna sesja) |
| ✅ | Ukończone i zcommitowane |
| ⏸ | Zablokowane (powód w notatce) |

---

## Aktywne zadanie

> Ustaw tutaj numer zadania przed startem sesji. Jedno zadanie na raz.

**Aktywne:** `— (ustaw przed startem sesji)`

---

## Iteracja v1.0 — Fundament GIS

Cel: działająca mapa z DPS-ami, Spring Boot serwuje dane z PostgreSQL przez REST.
IKE liczone synchronicznie na żądanie (bez eventów — uproszczona wersja na start).

Deliverable: `curl http://localhost:8080/api/layers` zwraca 7 warstw,
`http://localhost:5173` pokazuje mapę z 48 markerami DPS.

---

### ✅ 1.1 — Infrastruktura bazy danych

**Pliki do stworzenia:**
- `docker-compose.yml`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/seed_layers.sql`
- `backend/src/main/resources/db/seed_dps.sql`
- `backend/src/main/resources/db/seed_relokacja.sql`
- `backend/src/main/resources/db/seed_transport.sql`
- `backend/src/main/resources/db/seed_strefy.sql`
- `.env.example`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (sekcje 1–6), `docs/DEPLOYMENT.md` (sekcje 1–3)

**Opis:**
Uruchom PostgreSQL 15 + PostGIS w Dockerze. Stwórz schemat SQL ze wszystkimi tabelami
z `DATA_SCHEMA.md` §1. Napisz pliki seed dla wszystkich 5 tabel danych.
`docker-entrypoint-initdb.d` wykonuje pliki automatycznie przy pierwszym starcie.

**Weryfikacja:**
```bash
docker compose up -d postgres
sleep 10
docker compose exec postgres psql -U lublin -d gis_dashboard -c "SELECT COUNT(*) FROM placowka;"
# oczekiwane: 46

docker compose exec postgres psql -U lublin -d gis_dashboard -c "SELECT COUNT(*) FROM layer_config;"
# oczekiwane: 7

docker compose exec postgres psql -U lublin -d gis_dashboard -c "SELECT COUNT(*) FROM strefy_zagrozen;"
# oczekiwane: 3 (seed demo)

# Sprawdź PostGIS
docker compose exec postgres psql -U lublin -d gis_dashboard -c "SELECT ST_AsText(geom) FROM placowka LIMIT 1;"
# oczekiwane: POINT(...)
```

**Commit:** `feat(1.1): docker-compose + schema SQL + seed files (48 DPS, 7 layers)`

---

### ✅ 1.2 — Setup Spring Boot

**Pliki do stworzenia:**
- `backend/pom.xml`
- `backend/src/main/java/pl/lublin/dashboard/DashboardApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/pl/lublin/dashboard/config/DataSourceConfig.java`
- `backend/src/main/java/pl/lublin/dashboard/config/CorsConfig.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (sekcje 1, 6), `docs/DEPLOYMENT.md` (§10)

**Opis:**
Spring Boot 3.x, OpenJDK 21. Zależności Maven: `spring-boot-starter-web`,
`spring-boot-starter-data-jpa`, `postgresql`, `hibernate-spatial`, `spring-boot-starter-websocket`.
Profil `dev`: baza na `localhost:5432`, CORS dla `http://localhost:5173`.

**Weryfikacja:**
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# Oczekiwane w logach: "Started DashboardApplication in X seconds"

curl -s http://localhost:8080/actuator/health | jq .status
# oczekiwane: "UP"
```

**Commit:** `feat(1.2): Spring Boot 3 setup — pom.xml, DataSource, CORS`

---

### ✅ 1.3 — Setup frontend (Vite + React + Leaflet)

**Pliki do stworzenia:**
- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/tailwind.config.js`
- `frontend/src/main.tsx`
- `frontend/src/App.tsx`
- `frontend/src/services/api.ts`
- `frontend/src/components/layout/AppShell.tsx`
- `frontend/src/components/layout/Header.tsx`

**Dokumenty referencyjne:** `CLAUDE.md` (sekcja Stack technologiczny, Layout aplikacji)

**Opis:**
Vite + React 18 + Tailwind CSS. Zależności npm: `react-leaflet`, `leaflet`, `zustand`,
`@tanstack/react-query`, `axios`.
Layout 70/30: mapa po lewej (placeholder `<div>`), panel boczny po prawej (Do wykonania w przyszłym zadaniu)
`api.ts` — klient axios z `baseURL` z `VITE_API_BASE_URL`.
Ciemny motyw: `bg-gray-900`.

**Weryfikacja:**
```bash
cd frontend
npm install
npm run build   # 0 błędów
npm run dev
# http://localhost:5173 — widoczny layout z headerem
# DevTools: brak błędów konsoli
```

**Commit:** `feat(1.3): Vite + React 18 + Tailwind — AppShell, Header, api.ts`

---

### ✅ 1.4 — Encje JPA i repozytoria

**Pliki do stworzenia:**
- `backend/.../model/Placowka.java`
- `backend/.../model/LayerConfig.java`
- `backend/.../model/StrefaZagrozen.java`
- `backend/.../model/MiejsceRelokacji.java`
- `backend/.../model/ZasobTransportu.java`
- `backend/.../repository/PlacowkaRepository.java`
- `backend/.../repository/LayerConfigRepository.java`
- `backend/.../repository/StrefaZagrozenRepository.java`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (§1 — DDL każdej tabeli)

**Opis:**
Encje JPA mapowane na tabele z `schema.sql`. Geometrie przez Hibernate Spatial
(`org.locationtech.jts.geom.Point`, `Polygon`, `LineString`). Repozytoria jako
`JpaRepository`. Żadnej logiki biznesowej w encjach — tylko mapping.

**Weryfikacja:**
```bash
./mvnw compile -q
# 0 błędów kompilacji

# Test że encje ładują się z bazy:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
sleep 8
curl -s http://localhost:8080/actuator/health
# Brak błędów Hibernate w logach (grep "ERROR" w output mvnw)
```

Dodatkowo: pliki cmd uruchamiające aplikacje za pomocą dockera:
start-all.cmd (uruchamia docker-compose.full.yml)
i
start-dev.cmd (uruchamia docker-compose.yml)

**Commit:** `feat(1.4): encje JPA — Placowka, LayerConfig, StrefaZagrozen, repozytoria`

---

### ✅ 1.5 — GeoService + GeoJSON granic + endpoint warstw

**Pliki do stworzenia:**
- `backend/.../service/GeoService.java`
- `backend/.../controller/GeoController.java`
- `backend/.../controller/LayerConfigController.java`
- `backend/src/main/resources/geojson/lublin_powiaty.geojson`
- `backend/src/main/resources/geojson/lublin_gminy.geojson`

**Dokumenty referencyjne:** `docs/API_REFERENCE.md` (`GET /api/layers`, `GET /api/layers/{id}`), `docs/DATA_SCHEMA.md` (§7)

**Opis:**
`GeoService` ładuje GeoJSON granic z classpath (`geojson/`). `GeoController` serwuje:
- `GET /api/layers` — lista `LayerConfig` z bazy
- `GET /api/layers/L-00` — GeoJSON granic administracyjnych (z pliku, nie z bazy)
- `GET /api/layers/{id}` — dane GeoJSON warstwy L-01…L-07 z bazy jako FeatureCollection

Pobierz pliki granic zgodnie z instrukcją w `DATA_SCHEMA.md` §7 (GADM 4.1).
Jeśli GADM niedostępny — użyj bounding box fallback z `DATA_SCHEMA.md` §7.

Format odpowiedzi: dokładnie jak w `API_REFERENCE.md` §`GET /api/layers/{id}`.

**Weryfikacja:**
```bash
curl -s http://localhost:8080/api/layers | jq '. | length'
# oczekiwane: 7

curl -s http://localhost:8080/api/layers/L-01 | jq '.feature_count'
# oczekiwane: 48

curl -s http://localhost:8080/api/layers/L-00 | jq '.type'
# oczekiwane: "FeatureCollection"

curl -s http://localhost:8080/api/layers/L-99 | jq .code
# oczekiwane: "LAYER_NOT_FOUND"
```

**Commit:** `feat(1.5): GeoService + GeoController — GET /api/layers, granice administracyjne`

---

### ✅ 1.6 — IkeAgent (wersja synchroniczna) + IkeController

**Pliki do stworzenia:**
- `backend/.../agent/IkeAgent.java` (wersja v1.0 — bez eventów, wywołanie synchroniczne)
- `backend/.../controller/IkeController.java`
- `backend/src/main/resources/ike.config.json`
- `backend/.../model/IkeResult.java`
- `backend/.../repository/IkeResultRepository.java`

**Dokumenty referencyjne:** `docs/IKE_ALGORITHM.md` (cały dokument), `docs/DATA_SCHEMA.md` (§8), `docs/API_REFERENCE.md` (`GET /api/ike`, `GET /api/ike/{kod}`, `GET /api/ike/config`)

**Opis:**
W v1.0 `IkeAgent` jest zwykłym `@Service` wywoływanym synchronicznie przez kontroler
(bez `@EventListener` — to przychodzi w zadaniu 2.3).
Zaimplementuj pełny algorytm IKE z `IKE_ALGORITHM.md` — wszystkie 5 score'ów,
edge case'y z tabeli §6, zapis do `ike_results`.
Wczytaj `ike.config.json` przez `ClassPathResource` w `@PostConstruct`.

`IkeController`: `GET /api/ike`, `GET /api/ike/{kod}`, `GET /api/ike/config`.

**Weryfikacja:**
```bash
# Przelicz IKE dla wszystkich placówek
curl -s -X POST http://localhost:8080/api/ike/recalculate
sleep 5

# Sprawdź wyniki
curl -s "http://localhost:8080/api/ike?limit=3" | jq '.wyniki[0].ike_score'
# oczekiwane: liczba 0.0–1.0

# Placówka w strefie demo powinna mieć score_zagrozenia > 0
curl -s "http://localhost:8080/api/ike/DPS-CHE-002" | jq '.skladowe.score_zagrozenia'
# oczekiwane: > 0 (DPS w Sawinie jest w DEMO-POWODZ-001)

# Konfiguracja
curl -s http://localhost:8080/api/ike/config | jq '.wagi.zagrozenie'
# oczekiwane: 0.35

# Sprawdź zapis do bazy
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT placowka_kod, ike_score, ike_kategoria FROM ike_results ORDER BY ike_score DESC LIMIT 5;"
```

**Commit:** `feat(1.6): IkeAgent synchroniczny + IkeController + ike.config.json`

---

### ✅ 1.7 — Mapa Leaflet z granicami i warstwą DPS

**Pliki do stworzenia:**
- `frontend/src/components/map/MapContainer.tsx`
- `frontend/src/components/map/AdminBoundaries.tsx`
- `frontend/src/components/map/layers/DPSLayer.tsx`
- `frontend/src/components/map/layers/ThreatZoneLayer.tsx`
- `frontend/src/components/map/DPSPopup.tsx`
- `frontend/src/hooks/useLayerData.ts`

**Dokumenty referencyjne:** `CLAUDE.md` (Layout, Popup DPS, kolory IKE), `docs/API_REFERENCE.md` (`GET /api/layers/{id}`)

**Opis:**
`MapContainer` — React-Leaflet, viewport Lublin (51.25, 22.57, zoom 9), podkład OSM.
`AdminBoundaries` — GeoJSON z `GET /api/layers/L-00`, klik → podświetlenie powiatu.
`DPSLayer` — markery z `GET /api/layers/L-01`, kolor wg `ike_kategoria`
(czerwony `#EF4444` / żółty `#F59E0B` / zielony `#22C55E`).
`DPSPopup` — Leaflet Popup (nie modal) z danymi z `properties`.
`ThreatZoneLayer` — poligony z `GET /api/layers/L-03`.
`useLayerData` — React Query z `staleTime: 60_000`.

**Weryfikacja:**
```
Manualne — przeglądarka http://localhost:5173:
☐ Mapa widoczna, viewport na Lublin
☐ Granice powiatów widoczne jako linie
☐ Kliknięcie powiatu → podświetlenie
☐ Markery DPS widoczne — kolory zależne od IKE
☐ Kliknięcie markera → Popup z danymi placówki
☐ Strefy demo widoczne jako czerwone/żółte poligony
☐ DevTools Network: GET /api/layers/L-01 → 200, feature_count: 48
```

**Commit:** `feat(1.7): MapContainer + AdminBoundaries + DPSLayer + ThreatZoneLayer`

---

### ✅ 1.8 — Panele boczne v1.0

**Pliki do stworzenia:**
- `frontend/src/components/panels/LayerControlPanel.tsx`
- `frontend/src/components/panels/RegionInfoPanel.tsx`
- `frontend/src/store/mapStore.ts`

**Dokumenty referencyjne:** `CLAUDE.md` (Layout aplikacji), `docs/API_REFERENCE.md` (`GET /api/layers`)

**Opis:**
`LayerControlPanel` — lista warstw z `GET /api/layers`, toggle włącz/wyłącz każdą,
znacznik czasu ostatniej aktualizacji.
`RegionInfoPanel` — statystyki klikniętego regionu (liczba placówek, suma podopiecznych).
`mapStore` (Zustand) — aktywne warstwy, wybrany region.
Panel boczny zwijany (`<<` przycisk).

**Weryfikacja:**
```
Manualne:
☐ LayerControlPanel wyświetla 7 warstw z nazwami
☐ Toggle warstwy włącza/wyłącza markery/poligony na mapie
☐ Kliknięcie powiatu → RegionInfoPanel pokazuje statystyki
☐ Przycisk "<<" zwija panel — mapa zajmuje pełną szerokość
```

**Commit:** `feat(1.8): LayerControlPanel + RegionInfoPanel + Zustand mapStore`

---

### ✅ 1.9 — Tabela granice_administracyjne + AdminBoundaryImportAgent (PRG WFS)

**Pliki do stworzenia / modyfikacji:**
- `backend/src/main/resources/db/schema.sql` (dodaj tabelę `granice_administracyjne`)
- `backend/.../model/AdminBoundaryBoudries.java`
- `backend/.../repository/AdminBoundaryRepository.java`
- `backend/.../agent/AdminBoundaryImportAgent.java`
- `backend/.../controller/AdminBoundaryController.java`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (§1 — nowa tabela, §9), `docs/API_REFERENCE.md` (`POST /api/admin-boundaries/import`)

**Opis:**

Nowa tabela `granice_administracyjne` przechowuje granice administracyjne całej Polski
na trzech poziomach: województwo (16), powiat (~380), gmina (~2500).
Źródłem danych jest **GUGiK PRG WFS** (Państwowy Rejestr Granic):

```
URL: https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeDivision
TypeName poziomów:
  ms:A02_Granice_Wojewodztw   → poziom 'wojewodztwo'
  ms:A03_Granice_Powiatow     → poziom 'powiat'
  ms:A04_Granice_Gmin         → poziom 'gmina'
CRS źródłowy: EPSG:2180 (PUWG 1992) — transformacja do EPSG:4326 przez GeoTools
```

`AdminBoundaryImportAgent` to zwykły `@Service` wywoływany synchronicznie przez kontroler
(import trwa ~2-5 min — odpowiedź 202, agent działa w osobnym wątku `@Async`).
Kolejność importu: najpierw województwa, potem powiaty, potem gminy.
Import jest idempotentny: przed zapisem usuwa istniejące rekordy danego `poziom`.

**Nie implementuj** w tym zadaniu endpointów GET dla danych granic — to zadanie 1.10.

`POST /api/admin-boundaries/import` zwraca 202 z body:
```json
{ "status": "started", "poziomy": ["wojewodztwo", "powiat", "gmina"], "correlation_id": "..." }
```

**Weryfikacja:**
```bash
./mvnw compile -q

# Uruchom import (trwa kilka minut)
curl -s -X POST http://localhost:8080/api/admin-boundaries/import | jq .status
# oczekiwane: "started"

sleep 300  # poczekaj na zakończenie

# Sprawdź wyniki
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT poziom, COUNT(*) FROM granice_administracyjne GROUP BY poziom ORDER BY poziom;"
# oczekiwane:
#  gmina        | ~2477
#  powiat        | ~380
#  wojewodztwo   | 16

# Sprawdź SRID i geometrię
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT ST_SRID(geom), ST_AsText(ST_Centroid(geom)) FROM granice_administracyjne
      WHERE poziom='wojewodztwo' AND nazwa ILIKE '%lubel%';"
# SRID: 4326, centroid w okolicach (22-23°E, 51°N)
```

**Potencjalne usprawnienia (dług techniczny — nie implementuj teraz):**
- Podwójna geometria: `geom` (pełna) + `geom_uproszczona` (ST_Simplify ~100m) dla wydajności frontendu
- `@Scheduled` roczny autorefresh granic

**Commit:** `feat(1.9): granice_administracyjne + AdminBoundaryImportAgent (PRG WFS)`

---

### ✅ 1.10 — API: warstwy L-08/L-09/L-10 (granice administracyjne całej Polski)

**Pliki do stworzenia / modyfikacji:**
- `backend/.../service/AdminBoundaryService.java`
- `backend/.../controller/GeoController.java` (rozszerzenie obsługi L-08, L-09, L-10)
- `backend/src/main/resources/db/seed_layers.sql` (dodaj L-08, L-09, L-10)

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (§5 — seed_layers, §9), `docs/API_REFERENCE.md` (`GET /api/layers/{id}`)

**Opis:**

Trzy nowe warstwy w `layer_config`:

| ID | Poziom | Domyślnie | Opis |
|---|---|---|---|
| `L-08` | `wojewodztwo` | ✅ włączona | 16 granic województw |
| `L-09` | `powiat` | ❌ wyłączona | ~380 granic powiatów |
| `L-10` | `gmina` | ❌ wyłączona | ~2477 granic gmin |

`AdminBoundaryService` odpowiada za zapytania do `granice_administracyjne`:
- `GET /api/layers/L-08` → wszystkie województwa (brak filtrów, zawsze 16 features)
- `GET /api/layers/L-09` → powiaty; opcjonalny `?kod_woj=06` filtruje do województwa (zalecane przy dużych zbiorach)
- `GET /api/layers/L-10` → gminy; **wymagany** `?kod_woj=XX` lub `?bbox=...` — bez filtra zwróć 400 z `FILTER_REQUIRED`

Każda feature w FeatureCollection zawiera properties:
```json
{
  "kod_teryt": "0601011",
  "nazwa": "Lublin",
  "poziom": "gmina",
  "kod_nadrzedny": "0601"
}
```

Rozszerz istniejący `GeoController.getLayerById()` o delegację do `AdminBoundaryService`
dla ID `L-08`, `L-09`, `L-10`. Zachowaj istniejącą obsługę L-00…L-07 bez zmian.

Operacja PostGIS do filtrowania po bbox:
```sql
WHERE poziom = :poziom
AND (:bbox IS NULL OR ST_Intersects(geom, ST_MakeEnvelope(:xmin,:ymin,:xmax,:ymax,4326)))
AND (:kod_woj IS NULL OR LEFT(kod_teryt, 2) = :kod_woj)
```

**Weryfikacja:**
```bash
# Lista warstw — powinna pokazać 10 pozycji (7 + 3 nowe)
curl -s http://localhost:8080/api/layers | jq '. | length'
# oczekiwane: 10

# Województwa — 16 features
curl -s http://localhost:8080/api/layers/L-08 | jq '.feature_count'
# oczekiwane: 16

# Powiaty z filtrem województwa lubelskiego (kod 06)
curl -s "http://localhost:8080/api/layers/L-09?kod_woj=06" | jq '.feature_count'
# oczekiwane: 24 (powiaty woj. lubelskiego)

# Gminy bez filtra — powinno zwrócić błąd
curl -s http://localhost:8080/api/layers/L-10 | jq .code
# oczekiwane: "FILTER_REQUIRED"

# Gminy z filtrem
curl -s "http://localhost:8080/api/layers/L-10?kod_woj=06" | jq '.feature_count'
# oczekiwane: ~213 (gminy woj. lubelskiego)

# properties jednej cechy
curl -s http://localhost:8080/api/layers/L-08 | jq '.features[0].properties'
# oczekiwane: obiekt z kod_teryt, nazwa, poziom, kod_nadrzedny
```

**Commit:** `feat(1.10): AdminBoundaryService + GET /api/layers/L-08,L-09,L-10`

---

### ✅ 1.11 — Frontend: AdminBoundaryLayer wielopoziomowy + RegionInfoPanel update

**Pliki do stworzenia / modyfikacji:**
- `frontend/src/components/map/layers/AdminBoundaryLayer.tsx` (nowy, zastępuje AdminBoundaries.tsx)
- `frontend/src/components/map/MapContainer.tsx` (zamień AdminBoundaries → AdminBoundaryLayer)
- `frontend/src/store/mapStore.ts` (L-08: true, L-09: false, L-10: false)
- `frontend/src/components/panels/RegionInfoPanel.tsx` (dodaj kod TERYT, obsługa spoza Lubelskiego)
- `frontend/src/hooks/useAdminBoundaries.ts` (hook z logiką filtrowania)

**Dokumenty referencyjne:** `CLAUDE.md` (Layout, kolory), `docs/API_REFERENCE.md` (`GET /api/layers/L-08`, `L-09`, `L-10`)

**Opis:**

`AdminBoundaryLayer` renderuje trzy niezależne warstwy Leaflet GeoJSON — po jednej
na każdy poziom administracyjny. Widoczność każdej kontrolowana przez `activeLayers`
w Zustand store.

Styl warstw:
```
L-08 województwa:  kolor #6366F1, weight 2, fillOpacity 0.04
L-09 powiaty:      kolor #4B5563, weight 1, fillOpacity 0.03
L-10 gminy:        kolor #374151, weight 0.5, fillOpacity 0.02
```

Przy kliknięciu regionu — niezależnie od poziomu — wyświetlany styl:
```
kolor #60A5FA, weight 2.5, fillOpacity 0.15
```
oraz zapis do `setSelectedRegion({ name: feature.properties.nazwa, kod_teryt: feature.properties.kod_teryt, poziom: feature.properties.poziom, properties: ... })`.

`useAdminBoundaries(poziom, kodWoj?)` — hook opakowujący `useLayerData` z dynamicznym
queryKey uwzględniającym filtr `kod_woj`. L-10 zawsze wymaga `kod_woj`.

`RegionInfoPanel` — rozszerzenie:
- Wyświetl `kod_teryt` i `poziom` dla każdego regionu
- Gdy powiat/gmina poza woj. lubelskim: wyświetl nazwę + TERYT + tekst
  „Brak danych placówek dla tego regionu"
- Gdy powiat/gmina woj. lubelskiego (`kod_teryt` zaczyna się od `06`):
  statystyki placówek jak dotychczas

**Nie usuwaj** `AdminBoundaries.tsx` — oznacz go jako `@deprecated` w komentarzu
na górze pliku. Usunięcie planowane po zakończeniu zadania 1.11.

**Weryfikacja:**
```
Manualne — przeglądarka http://localhost:5173:
☐ Przy starcie widoczne tylko granice województw (L-08 domyślnie włączona)
☐ Włączenie L-09 → pojawiają się granice powiatów
☐ Włączenie L-10 → pojawiają się granice gmin (może być wolne — to OK)
☐ Kliknięcie województwa → RegionInfoPanel: nazwa + TERYT 2-znakowy
☐ Kliknięcie powiatu lubelskiego → statystyki DPS w panelu
☐ Kliknięcie powiatu mazowieckiego → "Brak danych placówek dla tego regionu"
☐ Wyłączenie L-09 → granice powiatów znikają z mapy
☐ npm run build → 0 błędów TypeScript
```

**Potencjalne usprawnienia (dług techniczny — nie implementuj teraz):**
- Lazy loading: L-09 i L-10 ładuj dopiero po włączeniu toggle (useLayerData z `enabled: isActive`)
- Uproszczone geometrie (`geom_uproszczona`) gdy gminy będą zbyt wolne

**Commit:** `feat(1.11): AdminBoundaryLayer wielopoziomowy + RegionInfoPanel TERYT`

---

## Iteracja v1.1 — Event-driven core

Cel: pełny flow — wybór scenariusza przez operatora uruchamia automatyczny łańcuch
ThreatUpdatedEvent → IKE → IkeRecalculatedEvent → rekomendacje → WebSocket push do UI.

Deliverable: po kliknięciu "Aktywuj scenariusz Q100 / powiat chełmski" mapa
aktualizuje się automatycznie w ciągu 30 sekund bez odświeżenia strony.

---

### ⬜ 2.1 — Klasy eventów + AsyncConfig

**Pliki do stworzenia:**
- `backend/.../event/ThreatUpdatedEvent.java`
- `backend/.../event/IkeRecalculatedEvent.java`
- `backend/.../event/IkeResultSummary.java`
- `backend/.../config/AsyncConfig.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.1, §4.1a, §6)

**Opis:**
`ThreatUpdatedEvent` i `IkeRecalculatedEvent` dokładnie jak w `ARCHITEKTURA_PLAN.md` §4.1 i §4.1a.
`AsyncConfig` — `ThreadPoolTaskExecutor` z `corePoolSize=3`, `maxPoolSize=6`,
`queueCapacity=25`, prefix wątków `"agent-"`, bean name `"agentTaskExecutor"`.
`@EnableAsync` na klasie konfiguracyjnej.

**Weryfikacja:**
```bash
./mvnw compile -q
# 0 błędów — klasy eventów kompilują się poprawnie
```

**Commit:** `feat(2.1): ThreatUpdatedEvent + IkeRecalculatedEvent + AsyncConfig`

---

### ⬜ 2.2 — IkeAgent refaktor na @EventListener

**Pliki do modyfikacji:**
- `backend/.../agent/IkeAgent.java` — zmiana z `@Service` na `@EventListener @Async`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.3), `docs/IKE_ALGORITHM.md` (§2, §11)

**Opis:**
Refaktoruj `IkeAgent` z v1.0 (synchroniczny `@Service`) na event-driven listener.
Dodaj metodę `onThreatUpdated(@EventListener @Async ThreatUpdatedEvent)`.
Po obliczeniu wszystkich IKE: `publisher.publishEvent(new IkeRecalculatedEvent(...))`.
Zachowaj `@PostConstruct` dla wczytywania `ike.config.json`.
Zachowaj endpoint `POST /api/ike/recalculate` — teraz publikuje event zamiast wywoływać wprost.
Dodaj obsługę E13 (409 gdy obliczanie już trwa) — `AtomicBoolean isRunning`.

**Weryfikacja:**
```bash
# Start backendu
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
sleep 8

# Wywołaj recalculate (publikuje ThreatUpdatedEvent)
curl -s -X POST http://localhost:8080/api/ike/recalculate | jq .status
# oczekiwane: "started"

# Poczekaj na async obliczenia
sleep 10

# Sprawdź wyniki w bazie
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM ike_results WHERE ike_score IS NOT NULL;"
# oczekiwane: > 40

# Test 409 — drugi call podczas obliczania
curl -s -X POST http://localhost:8080/api/ike/recalculate
curl -s -X POST http://localhost:8080/api/ike/recalculate | jq .code
# oczekiwane: "RECALCULATE_IN_PROGRESS" (jeśli pierwsze jeszcze trwa)

# Sprawdź logi — correlation_id powinien być widoczny
# grep "IkeAgent" w logach backendu
```

**Commit:** `feat(2.2): IkeAgent refaktor — @EventListener @Async + IkeRecalculatedEvent publish`

---

### ⬜ 2.3 — DecisionAgent

**Pliki do stworzenia:**
- `backend/.../agent/DecisionAgent.java`
- `backend/.../model/EvacuationDecision.java`
- `backend/.../repository/EvacuationDecisionRepository.java`
- `backend/.../controller/DecisionController.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.4, §7), `docs/API_REFERENCE.md` (`GET /api/decisions`, `PATCH /api/decisions/{id}`)

**Opis:**
`DecisionAgent` słucha `IkeRecalculatedEvent` (nie `ThreatUpdatedEvent`).
Logika prógów: IKE ≥ 0.70 → `ewakuuj_natychmiast`, 0.40–0.69 → `przygotuj_ewakuacje`,
< 0.40 → `monitoruj`, null → pomiń.
Pole `uzasadnienie` — wygeneruj z dominujących score'ów (pobierz `score_*` z `ike_results`).
Zapis do `evacuation_decisions` z `correlation_id` z eventu.
`DecisionController`: `GET /api/decisions`, `PATCH /api/decisions/{id}`.

**Weryfikacja:**
```bash
# Wywołaj pełny flow
curl -s -X POST http://localhost:8080/api/ike/recalculate
sleep 15

# Sprawdź rekomendacje
curl -s "http://localhost:8080/api/decisions" | jq '.liczba_decyzji'
# oczekiwane: > 0

curl -s "http://localhost:8080/api/decisions?rekomendacja=ewakuuj_natychmiast" \
  | jq '.decyzje[0].uzasadnienie'
# oczekiwane: niepusty string

# Zatwierdź rekomendację
DECISION_ID=$(curl -s "http://localhost:8080/api/decisions" | jq '.decyzje[0].id')
curl -s -X PATCH http://localhost:8080/api/decisions/$DECISION_ID \
  -H "Content-Type: application/json" \
  -d '{"zatwierdzona": true}' | jq .zatwierdzona
# oczekiwane: true

# Sprawdź bazę
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT rekomendacja, COUNT(*) FROM evacuation_decisions GROUP BY rekomendacja;"
```

**Commit:** `feat(2.3): DecisionAgent + EvacuationDecision + DecisionController`

---

### ⬜ 2.4 — FloodImportAgent (stub) + ThreatController

**Pliki do stworzenia:**
- `backend/.../agent/FloodImportAgent.java` (stub — tylko syntetyczne strefy, bez WFS)
- `backend/.../controller/ThreatController.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.2), `docs/API_REFERENCE.md` (`POST /api/threat/flood/import`, `POST /api/threat/clear`), `docs/DATA_SCHEMA.md` (tabela `strefy_zagrozen`)

**Opis:**
W v1.1 `FloodImportAgent` generuje syntetyczne strefy (prostokąty wzdłuż bbox powiatu)
— bez prawdziwego WFS (to przychodzi w zadaniu 3.1).
`ThreatController` — `POST /api/threat/flood/import` (202 Accepted, async),
`POST /api/threat/clear` (czyści `strefy_zagrozen`, publikuje `ThreatUpdatedEvent`
z pustą listą stref).
Oba endpointy zwracają `correlation_id`.
Obsługa 409 `IMPORT_IN_PROGRESS`.

**Weryfikacja:**
```bash
# Import syntetyczny
curl -s -X POST http://localhost:8080/api/threat/flood/import \
  -H "Content-Type: application/json" \
  -d '{"obszar": "chelm", "scenariusz": "Q100"}' | jq .status
# oczekiwane: "started"

sleep 20

# Sprawdź strefy w bazie
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT kod, poziom, scenariusz FROM strefy_zagrozen;"

# IKE powinno być już przeliczone (przez event chain)
curl -s "http://localhost:8080/api/ike?kategoria=czerwony" | jq '.liczba_wynikow'
# oczekiwane: > 0

# Clear
curl -s -X POST http://localhost:8080/api/threat/clear | jq .status
sleep 15
curl -s "http://localhost:8080/api/ike?kategoria=czerwony" | jq '.liczba_wynikow'
# oczekiwane: 0 (score_zagrozenia = 0 po ThreatClear)
```

**Commit:** `feat(2.4): FloodImportAgent stub + ThreatController (import syntetyczny + clear)`

---

### ⬜ 2.5 — WebSocket (LiveFeedService + konfiguracja)

**Pliki do stworzenia:**
- `backend/.../config/WebSocketConfig.java`
- `backend/.../service/LiveFeedService.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.5, §8), `docs/API_REFERENCE.md` (sekcja WebSocket)

**Opis:**
`WebSocketConfig` — STOMP over SockJS, endpoint `/ws`, broker `/topic`.
`LiveFeedService`:
- `@EventListener(ThreatUpdatedEvent)` → push do `/topic/layers/L-03` (sygnał `LAYER_UPDATED`)
  i `/topic/system` (typ `THREAT_IMPORT_COMPLETED` lub `THREAT_CLEARED`)
- `@EventListener(IkeRecalculatedEvent)` → push do `/topic/ike` (lista `IkeResultSummary`),
  pobierz rekomendacje z bazy i push do `/topic/decisions`, push do `/topic/system`

Payloady dokładnie jak w `API_REFERENCE.md` sekcja WebSocket.

**Weryfikacja:**
```bash
# Test WebSocket przez wscat (npm install -g wscat)
wscat -c ws://localhost:8080/ws

# W osobnym terminalu wywołaj import
curl -s -X POST http://localhost:8080/api/threat/flood/import \
  -H "Content-Type: application/json" \
  -d '{"obszar": "chelm", "scenariusz": "Q100"}'

# W konsoli wscat (po subskrypcji /topic/system) powinny pojawić się wiadomości:
# THREAT_IMPORT_COMPLETED → IKE_RECALCULATED → DECISIONS_GENERATED
```

**Commit:** `feat(2.5): WebSocketConfig + LiveFeedService — STOMP push po eventach`

---

### ⬜ 2.6 — Frontend: WebSocket client + ScenarioPanel

**Pliki do stworzenia:**
- `frontend/src/services/websocketService.ts`
- `frontend/src/hooks/useWebSocket.ts`
- `frontend/src/components/panels/ScenarioPanel.tsx`

**Dokumenty referencyjne:** `CLAUDE.md` (Layout — ScenarioPanel), `docs/API_REFERENCE.md` (sekcja WebSocket, `POST /api/threat/flood/import`)

**Opis:**
`websocketService.ts` — SockJS + `@stomp/stompjs`, reconnect 5s.
`useWebSocket` — hook subskrybujący topiki `/topic/ike`, `/topic/decisions`,
`/topic/layers/L-03`, `/topic/system`. Po `/topic/ike` aktualizuje Zustand store.
Po `/topic/layers/L-03` wywołuje `queryClient.invalidateQueries(['layers', 'L-03'])`.
`ScenarioPanel` — dropdown powiatu, dropdown scenariusza (Q10/Q100/Q500/pożar/blackout),
przycisk "Aktywuj", przycisk "Wyczyść zagrożenie", spinner podczas importu,
status z `/topic/system`.

**Weryfikacja:**
```
Manualne — przeglądarka http://localhost:5173:
☐ ScenarioPanel widoczny w panelu bocznym
☐ Wybranie "Q100 / chelm" + "Aktywuj" → spinner na przycisku
☐ Po ~20s markery DPS w rejonie Chełma zmieniają kolor na czerwony/żółty
☐ Bez ręcznego odświeżenia strony
☐ "Wyczyść zagrożenie" → markery wracają do zielonego
☐ Header pokazuje komunikat statusu z /topic/system
```

**Commit:** `feat(2.6): websocketService + useWebSocket + ScenarioPanel`

---

### ⬜ 2.7 — Frontend: DecisionPanel + Top10Panel

**Pliki do stworzenia:**
- `frontend/src/components/panels/DecisionPanel.tsx`
- `frontend/src/components/panels/Top10Panel.tsx`
- `frontend/src/components/panels/FilterPanel.tsx`
- `frontend/src/components/map/layers/EvacuationRoute.tsx`
- `frontend/src/services/routingService.ts`
- `frontend/src/utils/colorScale.ts`
- `frontend/src/components/shared/IKEScore.tsx`

**Dokumenty referencyjne:** `CLAUDE.md` (Layout, kolory IKE), `docs/API_REFERENCE.md` (`GET /api/decisions`, `PATCH /api/decisions/{id}`)

**Opis:**
`Top10Panel` — lista 10 placówek z najwyższym IKE (z Zustand store, odświeżana przez WebSocket).
`DecisionPanel` — lista rekomendacji, przyciski "Zatwierdź" / "Odrzuć" (`PATCH /api/decisions/{id}`).
`FilterPanel` — filtr IKE kategoria + powiat, wpływa na `DPSLayer`.
`EvacuationRoute` — rysuje `trasa_ewakuacji_geojson` jako LineString na mapie po kliknięciu.
`routingService.ts` — zapytanie do OSRM `router.project-osrm.org`.
`colorScale.ts` — funkcja `ikeToColor(score)` → hex.

**Weryfikacja:**
```
Manualne po aktywacji scenariusza Q100/chelm:
☐ Top10Panel pokazuje posortowaną listę z IKE i kolorami
☐ DecisionPanel pokazuje rekomendacje z uzasadnieniem
☐ Kliknięcie "Zatwierdź" → rekomendacja oznaczona jako zatwierdzona
☐ FilterPanel "czerwony" → tylko czerwone markery widoczne
☐ Kliknięcie "Pokaż trasę" w Popup → trasa narysowana na mapie
```

**Commit:** `feat(2.7): DecisionPanel + Top10Panel + FilterPanel + EvacuationRoute`

---

### ⬜ 2.8 — Pozostałe warstwy GIS (L-02, L-04–L-07)

**Pliki do stworzenia:**
- `frontend/src/components/map/layers/HeatmapLayer.tsx`
- `frontend/src/components/map/layers/DrogiLayer.tsx`
- `frontend/src/components/map/layers/TransportLayer.tsx`
- `frontend/src/components/map/layers/RelokacjaLayer.tsx`
- `frontend/src/components/map/layers/BialePlamiLayer.tsx`
- `backend/src/main/resources/db/seed_drogi.sql` (kilka przykładowych dróg)
- `backend/src/main/resources/db/seed_biale_plamy.sql`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (tabele `drogi_ewakuacyjne`, `biale_plamy`), `docs/API_REFERENCE.md` (`GET /api/layers/{id}`)

**Opis:**
Każda warstwa jako osobny komponent React-Leaflet. Widoczność kontrolowana przez
`LayerControlPanel` (Zustand `mapStore.activeLayers`).
`HeatmapLayer` — heatmapa gęstości podopiecznych (leaflet.heat lub własna implementacja).
`DrogiLayer` — linie z kolorem wg `droznosc` (zielony/żółty/czerwony).
`TransportLayer` — markery pojazdów.
`RelokacjaLayer` — markery miejsc relokacji z pojemnością.
`BialePlamiLayer` — poligony obszarów bez transportu.

**Weryfikacja:**
```
Manualne:
☐ Każda z 5 warstw włącza się/wyłącza przez LayerControlPanel
☐ DrogiLayer — kolory zależne od drożności
☐ RelokacjaLayer — marker z pojemnością w tooltipie
☐ HeatmapLayer — widoczna heatmapa przy włączonej warstwie L-02
☐ GET /api/layers/L-04 → feature_count > 0 (seed dróg wykonany)
```

**Commit:** `feat(2.8): warstwy L-02, L-04–L-07 — HeatmapLayer, DrogiLayer, Transport, Relokacja, BialePlamy`

---

## Iteracja v1.2 — Import WFS i kalkulatory

Cel: prawdziwy import stref powodziowych z WFS ISOK/RZGW z fallbackiem syntetycznym.
Trzy kalkulatory zasobów. Scraper placówek z mpips.gov.pl.

---

### ⬜ 3.1 — WfsClientService + FloodImportAgent (pełny)

**Pliki do modyfikacji/stworzenia:**
- `backend/.../service/WfsClientService.java`
- `backend/.../agent/FloodImportAgent.java` (zastępuje stub z 2.4)

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.2 — WFS endpoint, fallback)

**Opis:**
`WfsClientService` — HTTP GET na WFS ISOK (GML), parsowanie GML → GeoJSON,
transformacja EPSG:2180 → EPSG:4326 (GeoTools lub ręczna transformacja).
Timeout 10s, obsługa błędów → fallback syntetyczny z WARN.
`FloodImportAgent` pełny: spróbuj WFS → przy błędzie użyj syntetycznego.
Odpowiedź 202 zawiera `"zrodlo_danych": "wfs"` lub `"syntetyczne"`,
nagłówek `X-Fallback-Used: true` przy fallbacku.

**Weryfikacja:**
```bash
# Test z fallbackiem (WFS może być niedostępny — to normalne)
curl -s -X POST http://localhost:8080/api/threat/flood/import \
  -H "Content-Type: application/json" \
  -d '{"obszar": "wlodawski", "scenariusz": "Q100"}' \
  -i | grep -E "X-Fallback|zrodlo_danych"
# oczekiwane: zrodlo_danych = "wfs" lub "syntetyczne" — oba są OK

# Sprawdź że strefy mają poprawne współrzędne (EPSG:4326, nie 2180)
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT ST_SRID(geom), ST_AsText(ST_Centroid(geom)) FROM strefy_zagrozen LIMIT 1;"
# SRID: 4326, centroid w okolicach (22–24°E, 50–52°N)
```

**Commit:** `feat(3.1): WfsClientService + FloodImportAgent pełny (WFS + fallback syntetyczny)`

---

### ⬜ 3.2 — Kalkulatory zasobów (backend)

**Pliki do stworzenia:**
- `backend/.../service/KalkulatorService.java`
- `backend/.../controller/KalkulatorController.java`

**Dokumenty referencyjne:** `docs/API_REFERENCE.md` (`POST /api/calculate/transport`, `relocation`, `threat`)

**Opis:**
Trzy metody w `KalkulatorService`, każda z zapytaniem PostGIS `ST_DWithin`:
1. Transport — policz pojazdy w promieniu, szacuj kursy i czas ewakuacji
2. Relokacja — znajdź miejsca z pojemnością w promieniu, oblicz % wypełnienia
3. Zasięg zagrożenia — sprawdź czy placówka jest w strefie, oblicz czas do zagrożenia

Wszystkie operacje geospatiale w PostGIS, nie w Javie.

**Weryfikacja:**
```bash
curl -s -X POST http://localhost:8080/api/calculate/transport \
  -H "Content-Type: application/json" \
  -d '{"placowka_kod": "DPS-CHE-002", "promien_km": 30, "uwzgledniaj_tylko_przystosowane": true}' \
  | jq '.szacunek.liczba_kursow_min'
# oczekiwane: liczba całkowita > 0

curl -s -X POST http://localhost:8080/api/calculate/relocation \
  -H "Content-Type: application/json" \
  -d '{"placowka_kod": "DPS-LBL-001", "promien_km": 50, "tylko_dla_niesamodzielnych": false}' \
  | jq '.pokrycie'
# oczekiwane: "wystarczajace" lub "ograniczone"
```

**Commit:** `feat(3.2): KalkulatorService + KalkulatorController — 3 kalkulatory zasobów`

---

### ⬜ 3.3 — Kalkulatory (frontend UI)

**Pliki do stworzenia:**
- `frontend/src/components/calculators/TransportCalculator.tsx`
- `frontend/src/components/calculators/RelocationCalculator.tsx`
- `frontend/src/components/calculators/ThreatSpreadCalculator.tsx`
- `frontend/src/components/calculators/CalculatorHub.tsx`

**Dokumenty referencyjne:** `docs/PRD.md` (§5.2 F-10)

**Opis:**
`CalculatorHub` — drawer/modal z zakładkami 3 kalkulatorów, otwierany przyciskiem "Kalkulatory"
w stopce mapy. Każdy kalkulator: formularz wejściowy, przycisk "Oblicz", wyniki.
`TransportCalculator` — wybór placówki (dropdown z API), promień, wynik: lista pojazdów + szacunek.
`RelocationCalculator` — wybór placówki, wynik: tabela miejsc relokacji z odległością i %.
`ThreatSpreadCalculator` — wybór placówki + typ zagrożenia + prędkość.

**Weryfikacja:**
```
Manualne:
☐ Przycisk "Kalkulatory" w stopce otwiera CalculatorHub
☐ TransportCalculator: wybór DPS-CHE-002, promień 30km → wyniki widoczne
☐ RelocationCalculator: wybór DPS-LBL-001 → tabela miejsc
☐ ThreatSpreadCalculator: zwraca status zagrożenia
```

**Commit:** `feat(3.3): CalculatorHub + 3 kalkulatory zasobów (frontend)`

---

### ⬜ 3.4 — Scraper placówek

**Pliki do stworzenia:**
- `backend/.../service/ScraperService.java`
- `backend/.../service/JsoupScraperService.java`
- `backend/.../controller/ScraperController.java`

**Dokumenty referencyjne:** `docs/API_REFERENCE.md` (`POST /api/scraper/run`, `GET /api/scraper/log`), `docs/PRD.md` (§5.2 F-11)

**Opis:**
Scraper HTML z Jsoup, źródło: `mpips.gov.pl/rejestr-placowek` lub BIP powiatów.
Zapis do `placowka` z `zrodlo = 'scraping'`. Log scrapingu (liczba rekordów, błędy).
`ScraperController`: `POST /api/scraper/run` (202, async), `GET /api/scraper/log`.
`SCRAPER_INTERVAL_S` z `application.yml` — opcjonalny `@Scheduled`.

**Weryfikacja:**
```bash
curl -s -X POST http://localhost:8080/api/scraper/run | jq .status
# oczekiwane: "started"
sleep 180   # scraping zajmuje do 3 minut

curl -s http://localhost:8080/api/scraper/log | jq '.status'
# oczekiwane: "completed" lub "failed" (sieć może być niedostępna — OK)

curl -s http://localhost:8080/api/scraper/log | jq '.wyniki'
# oczekiwane: obiekt z polami pobrano_rekordow, zaktualizowano, bledow
```

**Commit:** `feat(3.4): ScraperService (Jsoup) + ScraperController`

---

## Iteracja v1.3 — UX i głos

Cel: asystent głosowy (Web Speech API + Whisper fallback), pełny Docker stack prod.

---

### ⬜ 4.1 — Asystent głosowy

**Pliki do stworzenia:**
- `frontend/src/services/voiceService.ts`
- `frontend/src/hooks/useVoiceCommands.ts`
- `frontend/src/utils/commandParser.ts`
- `frontend/src/components/voice/VoiceAssistant.tsx`
- `frontend/src/components/voice/VoiceButton.tsx`

**Dokumenty referencyjne:** `docs/PRD.md` (§5.2 F-12 — tabela komend)

**Opis:**
`voiceService.ts` — Web Speech API (`SpeechRecognition`), język `pl-PL`.
Fallback: Whisper API (`VITE_OPENAI_API_KEY`) gdy Web Speech API niedostępne.
`commandParser.ts` — mapowanie fraz na akcje (7 komend z tabeli F-12 w PRD).
`useVoiceCommands` — hook wywołujący akcje Zustand store lub API.
`VoiceButton` — przycisk mikrofonu w headerze, aktywacja Spacja.

**Weryfikacja:**
```
Manualne (Chrome, localhost lub HTTPS):
☐ Kliknięcie VoiceButton → przeglądarka prosi o dostęp do mikrofonu
☐ "Pokaż powiat lubelski" → zoom na powiat lubelski
☐ "Włącz warstwę zagrożeń" → warstwa L-03 włącza się
☐ "Które placówki są czerwone?" → filtr IKE = czerwony aktywny
☐ "Aktywuj powódź Q100" → wywołuje POST /api/threat/flood/import
☐ Fallback: gdy Web Speech API niedostępne → przycisk pozostaje aktywny (Whisper)
```

**Commit:** `feat(4.1): asystent głosowy — Web Speech API + Whisper fallback + 7 komend`

---

### ⬜ 4.2 — Docker stack produkcyjny

**Pliki do stworzenia:**
- `backend/Dockerfile`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `backend/src/main/resources/application-prod.yml`
- `docker-compose.full.yml` (finalizacja — wersja kompletna z healthcheckami)

**Dokumenty referencyjne:** `docs/DEPLOYMENT.md` (sekcje 5, 8), `docs/ARCHITEKTURA_PLAN.md` (§2)

**Opis:**
`backend/Dockerfile` — multi-stage: `maven:3.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre` (run).
`frontend/Dockerfile` — `node:20-alpine` (build) → `nginx:alpine` (serve).
`nginx.conf` — proxy `/api/` i `/ws` na backend, SPA routing.
`application-prod.yml` — `DATABASE_URL` z env, connection pool 20, brak hot reload.
`docker-compose.full.yml` — healthchecki, `depends_on`, sieć wewnętrzna.

**Weryfikacja:**
```bash
docker compose -f docker-compose.full.yml up --build -d
sleep 30

# Sprawdź kontenery
docker compose -f docker-compose.full.yml ps
# Oczekiwane: postgres healthy, backend running, frontend running

curl -s http://localhost:8080/api/layers | jq '. | length'
# oczekiwane: 7

curl -s http://localhost:3000
# oczekiwane: HTML strony frontendowej

# WebSocket przez Nginx proxy
# (test manualny — otwórz http://localhost:3000, aktywuj scenariusz)
```

**Commit:** `feat(4.2): Dockerfile backend/frontend + nginx.conf + docker-compose.full.yml`

---

## Dług techniczny

> Zapisuj tu problemy zauważone przy implementacji, które wykraczają poza zakres aktywnego zadania.

| # | Opis | Dotyczy zadania | Priorytet |
|---|---|---|---|
| DT-01 | Brak testów integracyjnych dla `IkeAgent` — ryzyko regresji przy refaktorze | 2.2 | Wysoki |
| DT-02 | `seed_transport.sql` — tylko 10 pojazdów, większość placówek dostaje `score_braku_transportu = 1.0` | 1.1 | Średni |
| DT-03 | Brak `.claudeignore` / `.gitignore` dla plików GeoJSON > 1MB | — | Niski |

---

## Ukończone zadania

> Przenoś tutaj po commicie.

*(brak — projekt nie rozpoczęty)*
