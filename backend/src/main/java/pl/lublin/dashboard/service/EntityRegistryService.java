package pl.lublin.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.lublin.dashboard.model.EntityCategory;
import pl.lublin.dashboard.model.EntityRegistryEntry;
import pl.lublin.dashboard.model.EntitySource;
import pl.lublin.dashboard.model.GranicaAdministracyjna;
import pl.lublin.dashboard.model.IkeResult;
import pl.lublin.dashboard.repository.EntityCategoryRepository;
import pl.lublin.dashboard.repository.EntityRegistryEntryRepository;
import pl.lublin.dashboard.repository.EntitySourceRepository;
import pl.lublin.dashboard.repository.GranicaAdministracyjnaRepository;
import pl.lublin.dashboard.repository.IkeResultRepository;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class EntityRegistryService {

    private static final Logger log = LoggerFactory.getLogger(EntityRegistryService.class);

    @Autowired private EntityRegistryEntryRepository entityRepository;
    @Autowired private EntityCategoryRepository categoryRepository;
    @Autowired private EntitySourceRepository sourceRepository;
    @Autowired private IkeResultRepository ikeResultRepository;
    @Autowired private GranicaAdministracyjnaRepository granicaRepository;
    @Autowired private ObjectMapper objectMapper;

    private final GeoJsonWriter geoJsonWriter;

    public EntityRegistryService() {
        geoJsonWriter = new GeoJsonWriter();
        geoJsonWriter.setEncodeCRS(false);
    }

    public List<Map<String, Object>> getCategories() {
        Map<String, Long> counts = entityRepository.findAll().stream()
                .collect(Collectors.groupingBy(EntityRegistryEntry::getCategoryCode, Collectors.counting()));

        return categoryRepository.findAll().stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .map(category -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("code", category.getCode());
                    response.put("name", category.getName());
                    response.put("act_ref", category.getActRef());
                    response.put("icon", category.getIcon());
                    response.put("default_layer_group", category.getDefaultLayerGroup());
                    response.put("geometry_mode", category.getGeometryMode());
                    response.put("entity_count", counts.getOrDefault(category.getCode(), 0L));
                    return response;
                })
                .toList();
    }

    public Map<String, Object> getEntities(
            String category,
            String source,
            String kodWoj,
            String kodPow,
            String kodGmina,
            String bbox,
            String q
    ) {
        List<EntityRegistryEntry> filtered = filterEntries(category, source, kodWoj, kodPow, kodGmina, bbox, q);
        List<Map<String, Object>> items = filtered.stream()
                .map(this::toResponseRow)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", items.size());
        response.put("items", items);
        return response;
    }

    public Map<String, Object> getEntity(Long id) {
        return entityRepository.findById(id)
                .map(this::toResponseRow)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
    }

    public Map<String, Object> getSummary(String kodTeryt) {
        List<EntityRegistryEntry> all = entityRepository.findAll();
        List<EntityRegistryEntry> filtered = all.stream()
                .filter(entry -> matchesKodTeryt(entry, kodTeryt))
                .toList();

        Map<String, Long> categoryCounts = filtered.stream()
                .collect(Collectors.groupingBy(EntityRegistryEntry::getCategoryCode, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> sourceCounts = filtered.stream()
                .collect(Collectors.groupingBy(EntityRegistryEntry::getSourceCode, LinkedHashMap::new, Collectors.counting()));

        long verified = filtered.stream()
                .filter(entry -> entry.getStatus() != null && entry.getStatus().contains("official"))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kod_teryt", kodTeryt);
        response.put("total_entities", filtered.size());
        response.put("verified_entities", verified);
        response.put("needs_review_entities", filtered.size() - verified);
        response.put("categories", categoryCounts.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", entry.getKey());
            row.put("count", entry.getValue());
            row.put("name", categoryRepository.findById(entry.getKey()).map(EntityCategory::getName).orElse(entry.getKey()));
            return row;
        }).toList());
        response.put("sources", sourceCounts.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", entry.getKey());
            row.put("count", entry.getValue());
            row.put("name", sourceRepository.findById(entry.getKey()).map(EntitySource::getName).orElse(entry.getKey()));
            return row;
        }).toList());
        return response;
    }

    public Map<String, Object> buildLayerGeoJson(
            String layerId,
            String category,
            String source,
            String kodWoj,
            String kodPow,
            String kodGmina,
            String bbox,
            String q
    ) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        List<EntityRegistryEntry> entries = filterEntries(category, source, kodWoj, kodPow, kodGmina, bbox, q).stream()
                .filter(entry -> entry.getGeom() != null)
                .toList();

        Map<String, IkeResult> ikeMap = ikeResultRepository.findAll().stream()
                .collect(Collectors.toMap(IkeResult::getPlacowkaKod, result -> result));

        List<Map<String, Object>> features = entries.stream()
                .map(entry -> toFeature(entry, ikeMap.get(entry.getSourceRecordId())))
                .filter(feature -> feature != null)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "FeatureCollection");
        response.put("layer_id", layerId);
        response.put("ostatnia_aktualizacja", timestamp);
        response.put("feature_count", features.size());
        response.put("features", features);
        return response;
    }

    private List<EntityRegistryEntry> filterEntries(
            String category,
            String source,
            String kodWoj,
            String kodPow,
            String kodGmina,
            String bbox,
            String q
    ) {
        Set<String> categorySet = splitFilter(category);
        Set<String> sourceSet = splitFilter(source);
        Envelope envelope = parseBbox(bbox);

        Predicate<EntityRegistryEntry> filter = entry -> matchesInSet(entry.getCategoryCode(), categorySet)
                && matchesInSet(entry.getSourceCode(), sourceSet)
                && matchesKod(entry.getTerytWoj(), kodWoj)
                && matchesKod(entry.getTerytPow(), kodPow)
                && matchesKod(entry.getTerytGmina(), kodGmina)
                && matchesQuery(entry, q)
                && matchesEnvelope(entry, envelope);

        return entityRepository.findAll().stream()
                .filter(filter)
                .sorted((left, right) -> {
                    OffsetDateTime leftSeen = Optional.ofNullable(left.getLastSeenAt()).orElse(OffsetDateTime.MIN);
                    OffsetDateTime rightSeen = Optional.ofNullable(right.getLastSeenAt()).orElse(OffsetDateTime.MIN);
                    return rightSeen.compareTo(leftSeen);
                })
                .toList();
    }

    private boolean matchesInSet(String value, Set<String> allowed) {
        return allowed.isEmpty() || allowed.contains(value);
    }

    private boolean matchesKod(String value, String required) {
        if (required == null || required.isBlank()) {
            return true;
        }
        return value != null && value.startsWith(required.trim());
    }

    private boolean matchesQuery(EntityRegistryEntry entry, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String normalized = q.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(entry.getName(), normalized)
                || containsIgnoreCase(entry.getSubtitle(), normalized)
                || containsIgnoreCase(entry.getOwnerName(), normalized)
                || containsIgnoreCase(entry.getAddressRaw(), normalized)
                || containsIgnoreCase(entry.getSourceRecordId(), normalized);
    }

    private boolean matchesEnvelope(EntityRegistryEntry entry, Envelope envelope) {
        if (envelope == null) {
            return true;
        }

        Point point = entry.getGeom();
        if (point != null) {
            return envelope.contains(point.getCoordinate());
        }

        Geometry geometry = entry.getCoverageGeom();
        return geometry != null && geometry.getEnvelopeInternal().intersects(envelope);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Map<String, Object> toFeature(EntityRegistryEntry entry, IkeResult ikeResult) {
        Map<String, Object> geometry = geometryToMap(entry.getGeom());
        if (geometry == null) {
            return null;
        }

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", buildProperties(entry, ikeResult));
        return feature;
    }

    private Map<String, Object> toResponseRow(EntityRegistryEntry entry) {
        return buildProperties(entry, ikeResultRepository.findByPlacowkaKod(entry.getSourceRecordId()).orElse(null));
    }

    private Map<String, Object> buildProperties(EntityRegistryEntry entry, IkeResult ikeResult) {
        Map<String, Object> attributes = parseAttributes(entry.getAttributesJson());
        EntityCategory category = categoryRepository.findById(entry.getCategoryCode()).orElse(null);
        EntitySource source = sourceRepository.findById(entry.getSourceCode()).orElse(null);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", entry.getId());
        properties.put("source_record_id", entry.getSourceRecordId());
        properties.put("name", entry.getName());
        properties.put("subtitle", entry.getSubtitle());
        properties.put("category_code", entry.getCategoryCode());
        properties.put("category_name", category != null ? category.getName() : entry.getCategoryCode());
        properties.put("category_icon", category != null ? category.getIcon() : null);
        properties.put("source_code", entry.getSourceCode());
        properties.put("source_name", source != null ? source.getName() : entry.getSourceCode());
        properties.put("status", entry.getStatus());
        properties.put("owner_name", entry.getOwnerName());
        properties.put("address_raw", entry.getAddressRaw());
        properties.put("teryt_woj", entry.getTerytWoj());
        properties.put("teryt_pow", entry.getTerytPow());
        properties.put("teryt_gmina", entry.getTerytGmina());
        properties.put("lat", entry.getLat());
        properties.put("lon", entry.getLon());
        properties.put("contact_phone", entry.getContactPhone());
        properties.put("contact_email", entry.getContactEmail());
        properties.put("www", entry.getWww());
        properties.put("source_url", entry.getSourceUrl());
        properties.put("last_seen_at", entry.getLastSeenAt() != null
                ? entry.getLastSeenAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null);
        properties.put("last_import_batch_id", entry.getLastImportBatchId());
        properties.put("source_priority", entry.getSourcePriority());
        properties.put("match_confidence", entry.getMatchConfidence());
        properties.put("attributes", attributes);
        properties.put("ike_score", ikeResult != null ? ikeResult.getIkeScore() : null);
        properties.put("ike_kategoria", ikeResult != null ? ikeResult.getIkeKategoria() : null);
        return properties;
    }

    private Map<String, Object> geometryToMap(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        try {
            return objectMapper.readValue(geoJsonWriter.write(geometry), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Cannot serialize geometry: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseAttributes(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Cannot parse attributes_json: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Envelope parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        try {
            String[] parts = bbox.split(",");
            if (parts.length != 4) {
                return null;
            }
            return new Envelope(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[3].trim())
            );
        } catch (NumberFormatException ex) {
            log.warn("Invalid bbox '{}': {}", bbox, ex.getMessage());
            return null;
        }
    }

    private Set<String> splitFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return Collections.emptySet();
        }
        return filter.lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matchesKodTeryt(EntityRegistryEntry entry, String kodTeryt) {
        if (kodTeryt == null || kodTeryt.isBlank()) {
            return true;
        }

        String normalizedKod = kodTeryt.trim();
        if (normalizedKod.length() == 2 && entry.getTerytWoj() != null) {
            return entry.getTerytWoj().startsWith(normalizedKod);
        }
        if (normalizedKod.length() == 4 && entry.getTerytPow() != null) {
            return entry.getTerytPow().startsWith(normalizedKod);
        }
        if (normalizedKod.length() == 7 && entry.getTerytGmina() != null) {
            return entry.getTerytGmina().startsWith(normalizedKod);
        }

        Optional<GranicaAdministracyjna> boundary = granicaRepository.findByKodTeryt(normalizedKod);
        if (boundary.isEmpty()) {
            return false;
        }

        Map<String, Object> attributes = parseAttributes(entry.getAttributesJson());
        String fallbackValue = switch (normalizedKod.length()) {
            case 2 -> slugify(String.valueOf(attributes.getOrDefault("legacy_woj", "")));
            case 4 -> slugify(String.valueOf(attributes.getOrDefault("legacy_powiat", "")));
            case 7 -> slugify(String.valueOf(attributes.getOrDefault("legacy_gmina", "")));
            default -> "";
        };
        return !fallbackValue.isBlank() && fallbackValue.equals(slugify(boundary.get().getNazwa()));
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("powiat ", "")
                .replace("wojewodztwo ", "")
                .replace("miasto ", "")
                .replace("m. ", "")
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return normalized;
    }
}
