package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "strefy_zagrozen")
public class StrefaZagrozen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "kod", unique = true, nullable = false, length = 30)
    private String kod;

    @Column(name = "typ_zagrozenia", nullable = false, length = 20)
    private String typZagrozenia;

    @Column(name = "poziom", nullable = false, length = 10)
    private String poziom;

    @Column(name = "scenariusz", length = 20)
    private String scenariusz;

    @Column(name = "nazwa", length = 255)
    private String nazwa;

    @Column(name = "obszar", length = 100)
    private String obszar;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Polygon,4326)", nullable = false)
    private Polygon geom;

    @Column(name = "szybkosc_wznoszenia_m_h", precision = 5, scale = 2)
    private BigDecimal szybkoscWznoszeniaM_h;

    @Column(name = "czas_do_zagrozenia_h", precision = 6, scale = 1)
    private BigDecimal czasDoZagrozenia_h;

    @Column(name = "zrodlo", length = 20)
    private String zrodlo;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "ostatnia_aktualizacja")
    private OffsetDateTime ostatniaAktualizacja;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getKod() { return kod; }
    public void setKod(String kod) { this.kod = kod; }

    public String getTypZagrozenia() { return typZagrozenia; }
    public void setTypZagrozenia(String typZagrozenia) { this.typZagrozenia = typZagrozenia; }

    public String getPoziom() { return poziom; }
    public void setPoziom(String poziom) { this.poziom = poziom; }

    public String getScenariusz() { return scenariusz; }
    public void setScenariusz(String scenariusz) { this.scenariusz = scenariusz; }

    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }

    public String getObszar() { return obszar; }
    public void setObszar(String obszar) { this.obszar = obszar; }

    public Polygon getGeom() { return geom; }
    public void setGeom(Polygon geom) { this.geom = geom; }

    public BigDecimal getSzybkoscWznoszeniaM_h() { return szybkoscWznoszeniaM_h; }
    public void setSzybkoscWznoszeniaM_h(BigDecimal szybkoscWznoszeniaM_h) { this.szybkoscWznoszeniaM_h = szybkoscWznoszeniaM_h; }

    public BigDecimal getCzasDoZagrozenia_h() { return czasDoZagrozenia_h; }
    public void setCzasDoZagrozenia_h(BigDecimal czasDoZagrozenia_h) { this.czasDoZagrozenia_h = czasDoZagrozenia_h; }

    public String getZrodlo() { return zrodlo; }
    public void setZrodlo(String zrodlo) { this.zrodlo = zrodlo; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public OffsetDateTime getOstatniaAktualizacja() { return ostatniaAktualizacja; }
    public void setOstatniaAktualizacja(OffsetDateTime ostatniaAktualizacja) { this.ostatniaAktualizacja = ostatniaAktualizacja; }
}
