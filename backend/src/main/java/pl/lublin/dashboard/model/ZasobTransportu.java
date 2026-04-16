package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Entity
@Table(name = "zasob_transportu")
public class ZasobTransportu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "kod", unique = true, nullable = false, length = 20)
    private String kod;

    @Column(name = "typ", length = 30)
    private String typ;

    @Column(name = "oznaczenie", length = 100)
    private String oznaczenie;

    @Column(name = "operator", length = 255)
    private String operator;

    @Column(name = "powiat", length = 100)
    private String powiat;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Point,4326)")
    private Point geom;

    @Column(name = "pojemnosc_osob")
    private Integer pojemnoscOsob;

    @Column(name = "przyjmuje_niesamodzielnych")
    private Boolean przyjmujeNiesamodzielnych;

    @Column(name = "dostepny")
    private Boolean dostepny;

    @Column(name = "ostatnia_aktualizacja")
    private OffsetDateTime ostatniaAktualizacja;

    @Column(name = "zrodlo", length = 20)
    private String zrodlo;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getKod() { return kod; }
    public void setKod(String kod) { this.kod = kod; }

    public String getTyp() { return typ; }
    public void setTyp(String typ) { this.typ = typ; }

    public String getOznaczenie() { return oznaczenie; }
    public void setOznaczenie(String oznaczenie) { this.oznaczenie = oznaczenie; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getPowiat() { return powiat; }
    public void setPowiat(String powiat) { this.powiat = powiat; }

    public Point getGeom() { return geom; }
    public void setGeom(Point geom) { this.geom = geom; }

    public Integer getPojemnoscOsob() { return pojemnoscOsob; }
    public void setPojemnoscOsob(Integer pojemnoscOsob) { this.pojemnoscOsob = pojemnoscOsob; }

    public Boolean getPrzyjmujeNiesamodzielnych() { return przyjmujeNiesamodzielnych; }
    public void setPrzyjmujeNiesamodzielnych(Boolean przyjmujeNiesamodzielnych) { this.przyjmujeNiesamodzielnych = przyjmujeNiesamodzielnych; }

    public Boolean getDostepny() { return dostepny; }
    public void setDostepny(Boolean dostepny) { this.dostepny = dostepny; }

    public OffsetDateTime getOstatniaAktualizacja() { return ostatniaAktualizacja; }
    public void setOstatniaAktualizacja(OffsetDateTime ostatniaAktualizacja) { this.ostatniaAktualizacja = ostatniaAktualizacja; }

    public String getZrodlo() { return zrodlo; }
    public void setZrodlo(String zrodlo) { this.zrodlo = zrodlo; }
}
