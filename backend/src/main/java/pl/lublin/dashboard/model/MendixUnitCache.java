package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mendix_unit_cache")
public class MendixUnitCache {

    @Id
    @Column(name = "mendix_id", length = 255)
    private String mendixId;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Point,4326)", nullable = false)
    private Point geom;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt;

    public String getMendixId() { return mendixId; }
    public void setMendixId(String mendixId) { this.mendixId = mendixId; }
    public Point getGeom() { return geom; }
    public void setGeom(Point geom) { this.geom = geom; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime syncedAt) { this.syncedAt = syncedAt; }
}
