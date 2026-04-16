package pl.lublin.dashboard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "layer_config")
public class LayerConfig {

    @Id
    @Column(name = "id", length = 10)
    private String id;

    @Column(name = "nazwa", nullable = false, length = 100)
    private String nazwa;

    @Column(name = "komponent", nullable = false, length = 100)
    private String komponent;

    @Column(name = "typ_geometrii", length = 20)
    private String typGeometrii;

    @Column(name = "domyslnie_wlaczona")
    private Boolean domyslnieWlaczona;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "interval_odswiezania_s")
    private Integer intervalOdswiezaniaS;

    @Column(name = "kolor_domyslny", length = 7)
    private String kolorDomyslny;

    @Column(name = "ikona", length = 50)
    private String ikona;

    @Column(name = "opis", columnDefinition = "TEXT")
    private String opis;

    @Column(name = "aktywna")
    private Boolean aktywna;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }

    public String getKomponent() { return komponent; }
    public void setKomponent(String komponent) { this.komponent = komponent; }

    public String getTypGeometrii() { return typGeometrii; }
    public void setTypGeometrii(String typGeometrii) { this.typGeometrii = typGeometrii; }

    public Boolean getDomyslnieWlaczona() { return domyslnieWlaczona; }
    public void setDomyslnieWlaczona(Boolean domyslnieWlaczona) { this.domyslnieWlaczona = domyslnieWlaczona; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public Integer getIntervalOdswiezaniaS() { return intervalOdswiezaniaS; }
    public void setIntervalOdswiezaniaS(Integer intervalOdswiezaniaS) { this.intervalOdswiezaniaS = intervalOdswiezaniaS; }

    public String getKolorDomyslny() { return kolorDomyslny; }
    public void setKolorDomyslny(String kolorDomyslny) { this.kolorDomyslny = kolorDomyslny; }

    public String getIkona() { return ikona; }
    public void setIkona(String ikona) { this.ikona = ikona; }

    public String getOpis() { return opis; }
    public void setOpis(String opis) { this.opis = opis; }

    public Boolean getAktywna() { return aktywna; }
    public void setAktywna(Boolean aktywna) { this.aktywna = aktywna; }
}
