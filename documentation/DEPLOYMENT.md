# DEPLOYMENT.md — Instrukcja uruchomienia i wdrożenia

> Obejmuje: dwa tryby uruchomienia (dev hybrydowy i full-stack), deploy produkcyjny
> (VPS + docker-compose), zmienne środowiskowe i troubleshooting.
> **Czytaj przed pierwszym uruchomieniem projektu.**

---

## Spis treści

1. [Dwa tryby uruchomienia — porównanie](#1-dwa-tryby-uruchomienia--porównanie)
2. [Wymagania wstępne](#2-wymagania-wstępne)
3. [Konfiguracja zmiennych środowiskowych](#3-konfiguracja-zmiennych-środowiskowych)
4. [Tryb dev — pierwsze uruchomienie (Windows + WSL2)](#4-tryb-dev--pierwsze-uruchomienie-windows--wsl2)
5. [Tryb full-stack — uruchomienie jedną komendą](#5-tryb-full-stack--uruchomienie-jedną-komendą)
6. [Codzienna praca developerska](#6-codzienna-praca-developerska)
7. [Debugowanie backendu w IntelliJ](#7-debugowanie-backendu-w-intellij)
8. [Deploy na VPS — pierwsze wdrożenie](#8-deploy-na-vps--pierwsze-wdrożenie)
9. [Aktualizacja aplikacji na produkcji](#9-aktualizacja-aplikacji-na-produkcji)
10. [Zmienne środowiskowe — pełna lista](#10-zmienne-środowiskowe--pełna-lista)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Dwa tryby uruchomienia — porównanie

| | Tryb dev (`docker-compose.yml`) | Tryb full-stack (`docker-compose.full.yml`) |
|---|---|---|
| **Co uruchamia Docker** | Tylko PostgreSQL + PostGIS | PostgreSQL + backend + frontend |
| **Backend** | Lokalnie (Maven / IntelliJ) | W kontenerze |
| **Frontend** | Lokalnie (Vite dev server) | W kontenerze (Nginx) |
| **Debugowanie** | ✅ Pełne (breakpointy, hot reload) | ❌ Brak (kontenery) |
| **Wymaga Javy lokalnie** | ✅ Tak (JDK 21) | ❌ Nie |
| **Wymaga Node.js lokalnie** | ✅ Tak (Node 20) | ❌ Nie |
| **Czas do działającej apki** | ~2 min (po pierwszej instalacji) | ~5 min (build obrazów) |
| **Kiedy używać** | Codzienna praca, debugowanie | Demo, onboarding, VPS |
| **Porty** | Backend: 8080, Frontend: 5173, DB: 5432 | Backend: 8080, Frontend: 3000, DB: wewnętrzny |

**Zalecenie dla codziennej pracy:** tryb dev (hybrydowy).
**Zalecenie dla nowego developera / demo:** tryb full-stack.

---

## 2. Wymagania wstępne

### Na maszynie developerskiej (Windows)

| Narzędzie | Wersja | Instalacja |
|---|---|---|
| WSL2 + Ubuntu 22.04 | dowolna | `wsl --install` w PowerShell (Admin), potem Ubuntu z Microsoft Store |
| Docker Desktop | 4.x+ | https://docs.docker.com/desktop/install/windows-install/ |
| IntelliJ IDEA | 2023.x+ | https://www.jetbrains.com/idea/ |
| OpenJDK 21 | 21 LTS | w WSL2: `sudo apt install openjdk-21-jdk` |
| Node.js 20 | 20 LTS | w WSL2: przez nvm — `nvm install 20` |

> **WSL2 + Docker Desktop:** włącz integrację WSL2 w Docker Desktop →
> Settings → Resources → WSL Integration → Ubuntu. Dzięki temu komenda `docker`
> działa bezpośrednio w terminalu Ubuntu.

**Tylko do trybu full-stack** (nie potrzebujesz Javy ani Node.js lokalnie):
wystarczy WSL2 + Docker Desktop.

### Na serwerze produkcyjnym (VPS)

| Narzędzie | Wersja | Instalacja |
|---|---|---|
| Ubuntu | 22.04 LTS | obraz serwera |
| Docker Engine + Compose v2 | 24.x+ | patrz sekcja 8.1 |
| Nginx | 1.24+ | `sudo apt install nginx` |
| Certbot | dowolna | `sudo apt install certbot python3-certbot-nginx` |
| Git | dowolna | `sudo apt install git` |

**Minimalne zasoby VPS:**

| Zasób | Minimum | Zalecane |
|---|---|---|
| vCPU | 2 | 4 |
| RAM | 4 GB | 8 GB |
| Dysk SSD | 20 GB | 40 GB |

---

## 3. Konfiguracja zmiennych środowiskowych

```bash
# W katalogu głównym projektu (gis-dashboard/)
cp .env.example .env
nano .env   # lub otwórz w edytorze
```

Minimalne wartości do uzupełnienia przed pierwszym startem:

```bash
POSTGRES_PASSWORD=zmien_na_silne_haslo_min_20_znakow
OPENAI_API_KEY=sk-...   # tylko jeśli używasz Whisper jako fallback głosowy
```

> `.env` jest w `.gitignore` — **nigdy nie commituj go do repozytorium**.
> Pełna lista zmiennych w sekcji 10.

---

## 4. Tryb dev — pierwsze uruchomienie (Windows + WSL2)

> **Skrót dla Windows (cmd.exe):** zamiast kroków 3–6 możesz uruchomić `start-dev.cmd`
> z katalogu głównego — startuje bazę i wyświetla instrukcję uruchomienia backendu i frontendu.

Wykonaj kroki w podanej kolejności.

### Krok 1 — sklonuj repozytorium w WSL2

```bash
# Otwórz terminal Ubuntu (WSL2)
# WAŻNE: sklonuj na dysk WSL2, NIE na /mnt/c/
cd ~
git clone https://github.com/twoja-org/gis-dashboard.git
cd gis-dashboard
```

> ⚠️ Projekt musi być na dysku WSL2 (`~/gis-dashboard`), nie na `/mnt/c/...`.
> Maven i Vite mają problemy z wydajnością i file watcherami na ścieżkach `/mnt/`.

### Krok 2 — skonfiguruj `.env`

```bash
cp .env.example .env
nano .env   # uzupełnij co najmniej POSTGRES_PASSWORD
```

### Krok 3 — uruchom bazę danych

```bash
docker compose up -d postgres

# Sprawdź status (poczekaj ~10 sekund na pełny start)
docker compose ps
# Oczekiwany output: postgres   running   0.0.0.0:5432->5432/tcp
```

### Krok 4 — zainicjuj bazę danych

Spring Boot może wykonać migracje automatycznie jeśli `spring.sql.init.mode=always`
jest ustawione w `application-dev.yml`. Jeśli nie — wykonaj ręcznie:

```bash
# Sprawdź czy Spring inicjalizuje bazę automatycznie
grep "sql.init.mode" backend/src/main/resources/application-dev.yml

# Jeśli nie (manual seed):
for f in schema seed_layers seed_dps seed_relokacja seed_transport seed_strefy; do
  docker compose exec postgres psql -U ${POSTGRES_USER:-lublin} -d ${POSTGRES_DB:-gis_dashboard} \
    -f /docker-entrypoint-initdb.d/${f}.sql
done

# Weryfikacja
docker compose exec postgres psql -U ${POSTGRES_USER:-lublin} -d ${POSTGRES_DB:-gis_dashboard} \
  -c "SELECT COUNT(*) as placowki FROM placowka;"
# Oczekiwany output: 48
```

### Krok 5 — uruchom backend

```bash
cd ~/gis-dashboard/backend

# Pierwsze uruchomienie pobiera zależności Maven (~2–5 min)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Poprawny start:
```
Started DashboardApplication in 4.2 seconds
```

Weryfikacja:
```bash
curl http://localhost:8080/api/layers
# Oczekiwany output: JSON z listą 7 warstw
```

### Krok 6 — uruchom frontend

Otwórz **nowy** terminal WSL2:

```bash
cd ~/gis-dashboard/frontend
npm install    # pierwsze uruchomienie: ~1–2 min
npm run dev
```

Poprawny start:
```
  VITE v5.x.x  ready in 312 ms
  ➜  Local:   http://localhost:5173/
```

Otwórz `http://localhost:5173` w przeglądarce na Windows.

---

## 5. Tryb full-stack — uruchomienie jedną komendą

> **Skrót dla Windows (cmd.exe):** `start-all.cmd` z katalogu głównego wykonuje
> `docker compose -f docker-compose.full.yml up --build`.

Nie wymaga lokalnej instalacji Javy ani Node.js. Tylko Docker Desktop + WSL2.

```bash
cd ~/gis-dashboard

# Skopiuj i uzupełnij .env (jednorazowo)
cp .env.example .env
nano .env

# Uruchom cały stack (pierwsze uruchomienie buduje obrazy: ~5–10 min)
docker compose -f docker-compose.full.yml up --build

# Kolejne uruchomienia (bez rebuild):
docker compose -f docker-compose.full.yml up
```

Po uruchomieniu:

| Serwis | URL |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080` |
| Baza (jeśli potrzebny dostęp z zewnątrz) | `localhost:5432` (tylko gdy dodasz port mapping) |

Zatrzymanie:
```bash
docker compose -f docker-compose.full.yml down

# Zatrzymanie z usunięciem danych (reset bazy)
docker compose -f docker-compose.full.yml down -v
```

---

## 6. Codzienna praca developerska

Po pierwszej instalacji codzienne uruchamianie trybu dev:

```bash
# Terminal 1 — baza (jeśli nie działa już w tle)
docker compose up -d postgres

# Terminal 2 — backend
cd ~/gis-dashboard/backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 3 — frontend
cd ~/gis-dashboard/frontend
npm run dev
```

### Zatrzymanie

```bash
# Backend i frontend: Ctrl+C w odpowiednich terminalach

# Zatrzymaj bazę (opcjonalnie — dane pozostają w volume)
docker compose stop postgres

# Pełne zatrzymanie bez utraty danych
docker compose down

# Reset bazy (USUWA DANE)
docker compose down -v
```

---

## 7. Debugowanie backendu w IntelliJ

IntelliJ otwiera projekt bezpośrednio z dysku WSL2 bez kopiowania plików.

### Konfiguracja projektu

1. **File → Open** → wybierz ścieżkę WSL:
   `\\wsl$\Ubuntu\home\<user>\gis-dashboard\backend`

2. **File → Project Structure → SDKs** → dodaj JDK 21 z WSL2:
   - `+` → Add JDK from disk
   - Ścieżka: `\\wsl$\Ubuntu\usr\lib\jvm\java-21-openjdk-amd64`

3. **Run → Edit Configurations** → Spring Boot:
   - Main class: `pl.lublin.dashboard.DashboardApplication`
   - Active profiles: `dev`
   - Environment variables: skopiuj zawartość `.env`

### Uruchomienie z breakpointami

**Run → Debug 'DashboardApplication'** (Shift+F9).
Breakpointy działają natywnie — bez dodatkowej konfiguracji JDWP.

> Jeśli błąd `./mvnw: Permission denied`:
> ```bash
> chmod +x ~/gis-dashboard/backend/mvnw
> ```

---

## 8. Deploy na VPS — pierwsze wdrożenie

### 8.1 Przygotowanie serwera

```bash
# Aktualizacja systemu
sudo apt update && sudo apt upgrade -y

# Instalacja Docker Engine
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

# Weryfikacja
docker --version
docker compose version

# Nginx i Certbot
sudo apt install -y nginx certbot python3-certbot-nginx git
```

### 8.2 Klonowanie i konfiguracja

```bash
sudo mkdir -p /opt/gis-dashboard
sudo chown $USER:$USER /opt/gis-dashboard
cd /opt/gis-dashboard

git clone https://github.com/twoja-org/gis-dashboard.git .
cp .env.example .env
nano .env   # uzupełnij zmienne produkcyjne (sekcja 10)
```

### 8.3 Konfiguracja Nginx

```bash
sudo nano /etc/nginx/sites-available/gis-dashboard
```

```nginx
server {
    listen 80;
    server_name dashboard.twojadomena.pl;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name dashboard.twojadomena.pl;
    # ssl_certificate i ssl_certificate_key — Certbot uzupełni automatycznie

    # Frontend React
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Backend — REST API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Backend — WebSocket (STOMP)
    # Uwaga: blok /ws musi być PRZED /api/ w konfiguracji
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/gis-dashboard /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 8.4 Certyfikat HTTPS

```bash
# Upewnij się że DNS domeny wskazuje na IP serwera
sudo certbot --nginx -d dashboard.twojadomena.pl

# Test auto-odnowienia
sudo certbot renew --dry-run
```

### 8.5 Uruchomienie aplikacji

```bash
cd /opt/gis-dashboard

# Build i start (tryb full-stack)
docker compose -f docker-compose.full.yml up --build -d

# Sprawdź status
docker compose -f docker-compose.full.yml ps

# Weryfikacja
curl https://dashboard.twojadomena.pl/api/layers
```

---

## 9. Aktualizacja aplikacji na produkcji

```bash
cd /opt/gis-dashboard

# Pobierz zmiany
git pull origin main

# Przebuduj i zrestartuj tylko backend i frontend (baza niezmieniona)
docker compose -f docker-compose.full.yml build backend frontend
docker compose -f docker-compose.full.yml up -d --no-deps backend frontend

# Sprawdź logi po restarcie
docker compose -f docker-compose.full.yml logs --tail=50 backend
```

### Rollback

```bash
git log --oneline -5        # znajdź hash poprzedniej wersji
git checkout <hash>
docker compose -f docker-compose.full.yml build backend frontend
docker compose -f docker-compose.full.yml up -d --no-deps backend frontend
```

### Monitoring logów

```bash
# Wszystkie serwisy na żywo
docker compose -f docker-compose.full.yml logs -f

# Tylko backend
docker compose -f docker-compose.full.yml logs -f backend

# Ostatnia godzina
docker compose -f docker-compose.full.yml logs --since="1h" backend
```

---

## 10. Zmienne środowiskowe — pełna lista

```bash
# ============================================================
# BAZA DANYCH
# ============================================================
POSTGRES_USER=lublin
POSTGRES_PASSWORD=ZMIEN_NA_SILNE_HASLO          # wymagane
POSTGRES_DB=gis_dashboard

# ============================================================
# BACKEND — SPRING BOOT
# ============================================================
SPRING_PROFILES_ACTIVE=dev          # prod: zmień na "prod"
BACKEND_PORT=8080

# Połączenie z bazą
# Dev (backend lokalnie, baza w Docker): localhost:5432
# Prod (oba w Docker): postgres:5432 — nazwa serwisu Docker Compose
DATABASE_URL=jdbc:postgresql://localhost:5432/gis_dashboard

# Pula połączeń
DB_POOL_SIZE=10                     # prod: 20

# CORS
# Dev: http://localhost:5173
# Prod: https://dashboard.twojadomena.pl
CORS_ALLOWED_ORIGINS=http://localhost:5173

# WebSocket
WEBSOCKET_ALLOWED_ORIGINS=http://localhost:5173

# Scraper — interwał automatyczny [sekundy]
SCRAPER_INTERVAL_S=86400            # co 24 godziny

# ============================================================
# FRONTEND — VITE
# ============================================================
# Zmienne muszą mieć prefiks VITE_ żeby być dostępne w przeglądarce
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws

# ============================================================
# ZEWNĘTRZNE API
# ============================================================
OPENAI_API_KEY=sk-...               # Whisper fallback asystenta głosowego
NOMINATIM_URL=https://nominatim.openstreetmap.org
OSRM_URL=https://router.project-osrm.org

# ============================================================
# PRODUKCJA — tylko na VPS
# ============================================================
APP_DOMAIN=dashboard.twojadomena.pl
```

### Różnice dev ↔ prod

| Zmienna | Dev | Prod |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/...` | `jdbc:postgresql://postgres:5432/...` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | `https://dashboard.twojadomena.pl` |
| `VITE_API_BASE_URL` | `http://localhost:8080` | `https://dashboard.twojadomena.pl` |
| `VITE_WS_URL` | `ws://localhost:8080/ws` | `wss://dashboard.twojadomena.pl/ws` |
| `DB_POOL_SIZE` | `10` | `20` |

> Na produkcji `DATABASE_URL` używa nazwy serwisu Docker (`postgres`) zamiast `localhost`.
> Kontenery w tej samej sieci Docker Compose komunikują się przez nazwy serwisów.

---

## 11. Troubleshooting

### Baza nie startuje / kontener restartuje się

```bash
docker compose logs postgres

# Najczęstsza przyczyna: specjalne znaki w POSTGRES_PASSWORD
# Używaj tylko: liter, cyfr, podkreślników
```

### Backend nie łączy się z bazą (Connection refused)

```bash
# Sprawdź czy baza działa
docker compose ps postgres

# Sprawdź port z WSL2
nc -zv localhost 5432

# Jeśli port niedostępny — sprawdź WSL Integration w Docker Desktop
# Settings → Resources → WSL Integration → włącz Ubuntu
```

### Vite HMR nie odświeża przeglądarki

```bash
# Sprawdź lokalizację projektu
pwd
# Powinno być: /home/<user>/gis-dashboard  ← dobrze
# Nie:         /mnt/c/Users/...            ← przenieś projekt na dysk WSL2

# Jeśli projekt już jest na WSL2, dodaj do vite.config.js:
# server: { watch: { usePolling: true } }
```

### `./mvnw: Permission denied`

```bash
chmod +x ./mvnw
```

### Backend startuje ale /api/layers zwraca 404 lub pustą tablicę

```bash
# Sprawdź czy seed został wykonany
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM layer_config;"
# Jeśli 0 — wykonaj seed z kroku 4

# Sprawdź logi backendu pod kątem błędów inicjalizacji
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "ERROR|WARN"
```

### WebSocket nie łączy się (błąd w konsoli przeglądarki)

```bash
# Sprawdź konfigurację CORS i WebSocket origins
grep -E "CORS|WEBSOCKET" .env

# Na prod — Nginx musi mieć blok /ws z Upgrade header
# (patrz sekcja 8.3 — location /ws)
# Bez tego STOMP handshake nie przejdzie przez proxy
```

### Asystent głosowy nie działa

```bash
# Web Speech API wymaga HTTPS lub localhost
# Na prod: sprawdź ważność certyfikatu SSL
curl -I https://dashboard.twojadomena.pl | grep "HTTP/"

# Web Speech API nie działa w Firefox — użyj Chrome lub Edge
# Sprawdź czy OPENAI_API_KEY jest ustawiony (fallback Whisper)
grep OPENAI_API_KEY .env
```

### Kontener backend nie startuje na produkcji

```bash
docker compose -f docker-compose.full.yml logs backend

# Najczęstsze przyczyny:
# a) DATABASE_URL wskazuje na localhost zamiast postgres
#    Poprawka: DATABASE_URL=jdbc:postgresql://postgres:5432/gis_dashboard

# b) Baza nie zdążyła się uruchomić — sprawdź depends_on z healthcheck
#    w docker-compose.full.yml (postgres musi mieć healthcheck)

# c) Zły profil — upewnij się że SPRING_PROFILES_ACTIVE=prod
grep SPRING_PROFILES_ACTIVE .env
```

### Przydatne komendy diagnostyczne

```bash
# Status wszystkich kontenerów
docker compose ps                                    # tryb dev
docker compose -f docker-compose.full.yml ps        # tryb full-stack

# Zużycie zasobów
docker stats

# Wejdź do kontenera bazy (psql)
docker compose exec postgres psql -U lublin -d gis_dashboard

# Wejdź do kontenera backendu
docker compose -f docker-compose.full.yml exec backend bash

# Sprawdź czy kontenery się widzą w sieci Docker
docker compose -f docker-compose.full.yml exec backend ping postgres
```
