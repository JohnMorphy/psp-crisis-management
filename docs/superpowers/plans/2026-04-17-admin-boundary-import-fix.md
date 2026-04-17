# AdminBoundaryImportAgent — Fix WFS Import Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Naprawić import granic administracyjnych z GUGiK PRG WFS — poprawny URL, parsowanie GML (nie JSON), paginacja przez `next` link, batch insert.

**Architecture:** Wyodrębnienie `WfsGmlParser` jako osobnej, testowalnej klasy; `AdminBoundaryImportAgent` orchestruje HTTP + parsowanie + zapis. Parser korzysta z JDK `DocumentBuilder` (brak nowych zależności Maven). Geometria ingresowana przez PostGIS `ST_GeomFromGML` — eliminuje potrzebę GeoTools.

**Tech Stack:** Spring Boot 3 / Java 21, `javax.xml.parsers.DocumentBuilder` (JDK), `JdbcTemplate.batchUpdate`, PostGIS `ST_GeomFromGML` + `ST_Transform`, JUnit 5 / AssertJ (już w `spring-boot-starter-test`)

---

## Ustalenia z analizy endpointu

```
URL: https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeBoundaries
Typ odpowiedzi: GML (nie JSON)
Namespace: xmlns:ms="http://mapserver.gis.umn.edu/mapserver"

TypeNames:
  A01_Granice_wojewodztw  → 'wojewodztwo' (~16 features)
  A02_Granice_powiatow    → 'powiat'      (~380 features)
  A03_Granice_gmin        → 'gmina'       (~2477 features)

Pola w każdym feature:
  <ms:JPT_KOD_JE>  → kod_teryt
  <ms:JPT_NAZWA_>  → nazwa
  <ms:msGeometry>  → wrapper geometrii GML (srsName="urn:ogc:def:crs:EPSG::2180")

Paginacja:
  Parametry żądania: count=N&STARTINDEX=0
  Atrybut odpowiedzi: next="<url następnej strony>" (brak = ostatnia strona)
  numberMatched="unknown" — serwer nie zna łącznej liczby
  numberReturned="N"      — liczba zwróconych features na tej stronie
```

---

## Pliki

| Plik | Akcja | Odpowiedzialność |
|---|---|---|
| `backend/src/main/java/pl/lublin/dashboard/agent/WfsGmlParser.java` | Utwórz | Parsowanie GML → `ParseResult` (features + nextUrl + numberReturned) |
| `backend/src/main/java/pl/lublin/dashboard/agent/AdminBoundaryImportAgent.java` | Zastąp | Orchestracja: HTTP + paginacja + batch insert |
| `backend/src/test/java/pl/lublin/dashboard/agent/WfsGmlParserTest.java` | Utwórz | Testy jednostkowe parsera (bez sieci, bez bazy) |

---

## Task 1: WfsGmlParser — test pierwsz, potem implementacja

**Files:**
- Create: `backend/src/test/java/pl/lublin/dashboard/agent/WfsGmlParserTest.java`
- Create: `backend/src/main/java/pl/lublin/dashboard/agent/WfsGmlParser.java`

- [ ] **Krok 1.1: Utwórz plik testowy z Sample GML**

Utwórz `backend/src/test/java/pl/lublin/dashboard/agent/WfsGmlParserTest.java`:

```java
package pl.lublin.dashboard.agent;

import org.junit.jupiter.api.Test;
import pl.lublin.dashboard.agent.WfsGmlParser.GranicaFeature;
import pl.lublin.dashboard.agent.WfsGmlParser.ParseResult;

import static org.assertj.core.api.Assertions.assertThat;

class WfsGmlParserTest {

    private final WfsGmlParser parser = new WfsGmlParser();

    private static final String SAMPLE_GML = """
        <?xml version='1.0' encoding="UTF-8" ?>
        <wfs:FeatureCollection
           xmlns:ms="http://mapserver.gis.umn.edu/mapserver"
           xmlns:gml="http://www.opengis.net/gml/3.2"
           xmlns:wfs="http://www.opengis.net/wfs/2.0"
           timeStamp="2026-04-17T10:00:00"
           numberMatched="unknown"
           numberReturned="2"
           next="http://example.com?STARTINDEX=2">
          <wfs:member>
            <ms:A01_Granice_wojewodztw>
              <ms:msGeometry>
                <gml:MultiSurface gml:id="ms.1" srsName="urn:ogc:def:crs:EPSG::2180">
                  <gml:surfaceMember>
                    <gml:Polygon>
                      <gml:exterior>
                        <gml:LinearRing>
                          <gml:posList>537986.0 171698.0 747606.0 171698.0 747606.0 367472.0 537986.0 367472.0 537986.0 171698.0</gml:posList>
                        </gml:LinearRing>
                      </gml:exterior>
                    </gml:Polygon>
                  </gml:surfaceMember>
                </gml:MultiSurface>
              </ms:msGeometry>
              <ms:JPT_KOD_JE>32</ms:JPT_KOD_JE>
              <ms:JPT_NAZWA_>zachodniopomorskie</ms:JPT_NAZWA_>
            </ms:A01_Granice_wojewodztw>
          </wfs:member>
          <wfs:member>
            <ms:A01_Granice_wojewodztw>
              <ms:msGeometry>
                <gml:MultiSurface gml:id="ms.2" srsName="urn:ogc:def:crs:EPSG::2180">
                  <gml:surfaceMember>
                    <gml:Polygon>
                      <gml:exterior>
                        <gml:LinearRing>
                          <gml:posList>300000.0 400000.0 350000.0 400000.0 350000.0 450000.0 300000.0 450000.0 300000.0 400000.0</gml:posList>
                        </gml:LinearRing>
                      </gml:exterior>
                    </gml:Polygon>
                  </gml:surfaceMember>
                </gml:MultiSurface>
              </ms:msGeometry>
              <ms:JPT_KOD_JE>06</ms:JPT_KOD_JE>
              <ms:JPT_NAZWA_>lubelskie</ms:JPT_NAZWA_>
            </ms:A01_Granice_wojewodztw>
          </wfs:member>
        </wfs:FeatureCollection>
        """;

    @Test
    void parsuje_dwa_features() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.features()).hasSize(2);
    }

    @Test
    void parsuje_kod_teryt_i_nazwe() {
        ParseResult result = parser.parse(SAMPLE_GML);
        GranicaFeature first = result.features().get(0);
        assertThat(first.kodTeryt()).isEqualTo("32");
        assertThat(first.nazwa()).isEqualTo("zachodniopomorskie");

        GranicaFeature second = result.features().get(1);
        assertThat(second.kodTeryt()).isEqualTo("06");
        assertThat(second.nazwa()).isEqualTo("lubelskie");
    }

    @Test
    void ekstrahuje_gml_geometrii_z_srsName() {
        ParseResult result = parser.parse(SAMPLE_GML);
        String gml = result.features().get(0).gmlGeometry();
        assertThat(gml).contains("MultiSurface");
        assertThat(gml).contains("srsName=\"urn:ogc:def:crs:EPSG::2180\"");
        assertThat(gml).contains("537986.0");
    }

    @Test
    void parsuje_next_url() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.nextUrl()).isEqualTo("http://example.com?STARTINDEX=2");
    }

    @Test
    void parsuje_number_returned() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.numberReturned()).isEqualTo(2);
    }

    @Test
    void zwraca_null_next_url_gdy_brak_atrybutu() {
        String gmlNoNext = SAMPLE_GML.replace(
            "next=\"http://example.com?STARTINDEX=2\"", "");
        ParseResult result = parser.parse(gmlNoNext);
        assertThat(result.nextUrl()).isNull();
    }

    @Test
    void pomija_feature_z_pustym_kod_teryt() {
        String gmlEmpty = SAMPLE_GML.replace(
            "<ms:JPT_KOD_JE>32</ms:JPT_KOD_JE>",
            "<ms:JPT_KOD_JE></ms:JPT_KOD_JE>");
        ParseResult result = parser.parse(gmlEmpty);
        assertThat(result.features()).hasSize(1);
        assertThat(result.features().get(0).kodTeryt()).isEqualTo("06");
    }
}
```

- [ ] **Krok 1.2: Uruchom testy — powinny failować (brak klasy)**

```bash
cd backend
./mvnw test -pl . -Dtest=WfsGmlParserTest -q 2>&1 | tail -5
```

Oczekiwane: błąd kompilacji `cannot find symbol: class WfsGmlParser`

- [ ] **Krok 1.3: Utwórz `WfsGmlParser.java`**

Utwórz `backend/src/main/java/pl/lublin/dashboard/agent/WfsGmlParser.java`:

```java
package pl.lublin.dashboard.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class WfsGmlParser {

    private static final Logger log = LoggerFactory.getLogger(WfsGmlParser.class);

    private static final String NS_WFS = "http://www.opengis.net/wfs/2.0";
    private static final String NS_MS  = "http://mapserver.gis.umn.edu/mapserver";

    public record GranicaFeature(String kodTeryt, String nazwa, String gmlGeometry) {}

    public record ParseResult(List<GranicaFeature> features, String nextUrl, int numberReturned) {}

    public ParseResult parse(String gml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(gml.getBytes(StandardCharsets.UTF_8)));

            Element root = doc.getDocumentElement();

            String nextUrl = root.getAttribute("next");
            if (nextUrl.isBlank()) nextUrl = null;

            int numberReturned = 0;
            try {
                numberReturned = Integer.parseInt(root.getAttribute("numberReturned"));
            } catch (NumberFormatException ignored) {}

            NodeList members = root.getElementsByTagNameNS(NS_WFS, "member");
            List<GranicaFeature> features = new ArrayList<>(members.getLength());

            for (int i = 0; i < members.getLength(); i++) {
                Element member = (Element) members.item(i);
                Element featureEl = firstChildElement(member);
                if (featureEl == null) continue;

                try {
                    String kodTeryt = msText(featureEl, "JPT_KOD_JE").trim();
                    String nazwa    = msText(featureEl, "JPT_NAZWA_").trim();
                    String gmlGeom  = extractInnerGeometry(featureEl);

                    if (kodTeryt.isEmpty() || nazwa.isEmpty() || gmlGeom == null) {
                        log.warn("Pominięto feature #{}: kodTeryt='{}' nazwa='{}'",
                            i, kodTeryt, nazwa);
                        continue;
                    }
                    features.add(new GranicaFeature(kodTeryt, nazwa, gmlGeom));
                } catch (Exception e) {
                    log.warn("Błąd parsowania feature #{}: {}", i, e.getMessage());
                }
            }

            return new ParseResult(features, nextUrl, numberReturned);

        } catch (Exception e) {
            throw new RuntimeException("GML parse failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String msText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(NS_MS, localName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }

    private String extractInnerGeometry(Element featureEl) throws Exception {
        NodeList geomNodes = featureEl.getElementsByTagNameNS(NS_MS, "msGeometry");
        if (geomNodes.getLength() == 0) return null;

        Element msGeomEl = (Element) geomNodes.item(0);
        Element child = firstChildElement(msGeomEl);
        if (child == null) return null;

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(child), new StreamResult(sw));
        return sw.toString();
    }

    private Element firstChildElement(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        return (Element) child;
    }
}
```

- [ ] **Krok 1.4: Uruchom testy — powinny przejść**

```bash
cd backend
./mvnw test -pl . -Dtest=WfsGmlParserTest -q 2>&1 | tail -10
```

Oczekiwane:
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Krok 1.5: Commit**

```
feat(1.9): WfsGmlParser — GML parsing z testami jednostkowymi
```

---

## Task 2: Przepisanie AdminBoundaryImportAgent

**Files:**
- Modify: `backend/src/main/java/pl/lublin/dashboard/agent/AdminBoundaryImportAgent.java` (pełne zastąpienie)

- [ ] **Krok 2.1: Zastąp całą zawartość pliku**

Zastąp `AdminBoundaryImportAgent.java` poniższą implementacją:

```java
package pl.lublin.dashboard.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.lublin.dashboard.agent.WfsGmlParser.GranicaFeature;
import pl.lublin.dashboard.agent.WfsGmlParser.ParseResult;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AdminBoundaryImportAgent {

    private static final Logger log = LoggerFactory.getLogger(AdminBoundaryImportAgent.class);

    private static final String WFS_BASE_URL =
        "https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeBoundaries";
    private static final int PAGE_SIZE = 100;   // 50 dla gmin by zredukowac chunk
    private static final int PAGE_SIZE_GMINY = 50;

    private static final String INSERT_SQL = """
        INSERT INTO granice_administracyjne
            (kod_teryt, nazwa, poziom, kod_nadrzedny, geom, zrodlo, data_importu)
        VALUES (?, ?, ?, ?,
            ST_Transform(ST_Multi(ST_GeomFromGML(?)), 4326),
            'prg_wfs', NOW())
        ON CONFLICT (kod_teryt) DO UPDATE SET
            nazwa        = EXCLUDED.nazwa,
            geom         = EXCLUDED.geom,
            data_importu = EXCLUDED.data_importu
        """;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WfsGmlParser parser;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final RestTemplate restTemplate;

    public AdminBoundaryImportAgent() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000); // 2 min per page
        restTemplate = new RestTemplate(factory);
    }

    public boolean tryAcquire() {
        return isRunning.compareAndSet(false, true);
    }

    @Async
    public void importAll(String correlationId) {
        try {
            log.info("Start importu granic administracyjnych [{}]", correlationId);
            importLevel("A01_Granice_wojewodztw", "wojewodztwo", PAGE_SIZE);
            importLevel("A02_Granice_powiatow",   "powiat",      PAGE_SIZE);
            importLevel("A03_Granice_gmin",       "gmina",       PAGE_SIZE_GMINY);
            log.info("Import granic zakoncozny pomyslnie [{}]", correlationId);
        } catch (Exception e) {
            log.error("Blad importu granic [{}]", correlationId, e);
        } finally {
            isRunning.set(false);
        }
    }

    // ── per-level orchestration ───────────────────────────────────────────────

    private void importLevel(String typeName, String poziom, int pageSize) {
        long start = System.currentTimeMillis();
        log.info("Usuwanie istniejacych granic: poziom={}", poziom);
        jdbcTemplate.update("DELETE FROM granice_administracyjne WHERE poziom = ?", poziom);

        String nextUrl = buildInitialUrl(typeName, pageSize);
        int totalImported = 0;
        int totalSkipped  = 0;
        int page          = 0;

        while (nextUrl != null) {
            page++;
            log.info("Pobieranie strony {} dla poziom={}", page, poziom);

            String gml = fetchWithRetry(nextUrl);
            if (gml == null) {
                log.error("Nie udalo sie pobrac strony {} dla poziom={} — przerywam", page, poziom);
                break;
            }

            ParseResult result = parser.parse(gml);
            List<GranicaFeature> features = result.features();

            int inserted = batchInsert(features, poziom);
            int skipped  = result.numberReturned() - features.size(); // features rejected by parser
            totalImported += inserted;
            totalSkipped  += skipped + (features.size() - inserted);  // + DB insert failures

            log.info("Strona {}: numberReturned={}, zapisano={}, pominieto={}",
                page, result.numberReturned(), inserted, skipped);

            nextUrl = (result.nextUrl() != null && result.numberReturned() >= pageSize)
                ? result.nextUrl()
                : null;
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("Poziom {} zakoncozny: zaladowano={}, pominieto={}, czas={}s",
            poziom, totalImported, totalSkipped, elapsed);
    }

    // ── batch insert ─────────────────────────────────────────────────────────

    private int batchInsert(List<GranicaFeature> features, String poziom) {
        if (features.isEmpty()) return 0;
        try {
            int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    GranicaFeature f = features.get(i);
                    ps.setString(1, f.kodTeryt());
                    ps.setString(2, f.nazwa());
                    ps.setString(3, poziom);
                    ps.setString(4, deriveKodNadrzedny(f.kodTeryt(), poziom));
                    ps.setString(5, f.gmlGeometry());
                }
                @Override
                public int getBatchSize() { return features.size(); }
            });
            int ok = 0;
            for (int r : results) if (r >= 0 || r == java.sql.Statement.SUCCESS_NO_INFO) ok++;
            return ok;
        } catch (Exception e) {
            log.error("Blad batch insert dla poziom={}: {}", poziom, e.getMessage());
            return 0;
        }
    }

    // ── HTTP with retry ───────────────────────────────────────────────────────

    private String fetchWithRetry(String url) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                log.warn("Blad HTTP (proba {}/3): {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(5_000L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                }
            }
        }
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildInitialUrl(String typeName, int pageSize) {
        return WFS_BASE_URL
            + "?service=WFS&version=2.0.0&request=GetFeature"
            + "&typeNames=" + typeName
            + "&count=" + pageSize
            + "&STARTINDEX=0";
    }

    private String deriveKodNadrzedny(String kodTeryt, String poziom) {
        return switch (poziom) {
            case "powiat" -> kodTeryt.length() >= 2 ? kodTeryt.substring(0, 2) : null;
            case "gmina"  -> kodTeryt.length() >= 4 ? kodTeryt.substring(0, 4) : null;
            default -> null;
        };
    }
}
```

- [ ] **Krok 2.2: Sprawdź kompilację**

```bash
cd backend
./mvnw compile -q 2>&1
```

Oczekiwane: brak output (zero błędów).

- [ ] **Krok 2.3: Uruchom wszystkie testy**

```bash
cd backend
./mvnw test -q 2>&1 | tail -15
```

Oczekiwane:
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Krok 2.4: Commit**

```
fix(1.9): AdminBoundaryImportAgent — poprawny URL WFS, GML parser, paginacja next-link, batch insert
```

---

## Task 3: Weryfikacja manualna (wymaga uruchomionego backendu + bazy)

- [ ] **Krok 3.1: Uruchom bazę i backend**

```bash
# Terminal 1 — baza
docker compose up -d postgres

# Terminal 2 — backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -q
# Poczekaj na: "Started DashboardApplication"
```

- [ ] **Krok 3.2: Uruchom import**

```bash
curl -s -X POST http://localhost:8080/api/admin-boundaries/import | jq .
```

Oczekiwana odpowiedź:
```json
{
  "status": "started",
  "poziomy": ["wojewodztwo", "powiat", "gmina"],
  "correlation_id": "<uuid>"
}
```

- [ ] **Krok 3.3: Obserwuj logi backendu**

W logach backendu powinny pojawiać się wpisy w stylu:
```
INFO  AdminBoundaryImportAgent - Start importu granic administracyjnych [<uuid>]
INFO  AdminBoundaryImportAgent - Usuwanie istniejacych granic: poziom=wojewodztwo
INFO  AdminBoundaryImportAgent - Pobieranie strony 1 dla poziom=wojewodztwo
INFO  AdminBoundaryImportAgent - Strona 1: numberReturned=16, zapisano=16, pominieto=0
INFO  AdminBoundaryImportAgent - Poziom wojewodztwo zakoncozny: zaladowano=16, pominieto=0, czas=Xs
...
```

- [ ] **Krok 3.4: Sprawdź wyniki w bazie (po ~5 minutach)**

```bash
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT poziom, COUNT(*) FROM granice_administracyjne GROUP BY poziom ORDER BY poziom;"
```

Oczekiwane:
```
   poziom    | count
-------------+-------
 gmina       |  ~2477
 powiat      |   ~380
 wojewodztwo |    16
```

- [ ] **Krok 3.5: Sprawdź SRID i geometrię**

```bash
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT ST_SRID(geom), ST_AsText(ST_Centroid(ST_Envelope(geom)))
      FROM granice_administracyjne
      WHERE poziom='wojewodztwo' AND nazwa ILIKE '%lubel%';"
```

Oczekiwane: `SRID = 4326`, centroid w okolicach `(22-23°E, 51°N)`.

---

## Self-Review

**Pokrycie wymagań z planu naprawczego:**
- ✅ Poprawny URL (`AdministrativeBoundaries`)
- ✅ Poprawne TypeNames (bez prefixu `ms:`)
- ✅ Parser GML zamiast JSON (`WfsGmlParser`)
- ✅ `ST_GeomFromGML` zamiast `ST_GeomFromGeoJSON` — czyta `srsName` automatycznie
- ✅ Paginacja przez `next` link z WFS response
- ✅ Batch insert przez `JdbcTemplate.batchUpdate`
- ✅ Retry 3x z backoffem 5s*attempt
- ✅ Log końcowy: zaladowano/pominieto/czas
- ✅ Unit testy `WfsGmlParserTest` (7 przypadków, bez sieci/bazy)
- ✅ Brak nowych zależności Maven

**Placeholder scan:** Brak TBD/TODO w planie. Każdy krok ma kompletny kod.

**Type consistency:**
- `WfsGmlParser.GranicaFeature(kodTeryt, nazwa, gmlGeometry)` — używane spójnie w obu klasach
- `WfsGmlParser.ParseResult(features, nextUrl, numberReturned)` — spójne
- `INSERT_SQL` — parametry 1-5 odpowiadają kolejności `setValues` w `BatchPreparedStatementSetter`
