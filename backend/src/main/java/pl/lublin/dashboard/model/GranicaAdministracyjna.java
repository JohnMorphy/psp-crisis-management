package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.MultiPolygon;

import java.time.OffsetDateTime;

@Entity
@Table(name = "granice_administracyjne")
public class GranicaAdministracyjna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "kod_teryt", unique = true, nullable = false, length = 7)
    private String kodTeryt;

    @Column(name = "nazwa", nullable = false, length = 200)
    private String nazwa;

    @Column(name = "poziom", nullable = false, length = 12)
    private String poziom;

    @Column(name = "kod_nadrzedny", length = 6)
    private String kodNadrzedny;

    @Column(name = "geom", columnDefinition = "GEOMETRY(MULTIPOLYGON,4326)", nullable = false)
    private MultiPolygon geom;

    @Column(name = "zrodlo", length = 20)
    private String zrodlo;

    @Column(name = "data_importu")
    private OffsetDateTime dataImportu;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getKodTeryt() { return kodTeryt; }
    public void setKodTeryt(String kodTeryt) { this.kodTeryt = kodTeryt; }

    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }

    public String getPoziom() { return poziom; }
    public void setPoziom(String poziom) { this.poziom = poziom; }

    public String getKodNadrzedny() { return kodNadrzedny; }
    public void setKodNadrzedny(String kodNadrzedny) { this.kodNadrzedny = kodNadrzedny; }

    public MultiPolygon getGeom() { return geom; }
    public void setGeom(MultiPolygon geom) { this.geom = geom; }

    public String getZrodlo() { return zrodlo; }
    public void setZrodlo(String zrodlo) { this.zrodlo = zrodlo; }

    public OffsetDateTime getDataImportu() { return dataImportu; }
    public void setDataImportu(OffsetDateTime dataImportu) { this.dataImportu = dataImportu; }
}
