package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lublin.dashboard.model.LayerConfig;
import pl.lublin.dashboard.repository.LayerConfigRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/layers")
public class LayerConfigController {

    @Autowired
    private LayerConfigRepository layerConfigRepository;

    @GetMapping
    public List<Map<String, Object>> getLayers() {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return layerConfigRepository.findByAktywnaTrue().stream()
            .map(lc -> toResponse(lc, timestamp))
            .toList();
    }

    private Map<String, Object> toResponse(LayerConfig lc, String timestamp) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", lc.getId());
        resp.put("nazwa", lc.getNazwa());
        resp.put("komponent", lc.getKomponent());
        resp.put("typ_geometrii", lc.getTypGeometrii());
        resp.put("domyslnie_wlaczona", lc.getDomyslnieWlaczona());
        resp.put("endpoint", lc.getEndpoint());
        resp.put("interval_odswiezania_s", lc.getIntervalOdswiezaniaS());
        resp.put("kolor_domyslny", lc.getKolorDomyslny());
        resp.put("ikona", lc.getIkona());
        resp.put("opis", lc.getOpis());
        resp.put("ostatnia_aktualizacja", timestamp);
        resp.put("status", "ok");
        return resp;
    }
}
