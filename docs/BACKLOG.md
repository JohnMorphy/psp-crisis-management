# BACKLOG — Ogólnopolski Dashboard Jednostek Ochrony Ludności

> Jedyne źródło prawdy dla planu implementacji.
> Czytaj przed każdą sesją implementacyjną.
> Format statusów: ⬜ Nie rozpoczęta → 🔄 W toku → ✅ Ukończona

**Aktywne:** `REVISION 1 — UX fixes`

---

## ✅ v1.0 — Fundament GIS (ukończona)

Wszystkie zadania 1.1–1.12 ukończone. Deliverable:
- Mapa React-Leaflet + warstwy GIS (AdminBoundaryLayer, EntityLayer)
- Granice administracyjne PL z GUGiK PRG WFS (AdminBoundaryImportAgent)
- Entity registry (entity_registry, entity_category, entity_source)
- Spring Boot 3 / PostGIS / Zustand / Tailwind CSS
- Powiadomienia importu (notificationStore, WebSocket basic)

---

## ✅ REVISION 1 — UX fixes

### ✅ R1.1 — Fix layer selection per warstwa

**Problem:** Jeden `selectedRegion` w mapStore podświetla naraz wiele warstw — po kliknięciu województwa i powiatu oba są podświetlone.

**Pliki do modyfikacji:**
- `frontend/src/store/mapStore.ts`
- `frontend/src/components/map/layers/AdminBoundaryLayer.tsx`

**Rozwiązanie:**
```typescript
// mapStore.ts — dodaj:
type BoundaryLayerId = 'L-08' | 'L-09' | 'L-10'
selectedFeatureByLayer: Record<BoundaryLayerId, string | null>
setSelectedFeatureForLayer: (layerId: BoundaryLayerId, featureId: string | null) => void
// setSelectedFeatureForLayer czyści pozostałe warstwy → cross-layer reset stylu
```

**Weryfikacja:**
```
☐ Kliknięcie województwa → tylko województwo podświetlone
☐ Następnie kliknięcie powiatu → powiat podświetlony, województwo traci podświetlenie
☐ npm run build → 0 błędów TypeScript
```

**Commit:** `feat(R1): fix layer selection — selectedFeatureByLayer, cross-layer style reset`

---

## ✅ REVISION 2 — Usunięcie legacy

### ✅ R2.1 — Backend legacy removal

Usunięto: Placowka, IkeAgent, IkeResult, IkeController, StrefaZagrozen, MiejsceRelokacji, ZasobTransportu, ike.config.json, seed files 02/04/05/06.
Zaktualizowano: GeoService.java (bez legacy repos), EntityRegistryService.java (bez ike_score/ike_kategoria), 01_schema.sql (bez legacy tabel), 03_seed_layers.sql (L-01/L-02 dla nowej architektury).
Weryfikacja: `./mvnw compile -q` → BUILD SUCCESS ✅

### ✅ R2.2 — Frontend legacy removal

Usunięto: ThreatZoneLayer.tsx, IKE coloring w EntityLayer.tsx, IKE sekcja w EntityPopup.tsx, typy IkeCategory/ThreatZoneProperties w gis.ts.
Zaktualizowano: MapContainer.tsx (viewport całej Polski), EntityLayer.tsx (CATEGORY_COLORS).
Weryfikacja: `npm run build` → 0 błędów TypeScript ✅

### ✅ R2.3 — Docs update

Zaktualizowano: CLAUDE.md, PRD.md, ARCHITEKTURA_PLAN.md, DATA_SCHEMA.md, API_REFERENCE.md.
Usunięto: IKE_ALGORITHM.md.
Weryfikacja: brak słowa "IKE" jako aktywna funkcja w dokumentacji.

---

## ⬜ DŁUG TECHNICZNY — Logi i testy

### ⬜ DT-LOGS-TESTS — Logi + testy dla istniejących serwisów

**Pliki do modyfikacji (dodaj logi + testy):**
- `backend/.../agent/AdminBoundaryImportAgent.java`
- `backend/.../service/EntityImportService.java`
- `backend/.../service/EntityRegistryService.java`
- `backend/.../service/AdminBoundaryService.java`
- `backend/.../service/GeoService.java` (już ma Logger — dodaj logi do key metod)

**Pliki do stworzenia (testy):**
- `backend/src/test/java/pl/lublin/dashboard/service/EntityRegistryServiceTest.java`
- `backend/src/test/java/pl/lublin/dashboard/service/AdminBoundaryServiceTest.java`
- `backend/src/test/java/pl/lublin/dashboard/agent/AdminBoundaryImportAgentTest.java`

**Wzorzec logowania:**
```java
private static final Logger log = LoggerFactory.getLogger(X.class);
log.info("[AdminBoundaryImportAgent] import started — poziomy={}", Arrays.toString(poziomy));
log.info("[AdminBoundaryImportAgent] import completed — inserted={}", count);
log.error("[AdminBoundaryImportAgent] import failed — {}", e.getMessage());
```

**Wzorzec testu:**
```java
@ExtendWith(MockitoExtension.class)
class EntityRegistryServiceTest {
    @Mock EntityRegistryEntryRepository entityRepository;
    @InjectMocks EntityRegistryService service;

    @Test
    void filterEntries_byKodWoj_returnsOnlyMatchingEntries() {
        EntityRegistryEntry match = new EntityRegistryEntry();
        match.setTerytWoj("06");
        EntityRegistryEntry noMatch = new EntityRegistryEntry();
        noMatch.setTerytWoj("14");
        when(entityRepository.findAll()).thenReturn(List.of(match, noMatch));

        Map<String, Object> result = service.getEntities(null, null, "06", null, null, null, null);

        assertThat((Integer) result.get("count")).isEqualTo(1);
    }
}
```

**Weryfikacja:**
```bash
cd backend
./mvnw test -Dtest="EntityRegistryServiceTest,AdminBoundaryServiceTest,AdminBoundaryImportAgentTest" -q
# oczekiwane: BUILD SUCCESS
```

**Commit:** `test(DT): logi + testy jednostkowe dla istniejących serwisów`

---

## ⬜ ITERATION v1.1 — Zasoby + Alerty zagrożeń

**Cel:** Operator widzi zasoby każdej jednostki na mapie i może filtrować po typie zasobu. Alert z IMGW (poziom wody > próg) automatycznie podświetla jednostki w zasięgu.

**Deliverable:** `GET /api/resource-types` zwraca typy zasobów; kliknięcie jednostki pokazuje jej zasoby w popupie; aktywny alert z IMGW podświetla jednostki w zasięgu w ciągu 30 sekund bez odświeżenia strony.

---

### ⬜ 2.1 — SQL: resource_type + entity_resources + threat_alert

**Pliki:**
- `backend/src/main/resources/db/01_schema.sql` (dodaj 3 tabele)
- `backend/.../model/ResourceType.java`
- `backend/.../model/EntityResource.java`
- `backend/.../model/ThreatAlert.java`
- `backend/.../repository/ResourceTypeRepository.java`
- `backend/.../repository/EntityResourceRepository.java`
- `backend/.../repository/ThreatAlertRepository.java`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (sekcje resource_type, entity_resources, threat_alert)

**Encja JPA — przykład EntityResource:**
```java
@Entity
@Table(name = "entity_resources")
public class EntityResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entity_id", nullable = false) private Long entityId;
    @Column(name = "resource_type_code", nullable = false) private String resourceTypeCode;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "is_available") private Boolean isAvailable = true;
    @Column(name = "last_updated") private OffsetDateTime lastUpdated;
    private String source;
    // getters/setters
}
```

**Weryfikacja:**
```bash
./mvnw compile -q  # BUILD SUCCESS

docker compose up -d postgres
sleep 10
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "\dt" | grep -E "resource_type|entity_resources|threat_alert"
# oczekiwane: 3 tabele widoczne
```

**Commit:** `feat(2.1): SQL schema — resource_type, entity_resources, threat_alert`

---

### ⬜ 2.2 — Seed: typy zasobów + mockowane zasoby

**Pliki:**
- `backend/src/main/resources/db/07_seed_resource_types.sql`
- `backend/src/main/resources/db/08_seed_entity_resources.sql`

**07_seed_resource_types.sql (~50 typów):**
```sql
INSERT INTO resource_type (code, name, category, unit_of_measure) VALUES
  ('woz_cysternowy',       'Wóz cysternowy GBA',           'pojazd_gasniczy',   'szt'),
  ('woz_drabinowy',        'Wóz drabinowy',                'pojazd_gasniczy',   'szt'),
  ('woz_ratownictwa',      'Wóz ratownictwa technicznego', 'pojazd_ratowniczy', 'szt'),
  ('ponton_motorowy',      'Ponton motorowy',              'sprzet_wodny',      'szt'),
  ('ponton_wioslowy',      'Ponton wiosłowy',              'sprzet_wodny',      'szt'),
  ('pompa_szlamowa',       'Pompa szlamowa',               'sprzet_wodny',      'szt'),
  ('agregat_pradotworczy', 'Agregat prądotwórczy',         'energetyczny',      'szt'),
  ('oswietlenie_polowe',   'Oświetlenie polowe',           'energetyczny',      'kpl'),
  ('nosze_transportowe',   'Nosze transportowe',           'medyczny',          'szt'),
  ('defibrylator_aed',     'Defibrylator AED',             'medyczny',          'szt'),
  ('respirator',           'Respirator transportowy',      'medyczny',          'szt'),
  ('ambulans_type_c',      'Ambulans typu C',              'pojazd_medyczny',   'szt'),
  ('ambulans_type_b',      'Ambulans typu B',              'pojazd_medyczny',   'szt'),
  ('namiot_polowy',        'Namiot polowy medyczny',       'logistyczny',       'szt'),
  ('generator_wody',       'Generator wody pitnej',        'logistyczny',       'szt'),
  ('samochod_osobowy',     'Samochód osobowy',             'pojazd_osobowy',    'szt'),
  ('bus_9',                'Bus 9-osobowy',                'pojazd_osobowy',    'szt'),
  ('minibus_15',           'Minibus 15-osobowy',           'pojazd_osobowy',    'szt'),
  ('ambulans_lotniczy',    'Ambulans lotniczy (LPR)',       'pojazd_medyczny',   'szt'),
  ('smigłowiec_ratowniczy','Śmigłowiec ratowniczy',        'lotniczy',          'szt'),
  ('łódź_motorowa',        'Łódź motorowa ratownicza',     'sprzet_wodny',      'szt'),
  ('quoad',                'Quad ratowniczy',              'pojazd_ratowniczy', 'szt'),
  ('skuter_wodny',         'Skuter wodny',                 'sprzet_wodny',      'szt'),
  ('namiot_logistyczny',   'Namiot logistyczny',           'logistyczny',       'szt'),
  ('kuchnia_polowa',       'Kuchnia polowa',               'logistyczny',       'szt'),
  ('agregat_oswietleniowy','Agregat oświetleniowy',        'energetyczny',      'szt'),
  ('woz_dowodzenia',       'Wóz dowodzenia i łączności',  'pojazd_specjalny',  'szt'),
  ('woz_chemiczny',        'Wóz chemiczny (Hazmat)',        'pojazd_specjalny',  'szt'),
  ('dron_ratowniczy',      'Dron ratowniczy',              'lotniczy',          'szt'),
  ('kamera_termiczna',     'Kamera termiczna',             'sprzet_elektroniczny', 'szt'),
  ('radiotelefon',         'Radiotelefon przenośny',       'sprzet_elektroniczny', 'szt'),
  ('dezynfektor',          'Urządzenie do dezynfekcji',    'medyczny',          'szt'),
  ('tlen_medyczny',        'Butla z tlenem medycznym',     'medyczny',          'szt'),
  ('ponton_gumowy',        'Ponton gumowy 4-osobowy',      'sprzet_wodny',      'szt'),
  ('pila_do_betonu',       'Piła do betonu/asfaltu',       'sprzet_ratowniczy', 'szt'),
  ('sprzet_wspinaczkowy',  'Sprzęt wspinaczkowy',          'sprzet_ratowniczy', 'szt'),
  ('nosze_drabinkowe',     'Nosze drabinkowe',             'medyczny',          'szt'),
  ('wentylator_oddymowy',  'Wentylator oddymowy',          'sprzet_gasniczy',   'szt'),
  ('dzialko_wodne',        'Działko wodne',                'sprzet_gasniczy',   'szt'),
  ('srodek_pianotwórczy',  'Środek pianotwórczy 200L',     'sprzet_gasniczy',   'beczka'),
  ('maska_ochronna',       'Maska ochronna SCBA',          'sprzet_ochrony',    'szt'),
  ('kombinezon_chem',      'Kombinezon chemoochronny',     'sprzet_ochrony',    'szt'),
  ('apteczka_torba',       'Torba PSP R1',                 'medyczny',          'szt'),
  ('koc_NRC',              'Koc termiczny NRC',            'medyczny',          'szt'),
  ('kotwica_ratownicza',   'Kotwica ratownicza',           'sprzet_wodny',      'szt'),
  ('plywak_ratowniczy',    'Pływak ratowniczy',            'sprzet_wodny',      'szt'),
  ('statek_ratowniczy',    'Statek ratowniczy',            'sprzet_wodny',      'szt'),
  ('sonda_ratownicza',     'Sonda ratownicza akustyczna',  'sprzet_ratowniczy', 'szt'),
  ('pies_ratowniczy',      'Pies ratowniczy z przewodnikiem', 'zasoby_ludzkie', 'para'),
  ('nurek_ratowniczy',     'Nurek ratowniczy',             'zasoby_ludzkie',    'os')
ON CONFLICT (code) DO NOTHING;
```

**08_seed_entity_resources.sql:**
```sql
INSERT INTO entity_resources (entity_id, resource_type_code, quantity, is_available, source)
SELECT e.id, rt.code, (floor(random() * 3) + 1)::int, true, 'mock'
FROM entity_registry e
CROSS JOIN resource_type rt
WHERE e.id <= 20
  AND rt.code IN ('woz_cysternowy', 'ponton_motorowy', 'agregat_pradotworczy', 'nosze_transportowe', 'ambulans_type_b')
ON CONFLICT (entity_id, resource_type_code) DO NOTHING;
```

**Weryfikacja:**
```bash
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM resource_type;"
# oczekiwane: 50
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM entity_resources;"
# oczekiwane: > 0
```

**Commit:** `feat(2.2): seed resource_type (50 typów) + mockowane entity_resources`

---

### ⬜ 2.3 — ThreatAlertImportAgent + ThreatAlertEvent + endpoint manual

**Pliki do stworzenia:**

- `backend/.../event/ThreatAlertEvent.java`
- `backend/.../event/NearbyUnitsComputedEvent.java`
- `backend/.../config/AsyncConfig.java`
- `backend/.../agent/ThreatAlertImportAgent.java`
- `backend/.../controller/ThreatController.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.1–4.3), `docs/API_REFERENCE.md`

**ThreatAlertEvent.java:**
```java
public class ThreatAlertEvent extends ApplicationEvent {
    private final Long alertId;
    private final String threatType;   // "flood" | "fire" | "blackout"
    private final String level;        // "warning" | "alarm" | "emergency"
    private final String sourceApi;    // "imgw_hydro" | "manual"
    private final Double lat;
    private final Double lon;
    private final Double radiusKm;
    private final String correlationId;
    // konstruktor + gettery
}
```

**ThreatAlertImportAgent:**
```java
@Service
@Slf4j
public class ThreatAlertImportAgent {
    private static final String IMGW_HYDRO_URL = "https://danepubliczne.imgw.pl/api/data/hydro";
    // Polling co N minut @Scheduled
    // Na start: używaj hardkodowanej mapy 5 stacji z koordynatami
    // Sprawdź stan_wody > 400 (przykładowy próg) → INSERT threat_alert → publishEvent
}
```

**ThreatController endpointy:**
- `GET /api/threats/active` → lista aktywnych alertów
- `POST /api/threats/manual` → ręczny trigger
- `POST /api/threats/{id}/deactivate` → deaktywacja

**IMGW format odpowiedzi (jeden element):**
```json
{
  "id_stacji": "150180180",
  "stacja": "Lublin",
  "rzeka": "Bystrzyca",
  "stan_wody": "245",
  "stan_wody_data_pomiaru": "2026-04-22 06:00"
}
```

Uwaga: IMGW nie zwraca koordynat. Użyj mapy `Map<String, double[]> STATION_COORDS` z hardkodowanymi kilkoma stacjami.

**Weryfikacja:**
```bash
# Backend uruchomiony, sprawdź
curl -s http://localhost:8080/api/threats/active | jq '.count'
# oczekiwane: 0

curl -s -X POST http://localhost:8080/api/threats/manual \
  -H "Content-Type: application/json" \
  -d '{"threat_type":"flood","level":"warning","lat":51.2,"lon":22.5,"radius_km":25.0}' \
  | jq '.status'
# oczekiwane: "started"
```

**Commit:** `feat(2.3): ThreatAlertEvent + ThreatAlertImportAgent (IMGW polling) + ThreatController`

---

### ⬜ 2.4 — NearbyUnitsAgent + NearbyUnitsComputedEvent

**Pliki:**
- `backend/.../agent/NearbyUnitsAgent.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.4)

```java
@Service
@Slf4j
public class NearbyUnitsAgent {
    @Autowired private EntityRegistryEntryRepository entityRepository;
    @Autowired private ApplicationEventPublisher publisher;

    @EventListener
    @Async("agentTaskExecutor")
    public void onThreatAlert(ThreatAlertEvent event) {
        log.info("[NearbyUnitsAgent] processing — alertId={}", event.getAlertId());
        List<Long> entityIds = findNearbyEntityIds(event.getLat(), event.getLon(),
                event.getRadiusKm() * 1000);
        log.info("[NearbyUnitsAgent] found {} units", entityIds.size());
        publisher.publishEvent(new NearbyUnitsComputedEvent(
                this, event.getCorrelationId(), entityIds, String.valueOf(event.getAlertId())));
    }
}
```

Native query w EntityRegistryEntryRepository:
```java
@Query(value = """
    SELECT e.id FROM entity_registry e
    WHERE ST_DWithin(e.geom::geography,
                     ST_MakePoint(:lon, :lat)::geography,
                     :radiusMeters)
    """, nativeQuery = true)
List<Long> findEntityIdsWithinRadius(
    @Param("lat") double lat,
    @Param("lon") double lon,
    @Param("radiusMeters") double radiusMeters
);
```

**Commit:** `feat(2.4): NearbyUnitsAgent — ST_DWithin spatial query + NearbyUnitsComputedEvent`

---

### ⬜ 2.5 — LiveFeedService + WebSocket push

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.5)

```java
@Service
@Slf4j
public class LiveFeedService {
    @Autowired private SimpMessagingTemplate messagingTemplate;

    @EventListener
    @Async("agentTaskExecutor")
    public void onThreatAlert(ThreatAlertEvent event) {
        log.info("[LiveFeedService] push threat-alerts — correlationId={}", event.getCorrelationId());
        messagingTemplate.convertAndSend("/topic/threat-alerts",
                Map.of("alertId", event.getAlertId(), "type", event.getThreatType(),
                       "level", event.getLevel(), "lat", event.getLat(), "lon", event.getLon(),
                       "radiusKm", event.getRadiusKm()));
    }

    @EventListener
    @Async("agentTaskExecutor")
    public void onNearbyUnitsComputed(NearbyUnitsComputedEvent event) {
        log.info("[LiveFeedService] push nearby-units — count={}", event.getEntityIds().size());
        messagingTemplate.convertAndSend("/topic/nearby-units",
                Map.of("alertId", event.getAlertId(),
                       "entityIds", event.getEntityIds(),
                       "correlationId", event.getCorrelationId()));
    }
}
```

**Commit:** `feat(2.5): LiveFeedService — STOMP push po ThreatAlertEvent + NearbyUnitsComputedEvent`

---

### ⬜ 2.6 — Frontend: ThreatAlertLayer

**Pliki:**
- `frontend/src/components/map/layers/ThreatAlertLayer.tsx`
- `frontend/src/hooks/useThreatAlerts.ts`

`useThreatAlerts` — React Query `GET /api/threats/active`, staleTime 30_000.

`ThreatAlertLayer` — CircleMarker dla każdego alertu, kolor wg level:
- `warning`: `#F59E0B`
- `alarm`: `#EF4444`
- `emergency`: `#7C3AED`

Radius markera ≈ `radius_km * 1.5` px. Dodaj do MapContainer.tsx.

**Commit:** `feat(2.6): ThreatAlertLayer — wizualizacja alertów zagrożeń na mapie`

---

### ⬜ 2.7 — Frontend: zasoby w EntityPopup + useEntityResources

**Pliki:**
- `frontend/src/hooks/useEntityResources.ts` (nowy)
- `frontend/src/components/map/EntityPopup.tsx` (modyfikacja)

`useEntityResources(entityId)` — `GET /api/entity-resources?entityId={id}`, staleTime 60_000.

W EntityPopup dodaj sekcję zasobów:
```tsx
const { data: resources } = useEntityResources(properties.id)
// W JSX:
{resources && resources.resources.length > 0 && (
  <>
    <hr style={dividerStyle} />
    <div style={{ fontWeight: 600, marginBottom: 4 }}>Zasoby</div>
    {resources.resources.map(r => (
      <div key={r.resource_type_code} style={rowStyle}>
        <span>{r.name}</span>
        <strong>{r.quantity} {r.is_available ? '✓' : '(niedostępny)'}</strong>
      </div>
    ))}
  </>
)}
```

**Commit:** `feat(2.7): zasoby jednostek w EntityPopup + useEntityResources hook`

---

### ⬜ 2.8 — Frontend: NearbyUnitsPanel + WebSocket client

**Pliki:**
- `frontend/src/services/websocketService.ts`
- `frontend/src/hooks/useWebSocket.ts`
- `frontend/src/components/panels/NearbyUnitsPanel.tsx`

`websocketService.ts` — SockJS + @stomp/stompjs, reconnect 5s.
`useWebSocket` — subskrypcja `/topic/nearby-units` → aktualizuje Zustand (`nearbyEntityIds`, `activeAlertId`).
`NearbyUnitsPanel` — lista jednostek w zasięgu alertu, przycisk "Wyczyść alert".

**Commit:** `feat(2.8): WebSocket client + NearbyUnitsPanel — live feed alertów`

---

### ⬜ 2.9 — Frontend: manual threat trigger UI

**Plik:** `frontend/src/components/panels/ThreatTriggerPanel.tsx`

Formularz: typ zagrożenia (dropdown), poziom (dropdown), radius (slider 10–100 km), przycisk "Aktywuj alert".
Wywołuje `POST /api/threats/manual`. Spinner podczas czekania.

**Commit:** `feat(2.9): ThreatTriggerPanel — ręczny trigger alertu zagrożenia`

---

## ⬜ ITERATION v1.2 — Importy z publicznych API

**Cel:** Rzeczywiste dane jednostek PSP, PRM i podmiotów leczniczych z polskich rejestrów.

**Deliverable:** mapa z > 300 stacjami PSP i > 200 ZRM, clustering działa przy zoom < 9.

---

### ⬜ 3.1 — PSP bulk import (CSV dane.gov.pl + geokodowanie Nominatim)

**Pliki:**
- `backend/.../agent/PspImportAgent.java`
- `backend/.../service/NominatimGeocoderService.java`

**Źródło:** `https://dane.gov.pl/pl/dataset/1050`
**Format:** CSV. Parsuj przez Apache Commons CSV lub ręcznie. Upsert do entity_registry.
**Rate limiting Nominatim:** max 1 req/s — `Thread.sleep(1100)` między zapytaniami.

**Commit:** `feat(3.1): PspImportAgent — import PSP z dane.gov.pl + Nominatim geokodowanie`

---

### ⬜ 3.2 — PRM/ZRM bulk import (XLSX rjwprm.ezdrowie.gov.pl)

**Plik:** `backend/.../agent/PrmImportAgent.java`

**Źródło:** `https://rjwprm.ezdrowie.gov.pl/` — XLSX codziennie.
Parsowanie przez Apache POI. Upsert z `category_code='prm_unit'`.

**Commit:** `feat(3.2): PrmImportAgent — import ZRM/SOR z RJWPRM XLSX + geokodowanie`

---

### ⬜ 3.3 — RPWDL import (podmioty lecznicze)

**Plik:** `backend/.../agent/RpwdlImportAgent.java`

**Źródło:** `https://ezdrowie.gov.pl/portal/home/rejestry-medyczne/dane-z-rejestrow-medycznych`
Szpitale, SPZOZ, hospicja. Parsowanie XLSX przez Apache POI.

**Commit:** `feat(3.3): RpwdlImportAgent — import podmiotów leczniczych z RPWDL`

---

### ⬜ 3.4 — Clustering jednostek na mapie

**Warunek wstępny:** 3.1 i 3.2 ukończone (> 500 punktów w entity_registry).

**Pliki:**
- `frontend/package.json` (dodaj `supercluster`)
- `frontend/src/components/map/layers/EntityLayer.tsx`
- `frontend/src/hooks/useEntityLayerData.ts`

**Logika:**
- zoom < 9: klastry (supercluster), marker = koło z liczbą
- zoom ≥ 11: pojedyncze markery
- kliknięcie klastra → fitBounds do bbox

**Commit:** `feat(3.4): clustering jednostek — supercluster, zoom-based grouping`

---

## ⬜ ITERATION v1.3 — UX i głos

### ⬜ 4.1 — Asystent głosowy

Web Speech API + fallback Whisper API. Komendy: "pokaż powiaty", "filtruj PSP", "aktywuj alert".

**Commit:** `feat(4.1): asystent głosowy — Web Speech API`

### ⬜ 4.2 — Docker stack produkcyjny

`docker-compose.full.yml` — cały stack w kontenerach. Nginx reverse proxy. Zmienne env dla prod.

**Commit:** `feat(4.2): docker-compose.full.yml — produkcyjny stack`

---

## Dług techniczny

| # | Opis | Dotyczy | Priorytet |
|---|---|---|---|
| DT-NOMINATIM-CACHE | Cachować wyniki Nominatim w Redis lub tabeli SQL — uniknąć limitu 1 req/s przy dużych importach | 3.1–3.3 | Wysoki |
