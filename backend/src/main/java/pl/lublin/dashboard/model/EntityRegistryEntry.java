package pl.lublin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "entity_registry")
public class EntityRegistryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, length = 50)
    private String sourceCode;

    @Column(name = "source_record_id", nullable = false, length = 120)
    private String sourceRecordId;

    @Column(name = "category_code", nullable = false, length = 80)
    private String categoryCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    @Column(name = "address_raw", columnDefinition = "TEXT")
    private String addressRaw;

    @Column(name = "teryt_woj", length = 2)
    private String terytWoj;

    @Column(name = "teryt_pow", length = 4)
    private String terytPow;

    @Column(name = "teryt_gmina", length = 7)
    private String terytGmina;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Point,4326)")
    private Point geom;

    @Column(name = "coverage_geom", columnDefinition = "GEOMETRY(Geometry,4326)")
    private Geometry coverageGeom;

    @Column(name = "contact_phone", length = 100)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "www", length = 255)
    private String www;

    @Column(name = "attributes_json", columnDefinition = "jsonb")
    private String attributesJson;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_import_batch_id")
    private Long lastImportBatchId;

    @Column(name = "source_priority")
    private Integer sourcePriority;

    @Column(name = "match_confidence", precision = 4, scale = 3)
    private BigDecimal matchConfidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getAddressRaw() { return addressRaw; }
    public void setAddressRaw(String addressRaw) { this.addressRaw = addressRaw; }

    public String getTerytWoj() { return terytWoj; }
    public void setTerytWoj(String terytWoj) { this.terytWoj = terytWoj; }

    public String getTerytPow() { return terytPow; }
    public void setTerytPow(String terytPow) { this.terytPow = terytPow; }

    public String getTerytGmina() { return terytGmina; }
    public void setTerytGmina(String terytGmina) { this.terytGmina = terytGmina; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public Point getGeom() { return geom; }
    public void setGeom(Point geom) { this.geom = geom; }

    public Geometry getCoverageGeom() { return coverageGeom; }
    public void setCoverageGeom(Geometry coverageGeom) { this.coverageGeom = coverageGeom; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getWww() { return www; }
    public void setWww(String www) { this.www = www; }

    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String attributesJson) { this.attributesJson = attributesJson; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Long getLastImportBatchId() { return lastImportBatchId; }
    public void setLastImportBatchId(Long lastImportBatchId) { this.lastImportBatchId = lastImportBatchId; }

    public Integer getSourcePriority() { return sourcePriority; }
    public void setSourcePriority(Integer sourcePriority) { this.sourcePriority = sourcePriority; }

    public BigDecimal getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(BigDecimal matchConfidence) { this.matchConfidence = matchConfidence; }
}
