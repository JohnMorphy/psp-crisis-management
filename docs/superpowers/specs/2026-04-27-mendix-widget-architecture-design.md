# Design: Mendix Widget Architecture + npm Workspaces

**Data:** 2026-04-27  
**Status:** Do zatwierdzenia  
**Dotyczy:** Przekształcenie standalone React app w Mendix pluggable widget przy użyciu npm workspaces monorepo

---

## 1. Kontekst i motywacja

Dotychczasowe podejście (iframe, build-dependency między frontend a widget) nie działało. Nowy kierunek: React app **jest** widżetem Mendix — jeden codebase, dwa outputy (standalone + Mendix widget), zero zależności buildowych między nimi.

**Odrzucone podejścia:**
- iframe (Nginx + widget) — nie udało się uruchomić
- build-dependency (prod build frontend → kopiuj do widget → build widget) — nieintuicyjne, niestabilne

---

## 2. Architektura

```
┌─────────────────────────────────────────────────────────────┐
│  MENDIX APP                                                  │
│  ┌──────────────────────────────────────────┐               │
│  │  Strona Mendix                            │               │
│  │  ┌────────────────────────────────────┐  │               │
│  │  │  GisMap Widget (frontend/widget/)  │  │               │
│  │  │  props: springBaseUrl, initialZoom │  │               │
│  │  │  → renders <GisMapApp />           │  │               │
│  │  └────────────────────────────────────┘  │               │
│  └──────────────────────────────────────────┘               │
│                                                             │
│  STANDALONE (frontend/app/)  ← dev / demo bez Mendix        │
│  VITE_API_BASE_URL → <GisMapApp apiBaseUrl={...} />         │
└──────────────────┬──────────────────────────────────────────┘
                   │ REST + WebSocket
                   ▼
┌──────────────────────────────────────────────────────────────┐
│  SPRING BOOT (backend/)                                       │
│                                                               │
│  Istniejące serwisy: GeoService, EntityRegistryService,      │
│  AdminBoundaryService, NearbyUnitsAgent, LiveFeedService...  │
│                                                               │
│  NOWE:                                                        │
│  MendixImportAgent (@Scheduled)                              │
│    → GET Mendix REST API (service account)                   │
│    → upsert tylko geom+category do mendix_unit_cache         │
│  MendixUnitsController                                       │
│    → GET /api/mendix-units (proxy szczegółów z Mendix REST)  │
│                                                               │
│            PostgreSQL + PostGIS                               │
│    entity_registry (istniejące dane)                         │
│    mendix_unit_cache (tylko geometria jednostek Mendix)      │
└──────────────────────────────────────────────────────────────┘
                   │ REST (service account)
                   ▼
┌──────────────────────────────────────────────────────────────┐
│  MENDIX REST API                                              │
│  (jednostki ochrony ludności — model danych Mendix)           │
│  ⚠️ BLOKADA: wymagana dokumentacja API przed implementacją   │
└──────────────────────────────────────────────────────────────┘
```

**Kluczowe zasady:**
- Widget i standalone app konsumują identyczne API Springa
- `frontend/shared/` nie zna Mendix ani Vite — czyste React + TypeScript
- Spring odpytuje Mendix REST tylko podczas importu (nie per żądanie)
- `springBaseUrl` pochodzi z właściwości widżetu Mendix (konfigurowalny w Studio Pro)
- Auth docelowy: OIDC federation (poza zakresem tego planu)

---

## 3. Struktura repozytorium

```
psp-crisis-management/
  backend/                          ← Spring Boot (bez zmian struktury)
  frontend/                         ← CAŁY frontend
    package.json                    ← npm workspaces root
    shared/                         ← @psp/shared: komponenty, hooki, typy
      package.json
      src/
        GisMapApp.tsx               ← root komponent (przyjmuje apiBaseUrl prop)
        components/
          layout/                   ← Header, AppShell, NotificationList
          map/                      ← MapContainer, EntityPopup
          map/layers/               ← EntityLayer, AdminBoundaryLayer
          panels/                   ← LayerControlPanel, EntityFilterPanel, RegionInfoPanel
        hooks/                      ← useLayerData, useAdminBoundaries, useEntityLayerData...
        services/
          api.ts                    ← createApiClient(baseUrl) — bez import.meta.env
          ApiContext.tsx            ← React Context dla baseUrl
        store/                      ← mapStore, notificationStore
        types/
          gis.ts
    app/                            ← standalone Vite (dev + demo)
      package.json
      vite.config.ts
      index.html
      src/
        main.tsx                    ← thin shell: czyta VITE_API_BASE_URL → GisMapApp
    widget/                         ← Mendix pluggable widget
      package.json
      src/
        GisMap.tsx                  ← thin shell: czyta props Mendix → GisMapApp
        GisMap.xml                  ← deklaracja właściwości widżetu
        ui/
          GisMap.css
  docs/
  docker-compose.yml
  CLAUDE.md
```

`frontend/package.json`:
```json
{
  "private": true,
  "workspaces": ["shared", "app", "widget"]
}
```

---

## 4. Pakiet `shared/` — kontrakt komponentów

### GisMapApp props
```typescript
interface GisMapAppProps {
  apiBaseUrl: string           // URL Springa — jedyny parametr wymagany
  initialCenter?: [number, number]  // default: [52.0, 19.5]
  initialZoom?: number              // default: 6
}
```

### api.ts — refaktor
```typescript
// Przed:
const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL })

// Po:
export const createApiClient = (baseUrl: string) =>
  axios.create({ baseURL: baseUrl })
```

`apiBaseUrl` przekazywany przez React Context → hooki i serwisy go konsumują. Zero `import.meta.env` w `shared/`.

### package.json
```json
{
  "name": "@psp/shared",
  "private": true,
  "peerDependencies": {
    "react": ">=18",
    "react-dom": ">=18"
  }
}
```

React jako peerDependency — nie bundlowany w shared, każdy consumer przynosi swój.

---

## 5. Widget Mendix (`widget/`)

### GisMap.xml — właściwości Studio Pro
```xml
<properties>
  <propertyGroup caption="Connection">
    <property key="springBaseUrl" type="string" required="true">
      <caption>Spring API URL</caption>
      <description>Base URL of Spring backend, e.g. https://gis-api.example.com</description>
    </property>
  </propertyGroup>
  <propertyGroup caption="Map">
    <property key="initialZoom" type="integer" required="false" defaultValue="6">
      <caption>Initial zoom level</caption>
      <description>1–18</description>
    </property>
  </propertyGroup>
</properties>
```

### GisMap.tsx — ~10 linii
```tsx
import { GisMapApp } from '@psp/shared'

interface GisMapContainerProps {
  springBaseUrl: string
  initialZoom?: number
}

export function GisMap({ springBaseUrl, initialZoom }: GisMapContainerProps) {
  return <GisMapApp apiBaseUrl={springBaseUrl} initialZoom={initialZoom} />
}
```

### widget/package.json
```json
{
  "dependencies": {
    "@psp/shared": "*",
    "classnames": "^2.2.6"
  }
}
```

---

## 6. Standalone app (`app/`)

```tsx
// app/src/main.tsx
import { GisMapApp } from '@psp/shared'

createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>
    <GisMapApp
      apiBaseUrl={import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}
    />
  </QueryClientProvider>
)
```

---

## 7. Backend — zmiany

### Nowa tabela: `mendix_unit_cache`

```sql
CREATE TABLE mendix_unit_cache (
  mendix_id     VARCHAR PRIMARY KEY,
  geom          GEOMETRY(Point, 4326) NOT NULL,
  category_code VARCHAR NOT NULL,
  synced_at     TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_mendix_unit_cache_geom ON mendix_unit_cache USING GIST (geom);
```

Przechowuje TYLKO geometrię i kategorię — bez duplikowania atrybutów biznesowych (nazwa, adres, telefon). Szczegóły zawsze z Mendix REST (proxied).

### Nowe klasy

| Klasa | Odpowiedzialność | Wyzwalacz |
|---|---|---|
| `MendixImportAgent` | Polling Mendix REST → upsert geom do `mendix_unit_cache` | `@Scheduled` co N minut |
| `MendixUnitCacheRepository` | JPA repo dla `mendix_unit_cache` | — |
| `MendixUnitCache` | JPA entity | — |
| `MendixUnitsController` | `GET /api/mendix-units` — proxy szczegółów z Mendix REST | HTTP |

### MendixImportAgent (szkielet)

```java
@Service @Slf4j
public class MendixImportAgent {

    @Value("${mendix.api.base-url}")   private String mendixBaseUrl;
    @Value("${mendix.api.token}")      private String mendixToken;

    @Scheduled(fixedDelayString = "${mendix.import.interval-ms:300000}")
    public void importUnits() {
        log.info("[MendixImportAgent] import started");
        // GET {mendixBaseUrl}/rest/crisis/v1/units  ← endpoint TBD (wymaga docs)
        // map response → MendixUnitCache (tylko mendix_id, geom, category_code)
        // saveAll upsert
        log.info("[MendixImportAgent] import completed — upserted={}", count);
    }
}
```

⚠️ **BLOKADA:** Implementacja `MendixImportAgent` i `MendixUnitsController` wymaga dostarczenia przez zespół Mendix:
- Listy endpointów REST (URL, metoda)
- Schematu odpowiedzi (pola, typy danych)
- Mechanizmu autentykacji (token format, nagłówek)

### Nowe zmienne środowiskowe (`.env`)
```
MENDIX_API_BASE_URL=https://mendix-app.example.com
MENDIX_API_TOKEN=secret
MENDIX_IMPORT_INTERVAL_MS=300000
```

### NearbyUnitsAgent — rozszerzenie spatial query
Po dodaniu `mendix_unit_cache`: `NearbyUnitsAgent` odpytuje OBIE tabele (`entity_registry` + `mendix_unit_cache`) i łączy wyniki przed `NearbyUnitsComputedEvent`.

`NearbyUnitsComputedEvent` rozszerzony o dwie listy:
- `entityIds: List<Long>` — IDs z `entity_registry` (bez zmian)
- `mendixIds: List<String>` — IDs z `mendix_unit_cache`

Frontend otrzymuje oba zbiory przez WebSocket i podświetla markery z obu warstw.

---

## 8. Auth — plan docelowy (poza zakresem)

Teraz: brak auth per-user (Spring otwarty lub API key).

Docelowo: OIDC federation.
- Mendix + Spring ufają temu samemu IdP (Azure AD / Keycloak)
- Widget dostaje OIDC JWT → Spring waliduje niezależnie
- Spring odpytuje Mendix REST własnym service account
- Token passthrough (Mendix session → Spring) **nie jest** docelowym wzorcem

---

## 9. Dev workflow

### Instalacja
```bash
cd frontend && npm install   # instaluje wszystkie workspace packages
```

### Codzienna praca
```bash
# Standalone dev (bez Mendix)
cd frontend/app && npm run dev         # http://localhost:5173

# Widget dev (wymaga Studio Pro + test project)
cd frontend/widget && npm run dev      # http://localhost:3000

# Backend
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Baza
docker compose up -d postgres
```

### Buildy — niezależne, zero zależności między sobą
```bash
cd frontend/app    && npm run build    # → frontend/app/dist/
cd frontend/widget && npm run release  # → frontend/widget/dist/*.mpk
```

### Strategia testowania
- Logika biznesowa testowana w `shared/` (vitest) — bez Mendix, bez Studio Pro
- Widget testowany w Studio Pro dopiero gdy logika potwierdzona w standalone

---

## 10. Migracja — mapa zmian

### Frontend (przepakowanie)
| Skąd | Dokąd |
|---|---|
| `frontend/src/components/` | `frontend/shared/src/components/` |
| `frontend/src/hooks/` | `frontend/shared/src/hooks/` |
| `frontend/src/services/api.ts` | `frontend/shared/src/services/api.ts` (refaktor) |
| `frontend/src/store/` | `frontend/shared/src/store/` |
| `frontend/src/types/` | `frontend/shared/src/types/` |
| `frontend/src/main.tsx` | `frontend/app/src/main.tsx` (thin shell) |
| `frontend/vite.config.ts` | `frontend/app/vite.config.ts` |
| `frontend/index.html` | `frontend/app/index.html` |
| `mendix-widget/` | `frontend/widget/` |

### Nowe pliki
- `frontend/package.json` (workspace root)
- `frontend/shared/package.json`
- `frontend/shared/src/GisMapApp.tsx`
- `frontend/shared/src/services/ApiContext.tsx`
- `frontend/app/package.json`
- `frontend/widget/` (przeniesiony, bez zmian struktury wewnętrznej)

### Usunięte
- `mendix-widget/` (przeniesiony do `frontend/widget/`)

### Dokumentacja do aktualizacji
- `CLAUDE.md` — nowa struktura katalogów, nowy agent `MendixImportAgent`, zmienne env
- `docs/ARCHITEKTURA_PLAN.md` — §5 struktura repo, §4 tabela agentów
- `docs/BACKLOG.md` — nowe zadania migracji + Mendix integration (z blokadą docs)
- `docs/API_REFERENCE.md` — `GET /api/mendix-units`
- `docs/DATA_SCHEMA.md` — tabela `mendix_unit_cache`
