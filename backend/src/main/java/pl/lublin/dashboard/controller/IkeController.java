package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.lublin.dashboard.agent.IkeAgent;
import pl.lublin.dashboard.model.IkeResult;
import pl.lublin.dashboard.model.MiejsceRelokacji;
import pl.lublin.dashboard.model.Placowka;
import pl.lublin.dashboard.repository.IkeResultRepository;
import pl.lublin.dashboard.repository.MiejsceRelokacjiRepository;
import pl.lublin.dashboard.repository.PlacowkaRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ike")
public class IkeController {

    @Autowired private IkeAgent ikeAgent;
    @Autowired private IkeResultRepository ikeResultRepository;
    @Autowired private PlacowkaRepository placowkaRepository;
    @Autowired private MiejsceRelokacjiRepository miejscaRelokacjiRepository;

    // ─── GET /api/ike/config ─────────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        IkeAgent.IkeConfigData cfg = ikeAgent.getConfig();
        Map<String, Object> resp = new LinkedHashMap<>();

        Map<String, Object> wagi = new LinkedHashMap<>();
        wagi.put("zagrozenie",       cfg.wagi.zagrozenie);
        wagi.put("niesamodzielni",    cfg.wagi.niesamodzielni);
        wagi.put("transport_brak",    cfg.wagi.transportBrak);
        wagi.put("droznosc_brak",     cfg.wagi.droznoscBrak);
        wagi.put("odleglosc_relokacji", cfg.wagi.odlegloscRelokacji);
        resp.put("wagi", wagi);

        Map<String, Object> progi = new LinkedHashMap<>();
        progi.put("czerwony", cfg.progi.czerwony);
        progi.put("zolty",    cfg.progi.zolty);
        resp.put("progi", progi);

        Map<String, Object> promienie = new LinkedHashMap<>();
        promienie.put("transport_dostepny", cfg.promienieKm.transportDostepny);
        promienie.put("miejsca_relokacji",  cfg.promienieKm.miejscaRelokacji);
        resp.put("promienie_km", promienie);

        resp.put("ostatnia_zmiana", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return ResponseEntity.ok(resp);
    }

    // ─── POST /api/ike/recalculate ───────────────────────────────────────────

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate() {
        if (!ikeAgent.tryAcquire()) {
            return ResponseEntity.status(409).body(error(
                "Przeliczanie IKE jest juz w toku",
                "RECALCULATE_IN_PROGRESS"));
        }
        String correlationId = IkeAgent.generateCorrelationId();
        IkeAgent.RecalculateResult result = ikeAgent.recalculateAll(correlationId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "completed");
        resp.put("correlation_id", result.correlationId);
        resp.put("liczba_przeliczonych", result.przetworzone);
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/ike ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String kategoria,
            @RequestParam(required = false) String powiat) {

        List<IkeResult> results;
        if (kategoria != null && !kategoria.isBlank()) {
            results = ikeResultRepository.findByKategoriaOrderByIkeScoreDesc(kategoria);
        } else {
            results = ikeResultRepository.findAllOrderByIkeScoreDesc();
        }

        // filter by powiat (requires join with placowka)
        if (powiat != null && !powiat.isBlank()) {
            Set<String> kodaByPowiat = placowkaRepository.findByPowiat(powiat)
                    .stream().map(Placowka::getKod).collect(Collectors.toSet());
            results = results.stream()
                    .filter(r -> kodaByPowiat.contains(r.getPlacowkaKod()))
                    .collect(Collectors.toList());
        }

        if (limit != null && limit > 0 && results.size() > limit) {
            results = results.subList(0, limit);
        }

        // load placowka and relokacja maps for enrichment
        Map<String, Placowka> placowkiMap = placowkaRepository.findAll()
                .stream().collect(Collectors.toMap(Placowka::getKod, p -> p));
        Map<String, MiejsceRelokacji> relokacjaMap = miejscaRelokacjiRepository.findAll()
                .stream().collect(Collectors.toMap(MiejsceRelokacji::getKod, r -> r));

        List<Map<String, Object>> wyniki = results.stream()
                .map(r -> buildWynikDto(r, placowkiMap, relokacjaMap))
                .collect(Collectors.toList());

        Optional<OffsetDateTime> lastCalc = ikeResultRepository.findLastCalculationTime();
        List<String> corrIds = ikeResultRepository.findLastCorrelationIds();
        String corrId = corrIds.isEmpty() ? null : corrIds.get(0);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("obliczone_o", lastCalc.map(t -> t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).orElse(null));
        resp.put("correlation_id", corrId);
        resp.put("liczba_wynikow", wyniki.size());
        resp.put("wyniki", wyniki);
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/ike/{kod} ──────────────────────────────────────────────────

    @GetMapping("/{kod}")
    public ResponseEntity<Map<String, Object>> getByKod(@PathVariable String kod) {
        Optional<IkeResult> optResult = ikeResultRepository.findByPlacowkaKod(kod);
        if (optResult.isEmpty()) {
            // check if placowka exists at all
            if (placowkaRepository.findByKod(kod).isEmpty()) {
                return ResponseEntity.status(404).body(error(
                    "Placowka nie istnieje: " + kod, "PLACOWKA_NOT_FOUND"));
            }
            return ResponseEntity.status(404).body(error(
                "Brak wynikow IKE dla placowki: " + kod + ". Wywolaj POST /api/ike/recalculate.",
                "IKE_NULL_RESULT"));
        }

        Map<String, Placowka> placowkiMap = placowkaRepository.findAll()
                .stream().collect(Collectors.toMap(Placowka::getKod, p -> p));
        Map<String, MiejsceRelokacji> relokacjaMap = miejscaRelokacjiRepository.findAll()
                .stream().collect(Collectors.toMap(MiejsceRelokacji::getKod, r -> r));

        return ResponseEntity.ok(buildWynikDto(optResult.get(), placowkiMap, relokacjaMap));
    }

    // ─── DTO builder ─────────────────────────────────────────────────────────

    private Map<String, Object> buildWynikDto(
            IkeResult r,
            Map<String, Placowka> placowkiMap,
            Map<String, MiejsceRelokacji> relokacjaMap) {

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("placowka_kod", r.getPlacowkaKod());

        Placowka p = placowkiMap.get(r.getPlacowkaKod());
        if (p != null) {
            dto.put("placowka_nazwa", p.getNazwa());
            dto.put("powiat", p.getPowiat());
            if (p.getGeom() != null) {
                dto.put("lat", p.getGeom().getY());
                dto.put("lon", p.getGeom().getX());
            }
            dto.put("liczba_podopiecznych", p.getLiczbaPodopiecznych());
            if (p.getLiczbaPodopiecznych() != null && p.getNiesamodzielniProcent() != null) {
                int nies = (int) Math.round(p.getLiczbaPodopiecznych() * p.getNiesamodzielniProcent().doubleValue());
                dto.put("niesamodzielni_liczba", nies);
            }
        }

        dto.put("ike_score", r.getIkeScore());
        dto.put("ike_kategoria", r.getIkeKategoria());

        Map<String, Object> skladowe = new LinkedHashMap<>();
        skladowe.put("score_zagrozenia",           r.getScoreZagrozenia());
        skladowe.put("score_niesamodzielnych",      r.getScoreNiesamodzielnych());
        skladowe.put("score_braku_transportu",      r.getScoreBrakuTransportu());
        skladowe.put("score_braku_droznosci",       r.getScoreBrakuDroznosci());
        skladowe.put("score_odleglosci_relokacji",  r.getScoreOdleglosci());
        dto.put("skladowe", skladowe);

        if (r.getCelRelokacjiKod() != null) {
            MiejsceRelokacji rel = relokacjaMap.get(r.getCelRelokacjiKod());
            if (rel != null) {
                Map<String, Object> celRelokacji = new LinkedHashMap<>();
                celRelokacji.put("kod", rel.getKod());
                celRelokacji.put("nazwa", rel.getNazwa());
                celRelokacji.put("pojemnosc_dostepna", rel.getPojemnoscDostepna());
                celRelokacji.put("przyjmuje_niesamodzielnych", rel.getPrzyjmujeNiesamodzielnych());
                dto.put("cel_relokacji", celRelokacji);
            }
        } else {
            dto.put("cel_relokacji", null);
        }

        dto.put("trasa_ewakuacji_geojson", null); // OSRM w przyszlych iteracjach
        dto.put("czas_przejazdu_min", r.getCzasPrzejazduMin());
        dto.put("correlation_id", r.getCorrelationId());
        dto.put("data_warnings", r.getDataWarnings() != null ? r.getDataWarnings() : List.of());
        dto.put("obliczone_o", r.getObliczoneO() != null
                ? r.getObliczoneO().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);

        return dto;
    }

    // ─── Error helper ─────────────────────────────────────────────────────────

    private Map<String, Object> error(String message, String code) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("error", message);
        resp.put("code", code);
        resp.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return resp;
    }
}
