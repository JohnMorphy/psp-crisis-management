package pl.lublin.dashboard.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lublin.dashboard.model.IkeResult;
import pl.lublin.dashboard.model.Placowka;
import pl.lublin.dashboard.repository.IkeResultRepository;
import pl.lublin.dashboard.repository.PlacowkaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IkeAgent {

    private static final Logger log = LoggerFactory.getLogger(IkeAgent.class);

    @Autowired private PlacowkaRepository placowkaRepository;
    @Autowired private IkeResultRepository ikeResultRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private IkeConfigData config;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // ─── Config POJOs ─────────────────────────────────────────────────────────

    public static class IkeConfigData {
        public Wagi wagi;
        public Progi progi;
        @JsonProperty("promienie_km") public PromienieKm promienieKm;
        @JsonProperty("wartosci_domyslne") public WartosciDomyslne wartosciDomyslne;
    }

    public static class Wagi {
        public double zagrozenie;
        public double niesamodzielni;
        @JsonProperty("transport_brak") public double transportBrak;
        @JsonProperty("droznosc_brak") public double droznoscBrak;
        @JsonProperty("odleglosc_relokacji") public double odlegloscRelokacji;
    }

    public static class Progi {
        public double czerwony;
        public double zolty;
    }

    public static class PromienieKm {
        @JsonProperty("transport_dostepny") public double transportDostepny;
        @JsonProperty("miejsca_relokacji") public double miejscaRelokacji;
    }

    public static class WartosciDomyslne {
        @JsonProperty("brak_danych_drogi") public double brakDanychDrogi;
        @JsonProperty("brak_transportu_w_promieniu") public double brakTransportu;
        @JsonProperty("brak_danych_zagrozenia") public double brakZagrozenia;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void loadConfig() {
        try {
            ClassPathResource res = new ClassPathResource("ike.config.json");
            config = objectMapper.readValue(res.getInputStream(), IkeConfigData.class);
            validateWeights();
            log.info("[IkeAgent] Zaladowano ike.config.json, suma wag = 1.0");
        } catch (Exception e) {
            throw new IllegalStateException("Nie mozna wczytac ike.config.json: " + e.getMessage(), e);
        }
    }

    private void validateWeights() {
        double sum = config.wagi.zagrozenie + config.wagi.niesamodzielni
                   + config.wagi.transportBrak + config.wagi.droznoscBrak
                   + config.wagi.odlegloscRelokacji;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                "Suma wag IKE != 1.0 (wartosc: " + sum + "). Sprawdz ike.config.json.");
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public boolean tryAcquire() {
        return isRunning.compareAndSet(false, true);
    }

    public IkeConfigData getConfig() {
        return config;
    }

    @Transactional
    public RecalculateResult recalculateAll(String correlationId) {
        long start = System.currentTimeMillis();
        log.info("[IkeAgent] Start przeliczania IKE, correlation_id={}", correlationId);

        List<Placowka> placowki = placowkaRepository.findAll();
        int przetworzone = 0;

        for (Placowka p : placowki) {
            try {
                IkeResult result = calculateIke(p, correlationId);
                ikeResultRepository.save(result);
                przetworzone++;
            } catch (Exception e) {
                log.error("[IkeAgent] Blad obliczania IKE dla {}: {}", p.getKod(), e.getMessage(), e);
            }
        }

        isRunning.set(false);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[IkeAgent] Zakończono, correlation_id={}, placowki={}, czas={}ms",
                correlationId, przetworzone, elapsed);

        return new RecalculateResult(correlationId, przetworzone);
    }

    // ─── Algorithm ────────────────────────────────────────────────────────────

    private IkeResult calculateIke(Placowka p, String correlationId) {
        IkeResult result = new IkeResult();
        result.setPlacowkaKod(p.getKod());
        result.setCorrelationId(correlationId);
        result.setObliczoneO(OffsetDateTime.now());
        List<String> warnings = new ArrayList<>();

        // E9: geom IS NULL
        if (p.getGeom() == null) {
            log.error("[IkeAgent] E9: geom IS NULL dla {}", p.getKod());
            result.setIkeKategoria("nieznany");
            result.setDataWarnings(warnings);
            return result;
        }

        // E8: pojemnosc_ogolna = NULL or <= 0
        if (p.getPojemnoscOgolna() == null || p.getPojemnoscOgolna() <= 0) {
            log.error("[IkeAgent] E8: pojemnosc_ogolna = NULL lub 0 dla {}", p.getKod());
            result.setIkeKategoria("nieznany");
            result.setDataWarnings(warnings);
            return result;
        }

        // E1: liczba_podopiecznych = 0
        if (p.getLiczbaPodopiecznych() != null && p.getLiczbaPodopiecznych() == 0) {
            log.info("[IkeAgent] E1: liczba_podopiecznych = 0 dla {}", p.getKod());
            result.setIkeScore(BigDecimal.ZERO);
            result.setIkeKategoria("zielony");
            result.setScoreZagrozenia(BigDecimal.ZERO);
            result.setScoreNiesamodzielnych(BigDecimal.ZERO);
            result.setScoreBrakuTransportu(BigDecimal.ZERO);
            result.setScoreBrakuDroznosci(BigDecimal.ZERO);
            result.setScoreOdleglosci(BigDecimal.ZERO);
            result.setDataWarnings(warnings);
            return result;
        }

        // c. score_zagrozenia
        BigDecimal scoreZag = calcScoreZagrozenia(p.getKod(), warnings);
        result.setScoreZagrozenia(scoreZag);

        // d. score_niesamodzielnych (E2, E12)
        BigDecimal scoreNies = calcScoreNiesamodzielnych(p, warnings);
        result.setScoreNiesamodzielnych(scoreNies);

        // e. score_braku_transportu (E4)
        boolean wymagaPrzystosowania = scoreNies.compareTo(new BigDecimal("0.5")) > 0;
        BigDecimal scoreTran = calcScoreBrakuTransportu(p.getKod(), wymagaPrzystosowania, warnings);
        result.setScoreBrakuTransportu(scoreTran);

        // f. score_braku_droznosci (E5)
        BigDecimal scoreDroz = calcScoreBrakuDroznosci(p.getKod(), warnings);
        result.setScoreBrakuDroznosci(scoreDroz);

        // g. nearest relocation (E6, E11)
        RelocationData reloc = findNearestRelocation(p.getKod(), warnings);
        if (reloc == null) {
            result.setIkeKategoria("nieznany");
            result.setDataWarnings(warnings);
            return result;
        }

        // h. score_odleglosci_relokacji
        BigDecimal scoreOdl = calcScoreOdleglosci(reloc.odlegnoscKm, p.getKod(), warnings);
        if (scoreOdl == null) {
            // d > 50 km → IKE = null
            result.setIkeKategoria("nieznany");
            result.setDataWarnings(warnings);
            return result;
        }
        result.setScoreOdleglosci(scoreOdl);

        // i. IKE = weighted sum, rounded to 4 decimals
        double ike = config.wagi.zagrozenie       * scoreZag.doubleValue()
                   + config.wagi.niesamodzielni    * scoreNies.doubleValue()
                   + config.wagi.transportBrak     * scoreTran.doubleValue()
                   + config.wagi.droznoscBrak      * scoreDroz.doubleValue()
                   + config.wagi.odlegloscRelokacji * scoreOdl.doubleValue();

        BigDecimal ikeRounded = BigDecimal.valueOf(ike).setScale(4, RoundingMode.HALF_UP);
        result.setIkeScore(ikeRounded);

        // j. kategoria
        result.setIkeKategoria(classifyIke(ikeRounded.doubleValue()));

        // relocation metadata
        result.setCelRelokacjiKod(reloc.kod);
        // k. OSRM trasa: pomijamy — OSRM offline: trasa = null, nie blokuj IKE

        result.setDataWarnings(warnings);
        return result;
    }

    // ─── Score calculations ───────────────────────────────────────────────────

    private BigDecimal calcScoreZagrozenia(String placowkaKod, List<String> warnings) {
        String sql = """
            SELECT s.poziom
            FROM strefy_zagrozen s
            WHERE ST_Intersects(
                ST_Transform((SELECT geom FROM placowka WHERE kod = ?), 2180),
                ST_Transform(s.geom, 2180)
            )
            ORDER BY CASE s.poziom
                WHEN 'czerwony' THEN 1
                WHEN 'zolty'    THEN 2
                WHEN 'zielony'  THEN 3
                ELSE 4
            END
            LIMIT 1
            """;
        try {
            List<String> rows = jdbcTemplate.queryForList(sql, String.class, placowkaKod);
            if (rows.isEmpty()) return BigDecimal.ZERO; // E3: brak stref
            return switch (rows.get(0)) {
                case "czerwony" -> new BigDecimal("1.00");
                case "zolty"    -> new BigDecimal("0.60");
                case "zielony"  -> new BigDecimal("0.20");
                default         -> BigDecimal.ZERO;
            };
        } catch (Exception e) {
            log.warn("[IkeAgent] Warstwa stref niedostepna dla {}: {}", placowkaKod, e.getMessage());
            warnings.add("warstwa_strefy_offline");
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calcScoreNiesamodzielnych(Placowka p, List<String> warnings) {
        if (p.getNiesamodzielniProcent() == null) {
            log.warn("[IkeAgent] E2: niesamodzielni_procent NULL dla {}", p.getKod());
            warnings.add("niesamodzielni_procent_brak");
            return new BigDecimal("0.50");
        }
        if (p.getNiesamodzielniProcent().compareTo(BigDecimal.ONE) > 0) {
            log.warn("[IkeAgent] E12: niesamodzielni_procent > 1.0 dla {}", p.getKod());
            warnings.add("niesamodzielni_procent_ponad_1");
            return BigDecimal.ONE;
        }
        return p.getNiesamodzielniProcent();
    }

    private BigDecimal calcScoreBrakuTransportu(String placowkaKod, boolean wymagaPrzystosowania, List<String> warnings) {
        double promienM = config.promienieKm.transportDostepny * 1000.0;
        String sql = """
            SELECT COUNT(*) AS n
            FROM zasob_transportu t
            WHERE t.dostepny = TRUE
              AND (CAST(? AS BOOLEAN) = FALSE OR t.przyjmuje_niesamodzielnych = TRUE)
              AND ST_DWithin(
                  ST_Transform(t.geom, 2180),
                  ST_Transform((SELECT geom FROM placowka WHERE kod = ?), 2180),
                  ?
              )
            """;
        try {
            Integer n = jdbcTemplate.queryForObject(sql, Integer.class,
                    wymagaPrzystosowania, placowkaKod, promienM);
            if (n == null) n = 0;
            return switch (n) {
                case 0  -> new BigDecimal("1.00");
                case 1  -> new BigDecimal("0.75");
                case 2  -> new BigDecimal("0.50");
                case 3  -> new BigDecimal("0.25");
                default -> BigDecimal.ZERO;
            };
        } catch (Exception e) {
            log.warn("[IkeAgent] E4: warstwa transportu niedostepna dla {}: {}", placowkaKod, e.getMessage());
            warnings.add("warstwa_transport_offline");
            return BigDecimal.ONE;
        }
    }

    private BigDecimal calcScoreBrakuDroznosci(String placowkaKod, List<String> warnings) {
        String sql = """
            SELECT d.droznosc, COUNT(*) AS liczba
            FROM drogi_ewakuacyjne d
            WHERE ST_DWithin(
                ST_Transform(d.geom, 2180),
                ST_Transform((SELECT geom FROM placowka WHERE kod = ?), 2180),
                2000
            )
            GROUP BY d.droznosc
            """;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, placowkaKod);
            if (rows.isEmpty()) {
                log.warn("[IkeAgent] E5: brak drog w promieniu 2 km dla {}", placowkaKod);
                warnings.add("brak_drog_w_promieniu");
                return new BigDecimal("0.50");
            }
            boolean hasPrzejezdna  = false;
            boolean hasUtrudnienia = false;
            boolean hasZablokowana = false;
            for (Map<String, Object> row : rows) {
                String droznosc = (String) row.get("droznosc");
                if ("przejezdna".equals(droznosc))  hasPrzejezdna  = true;
                if ("utrudnienia".equals(droznosc))  hasUtrudnienia = true;
                if ("zablokowana".equals(droznosc))  hasZablokowana = true;
            }
            if (hasZablokowana) {
                if (!hasPrzejezdna && !hasUtrudnienia) return new BigDecimal("1.00"); // all blocked
                if (hasUtrudnienia && !hasPrzejezdna)  return new BigDecimal("0.70"); // blocked + difficulties
                return new BigDecimal("0.50"); // blocked + passable
            }
            if (hasUtrudnienia) return new BigDecimal("0.30"); // only difficulties
            return BigDecimal.ZERO; // at least one passable, no blocked
        } catch (Exception e) {
            log.warn("[IkeAgent] E5: warstwa drog niedostepna dla {}: {}", placowkaKod, e.getMessage());
            warnings.add("warstwa_drogi_offline");
            return new BigDecimal("0.50");
        }
    }

    private RelocationData findNearestRelocation(String placowkaKod, List<String> warnings) {
        double maxPromienM = config.promienieKm.miejscaRelokacji * 1000.0;
        String sql = """
            SELECT r.kod, r.nazwa, r.przyjmuje_niesamodzielnych, r.pojemnosc_dostepna,
                ST_Distance(
                    ST_Transform(r.geom, 2180),
                    ST_Transform((SELECT geom FROM placowka WHERE kod = ?), 2180)
                ) / 1000.0 AS odleglosc_km
            FROM miejsca_relokacji r
            WHERE r.pojemnosc_dostepna > 0
            ORDER BY odleglosc_km ASC
            LIMIT 1
            """;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, placowkaKod);
            if (rows.isEmpty()) {
                log.warn("[IkeAgent] E6/E11: brak miejsc relokacji dla {}", placowkaKod);
                warnings.add("brak_miejsca_relokacji_w_promieniu_50km");
                return null;
            }
            Map<String, Object> row = rows.get(0);
            double odl = ((Number) row.get("odleglosc_km")).doubleValue();
            if (odl > config.promienieKm.miejscaRelokacji) {
                log.warn("[IkeAgent] E6: najblizsze miejsce relokacji > {}km dla {}", config.promienieKm.miejscaRelokacji, placowkaKod);
                warnings.add("brak_miejsca_relokacji_w_promieniu_50km");
                return null;
            }
            RelocationData rd = new RelocationData();
            rd.kod = (String) row.get("kod");
            rd.nazwa = (String) row.get("nazwa");
            rd.odlegnoscKm = odl;
            rd.pojemnoscDostepna = (Integer) row.get("pojemnosc_dostepna");
            Object przyjm = row.get("przyjmuje_niesamodzielnych");
            rd.przyjmujeNiesamodzielnych = przyjm != null && (Boolean) przyjm;
            return rd;
        } catch (Exception e) {
            log.warn("[IkeAgent] Blad zapytania relokacji dla {}: {}", placowkaKod, e.getMessage());
            warnings.add("brak_miejsca_relokacji_w_promieniu_50km");
            return null;
        }
    }

    private BigDecimal calcScoreOdleglosci(double odlKm, String placowkaKod, List<String> warnings) {
        if (odlKm > 50.0) {
            warnings.add("brak_miejsca_relokacji_w_promieniu_50km");
            return null; // IKE = null
        }
        if (odlKm <= 10.0) return BigDecimal.ZERO;
        if (odlKm <= 25.0) return new BigDecimal("0.25");
        if (odlKm <= 40.0) return new BigDecimal("0.50");
        return new BigDecimal("0.75"); // 40 < d <= 50
    }

    private String classifyIke(double score) {
        if (score >= config.progi.czerwony) return "czerwony";
        if (score >= config.progi.zolty)    return "zolty";
        return "zielony";
    }

    // ─── Result helpers ────────────────────────────────────────────────────────

    public static class RelocationData {
        public String kod;
        public String nazwa;
        public double odlegnoscKm;
        public int pojemnoscDostepna;
        public boolean przyjmujeNiesamodzielnych;
    }

    public static class RecalculateResult {
        public final String correlationId;
        public final int przetworzone;

        public RecalculateResult(String correlationId, int przetworzone) {
            this.correlationId = correlationId;
            this.przetworzone = przetworzone;
        }
    }

    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
