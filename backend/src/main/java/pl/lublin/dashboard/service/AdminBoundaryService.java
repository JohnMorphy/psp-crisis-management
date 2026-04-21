package pl.lublin.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.lublin.dashboard.model.GranicaAdministracyjna;
import pl.lublin.dashboard.repository.GranicaAdministracyjnaRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminBoundaryService {

    private static final Logger log = LoggerFactory.getLogger(AdminBoundaryService.class);

    @Autowired private GranicaAdministracyjnaRepository repository;
    @Autowired private ObjectMapper objectMapper;

    private final GeoJsonWriter geoJsonWriter;

    public AdminBoundaryService() {
        geoJsonWriter = new GeoJsonWriter();
        geoJsonWriter.setEncodeCRS(false);
    }

    public Map<String, Object> getWojewodztwa() {
        List<GranicaAdministracyjna> granice = repository.findByPoziom("wojewodztwo");
        return toFeatureCollection("L-08", granice);
    }

    public Map<String, Object> getPowiaty(String kodWoj, String bbox) {
        List<GranicaAdministracyjna> granice;
        if (kodWoj != null && !kodWoj.isBlank()) {
            granice = repository.findByPoziomAndKodWoj("powiat", kodWoj.trim());
        } else if (bbox != null && !bbox.isBlank()) {
            double[] coords = parseBbox(bbox);
            if (coords == null) return toFeatureCollection("L-09", Collections.emptyList());
            granice = repository.findByPoziomAndBbox("powiat", coords[0], coords[1], coords[2], coords[3]);
        } else {
            granice = repository.findByPoziom("powiat");
        }
        return toFeatureCollection("L-09", granice);
    }

    public Map<String, Object> getGminy(String kodWoj, String bbox) {
        List<GranicaAdministracyjna> granice;
        if (kodWoj != null && !kodWoj.isBlank()) {
            granice = repository.findByPoziomAndKodWoj("gmina", kodWoj.trim());
        } else {
            double[] coords = parseBbox(bbox);
            // forced filtering on gmina; doesn't allow full import; reason: too much data
            if (coords == null) return toFeatureCollection("L-10", Collections.emptyList());
            granice = repository.findByPoziomAndBbox("gmina", coords[0], coords[1], coords[2], coords[3]);
        }
        return toFeatureCollection("L-10", granice);
    }

    private Map<String, Object> toFeatureCollection(String layerId, List<GranicaAdministracyjna> granice) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        List<Map<String, Object>> features = granice.stream()
                .map(this::toFeature)
                .filter(f -> f != null)
                .toList();

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("layer_id", layerId);
        fc.put("ostatnia_aktualizacja", timestamp);
        fc.put("feature_count", features.size());
        fc.put("features", features);
        return fc;
    }

    private Map<String, Object> toFeature(GranicaAdministracyjna g) {
        if (g.getGeom() == null) return null;
        try {
            String geomJson = geoJsonWriter.write(g.getGeom());
            Map<String, Object> geometry = objectMapper.readValue(geomJson, Map.class);

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("kod_teryt", g.getKodTeryt());
            props.put("nazwa", g.getNazwa());
            props.put("poziom", g.getPoziom());
            props.put("kod_nadrzedny", g.getKodNadrzedny());

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", geometry);
            feature.put("properties", props);
            return feature;
        } catch (Exception e) {
            log.warn("Cannot serialize granica {}: {}", g.getKodTeryt(), e.getMessage());
            return null;
        }
    }

    private double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) return null;
        try {
            String[] parts = bbox.split(",");
            if (parts.length != 4) return null;
            return new double[]{
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()),
                Double.parseDouble(parts[3].trim())
            };
        } catch (NumberFormatException e) {
            log.warn("Invalid bbox format: {}", bbox);
            return null;
        }
    }
}
