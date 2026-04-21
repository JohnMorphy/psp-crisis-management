package pl.lublin.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lublin.dashboard.agent.AdminBoundaryImportAgent;
import pl.lublin.dashboard.model.EntityImportBatch;
import pl.lublin.dashboard.model.EntityRegistryEntry;
import pl.lublin.dashboard.repository.EntityImportBatchRepository;
import pl.lublin.dashboard.repository.EntityRegistryEntryRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntityImportService {

    private static final Logger log = LoggerFactory.getLogger(EntityImportService.class);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private EntityImportBatchRepository batchRepository;
    @Autowired private EntityRegistryEntryRepository entityRepository;
    @Autowired private AdminBoundaryImportAgent adminBoundaryImportAgent;
    @Autowired private ObjectMapper objectMapper;

    @Transactional
    public EntityImportBatch runImport(String sourceCode) {
        return switch (sourceCode) {
            case "rjps" -> importSeedRecords("rjps", sampleRjpsRecords());
            case "rprm" -> importSeedRecords("rprm", sampleRprmRecords());
            case "rjwprm" -> importSeedRecords("rjwprm", sampleRjwprmRecords());
            case "bdl" -> importSeedRecords("bdl", sampleBdlRecords());
            case "wody_polskie" -> importSeedRecords("wody_polskie", sampleWodyRecords());
            case "prg", "prg-boundary-import" -> runPrgImport();
            default -> throw new IllegalArgumentException("Unsupported import source: " + sourceCode);
        };
    }

    public List<EntityImportBatch> getRecentRuns(String sourceCode) {
        if (sourceCode != null && !sourceCode.isBlank()) {
            return batchRepository.findTop20BySourceCodeOrderByStartedAtDesc(sourceCode.trim());
        }
        return batchRepository.findTop20ByOrderByStartedAtDesc();
    }

    private EntityImportBatch runPrgImport() {
        EntityImportBatch batch = startBatch("prg");
        try {
            if (!adminBoundaryImportAgent.tryAcquire()) {
                batch.setStatus("skipped");
                batch.setRecordsSkipped(1);
                batch.setErrorLog("Administrative boundary import is already running");
                batch.setFinishedAt(OffsetDateTime.now());
                return batchRepository.save(batch);
            }

            adminBoundaryImportAgent.importAll("entity-import-prg");
            batch.setStatus("completed");
            batch.setRecordsTotal(3);
            batch.setRecordsUpdated(3);
            batch.setFinishedAt(OffsetDateTime.now());
            return batchRepository.save(batch);
        } catch (Exception ex) {
            log.warn("PRG import failed: {}", ex.getMessage(), ex);
            batch.setStatus("failed");
            batch.setErrorLog(ex.getMessage());
            batch.setFinishedAt(OffsetDateTime.now());
            return batchRepository.save(batch);
        }
    }

    private EntityImportBatch importSeedRecords(String sourceCode, List<SeedEntityRecord> records) {
        EntityImportBatch batch = startBatch(sourceCode);
        int created = 0;
        int updated = 0;

        try {
            for (SeedEntityRecord record : records) {
                boolean wasCreated = upsertRecord(sourceCode, record, batch.getId());
                if (wasCreated) {
                    created++;
                } else {
                    updated++;
                }
            }
            batch.setStatus("completed");
            batch.setRecordsTotal(records.size());
            batch.setRecordsNew(created);
            batch.setRecordsUpdated(updated);
            batch.setFinishedAt(OffsetDateTime.now());
            return batchRepository.save(batch);
        } catch (Exception ex) {
            log.warn("Import {} failed: {}", sourceCode, ex.getMessage(), ex);
            batch.setStatus("failed");
            batch.setRecordsTotal(records.size());
            batch.setRecordsNew(created);
            batch.setRecordsUpdated(updated);
            batch.setErrorLog(ex.getMessage());
            batch.setFinishedAt(OffsetDateTime.now());
            return batchRepository.save(batch);
        }
    }

    private EntityImportBatch startBatch(String sourceCode) {
        EntityImportBatch batch = new EntityImportBatch();
        batch.setSourceCode(sourceCode);
        batch.setStartedAt(OffsetDateTime.now());
        batch.setStatus("running");
        batch.setRecordsTotal(0);
        batch.setRecordsNew(0);
        batch.setRecordsUpdated(0);
        batch.setRecordsSkipped(0);
        return batchRepository.save(batch);
    }

    private boolean upsertRecord(String sourceCode, SeedEntityRecord record, Long batchId) throws Exception {
        EntityRegistryEntry entry = entityRepository
                .findBySourceCodeAndSourceRecordId(sourceCode, record.sourceRecordId())
                .orElseGet(EntityRegistryEntry::new);
        boolean created = entry.getId() == null;

        entry.setSourceCode(sourceCode);
        entry.setSourceRecordId(record.sourceRecordId());
        entry.setCategoryCode(record.categoryCode());
        entry.setName(record.name());
        entry.setSubtitle(record.subtitle());
        entry.setStatus(record.status());
        entry.setOwnerName(record.ownerName());
        entry.setAddressRaw(record.addressRaw());
        entry.setTerytWoj(record.terytWoj());
        entry.setTerytPow(record.terytPow());
        entry.setTerytGmina(record.terytGmina());
        entry.setLat(record.lat());
        entry.setLon(record.lon());
        entry.setGeom(record.point());
        entry.setCoverageGeom(null);
        entry.setContactPhone(record.contactPhone());
        entry.setContactEmail(record.contactEmail());
        entry.setWww(record.www());
        entry.setAttributesJson(objectMapper.writeValueAsString(record.attributes()));
        entry.setSourceUrl(record.sourceUrl());
        entry.setLastSeenAt(OffsetDateTime.now());
        entry.setLastImportBatchId(batchId);
        entry.setSourcePriority(record.sourcePriority());
        entry.setMatchConfidence(record.matchConfidence());
        entityRepository.save(entry);
        return created;
    }

    private List<SeedEntityRecord> sampleRjpsRecords() {
        return List.of(
                seed("RJPS-DPS-001", "social_care_dps", "Dom Pomocy Spolecznej Kombatant", "DPS",
                        "official_registry", "Miasto Warszawa", "ul. Sternicza 125, Warszawa", "14", "1465", "1465011",
                        52.2242, 20.9218, "22-123-45-67", null, "https://rjps.mrips.gov.pl/",
                        Map.of("pojemnosc_ogolna", 210, "liczba_podopiecznych", 198, "niesamodzielni_procent", 0.72)),
                seed("RJPS-DPS-002", "social_care_dps", "Dom Pomocy Spolecznej im. Jana Pawla II", "DPS",
                        "official_registry", "Powiat poznanski", "ul. Ugory 18/20, Poznan", "30", "3064", "3064011",
                        52.4146, 16.8953, "61-111-22-33", null, "https://rjps.mrips.gov.pl/",
                        Map.of("pojemnosc_ogolna", 126, "liczba_podopiecznych", 118, "niesamodzielni_procent", 0.66))
        );
    }

    private List<SeedEntityRecord> sampleRprmRecords() {
        return List.of(
                seed("RPRM-ZRM-001", "prm_unit", "Stacja ZRM Warszawa Srodmiescie", "ZRM P",
                        "official_registry", "Wojewodzka Stacja Pogotowia Ratunkowego", "ul. Poznanska 22, Warszawa",
                        "14", "1465", "1465011", 52.2278, 21.0122, "22-599-20-00", null, "https://rprm.ezdrowie.gov.pl/",
                        Map.of("unit_type", "ZRM", "dispatch_region", "Warszawa", "readiness", "24/7")),
                seed("RPRM-SOR-001", "prm_unit", "SOR Uniwersytecki Szpital Kliniczny we Wroclawiu", "SOR",
                        "official_registry", "Uniwersytecki Szpital Kliniczny", "ul. Borowska 213, Wroclaw",
                        "02", "0264", "0264011", 51.0898, 17.0366, "71-733-11-10", null, "https://rprm.ezdrowie.gov.pl/",
                        Map.of("unit_type", "SOR", "readiness", "24/7"))
        );
    }

    private List<SeedEntityRecord> sampleRjwprmRecords() {
        return List.of(
                seed("RJWPRM-GOPR-001", "prm_cooperating_unit", "GOPR Grupa Podhalanska", "Jednostka wspolpracujaca",
                        "official_registry", "GOPR", "ul. Pilsudskiego 63A, Zakopane", "12", "1217", "1217011",
                        49.2982, 19.9496, "18-201-20-22", "centrala@gopr.pl", "https://rjwprm.ezdrowie.gov.pl/",
                        Map.of("rescue_type", "gorskie", "coverage", "Tatry")),
                seed("RJWPRM-WOPR-001", "prm_cooperating_unit", "Mazurskie WOPR", "Jednostka wspolpracujaca",
                        "official_registry", "WOPR", "ul. Nadbrzezna 5, Gizycko", "28", "2818", "2818011",
                        54.0380, 21.7613, "87-428-13-14", "sekretariat@wopr.pl", "https://rjwprm.ezdrowie.gov.pl/",
                        Map.of("rescue_type", "wodne", "coverage", "Mazury"))
        );
    }

    private List<SeedEntityRecord> sampleBdlRecords() {
        return List.of(
                seed("BDL-NAD-001", "state_forest_unit", "Nadlesnictwo Bialowieza", "Nadlesnictwo",
                        "official_registry", "Lasy Panstwowe", "ul. Wojciechowka 4, Bialowieza", "20", "2005", "2005021",
                        52.7001, 23.8537, "85-681-24-05", null, "https://bdl.lasy.gov.pl/",
                        Map.of("forest_level", "nadlesnictwo", "rdlp", "Bialystok"))
        );
    }

    private List<SeedEntityRecord> sampleWodyRecords() {
        return List.of(
                seed("WP-RGW-001", "water_management_unit", "Regionalny Zarzad Gospodarki Wodnej w Krakowie", "RZGW",
                        "official_registry", "Wody Polskie", "ul. Marszalka J. Pilsudskiego 22, Krakow", "12", "1261", "1261011",
                        50.0619, 19.9495, "12-628-41-11", null, "https://www.gov.pl/web/wody-polskie",
                        Map.of("unit_type", "RZGW", "hydro_region", "Gorna Wisla"))
        );
    }

    private SeedEntityRecord seed(
            String sourceRecordId,
            String categoryCode,
            String name,
            String subtitle,
            String status,
            String ownerName,
            String addressRaw,
            String terytWoj,
            String terytPow,
            String terytGmina,
            double lat,
            double lon,
            String contactPhone,
            String contactEmail,
            String sourceUrl,
            Map<String, Object> attributes
    ) {
        return new SeedEntityRecord(
                sourceRecordId,
                categoryCode,
                name,
                subtitle,
                status,
                ownerName,
                addressRaw,
                terytWoj,
                terytPow,
                terytGmina,
                lat,
                lon,
                contactPhone,
                contactEmail,
                null,
                sourceUrl,
                attributes,
                100,
                new BigDecimal("0.980")
        );
    }

    private record SeedEntityRecord(
            String sourceRecordId,
            String categoryCode,
            String name,
            String subtitle,
            String status,
            String ownerName,
            String addressRaw,
            String terytWoj,
            String terytPow,
            String terytGmina,
            Double lat,
            Double lon,
            String contactPhone,
            String contactEmail,
            String www,
            String sourceUrl,
            Map<String, Object> attributes,
            Integer sourcePriority,
            BigDecimal matchConfidence
    ) {
        Point point() {
            return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        }
    }
}
