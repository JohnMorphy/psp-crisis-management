package pl.lublin.dashboard.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityBootstrapService {

    @Autowired private JdbcTemplate jdbcTemplate;

    @PostConstruct
    @Transactional
    public void initialize() {
        createTables();
        seedCatalogs();
        upsertLayerConfig();
        migrateLegacyPlacowki();
    }

    private void createTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS entity_category (
                code VARCHAR(80) PRIMARY KEY,
                name VARCHAR(150) NOT NULL,
                act_ref TEXT,
                icon VARCHAR(50),
                default_layer_group VARCHAR(50),
                geometry_mode VARCHAR(20),
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS entity_source (
                code VARCHAR(50) PRIMARY KEY,
                name VARCHAR(150) NOT NULL,
                protocol VARCHAR(30),
                official BOOLEAN DEFAULT FALSE,
                import_mode VARCHAR(30),
                endpoint_or_homepage TEXT,
                license_note TEXT,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS entity_import_batch (
                id BIGSERIAL PRIMARY KEY,
                source_code VARCHAR(50) NOT NULL REFERENCES entity_source(code),
                started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                finished_at TIMESTAMPTZ,
                status VARCHAR(20) NOT NULL,
                records_total INTEGER DEFAULT 0,
                records_new INTEGER DEFAULT 0,
                records_updated INTEGER DEFAULT 0,
                records_skipped INTEGER DEFAULT 0,
                error_log TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS entity_registry (
                id BIGSERIAL PRIMARY KEY,
                source_code VARCHAR(50) NOT NULL REFERENCES entity_source(code),
                source_record_id VARCHAR(120) NOT NULL,
                category_code VARCHAR(80) NOT NULL REFERENCES entity_category(code),
                name VARCHAR(255) NOT NULL,
                subtitle VARCHAR(255),
                status VARCHAR(50),
                owner_name VARCHAR(255),
                address_raw TEXT,
                teryt_woj VARCHAR(2),
                teryt_pow VARCHAR(4),
                teryt_gmina VARCHAR(7),
                lat DOUBLE PRECISION,
                lon DOUBLE PRECISION,
                geom GEOMETRY(Point, 4326),
                coverage_geom GEOMETRY(Geometry, 4326),
                contact_phone VARCHAR(100),
                contact_email VARCHAR(255),
                www VARCHAR(255),
                attributes_json JSONB DEFAULT '{}'::jsonb,
                source_url TEXT,
                last_seen_at TIMESTAMPTZ DEFAULT NOW(),
                last_import_batch_id BIGINT REFERENCES entity_import_batch(id),
                source_priority INTEGER DEFAULT 100,
                match_confidence DECIMAL(4, 3),
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW(),
                UNIQUE (source_code, source_record_id)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS entity_alias (
                id BIGSERIAL PRIMARY KEY,
                entity_id BIGINT NOT NULL REFERENCES entity_registry(id) ON DELETE CASCADE,
                alias_type VARCHAR(50) NOT NULL,
                alias_value VARCHAR(255) NOT NULL,
                match_confidence DECIMAL(4, 3),
                UNIQUE (entity_id, alias_type, alias_value)
            )
            """);

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_source ON entity_registry(source_code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_category ON entity_registry(category_code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_woj ON entity_registry(teryt_woj)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_pow ON entity_registry(teryt_pow)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_gmina ON entity_registry(teryt_gmina)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_geom ON entity_registry USING GIST(geom)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_entity_registry_coverage_geom ON entity_registry USING GIST(coverage_geom)");
    }

    private void seedCatalogs() {
        jdbcTemplate.update("""
            INSERT INTO entity_category (code, name, act_ref, icon, default_layer_group, geometry_mode)
            VALUES
              ('social_care_dps', 'Jednostki pomocy spolecznej / DPS', 'Art. 17 ust. 1 pkt 18', 'building', 'social_care', 'point'),
              ('prm_unit', 'Jednostki Panstwowego Ratownictwa Medycznego', 'Art. 17 ust. 1 pkt 21-23', 'ambulance', 'medical', 'point'),
              ('prm_cooperating_unit', 'Jednostki wspolpracujace z PRM', 'Art. 17 ust. 1 pkt 10, 24, 25', 'shield', 'rescue', 'point'),
              ('state_forest_unit', 'Jednostki Lasow Panstwowych', 'Art. 17 ust. 1 pkt 15', 'trees', 'environment', 'point'),
              ('water_management_unit', 'Jednostki Wod Polskich', 'Art. 17 ust. 1 pkt 14', 'waves', 'environment', 'area'),
              ('hospital_public', 'Podmioty lecznicze i szpitale', 'Art. 17 ust. 1 pkt 22-23', 'hospital', 'medical', 'point')
            ON CONFLICT (code) DO UPDATE
            SET name = EXCLUDED.name,
                act_ref = EXCLUDED.act_ref,
                icon = EXCLUDED.icon,
                default_layer_group = EXCLUDED.default_layer_group,
                geometry_mode = EXCLUDED.geometry_mode
            """);

        jdbcTemplate.update("""
            INSERT INTO entity_source (code, name, protocol, official, import_mode, endpoint_or_homepage, license_note)
            VALUES
              ('seed_dps', 'Legacy DPS seed', 'sql-seed', FALSE, 'seed', NULL, 'Synthetic bootstrap data from legacy placowka table'),
              ('rjps', 'RJPS MRiPS', 'public-registry', TRUE, 'registry-import', 'https://rjps.mrips.gov.pl/RJPS-pomoc/pomoc/wyszukiwarka_jednostek.htm', 'Public registry import adapter'),
              ('rprm', 'RPRM', 'public-registry', TRUE, 'export-import', 'https://rprm.ezdrowie.gov.pl/', 'Public register export adapter'),
              ('rjwprm', 'RJWPRM', 'public-registry', TRUE, 'registry-import', 'https://rjwprm.ezdrowie.gov.pl/', 'Public registry import adapter'),
              ('nfz', 'NFZ API', 'rest', TRUE, 'enrichment', 'https://api.nfz.gov.pl/', 'Public API enrichment'),
              ('prg', 'PRG GUGiK WFS', 'wfs', TRUE, 'boundary-import', 'https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeDivision', 'Administrative boundary source'),
              ('bdl', 'Bank Danych o Lasach', 'ogc-api', TRUE, 'features-import', 'https://bdl.lasy.gov.pl/portal/uslugi-mapowe-ogc', 'Public OGC API Features source'),
              ('wody_polskie', 'Wody Polskie', 'wms-download', TRUE, 'area-import', 'https://www.gov.pl/web/wody-polskie', 'Official area and hydro source')
            ON CONFLICT (code) DO UPDATE
            SET name = EXCLUDED.name,
                protocol = EXCLUDED.protocol,
                official = EXCLUDED.official,
                import_mode = EXCLUDED.import_mode,
                endpoint_or_homepage = EXCLUDED.endpoint_or_homepage,
                license_note = EXCLUDED.license_note
            """);
    }

    private void upsertLayerConfig() {
        jdbcTemplate.update("""
            INSERT INTO layer_config
                (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
                 endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis, aktywna)
            VALUES
                ('L-01', 'Podmioty ochrony ludnosci', 'EntityLayer', 'Point', TRUE,
                 '/api/layers/L-01', 900, '#2563EB', 'building',
                 'Krajowa warstwa podmiotow ochrony ludnosci z jednego rejestru aplikacyjnego', TRUE)
            ON CONFLICT (id) DO UPDATE
            SET nazwa = EXCLUDED.nazwa,
                komponent = EXCLUDED.komponent,
                typ_geometrii = EXCLUDED.typ_geometrii,
                domyslnie_wlaczona = EXCLUDED.domyslnie_wlaczona,
                endpoint = EXCLUDED.endpoint,
                interval_odswiezania_s = EXCLUDED.interval_odswiezania_s,
                kolor_domyslny = EXCLUDED.kolor_domyslny,
                ikona = EXCLUDED.ikona,
                opis = EXCLUDED.opis,
                aktywna = EXCLUDED.aktywna
            """);
    }

    private void migrateLegacyPlacowki() {
        jdbcTemplate.update("""
            INSERT INTO entity_registry (
                source_code,
                source_record_id,
                category_code,
                name,
                subtitle,
                status,
                owner_name,
                address_raw,
                teryt_woj,
                lat,
                lon,
                geom,
                contact_phone,
                attributes_json,
                source_url,
                last_seen_at,
                source_priority,
                match_confidence
            )
            SELECT
                'seed_dps',
                p.kod,
                'social_care_dps',
                p.nazwa,
                p.typ,
                'seed',
                NULL,
                p.adres,
                '06',
                ST_Y(p.geom),
                ST_X(p.geom),
                p.geom,
                p.kontakt,
                jsonb_strip_nulls(jsonb_build_object(
                    'pojemnosc_ogolna', p.pojemnosc_ogolna,
                    'liczba_podopiecznych', p.liczba_podopiecznych,
                    'niesamodzielni_procent', p.niesamodzielni_procent,
                    'generator_backup', p.generator_backup,
                    'personel_dyzurny', p.personel_dyzurny,
                    'legacy_powiat', p.powiat,
                    'legacy_gmina', p.gmina,
                    'legacy_woj', 'lubelskie',
                    'legacy_source', p.zrodlo
                )),
                NULL,
                COALESCE(p.ostatnia_aktualizacja, NOW()),
                900,
                1.000
            FROM placowka p
            ON CONFLICT (source_code, source_record_id) DO UPDATE
            SET category_code = EXCLUDED.category_code,
                name = EXCLUDED.name,
                subtitle = EXCLUDED.subtitle,
                status = EXCLUDED.status,
                address_raw = EXCLUDED.address_raw,
                teryt_woj = EXCLUDED.teryt_woj,
                lat = EXCLUDED.lat,
                lon = EXCLUDED.lon,
                geom = EXCLUDED.geom,
                contact_phone = EXCLUDED.contact_phone,
                attributes_json = EXCLUDED.attributes_json,
                last_seen_at = EXCLUDED.last_seen_at,
                source_priority = EXCLUDED.source_priority,
                match_confidence = EXCLUDED.match_confidence,
                updated_at = NOW()
            """);

        jdbcTemplate.update("""
            INSERT INTO entity_alias (entity_id, alias_type, alias_value, match_confidence)
            SELECT er.id, 'legacy_placowka_kod', er.source_record_id, 1.000
            FROM entity_registry er
            WHERE er.source_code = 'seed_dps'
            ON CONFLICT (entity_id, alias_type, alias_value) DO NOTHING
            """);
    }
}
