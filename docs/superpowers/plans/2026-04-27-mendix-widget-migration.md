# Mendix Widget Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Przekształcenie standalone React app + pustego mendix-widget w npm workspaces monorepo z dwoma niezależnymi outputami: Vite standalone app i Mendix pluggable widget — obie konsumują ten sam kod z `frontend/shared/`.

**Architecture:** npm workspaces root w `frontend/`. Kod biznesowy w `frontend/shared/` jako `@psp/shared` (czyste React, bez zależności od Vite/Mendix). `frontend/app/` to thin shell Vite czytający `VITE_API_BASE_URL`. `frontend/widget/` to thin shell Mendix czytający props z widget XML. Oba budowane niezależnie.

**Tech Stack:** React 19, Vite 8, TypeScript 6, Tailwind v4, npm workspaces, Mendix pluggable-widgets-tools 11, axios, zustand, @tanstack/react-query, react-leaflet

> **UWAGA: git** — projekt używa code review przez człowieka. Nie commituj automatycznie. Na końcu każdego taska sugerowany jest commit message — użyj go manualnie po weryfikacji.

---

## Mapa pliku — co się tworzy / modyfikuje

### Nowe pliki (Phase 1 — Frontend)
| Plik | Odpowiedzialność |
|---|---|
| `frontend/package.json` | npm workspaces root |
| `frontend/shared/package.json` | `@psp/shared` package manifest |
| `frontend/shared/tsconfig.json` | TypeScript config dla shared |
| `frontend/shared/src/index.ts` | Barrel export |
| `frontend/shared/src/GisMapApp.tsx` | Root komponent (QueryClient + ApiProvider + AppShell) |
| `frontend/shared/src/services/ApiContext.tsx` | React Context dla axios instance |
| `frontend/shared/src/services/api.ts` | `createApiClient(baseUrl)` |
| `frontend/shared/src/components/**` | Migracja z `frontend/src/components/` |
| `frontend/shared/src/hooks/**` | Migracja z `frontend/src/hooks/` |
| `frontend/shared/src/store/**` | Migracja z `frontend/src/store/` |
| `frontend/shared/src/types/**` | Migracja z `frontend/src/types/` |
| `frontend/app/package.json` | Standalone Vite app manifest |
| `frontend/app/tsconfig.json` | TS config references |
| `frontend/app/tsconfig.app.json` | TS config dla src/ + path alias do @psp/shared |
| `frontend/app/tsconfig.node.json` | TS config dla vite.config.ts |
| `frontend/app/vite.config.ts` | Vite config + alias @psp/shared |
| `frontend/app/index.html` | HTML entry point |
| `frontend/app/src/main.tsx` | Thin shell: czyta VITE_API_BASE_URL → GisMapApp |
| `frontend/app/src/index.css` | Tailwind import + @source dla shared |
| `frontend/widget/` | Przeniesiony z `mendix-widget/` |

### Modyfikowane (Phase 1)
| Plik | Zmiana |
|---|---|
| `frontend/shared/src/services/api.ts` | `createApiClient(baseUrl)` zamiast `import.meta.env` |
| `frontend/shared/src/hooks/useLayerData.ts` | `useApi()` zamiast `import api` |
| `frontend/shared/src/hooks/useAdminBoundaries.ts` | `useApi()` |
| `frontend/shared/src/hooks/useEntityLayerData.ts` | `useApi()` |
| `frontend/shared/src/hooks/importAdminBoundaries.ts` | `useApi()` |
| `frontend/widget/package.json` | Dodaj `@psp/shared: "*"` dependency |
| `frontend/widget/src/GisMap.tsx` | Importuj `GisMapApp` z `@psp/shared`, usuń HelloWorldSample |

### Usuwane (po weryfikacji buildów)
- `mendix-widget/` (cały katalog — przeniesiony do `frontend/widget/`)
- `frontend/src/` (całe — przeniesione do `frontend/shared/src/`)
- `frontend/vite.config.ts`, `frontend/index.html`, `frontend/App.tsx`, `frontend/App.css` (przeniesione do `frontend/app/`)
- Stary `frontend/package.json` (zastąpiony workspace root)

### Nowe pliki (Phase 2 — Backend, częściowo zablokowane)
| Plik | Status |
|---|---|
| `backend/src/main/resources/db/01_schema.sql` (modyfikacja) | ✅ odblokowane |
| `backend/.../model/MendixUnitCache.java` | ✅ odblokowane |
| `backend/.../repository/MendixUnitCacheRepository.java` | ✅ odblokowane |
| `backend/.../agent/MendixImportAgent.java` | 🔴 ZABLOKOWANE — wymaga docs Mendix API |
| `backend/.../controller/MendixUnitsController.java` | 🔴 ZABLOKOWANE |
| `.env.example` (modyfikacja) | ✅ odblokowane |

---

## PHASE 1 — Frontend restructuring

---

### Task 1: Workspace root + `shared/` package scaffold

**Files:**
- Create: `frontend/package.json` (zastępuje obecny)
- Create: `frontend/shared/package.json`
- Create: `frontend/shared/tsconfig.json`

- [ ] **Step 1.1: Zachowaj kopię obecnego `frontend/package.json`**

```bash
cp frontend/package.json frontend/package.json.backup
```

- [ ] **Step 1.2: Napisz nowy `frontend/package.json` — workspace root**

```json
{
  "name": "psp-frontend-workspace",
  "private": true,
  "workspaces": [
    "shared",
    "app",
    "widget"
  ]
}
```

- [ ] **Step 1.3: Utwórz `frontend/shared/package.json`**

```json
{
  "name": "@psp/shared",
  "version": "1.0.0",
  "private": true,
  "main": "src/index.ts",
  "types": "src/index.ts",
  "dependencies": {
    "@tanstack/react-query": "^5.99.0",
    "axios": "^1.15.0",
    "leaflet": "^1.9.4",
    "react-leaflet": "^5.0.0",
    "zustand": "^5.0.12"
  },
  "devDependencies": {
    "@types/leaflet": "^1.9.21",
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "typescript": "~6.0.2"
  },
  "peerDependencies": {
    "react": ">=18",
    "react-dom": ">=18"
  }
}
```

- [ ] **Step 1.4: Utwórz `frontend/shared/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "jsx": "react-jsx",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true
  },
  "include": ["src"]
}
```

- [ ] **Step 1.5: Utwórz strukturę katalogów `shared/src/`**

```bash
mkdir -p frontend/shared/src/components/layout
mkdir -p frontend/shared/src/components/map/layers
mkdir -p frontend/shared/src/components/panels
mkdir -p frontend/shared/src/hooks
mkdir -p frontend/shared/src/services
mkdir -p frontend/shared/src/store
mkdir -p frontend/shared/src/types
```

> Commit suggestion: `feat(workspace): npm workspaces root + @psp/shared package scaffold`

---

### Task 2: ApiContext + refaktor `api.ts`

**Files:**
- Create: `frontend/shared/src/services/ApiContext.tsx`
- Create: `frontend/shared/src/services/api.ts`

- [ ] **Step 2.1: Napisz `frontend/shared/src/services/api.ts`**

```typescript
import axios, { type AxiosInstance } from 'axios'

export const createApiClient = (baseUrl: string): AxiosInstance =>
  axios.create({ baseURL: baseUrl })
```

- [ ] **Step 2.2: Napisz `frontend/shared/src/services/ApiContext.tsx`**

```typescript
import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { type AxiosInstance } from 'axios'
import { createApiClient } from './api'

const ApiContext = createContext<AxiosInstance | null>(null)

export function ApiProvider({ baseUrl, children }: { baseUrl: string; children: ReactNode }) {
  const client = useMemo(() => createApiClient(baseUrl), [baseUrl])
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>
}

export function useApi(): AxiosInstance {
  const client = useContext(ApiContext)
  if (!client) throw new Error('useApi must be used within ApiProvider')
  return client
}
```

> Commit suggestion: `feat(shared): ApiContext + createApiClient — usuwa import.meta.env z shared`

---

### Task 3: Migracja plików źródłowych do `shared/`

**Files:**
- Copy: `frontend/src/types/gis.ts` → `frontend/shared/src/types/gis.ts`
- Copy: `frontend/src/store/mapStore.ts` → `frontend/shared/src/store/mapStore.ts`
- Copy: `frontend/src/store/notificationStore.ts` → `frontend/shared/src/store/notificationStore.ts`
- Copy: `frontend/src/components/**` → `frontend/shared/src/components/**`

- [ ] **Step 3.1: Kopiuj typy**

```bash
cp frontend/src/types/gis.ts frontend/shared/src/types/gis.ts
```

- [ ] **Step 3.2: Kopiuj store**

```bash
cp frontend/src/store/mapStore.ts frontend/shared/src/store/mapStore.ts
cp frontend/src/store/notificationStore.ts frontend/shared/src/store/notificationStore.ts
```

- [ ] **Step 3.3: Kopiuj komponenty**

```bash
cp frontend/src/components/layout/Header.tsx          frontend/shared/src/components/layout/Header.tsx
cp frontend/src/components/layout/Footer.tsx          frontend/shared/src/components/layout/Footer.tsx
cp frontend/src/components/layout/AppShell.tsx        frontend/shared/src/components/layout/AppShell.tsx
cp frontend/src/components/layout/NotificationList.tsx frontend/shared/src/components/layout/NotificationList.tsx
cp frontend/src/components/map/MapContainer.tsx       frontend/shared/src/components/map/MapContainer.tsx
cp frontend/src/components/map/EntityPopup.tsx        frontend/shared/src/components/map/EntityPopup.tsx
cp frontend/src/components/map/layers/EntityLayer.tsx         frontend/shared/src/components/map/layers/EntityLayer.tsx
cp frontend/src/components/map/layers/AdminBoundaryLayer.tsx  frontend/shared/src/components/map/layers/AdminBoundaryLayer.tsx
cp frontend/src/components/panels/LayerControlPanel.tsx  frontend/shared/src/components/panels/LayerControlPanel.tsx
cp frontend/src/components/panels/EntityFilterPanel.tsx  frontend/shared/src/components/panels/EntityFilterPanel.tsx
cp frontend/src/components/panels/RegionInfoPanel.tsx    frontend/shared/src/components/panels/RegionInfoPanel.tsx
```

- [ ] **Step 3.4: Kopiuj hooki**

```bash
cp frontend/src/hooks/useLayerData.ts          frontend/shared/src/hooks/useLayerData.ts
cp frontend/src/hooks/useAdminBoundaries.ts    frontend/shared/src/hooks/useAdminBoundaries.ts
cp frontend/src/hooks/useEntityLayerData.ts    frontend/shared/src/hooks/useEntityLayerData.ts
cp frontend/src/hooks/importAdminBoundaries.ts frontend/shared/src/hooks/importAdminBoundaries.ts
```

- [ ] **Step 3.5: Popraw ścieżki importów w skopiowanych plikach**

W każdym pliku w `frontend/shared/src/` zamień importy względne wskazujące na `../services/api` na użycie `useApi()` (Task 5 to zrobi szczegółowo). Na razie sprawdź, że żaden plik nie importuje `import.meta.env`.

```bash
grep -r "import.meta.env" frontend/shared/src/
```

Oczekiwane: brak wyników (jeśli są — znajdź plik i usuń zależność od env var).

> Commit suggestion: `feat(shared): migracja komponentów, hooków i store z frontend/src do shared/src`

---

### Task 4: `GisMapApp` root komponent + barrel export

**Files:**
- Create: `frontend/shared/src/GisMapApp.tsx`
- Create: `frontend/shared/src/index.ts`

- [ ] **Step 4.1: Napisz `frontend/shared/src/GisMapApp.tsx`**

```typescript
import { useMemo } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiProvider } from './services/ApiContext'
import AppShell from './components/layout/AppShell'

export interface GisMapAppProps {
  apiBaseUrl: string
  initialZoom?: number
}

export function GisMapApp({ apiBaseUrl }: GisMapAppProps) {
  const queryClient = useMemo(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 60_000, retry: 1 } }
  }), [])

  return (
    <QueryClientProvider client={queryClient}>
      <ApiProvider baseUrl={apiBaseUrl}>
        <AppShell />
      </ApiProvider>
    </QueryClientProvider>
  )
}
```

- [ ] **Step 4.2: Napisz `frontend/shared/src/index.ts`**

```typescript
export { GisMapApp } from './GisMapApp'
export type { GisMapAppProps } from './GisMapApp'
```

> Commit suggestion: `feat(shared): GisMapApp root komponent + barrel export`

---

### Task 5: Aktualizacja hooków — `useApi()` zamiast `import api`

**Files:**
- Modify: `frontend/shared/src/hooks/useLayerData.ts`
- Modify: `frontend/shared/src/hooks/useAdminBoundaries.ts`
- Modify: `frontend/shared/src/hooks/useEntityLayerData.ts`
- Modify: `frontend/shared/src/hooks/importAdminBoundaries.ts`

- [ ] **Step 5.1: Zaktualizuj `frontend/shared/src/hooks/useLayerData.ts`**

```typescript
import { useQuery } from '@tanstack/react-query'
import type { UseQueryOptions } from '@tanstack/react-query'
import { useApi } from '../services/ApiContext'

export function useLayerData<T = unknown>(
  layerId: string,
  options?: Omit<UseQueryOptions<T>, 'queryKey' | 'queryFn'>
) {
  const api = useApi()
  return useQuery<T>({
    queryKey: ['layers', layerId],
    queryFn: () => api.get<T>(`/api/layers/${layerId}`).then(r => r.data),
    staleTime: 60_000,
    ...options,
  })
}
```

- [ ] **Step 5.2: Przeczytaj `frontend/shared/src/hooks/useAdminBoundaries.ts` i zaktualizuj analogicznie**

Zamień:
```typescript
import api from '../services/api'
```
na:
```typescript
import { useApi } from '../services/ApiContext'
```
Dodaj `const api = useApi()` jako pierwszą linię w ciele funkcji/hooka (przed `useQuery`/`useMutation`).

- [ ] **Step 5.3: Przeczytaj i zaktualizuj `frontend/shared/src/hooks/useEntityLayerData.ts`**

Tak samo jak Step 5.2.

- [ ] **Step 5.4: Przeczytaj i zaktualizuj `frontend/shared/src/hooks/importAdminBoundaries.ts`**

Tak samo jak Step 5.2. Uwaga: jeśli to funkcja (nie hook React), nie może używać `useApi()`. W takim przypadku zmień sygnaturę na `importAdminBoundaries(api: AxiosInstance, ...)` i przekaż `api` jako argument z miejsca wywołania (komponent który go wywołuje użyje `useApi()`).

- [ ] **Step 5.5: Sprawdź TypeScript w shared**

```bash
cd frontend/shared && npx tsc --noEmit
```

Oczekiwane: 0 błędów TypeScript.

> Commit suggestion: `feat(shared): hooki używają useApi() z ApiContext — usunięto zależność od singleton api`

---

### Task 6: `frontend/app/` — scaffold Vite standalone app

**Files:**
- Create: `frontend/app/package.json`
- Create: `frontend/app/tsconfig.json`
- Create: `frontend/app/tsconfig.app.json`
- Create: `frontend/app/tsconfig.node.json`
- Create: `frontend/app/vite.config.ts`
- Create: `frontend/app/index.html`

- [ ] **Step 6.1: Napisz `frontend/app/package.json`**

```json
{
  "name": "gis-dashboard-app",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview"
  },
  "dependencies": {
    "@psp/shared": "*",
    "react": "^19.2.4",
    "react-dom": "^19.2.4"
  },
  "devDependencies": {
    "@tailwindcss/vite": "^4.2.2",
    "@eslint/js": "^9.39.4",
    "@types/node": "^24.12.2",
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "@vitejs/plugin-react": "^6.0.1",
    "eslint": "^9.39.4",
    "eslint-plugin-react-hooks": "^7.0.1",
    "eslint-plugin-react-refresh": "^0.5.2",
    "globals": "^17.4.0",
    "tailwindcss": "^4.2.2",
    "typescript": "~6.0.2",
    "typescript-eslint": "^8.58.0",
    "vite": "^8.0.4"
  }
}
```

- [ ] **Step 6.2: Napisz `frontend/app/vite.config.ts`**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@psp/shared': path.resolve(__dirname, '../shared/src/index.ts'),
    },
  },
})
```

- [ ] **Step 6.3: Napisz `frontend/app/tsconfig.json`**

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

- [ ] **Step 6.4: Napisz `frontend/app/tsconfig.app.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@psp/shared": ["../shared/src/index.ts"],
      "@psp/shared/*": ["../shared/src/*"]
    }
  },
  "include": ["src", "../shared/src"]
}
```

- [ ] **Step 6.5: Napisz `frontend/app/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 6.6: Skopiuj `index.html` z obecnego `frontend/` do `frontend/app/`**

```bash
cp frontend/index.html frontend/app/index.html
```

Sprawdź czy `<script src="/src/main.tsx">` wskazuje na właściwy plik — powinno być bez zmian.

- [ ] **Step 6.7: Utwórz `frontend/app/src/`**

```bash
mkdir -p frontend/app/src
```

Skopiuj asset jeśli istnieje:
```bash
cp -r frontend/src/assets frontend/app/src/assets 2>/dev/null || true
```

> Commit suggestion: `feat(app): scaffold Vite standalone app — package.json, vite.config, tsconfigs`

---

### Task 7: `frontend/app/src/main.tsx` + style

**Files:**
- Create: `frontend/app/src/main.tsx`
- Create: `frontend/app/src/index.css`

- [ ] **Step 7.1: Napisz `frontend/app/src/main.tsx`**

```typescript
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { GisMapApp } from '@psp/shared'
import './index.css'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GisMapApp apiBaseUrl={apiBaseUrl} />
  </StrictMode>,
)
```

- [ ] **Step 7.2: Napisz `frontend/app/src/index.css`**

```css
@import "tailwindcss";
@source "../../shared/src";
```

Dyrektywa `@source` informuje Tailwind v4 żeby skanował pliki w `shared/src/` w poszukiwaniu klas CSS.

> Commit suggestion: `feat(app): main.tsx thin shell + index.css z @source dla shared`

---

### Task 8: Weryfikacja buildu `frontend/app/`

- [ ] **Step 8.1: Zainstaluj wszystkie workspace packages**

```bash
cd frontend && npm install
```

Oczekiwane: instalacja bez błędów. W `frontend/node_modules/@psp/shared` powinien być symlink do `frontend/shared/`.

- [ ] **Step 8.2: Sprawdź TypeScript w app/**

```bash
cd frontend/app && npx tsc -b --noEmit
```

Oczekiwane: 0 błędów.

- [ ] **Step 8.3: Build produkcyjny app/**

```bash
cd frontend/app && npm run build
```

Oczekiwane: `dist/` wygenerowany, 0 błędów TypeScript/Vite.

- [ ] **Step 8.4: Uruchom dev server i sprawdź manualnie**

```bash
cd frontend/app && npm run dev
```

Otwórz `http://localhost:5173`. Sprawdź:
- Mapa się ładuje
- Warstwy toggleują się poprawnie
- Kliknięcie na placówkę otwiera popup
- Konsola: 0 błędów JS

> Jeśli build lub dev serwer failuje: sprawdź import `@psp/shared` (symlink w node_modules), sprawdź czy `useApi()` jest wywoływane wewnątrz `ApiProvider` (w drzewie komponentów poniżej `GisMapApp`).

> Commit suggestion: `test(app): weryfikacja buildu standalone app — 0 błędów`

---

### Task 9: Przeniesienie `mendix-widget/` → `frontend/widget/`

**Files:**
- Move: `mendix-widget/` → `frontend/widget/`
- Modify: `frontend/widget/package.json`
- Modify: `frontend/widget/src/GisMap.tsx`

- [ ] **Step 9.1: Skopiuj cały mendix-widget do frontend/widget/**

```bash
cp -r mendix-widget frontend/widget
```

(Nie usuwaj jeszcze `mendix-widget/` — zrobimy to po weryfikacji.)

- [ ] **Step 9.2: Zaktualizuj `frontend/widget/package.json` — dodaj `@psp/shared`**

W sekcji `dependencies` dodaj:
```json
"@psp/shared": "*"
```

Pełny `dependencies`:
```json
"dependencies": {
  "@psp/shared": "*",
  "classnames": "^2.2.6"
}
```

Sekcja `config` — zaktualizuj `developmentPort` jeśli koliduje z app (domyślnie 3000, app używa 5173 — OK):
```json
"config": {
  "projectPath": "./tests/testProject",
  "mendixHost": "http://localhost:8080",
  "developmentPort": 3000
}
```

- [ ] **Step 9.3: Zaktualizuj `frontend/widget/src/GisMap.xml` — właściwości widżetu**

Zastąp zawartość `<properties>`:
```xml
<properties>
    <propertyGroup caption="Connection">
        <property key="springBaseUrl" type="string" required="true">
            <caption>Spring API URL</caption>
            <description>Base URL of Spring backend, e.g. http://localhost:8080</description>
        </property>
    </propertyGroup>
    <propertyGroup caption="Map">
        <property key="initialZoom" type="integer" required="false" defaultValue="6">
            <caption>Initial zoom level</caption>
            <description>Map zoom level on load (1–18)</description>
        </property>
    </propertyGroup>
</properties>
```

- [ ] **Step 9.4: Przepisz `frontend/widget/src/GisMap.tsx`**

Usuń HelloWorldSample, zastąp GisMapApp z shared:

```typescript
import { ReactElement } from "react"
import { GisMapApp } from "@psp/shared"

interface GisMapContainerProps {
  springBaseUrl: string
  initialZoom?: number
}

export function GisMap({ springBaseUrl, initialZoom }: GisMapContainerProps): ReactElement {
  return (
    <GisMapApp
      apiBaseUrl={springBaseUrl}
      initialZoom={initialZoom}
    />
  )
}
```

- [ ] **Step 9.5: Zainstaluj zależności w widget/**

```bash
cd frontend && npm install
```

Sprawdź czy `frontend/widget/node_modules/@psp/shared` istnieje lub jest w `frontend/node_modules/@psp/shared`.

> Commit suggestion: `feat(widget): przeniesienie mendix-widget do frontend/widget + połączenie z @psp/shared`

---

### Task 10: Weryfikacja buildu `frontend/widget/`

- [ ] **Step 10.1: Build widżetu**

```bash
cd frontend/widget && npm run build
```

Oczekiwane: `dist/*.mpk` wygenerowany, 0 błędów.

**Jeśli build failuje z błędem resolucji `@psp/shared`:**

Dodaj do `frontend/widget/` plik `webpack.config.js` (pluggable-widgets-tools obsługuje ten plik jako extend):
```javascript
const path = require('path')

module.exports = {
  resolve: {
    alias: {
      '@psp/shared': path.resolve(__dirname, '../shared/src/index.ts'),
    },
    symlinks: true,
  },
}
```

Uruchom build ponownie.

**Jeśli błąd dot. React version (widget ma React 19 via resolutions, shared ma peerDep >=18):**

Sprawdź `frontend/widget/package.json` — sekcja `resolutions` powinna mieć:
```json
"resolutions": {
  "react": "^19.0.0",
  "react-dom": "^19.0.0"
}
```
(Była w oryginalnym mendix-widget — upewnij się że przeniesiona.)

- [ ] **Step 10.2: Sprawdź TypeScript w widget/**

```bash
cd frontend/widget && npx tsc --noEmit
```

Oczekiwane: 0 błędów.

> Commit suggestion: `test(widget): weryfikacja buildu Mendix widget — 0 błędów`

---

### Task 11: Cleanup starych plików

> Wykonaj tylko po pomyślnym zakończeniu Task 8 i Task 10.

- [ ] **Step 11.1: Usuń stary mendix-widget/**

```bash
rm -rf mendix-widget
```

- [ ] **Step 11.2: Usuń stary frontend/src/**

```bash
rm -rf frontend/src
```

- [ ] **Step 11.3: Usuń stare pliki z roota frontend/**

```bash
rm frontend/package.json.backup
rm -f frontend/vite.config.ts
rm -f frontend/index.html
rm -f frontend/App.tsx
rm -f frontend/App.css
```

(Niektóre pliki mogły już nie istnieć w roota — to OK.)

- [ ] **Step 11.4: Weryfikacja końcowa struktury**

```bash
ls frontend/
```

Oczekiwane: `package.json  shared/  app/  widget/  node_modules/`

```bash
cd frontend/app && npm run build && echo "app OK"
cd frontend/widget && npm run build && echo "widget OK"
```

Oba muszą przejść.

> Commit suggestion: `chore(cleanup): usunięto mendix-widget/ i frontend/src/ — zastąpione workspace struktura`

---

## PHASE 2 — Backend Mendix integration

---

### Task 12: SQL schema — `mendix_unit_cache` + zmienne env

**Files:**
- Modify: `backend/src/main/resources/db/01_schema.sql`
- Modify: `.env.example`

- [ ] **Step 12.1: Dodaj tabelę do `backend/src/main/resources/db/01_schema.sql`**

Na końcu pliku dodaj:
```sql
-- Mendix unit geo-cache: stores only geometry for spatial queries.
-- Full unit details are fetched from Mendix REST API on demand (proxied by MendixUnitsController).
CREATE TABLE IF NOT EXISTS mendix_unit_cache (
    mendix_id     VARCHAR(255) PRIMARY KEY,
    geom          GEOMETRY(Point, 4326) NOT NULL,
    category_code VARCHAR(100) NOT NULL,
    synced_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mendix_unit_cache_geom
    ON mendix_unit_cache USING GIST (geom);
```

- [ ] **Step 12.2: Dodaj zmienne Mendix do `.env.example`**

```bash
# Mendix API integration
MENDIX_API_BASE_URL=https://your-mendix-app.mendixcloud.com
MENDIX_API_TOKEN=your-mendix-service-account-token
MENDIX_IMPORT_INTERVAL_MS=300000
```

- [ ] **Step 12.3: Weryfikacja — schema kompiluje się**

```bash
cd backend && ./mvnw compile -q
```

Oczekiwane: BUILD SUCCESS

> Commit suggestion: `feat(2.M1): mendix_unit_cache SQL schema + zmienne env`

---

### Task 13: JPA Entity + Repository dla `mendix_unit_cache`

**Files:**
- Create: `backend/src/main/java/pl/lublin/dashboard/model/MendixUnitCache.java`
- Create: `backend/src/main/java/pl/lublin/dashboard/repository/MendixUnitCacheRepository.java`

- [ ] **Step 13.1: Napisz test jednostkowy (TDD — najpierw test)**

Utwórz `backend/src/test/java/pl/lublin/dashboard/repository/MendixUnitCacheRepositoryTest.java`:

```java
package pl.lublin.dashboard.repository;

import org.junit.jupiter.api.Test;
import pl.lublin.dashboard.model.MendixUnitCache;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MendixUnitCacheTest {

    @Test
    void mendixUnitCache_setsFieldsCorrectly() {
        MendixUnitCache unit = new MendixUnitCache();
        unit.setMendixId("MX-001");
        unit.setCategoryCode("psp");
        unit.setSyncedAt(OffsetDateTime.now());

        assertThat(unit.getMendixId()).isEqualTo("MX-001");
        assertThat(unit.getCategoryCode()).isEqualTo("psp");
        assertThat(unit.getSyncedAt()).isNotNull();
    }
}
```

- [ ] **Step 13.2: Uruchom test — oczekiwane FAIL (klasa nie istnieje)**

```bash
cd backend && ./mvnw test -Dtest="MendixUnitCacheTest" -q 2>&1 | tail -5
```

Oczekiwane: błąd kompilacji — `MendixUnitCache` nie istnieje.

- [ ] **Step 13.3: Napisz `MendixUnitCache.java`**

```java
package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mendix_unit_cache")
public class MendixUnitCache {

    @Id
    @Column(name = "mendix_id", length = 255)
    private String mendixId;

    @Column(name = "geom", nullable = false)
    private Point geom;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt;

    public String getMendixId() { return mendixId; }
    public void setMendixId(String mendixId) { this.mendixId = mendixId; }
    public Point getGeom() { return geom; }
    public void setGeom(Point geom) { this.geom = geom; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime syncedAt) { this.syncedAt = syncedAt; }
}
```

- [ ] **Step 13.4: Napisz `MendixUnitCacheRepository.java`**

```java
package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lublin.dashboard.model.MendixUnitCache;
import java.util.List;

public interface MendixUnitCacheRepository extends JpaRepository<MendixUnitCache, String> {

    @Query(value = """
        SELECT m.mendix_id FROM mendix_unit_cache m
        WHERE ST_DWithin(m.geom::geography,
                         ST_MakePoint(:lon, :lat)::geography,
                         :radiusMeters)
        """, nativeQuery = true)
    List<String> findMendixIdsWithinRadius(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") double radiusMeters
    );
}
```

- [ ] **Step 13.5: Uruchom test — oczekiwane PASS**

```bash
cd backend && ./mvnw test -Dtest="MendixUnitCacheTest" -q
```

Oczekiwane: BUILD SUCCESS

- [ ] **Step 13.6: Kompilacja całego backendu**

```bash
cd backend && ./mvnw compile -q
```

Oczekiwane: BUILD SUCCESS

> Commit suggestion: `feat(2.M2): MendixUnitCache JPA entity + MendixUnitCacheRepository z ST_DWithin`

---

### Task 14: MendixImportAgent 🔴 ZABLOKOWANE

> **STOP — nie implementuj tego taska bez dokumentacji Mendix REST API.**
>
> Wymagane od zespołu Mendix przed implementacją:
> 1. URL endpointu zwracającego jednostki (np. `GET /rest/crisis/v1/units`)
> 2. Schemat JSON odpowiedzi (pola: id, lat, lon, category, ...)
> 3. Mechanizm autentykacji (nagłówek, format tokenu)
> 4. Czy endpoint obsługuje paginację?
>
> Po otrzymaniu dokumentacji — napisz test integracyjny mockujący Mendix REST, potem implementuj agenta.

Szkielet do wypełnienia po odblokowaniu:

```java
// backend/src/main/java/pl/lublin/dashboard/agent/MendixImportAgent.java
@Service
@Slf4j
public class MendixImportAgent {
    // TODO po otrzymaniu dokumentacji Mendix API
}
```

---

### Task 15: MendixUnitsController 🔴 ZABLOKOWANE

> **STOP — zablokowane jak Task 14.** Controller proxy do Mendix REST wymaga znajomości endpointów i schematu danych.

Szkielet do wypełnienia:

```java
// backend/src/main/java/pl/lublin/dashboard/controller/MendixUnitsController.java
@RestController
@RequestMapping("/api/mendix-units")
@Slf4j
public class MendixUnitsController {
    // TODO po odblokowaniu Task 14
}
```

---

## PHASE 3 — Aktualizacja dokumentacji

---

### Task 16: Aktualizacja `CLAUDE.md`

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 16.1: Zaktualizuj sekcję `## Struktura repozytorium`**

Zmień opis na:
```
frontend/               ← CAŁY frontend (npm workspaces root)
  shared/               ← @psp/shared: komponenty, hooki, typy (bez zależności od Mendix/Vite)
  app/                  ← standalone Vite app (dev + demo)
  widget/               ← Mendix pluggable widget
backend/                ← Spring Boot (bez zmian)
```

- [ ] **Step 16.2: Zaktualizuj sekcję `## Stack technologiczny — Frontend`**

Dodaj:
```
- **npm workspaces** — monorepo: shared/, app/, widget/
- **@psp/shared** — wspólny pakiet React (bez Mendix/Vite zależności)
- **Mendix pluggable-widgets-tools 11** — build widżetu Mendix
```

- [ ] **Step 16.3: Zaktualizuj tabelę agentów**

Dodaj wiersz:
```
| `MendixImportAgent` | Polling Mendix REST → upsert geom do `mendix_unit_cache` | `@Scheduled` co N minut |
| `MendixUnitsController` | `GET /api/mendix-units` — proxy szczegółów z Mendix REST | HTTP GET |
```

- [ ] **Step 16.4: Zaktualizuj sekcję `## Jak uruchomić projekt lokalnie`**

```bash
# Frontend shared + app dev
cd frontend && npm install
cd frontend/app && npm run dev       # http://localhost:5173

# Widget dev (wymaga Mendix Studio Pro)
cd frontend/widget && npm run dev    # http://localhost:3000

# Widget build produkcyjny
cd frontend/widget && npm run release  # → dist/*.mpk
```

- [ ] **Step 16.5: Zaktualizuj sekcję `## Status projektu`**

Dodaj wiersz:
```
| CONCEPT CHANGE — Mendix Widget | ✅ | npm workspaces + @psp/shared + widget thin shell |
```

---

### Task 17: Aktualizacja `docs/ARCHITEKTURA_PLAN.md` i `docs/DATA_SCHEMA.md`

**Files:**
- Modify: `docs/ARCHITEKTURA_PLAN.md`
- Modify: `docs/DATA_SCHEMA.md`

- [ ] **Step 17.1: W `docs/ARCHITEKTURA_PLAN.md` zaktualizuj §5 Struktura katalogów**

Zastąp opis struktury frontendu zgodnie z nową hierarchią workspace.

- [ ] **Step 17.2: W `docs/ARCHITEKTURA_PLAN.md` zaktualizuj §4 Agenci**

Dodaj `MendixImportAgent` i `MendixUnitsController` do tabeli agentów.

- [ ] **Step 17.3: W `docs/DATA_SCHEMA.md` dodaj schemat `mendix_unit_cache`**

```markdown
## Tabela: `mendix_unit_cache`

Przechowuje wyłącznie geometrię i kategorię jednostek z Mendix (nie duplikuje atrybutów biznesowych).
Szczegóły jednostki (nazwa, adres, telefon) są zawsze pobierane z Mendix REST przez proxy.

| Kolumna | Typ | Opis |
|---|---|---|
| `mendix_id` | VARCHAR(255) PK | ID jednostki w Mendix |
| `geom` | GEOMETRY(Point, 4326) | Lokalizacja — używana przez ST_DWithin |
| `category_code` | VARCHAR(100) | Kategoria (np. `psp`, `osp`) |
| `synced_at` | TIMESTAMPTZ | Czas ostatniej synchronizacji |

**Dlaczego tylko geometria?** Tabela służy wyłącznie do spatial queries (NearbyUnitsAgent).
Pełne dane jednostek zawsze origin w Mendix — Spring jest proxy, nie kopią.
```

---

### Task 18: Aktualizacja `docs/BACKLOG.md`

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 18.1: Dodaj nową sekcję `CONCEPT CHANGE — Mendix Widget` jako ukończoną**

```markdown
## ✅ CONCEPT CHANGE — Mendix Widget Architecture

Zmiana architektury frontendu: z standalone React app na npm workspaces monorepo.
- `frontend/shared/` = @psp/shared (wspólne komponenty)
- `frontend/app/` = standalone Vite app (dev + demo)
- `frontend/widget/` = Mendix pluggable widget
- Buildy niezależne — zero artifact-dependency

Spec: `docs/superpowers/specs/2026-04-27-mendix-widget-architecture-design.md`
```

- [ ] **Step 18.2: Dodaj blok integracji Mendix jako nowe zadanie**

```markdown
## ⬜ MENDIX INTEGRATION — MendixImportAgent + proxy

🔴 BLOKADA: Wymaga dokumentacji Mendix REST API od zespołu Mendix.

### ⬜ M.1 — Dokumentacja Mendix REST API [BLOKER]

**Wymagane od zespołu Mendix:**
- Endpoint URL zwracający jednostki ochrony ludności
- Schemat JSON odpowiedzi (pola, typy)
- Mechanizm autentykacji (token format, nagłówek HTTP)
- Paginacja (czy jest, parametry)

Bez tej dokumentacji Task M.2 i M.3 nie mogą startować.

### ⬜ M.2 — MendixImportAgent (po odblokowaniu M.1)

`@Scheduled` polling Mendix REST → upsert do `mendix_unit_cache` (tylko geom + category).

### ⬜ M.3 — MendixUnitsController (po odblokowaniu M.1)

`GET /api/mendix-units` — proxy szczegółów jednostek z Mendix REST.
```

> Commit suggestion: `docs: aktualizacja CLAUDE.md, ARCHITEKTURA_PLAN.md, DATA_SCHEMA.md, BACKLOG.md — Mendix widget architecture`

---

## Self-Review

**Spec coverage:**
- [x] npm workspaces root w `frontend/` — Task 1
- [x] `@psp/shared` package — Tasks 2–5
- [x] `ApiContext` / `createApiClient` refaktor — Task 2
- [x] `GisMapApp` root komponent — Task 4
- [x] `frontend/app/` thin shell — Tasks 6–7
- [x] `frontend/widget/` thin shell — Task 9
- [x] Niezależne buildy obu — Tasks 8, 10
- [x] `mendix_unit_cache` SQL + JPA — Tasks 12–13
- [x] `MendixImportAgent` — Task 14 (zablokowane + udokumentowane)
- [x] `MendixUnitsController` — Task 15 (zablokowane + udokumentowane)
- [x] Dokumentacja — Tasks 16–18
- [x] Blokada Mendix API docs — zaznaczona w Task 14, 15, 18

**Placeholder scan:** Brak TBD bez uzasadnienia. Każde zablokowane zadanie ma jasną przyczynę i listę wymaganych informacji.

**Type consistency:**
- `GisMapApp` props: `apiBaseUrl: string, initialZoom?: number` — spójne w Tasks 4, 7, 9
- `useApi()` zwraca `AxiosInstance` — spójne w Tasks 2, 5
- `MendixUnitCache` pola: `mendixId, geom, categoryCode, syncedAt` — spójne w Tasks 13
- `findMendixIdsWithinRadius` zwraca `List<String>` — spójne z typem `mendixId: String`
