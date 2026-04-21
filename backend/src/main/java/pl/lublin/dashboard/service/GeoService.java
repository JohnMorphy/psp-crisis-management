package pl.lublin.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pl.lublin.dashboard.model.IkeResult;
import pl.lublin.dashboard.model.MiejsceRelokacji;
import pl.lublin.dashboard.model.Placowka;
import pl.lublin.dashboard.model.StrefaZagrozen;
import pl.lublin.dashboard.model.ZasobTransportu;
import pl.lublin.dashboard.repository.IkeResultRepository;
import pl.lublin.dashboard.repository.MiejsceRelokacjiRepository;
import pl.lublin.dashboard.repository.PlacowkaRepository;
import pl.lublin.dashboard.repository.StrefaZagrozenRepository;
import pl.lublin.dashboard.repository.ZasobTransportuRepository;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

    @Autowired private PlacowkaRepository placowkaRepository;
    @Autowired private StrefaZagrozenRepository strefaZagrozenRepository;
    @Autowired private MiejsceRelokacjiRepository miejscaRelokacjiRepository;
    @Autowired private ZasobTransportuRepository zasobTransportuRepository;
    @Autowired private IkeResultRepository ikeResultRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityRegistryService entityRegistryService;

    private final GeoJsonWriter geoJsonWriter;
    private Map<String, Object> powiaty;

    public GeoService() {
        geoJsonWriter = new GeoJsonWriter();
        geoJsonWriter.setEncodeCRS(false);
    }

    @PostConstruct
    public void loadBoundaries() {
        powiaty = loadGeoJson("geojson/lublin_powiaty.geojson");
        log.info("Loaded admin boundaries, features: {}",
            ((List<?>) powiaty.getOrDefault("features", Collections.emptyList())).size());
    }

    private Map<String, Object> loadGeoJson(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, Map.class);
        } catch (Exception e) {
            log.warn("Cannot load {}: {} — using empty fallback", path, e.getMessage());
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("type", "FeatureCollection");
            fc.put("features", Collections.emptyList());
            return fc;
        }
    }

    public Map<String, Object> getAdminBoundaries() {
        return powiaty;
    }

    public Map<String, Object> buildLayerGeoJson(
            String layerId,
            String powiat,
            String gmina,
            String bbox,
            String kodWoj,
            String kodPow,
            String kodGmina,
            String category,
            String source,
            String q
    ) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return switch (layerId) {
            case "L-01", "L-02" -> entityRegistryService.buildLayerGeoJson(layerId, category, source, kodWoj, kodPow, kodGmina, bbox, q);
            case "L-03"         -> buildStrefyLayer(timestamp);
            case "L-05"         -> buildTransportLayer(timestamp);
            case "L-06"         -> buildRelokacjaLayer(timestamp);
            default             -> buildEmptyLayer(layerId, timestamp);
        };
    }

    private Map<String, Object> buildPlacowkiLayer(String layerId, String timestamp, String powiat) {
        List<Placowka> placowki = (powiat != null && !powiat.isBlank())
            ? placowkaRepository.findByPowiat(powiat)
            : placowkaRepository.findAll();

        Map<String, IkeResult> ikeMap = ikeResultRepository.findAll().stream()
            .collect(Collectors.toMap(IkeResult::getPlacowkaKod, r -> r));

        List<Map<String, Object>> features = placowki.stream()
            .map(p -> placowkaToFeature(p, ikeMap.get(p.getKod())))
            .filter(Objects::nonNull)
            .toList();

        return featureCollection(layerId, timestamp, features);
    }

    private Map<String, Object> buildStrefyLayer(String timestamp) {
        List<StrefaZagrozen> strefy = strefaZagrozenRepository.findAll();

        List<Map<String, Object>> features = strefy.stream()
            .map(this::strefaToFeature)
            .filter(Objects::nonNull)
            .toList();

        return featureCollection("L-03", timestamp, features);
    }

    private Map<String, Object> buildTransportLayer(String timestamp) {
        List<ZasobTransportu> transport = zasobTransportuRepository.findAll();

        List<Map<String, Object>> features = transport.stream()
            .map(this::transportToFeature)
            .filter(Objects::nonNull)
            .toList();

        return featureCollection("L-05", timestamp, features);
    }

    private Map<String, Object> buildRelokacjaLayer(String timestamp) {
        List<MiejsceRelokacji> miejsca = miejscaRelokacjiRepository.findAll();

        List<Map<String, Object>> features = miejsca.stream()
            .map(this::relokacjaToFeature)
            .filter(Objects::nonNull)
            .toList();

        return featureCollection("L-06", timestamp, features);
    }

    private Map<String, Object> buildEmptyLayer(String layerId, String timestamp) {
        return featureCollection(layerId, timestamp, Collections.emptyList());
    }

    private Map<String, Object> featureCollection(String layerId, String timestamp, List<Map<String, Object>> features) {
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("layer_id", layerId);
        fc.put("ostatnia_aktualizacja", timestamp);
        fc.put("feature_count", features.size());
        fc.put("features", features);
        return fc;
    }

    private Map<String, Object> placowkaToFeature(Placowka p, IkeResult ike) {
        Map<String, Object> geometry = geometryToMap(p.getGeom());
        if (geometry == null) return null;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kod", p.getKod());
        props.put("nazwa", p.getNazwa());
        props.put("typ", p.getTyp());
        props.put("powiat", p.getPowiat());
        props.put("gmina", p.getGmina());
        props.put("liczba_podopiecznych", p.getLiczbaPodopiecznych());
        props.put("pojemnosc_ogolna", p.getPojemnoscOgolna());
        props.put("niesamodzielni_procent", p.getNiesamodzielniProcent());
        props.put("generator_backup", p.getGeneratorBackup());
        props.put("kontakt", p.getKontakt());
        props.put("ike_score", ike != null ? ike.getIkeScore() : null);
        props.put("ike_kategoria", ike != null ? ike.getIkeKategoria() : null);

        return buildFeature(geometry, props);
    }

    private Map<String, Object> strefaToFeature(StrefaZagrozen s) {
        Map<String, Object> geometry = geometryToMap(s.getGeom());
        if (geometry == null) return null;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", s.getKod());
        props.put("typ_zagrozenia", s.getTypZagrozenia());
        props.put("poziom", s.getPoziom());
        props.put("scenariusz", s.getScenariusz());
        props.put("nazwa", s.getNazwa());
        props.put("szybkosc_wznoszenia_m_h", s.getSzybkoscWznoszeniaM_h());
        props.put("czas_do_zagrozenia_h", s.getCzasDoZagrozenia_h());
        props.put("zrodlo", s.getZrodlo());

        return buildFeature(geometry, props);
    }

    private Map<String, Object> transportToFeature(ZasobTransportu t) {
        if (t.getGeom() == null) return null;
        Map<String, Object> geometry = geometryToMap(t.getGeom());
        if (geometry == null) return null;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kod", t.getKod());
        props.put("typ", t.getTyp());
        props.put("oznaczenie", t.getOznaczenie());
        props.put("pojemnosc_osob", t.getPojemnoscOsob());
        props.put("przyjmuje_niesamodzielnych", t.getPrzyjmujeNiesamodzielnych());
        props.put("dostepny", t.getDostepny());
        props.put("powiat", t.getPowiat());

        return buildFeature(geometry, props);
    }

    private Map<String, Object> relokacjaToFeature(MiejsceRelokacji r) {
        Map<String, Object> geometry = geometryToMap(r.getGeom());
        if (geometry == null) return null;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kod", r.getKod());
        props.put("nazwa", r.getNazwa());
        props.put("typ", r.getTyp());
        props.put("powiat", r.getPowiat());
        props.put("pojemnosc_ogolna", r.getPojemnoscOgolna());
        props.put("pojemnosc_dostepna", r.getPojemnoscDostepna());
        props.put("przyjmuje_niesamodzielnych", r.getPrzyjmujeNiesamodzielnych());

        return buildFeature(geometry, props);
    }

    private Map<String, Object> buildFeature(Map<String, Object> geometry, Map<String, Object> properties) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        return feature;
    }

    private Map<String, Object> geometryToMap(Geometry geometry) {
        if (geometry == null) return null;
        try {
            String geoJson = geoJsonWriter.write(geometry);
            return objectMapper.readValue(geoJson, Map.class);
        } catch (Exception e) {
            log.warn("Cannot serialize geometry: {}", e.getMessage());
            return null;
        }
    }
}
