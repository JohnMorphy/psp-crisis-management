package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Entity
@Table(name = "miejsca_relokacji")
public class MiejsceRelokacji {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "kod", unique = true, nullable = false, length = 20)
    private String kod;

    @Column(name = "nazwa", nullable = false, length = 255)
    private String nazwa;

    @Column(name = "typ", length = 30)
    private String typ;

    @Column(name = "powiat", nullable = false, length = 100)
    private String powiat;

    @Column(name = "gmina", nullable = false, length = 100)
    private String gmina;

    @Column(name = "adres", columnDefinition = "TEXT")
    private String adres;

    @Column(name = "geom", columnDefinition = "GEOMETRY(Point,4326)", nullable = false)
    private Point geom;

    @Column(name = "pojemnosc_ogolna")
    private Integer pojemnoscOgolna;

    @Column(name = "pojemnosc_dostepna")
    private Integer pojemnoscDostepna;

    @Column(name = "przyjmuje_niesamodzielnych")
    private Boolean przyjmujeNiesamodzielnych;

    @Column(name = "kontakt", length = 50)
    private String kontakt;

    @Column(name = "ostatnia_aktualizacja")
    private OffsetDateTime ostatniaAktualizacja;

    @Column(name = "zrodlo", length = 20)
    private String zrodlo;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getKod() { return kod; }
    public void setKod(String kod) { this.kod = kod; }

    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }

    public String getTyp() { return typ; }
    public void setTyp(String typ) { this.typ = typ; }

    public String getPowiat() { return powiat; }
    public void setPowiat(String powiat) { this.powiat = powiat; }

    public String getGmina() { return gmina; }
    public void setGmina(String gmina) { this.gmina = gmina; }

    public String getAdres() { return adres; }
    public void setAdres(String adres) { this.adres = adres; }

    public Point getGeom() { return geom; }
    public void setGeom(Point geom) { this.geom = geom; }

    public Integer getPojemnoscOgolna() { return pojemnoscOgolna; }
    public void setPojemnoscOgolna(Integer pojemnoscOgolna) { this.pojemnoscOgolna = pojemnoscOgolna; }

    public Integer getPojemnoscDostepna() { return pojemnoscDostepna; }
    public void setPojemnoscDostepna(Integer pojemnoscDostepna) { this.pojemnoscDostepna = pojemnoscDostepna; }

    public Boolean getPrzyjmujeNiesamodzielnych() { return przyjmujeNiesamodzielnych; }
    public void setPrzyjmujeNiesamodzielnych(Boolean przyjmujeNiesamodzielnych) { this.przyjmujeNiesamodzielnych = przyjmujeNiesamodzielnych; }

    public String getKontakt() { return kontakt; }
    public void setKontakt(String kontakt) { this.kontakt = kontakt; }

    public OffsetDateTime getOstatniaAktualizacja() { return ostatniaAktualizacja; }
    public void setOstatniaAktualizacja(OffsetDateTime ostatniaAktualizacja) { this.ostatniaAktualizacja = ostatniaAktualizacja; }

    public String getZrodlo() { return zrodlo; }
    public void setZrodlo(String zrodlo) { this.zrodlo = zrodlo; }
}
