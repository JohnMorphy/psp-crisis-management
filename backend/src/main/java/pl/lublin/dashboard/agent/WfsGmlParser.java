package pl.lublin.dashboard.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class WfsGmlParser {

    private static final Logger log = LoggerFactory.getLogger(WfsGmlParser.class);

    private static final String NS_WFS = "http://www.opengis.net/wfs/2.0";
    private static final String NS_MS  = "http://mapserver.gis.umn.edu/mapserver";

    private static final DocumentBuilderFactory DBF;
    private static final TransformerFactory TF;

    static {
        try {
            DBF = DocumentBuilderFactory.newInstance();
            DBF.setNamespaceAware(true);
            DBF.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DBF.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            TF = TransformerFactory.newInstance();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public record GranicaFeature(String kodTeryt, String nazwa, String gmlGeometry) {}

    public record ParseResult(List<GranicaFeature> features, String nextUrl, int numberReturned) {}

    public ParseResult parse(String gml) {
        try {
            DocumentBuilder builder = DBF.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(gml.getBytes(StandardCharsets.UTF_8)));

            Element root = doc.getDocumentElement();

            String nextUrl = root.getAttribute("next");
            if (nextUrl.isBlank()) nextUrl = null;

            int numberReturned = 0;
            try {
                numberReturned = Integer.parseInt(root.getAttribute("numberReturned"));
            } catch (NumberFormatException e) {
                log.warn("numberReturned nie jest liczba: '{}'", root.getAttribute("numberReturned"));
            }

            NodeList members = root.getElementsByTagNameNS(NS_WFS, "member");
            List<GranicaFeature> features = new ArrayList<>(members.getLength());

            for (int i = 0; i < members.getLength(); i++) {
                Element member = (Element) members.item(i);
                Element featureEl = firstChildElement(member);
                if (featureEl == null) continue;

                try {
                    String kodTeryt = msText(featureEl, "JPT_KOD_JE").trim();
                    String nazwa    = msText(featureEl, "JPT_NAZWA_").trim();
                    String gmlGeom  = extractInnerGeometry(featureEl);

                    if (kodTeryt.isEmpty() || nazwa.isEmpty() || gmlGeom == null) {
                        log.warn("Pominięto feature #{}: kodTeryt='{}' nazwa='{}'",
                            i, kodTeryt, nazwa);
                        continue;
                    }
                    features.add(new GranicaFeature(kodTeryt, nazwa, gmlGeom));
                } catch (Exception e) {
                    log.warn("Błąd parsowania feature #{}: {}", i, e.getMessage());
                }
            }

            return new ParseResult(features, nextUrl, numberReturned);

        } catch (Exception e) {
            throw new RuntimeException("GML parse failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String msText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(NS_MS, localName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }

    private String extractInnerGeometry(Element featureEl) throws Exception {
        NodeList geomNodes = featureEl.getElementsByTagNameNS(NS_MS, "msGeometry");
        if (geomNodes.getLength() == 0) return null;

        Element msGeomEl = (Element) geomNodes.item(0);
        Element child = firstChildElement(msGeomEl);
        if (child == null) return null;

        Transformer t = TF.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(child), new StreamResult(sw));
        return sw.toString();
    }

    @Nullable
    private Element firstChildElement(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        return (Element) child;
    }
}
