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

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

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
        log.info("[GeoService] Loaded admin boundaries, features: {}",
            ((List<?>) powiaty.getOrDefault("features", Collections.emptyList())).size());
    }

    private Map<String, Object> loadGeoJson(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, Map.class);
        } catch (Exception e) {
            log.warn("[GeoService] Cannot load {} — {}", path, e.getMessage());
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
        log.info("[GeoService] buildLayerGeoJson — layerId={}", layerId);
        return switch (layerId) {
            case "L-01", "L-02" -> entityRegistryService.buildLayerGeoJson(
                    layerId, category, source, kodWoj, kodPow, kodGmina, bbox, q);
            default -> buildEmptyLayer(layerId);
        };
    }

    private Map<String, Object> buildEmptyLayer(String layerId) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("layer_id", layerId);
        fc.put("ostatnia_aktualizacja", timestamp);
        fc.put("feature_count", 0);
        fc.put("features", Collections.emptyList());
        return fc;
    }

    public Map<String, Object> geometryToMap(Geometry geometry) {
        if (geometry == null) return null;
        try {
            String geoJson = geoJsonWriter.write(geometry);
            return objectMapper.readValue(geoJson, Map.class);
        } catch (Exception e) {
            log.warn("[GeoService] Cannot serialize geometry — {}", e.getMessage());
            return null;
        }
    }
}
