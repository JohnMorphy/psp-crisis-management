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
    private static final int PAGE_SIZE       = 100;
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
        factory.setReadTimeout(120_000);
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
        long start    = System.currentTimeMillis();
        log.info("Usuwanie istniejacych granic: poziom={}", poziom);
        jdbcTemplate.update("DELETE FROM granice_administracyjne WHERE poziom = ?", poziom);

        String nextUrl    = buildInitialUrl(typeName, pageSize);
        int totalImported = 0;
        int totalSkipped  = 0;
        int page          = 0;

        while (nextUrl != null) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Przerwanie watku — przerywam import poziom={}", poziom);
                break;
            }
            page++;
            log.info("Pobieranie strony {} dla poziom={}", page, poziom);

            String gml = fetchWithRetry(nextUrl);
            if (gml == null) {
                log.error("Nie udalo sie pobrac strony {} dla poziom={} — przerywam", page, poziom);
                break;
            }

            ParseResult result           = parser.parse(gml);
            List<GranicaFeature> features = result.features();

            int inserted  = batchInsert(features, poziom);
            int skipped   = result.numberReturned() - features.size();
            totalImported += inserted;
            totalSkipped  += skipped + (features.size() - inserted);

            log.info("Strona {}: numberReturned={}, zapisano={}, pominieto={}",
                page, result.numberReturned(), inserted, skipped);

            nextUrl = result.nextUrl();
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
            log.error("Blad batch insert dla poziom={}", poziom, e);
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
