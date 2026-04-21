package pl.lublin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "entity_source")
public class EntitySource {

    @Id
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "protocol", length = 30)
    private String protocol;

    @Column(name = "official")
    private Boolean official;

    @Column(name = "import_mode", length = 30)
    private String importMode;

    @Column(name = "endpoint_or_homepage", columnDefinition = "TEXT")
    private String endpointOrHomepage;

    @Column(name = "license_note", columnDefinition = "TEXT")
    private String licenseNote;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public Boolean getOfficial() { return official; }
    public void setOfficial(Boolean official) { this.official = official; }

    public String getImportMode() { return importMode; }
    public void setImportMode(String importMode) { this.importMode = importMode; }

    public String getEndpointOrHomepage() { return endpointOrHomepage; }
    public void setEndpointOrHomepage(String endpointOrHomepage) { this.endpointOrHomepage = endpointOrHomepage; }

    public String getLicenseNote() { return licenseNote; }
    public void setLicenseNote(String licenseNote) { this.licenseNote = licenseNote; }
}
