INSERT INTO entity_category (code, name, act_ref, icon, default_layer_group, geometry_mode)
VALUES
  ('social_care_dps',       'Jednostki pomocy spolecznej / DPS',           'Art. 17 ust. 1 pkt 18',       'building',  'social_care', 'point'),
  ('prm_unit',              'Jednostki Panstwowego Ratownictwa Medycznego', 'Art. 17 ust. 1 pkt 21-23',    'ambulance', 'medical',     'point'),
  ('prm_cooperating_unit',  'Jednostki wspolpracujace z PRM',              'Art. 17 ust. 1 pkt 10, 24, 25','shield',    'rescue',      'point'),
  ('state_forest_unit',     'Jednostki Lasow Panstwowych',                 'Art. 17 ust. 1 pkt 15',       'trees',     'environment', 'point'),
  ('water_management_unit', 'Jednostki Wod Polskich',                      'Art. 17 ust. 1 pkt 14',       'waves',     'environment', 'area'),
  ('hospital_public',       'Podmioty lecznicze i szpitale',               'Art. 17 ust. 1 pkt 22-23',    'hospital',  'medical',     'point')
ON CONFLICT (code) DO UPDATE
  SET name                = EXCLUDED.name,
      act_ref             = EXCLUDED.act_ref,
      icon                = EXCLUDED.icon,
      default_layer_group = EXCLUDED.default_layer_group,
      geometry_mode       = EXCLUDED.geometry_mode;

INSERT INTO entity_source (code, name, protocol, official, import_mode, endpoint_or_homepage, license_note)
VALUES
  ('rjps',          'RJPS MRiPS',        'public-registry', TRUE, 'registry-import', 'https://rjps.mrips.gov.pl/RJPS-pomoc/pomoc/wyszukiwarka_jednostek.htm', 'Public registry import adapter'),
  ('rprm',          'RPRM',              'public-registry', TRUE, 'export-import',   'https://rprm.ezdrowie.gov.pl/',                                         'Public register export adapter'),
  ('rjwprm',        'RJWPRM',            'public-registry', TRUE, 'registry-import', 'https://rjwprm.ezdrowie.gov.pl/',                                       'Public registry import adapter'),
  ('nfz',           'NFZ API',           'rest',            TRUE, 'enrichment',      'https://api.nfz.gov.pl/',                                               'Public API enrichment'),
  ('prg',           'PRG GUGiK WFS',     'wfs',             TRUE, 'boundary-import', 'https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeDivision', 'Administrative boundary source'),
  ('bdl',           'Bank Danych o Lasach', 'ogc-api',      TRUE, 'features-import', 'https://bdl.lasy.gov.pl/portal/uslugi-mapowe-ogc',                      'Public OGC API Features source'),
  ('wody_polskie',  'Wody Polskie',       'wms-download',   TRUE, 'area-import',     'https://www.gov.pl/web/wody-polskie',                                   'Official area and hydro source')
ON CONFLICT (code) DO UPDATE
  SET name                 = EXCLUDED.name,
      protocol             = EXCLUDED.protocol,
      official             = EXCLUDED.official,
      import_mode          = EXCLUDED.import_mode,
      endpoint_or_homepage = EXCLUDED.endpoint_or_homepage,
      license_note         = EXCLUDED.license_note;
