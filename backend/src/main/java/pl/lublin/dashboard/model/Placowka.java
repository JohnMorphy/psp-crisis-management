package pl.lublin.dashboard.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "placowka")
public class Placowka {

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

    @Column(name = "liczba_podopiecznych")
    private Integer liczbaPodopiecznych;

    @Column(name = "niesamodzielni_procent", precision = 4, scale = 3)
    private BigDecimal niesamodzielniProcent;

    @Column(name = "generator_backup")
    private Boolean generatorBackup;

    @Column(name = "personel_dyzurny")
    private Integer personelDyzurny;

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

    public Integer getLiczbaPodopiecznych() { return liczbaPodopiecznych; }
    public void setLiczbaPodopiecznych(Integer liczbaPodopiecznych) { this.liczbaPodopiecznych = liczbaPodopiecznych; }

    public BigDecimal getNiesamodzielniProcent() { return niesamodzielniProcent; }
    public void setNiesamodzielniProcent(BigDecimal niesamodzielniProcent) { this.niesamodzielniProcent = niesamodzielniProcent; }

    public Boolean getGeneratorBackup() { return generatorBackup; }
    public void setGeneratorBackup(Boolean generatorBackup) { this.generatorBackup = generatorBackup; }

    public Integer getPersonelDyzurny() { return personelDyzurny; }
    public void setPersonelDyzurny(Integer personelDyzurny) { this.personelDyzurny = personelDyzurny; }

    public String getKontakt() { return kontakt; }
    public void setKontakt(String kontakt) { this.kontakt = kontakt; }

    public OffsetDateTime getOstatniaAktualizacja() { return ostatniaAktualizacja; }
    public void setOstatniaAktualizacja(OffsetDateTime ostatniaAktualizacja) { this.ostatniaAktualizacja = ostatniaAktualizacja; }

    public String getZrodlo() { return zrodlo; }
    public void setZrodlo(String zrodlo) { this.zrodlo = zrodlo; }
}
