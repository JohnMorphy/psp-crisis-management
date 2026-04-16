package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ike_results")
public class IkeResult {

    @Id
    @Column(name = "placowka_kod", length = 20)
    private String placowkaKod;

    @Column(name = "ike_score", precision = 5, scale = 4)
    private BigDecimal ikeScore;

    @Column(name = "ike_kategoria", length = 10)
    private String ikeKategoria;

    @Column(name = "score_zagrozenia", precision = 5, scale = 4)
    private BigDecimal scoreZagrozenia;

    @Column(name = "score_niesamodzielnych", precision = 5, scale = 4)
    private BigDecimal scoreNiesamodzielnych;

    @Column(name = "score_braku_transportu", precision = 5, scale = 4)
    private BigDecimal scoreBrakuTransportu;

    @Column(name = "score_braku_droznosci", precision = 5, scale = 4)
    private BigDecimal scoreBrakuDroznosci;

    @Column(name = "score_odleglosci_relokacji", precision = 5, scale = 4)
    private BigDecimal scoreOdleglosci;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_warnings", columnDefinition = "jsonb")
    private List<String> dataWarnings = new ArrayList<>();

    @Column(name = "cel_relokacji_kod", length = 20)
    private String celRelokacjiKod;

    @Column(name = "trasa_relokacji_geom", columnDefinition = "GEOMETRY(LineString,4326)")
    private LineString trasaRelokacjiGeom;

    @Column(name = "czas_przejazdu_min")
    private Integer czasPrzejazduMin;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "obliczone_o")
    private OffsetDateTime obliczoneO;

    public String getPlacowkaKod() { return placowkaKod; }
    public void setPlacowkaKod(String placowkaKod) { this.placowkaKod = placowkaKod; }

    public BigDecimal getIkeScore() { return ikeScore; }
    public void setIkeScore(BigDecimal ikeScore) { this.ikeScore = ikeScore; }

    public String getIkeKategoria() { return ikeKategoria; }
    public void setIkeKategoria(String ikeKategoria) { this.ikeKategoria = ikeKategoria; }

    public BigDecimal getScoreZagrozenia() { return scoreZagrozenia; }
    public void setScoreZagrozenia(BigDecimal scoreZagrozenia) { this.scoreZagrozenia = scoreZagrozenia; }

    public BigDecimal getScoreNiesamodzielnych() { return scoreNiesamodzielnych; }
    public void setScoreNiesamodzielnych(BigDecimal scoreNiesamodzielnych) { this.scoreNiesamodzielnych = scoreNiesamodzielnych; }

    public BigDecimal getScoreBrakuTransportu() { return scoreBrakuTransportu; }
    public void setScoreBrakuTransportu(BigDecimal scoreBrakuTransportu) { this.scoreBrakuTransportu = scoreBrakuTransportu; }

    public BigDecimal getScoreBrakuDroznosci() { return scoreBrakuDroznosci; }
    public void setScoreBrakuDroznosci(BigDecimal scoreBrakuDroznosci) { this.scoreBrakuDroznosci = scoreBrakuDroznosci; }

    public BigDecimal getScoreOdleglosci() { return scoreOdleglosci; }
    public void setScoreOdleglosci(BigDecimal scoreOdleglosci) { this.scoreOdleglosci = scoreOdleglosci; }

    public List<String> getDataWarnings() { return dataWarnings; }
    public void setDataWarnings(List<String> dataWarnings) { this.dataWarnings = dataWarnings != null ? dataWarnings : new ArrayList<>(); }

    public String getCelRelokacjiKod() { return celRelokacjiKod; }
    public void setCelRelokacjiKod(String celRelokacjiKod) { this.celRelokacjiKod = celRelokacjiKod; }

    public LineString getTrasaRelokacjiGeom() { return trasaRelokacjiGeom; }
    public void setTrasaRelokacjiGeom(LineString trasaRelokacjiGeom) { this.trasaRelokacjiGeom = trasaRelokacjiGeom; }

    public Integer getCzasPrzejazduMin() { return czasPrzejazduMin; }
    public void setCzasPrzejazduMin(Integer czasPrzejazduMin) { this.czasPrzejazduMin = czasPrzejazduMin; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public OffsetDateTime getObliczoneO() { return obliczoneO; }
    public void setObliczoneO(OffsetDateTime obliczoneO) { this.obliczoneO = obliczoneO; }
}
