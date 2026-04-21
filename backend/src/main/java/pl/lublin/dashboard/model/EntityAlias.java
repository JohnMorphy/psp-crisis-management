package pl.lublin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "entity_alias")
public class EntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "alias_type", nullable = false, length = 50)
    private String aliasType;

    @Column(name = "alias_value", nullable = false, length = 255)
    private String aliasValue;

    @Column(name = "match_confidence", precision = 4, scale = 3)
    private BigDecimal matchConfidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getAliasType() { return aliasType; }
    public void setAliasType(String aliasType) { this.aliasType = aliasType; }

    public String getAliasValue() { return aliasValue; }
    public void setAliasValue(String aliasValue) { this.aliasValue = aliasValue; }

    public BigDecimal getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(BigDecimal matchConfidence) { this.matchConfidence = matchConfidence; }
}
