package pl.lublin.dashboard.agent;

import org.junit.jupiter.api.Test;
import pl.lublin.dashboard.agent.WfsGmlParser.GranicaFeature;
import pl.lublin.dashboard.agent.WfsGmlParser.ParseResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WfsGmlParserTest {

    private final WfsGmlParser parser = new WfsGmlParser();

    private static final String SAMPLE_GML = """
        <?xml version='1.0' encoding="UTF-8" ?>
        <wfs:FeatureCollection
           xmlns:ms="http://mapserver.gis.umn.edu/mapserver"
           xmlns:gml="http://www.opengis.net/gml/3.2"
           xmlns:wfs="http://www.opengis.net/wfs/2.0"
           timeStamp="2026-04-17T10:00:00"
           numberMatched="unknown"
           numberReturned="2"
           next="http://example.com?STARTINDEX=2">
          <wfs:member>
            <ms:A01_Granice_wojewodztw>
              <ms:msGeometry>
                <gml:MultiSurface gml:id="ms.1" srsName="urn:ogc:def:crs:EPSG::2180">
                  <gml:surfaceMember>
                    <gml:Polygon>
                      <gml:exterior>
                        <gml:LinearRing>
                          <gml:posList>537986.0 171698.0 747606.0 171698.0 747606.0 367472.0 537986.0 367472.0 537986.0 171698.0</gml:posList>
                        </gml:LinearRing>
                      </gml:exterior>
                    </gml:Polygon>
                  </gml:surfaceMember>
                </gml:MultiSurface>
              </ms:msGeometry>
              <ms:JPT_KOD_JE>32</ms:JPT_KOD_JE>
              <ms:JPT_NAZWA_>zachodniopomorskie</ms:JPT_NAZWA_>
            </ms:A01_Granice_wojewodztw>
          </wfs:member>
          <wfs:member>
            <ms:A01_Granice_wojewodztw>
              <ms:msGeometry>
                <gml:MultiSurface gml:id="ms.2" srsName="urn:ogc:def:crs:EPSG::2180">
                  <gml:surfaceMember>
                    <gml:Polygon>
                      <gml:exterior>
                        <gml:LinearRing>
                          <gml:posList>300000.0 400000.0 350000.0 400000.0 350000.0 450000.0 300000.0 450000.0 300000.0 400000.0</gml:posList>
                        </gml:LinearRing>
                      </gml:exterior>
                    </gml:Polygon>
                  </gml:surfaceMember>
                </gml:MultiSurface>
              </ms:msGeometry>
              <ms:JPT_KOD_JE>06</ms:JPT_KOD_JE>
              <ms:JPT_NAZWA_>lubelskie</ms:JPT_NAZWA_>
            </ms:A01_Granice_wojewodztw>
          </wfs:member>
        </wfs:FeatureCollection>
        """;

    @Test
    void parsuje_dwa_features() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.features()).hasSize(2);
    }

    @Test
    void parsuje_kod_teryt_i_nazwe() {
        ParseResult result = parser.parse(SAMPLE_GML);
        GranicaFeature first = result.features().get(0);
        assertThat(first.kodTeryt()).isEqualTo("32");
        assertThat(first.nazwa()).isEqualTo("zachodniopomorskie");

        GranicaFeature second = result.features().get(1);
        assertThat(second.kodTeryt()).isEqualTo("06");
        assertThat(second.nazwa()).isEqualTo("lubelskie");
    }

    @Test
    void ekstrahuje_gml_geometrii_z_srsName() {
        ParseResult result = parser.parse(SAMPLE_GML);
        String gml = result.features().get(0).gmlGeometry();
        assertThat(gml).contains("MultiSurface");
        assertThat(gml).contains("srsName=\"urn:ogc:def:crs:EPSG::2180\"");
        assertThat(gml).contains("537986.0");
    }

    @Test
    void parsuje_next_url() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.nextUrl()).isEqualTo("http://example.com?STARTINDEX=2");
    }

    @Test
    void parsuje_number_returned() {
        ParseResult result = parser.parse(SAMPLE_GML);
        assertThat(result.numberReturned()).isEqualTo(2);
    }

    @Test
    void zwraca_null_next_url_gdy_brak_atrybutu() {
        String gmlNoNext = SAMPLE_GML.replace(
            "next=\"http://example.com?STARTINDEX=2\"", "");
        ParseResult result = parser.parse(gmlNoNext);
        assertThat(result.nextUrl()).isNull();
    }

    @Test
    void pomija_feature_z_pustym_kod_teryt() {
        String gmlEmpty = SAMPLE_GML.replace(
            "<ms:JPT_KOD_JE>32</ms:JPT_KOD_JE>",
            "<ms:JPT_KOD_JE></ms:JPT_KOD_JE>");
        ParseResult result = parser.parse(gmlEmpty);
        assertThat(result.features()).hasSize(1);
        assertThat(result.features().get(0).kodTeryt()).isEqualTo("06");
    }

    @Test
    void rzuca_wyjatek_na_niepoprawny_xml() {
        assertThatThrownBy(() -> parser.parse("not xml at all"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("GML parse failed");
    }

    @Test
    void pomija_feature_bez_geometrii() {
        String gmlNoGeom = SAMPLE_GML.replaceFirst(
            "<ms:msGeometry>[\\s\\S]*?</ms:msGeometry>", "");
        ParseResult result = parser.parse(gmlNoGeom);
        assertThat(result.features()).hasSize(1);
        assertThat(result.features().get(0).kodTeryt()).isEqualTo("06");
    }

    @Test
    void zwraca_zero_gdy_number_returned_niepoprawny() {
        String gmlUnknown = SAMPLE_GML.replace(
            "numberReturned=\"2\"", "numberReturned=\"unknown\"");
        ParseResult result = parser.parse(gmlUnknown);
        assertThat(result.numberReturned()).isEqualTo(0);
    }
}
