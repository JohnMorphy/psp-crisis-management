package pl.lublin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "entity_category")
public class EntityCategory {

    @Id
    @Column(name = "code", length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "act_ref", columnDefinition = "TEXT")
    private String actRef;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "default_layer_group", length = 50)
    private String defaultLayerGroup;

    @Column(name = "geometry_mode", length = 20)
    private String geometryMode;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getActRef() { return actRef; }
    public void setActRef(String actRef) { this.actRef = actRef; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDefaultLayerGroup() { return defaultLayerGroup; }
    public void setDefaultLayerGroup(String defaultLayerGroup) { this.defaultLayerGroup = defaultLayerGroup; }

    public String getGeometryMode() { return geometryMode; }
    public void setGeometryMode(String geometryMode) { this.geometryMode = geometryMode; }
}
