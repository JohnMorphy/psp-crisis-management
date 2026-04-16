package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.lublin.dashboard.repository.LayerConfigRepository;
import pl.lublin.dashboard.service.GeoService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/layers")
public class GeoController {

    @Autowired private GeoService geoService;
    @Autowired private LayerConfigRepository layerConfigRepository;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLayer(
            @PathVariable String id,
            @RequestParam(required = false) String powiat,
            @RequestParam(required = false) String gmina,
            @RequestParam(required = false) String bbox) {

        if ("L-00".equals(id)) {
            return ResponseEntity.ok(geoService.getAdminBoundaries());
        }

        if (layerConfigRepository.findById(id).isEmpty()) {
            return ResponseEntity.status(404).body(error("Warstwa nie istnieje: " + id, "LAYER_NOT_FOUND"));
        }

        return ResponseEntity.ok(geoService.buildLayerGeoJson(id, powiat, gmina, bbox));
    }

    private Map<String, Object> error(String message, String code) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("error", message);
        resp.put("code", code);
        resp.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return resp;
    }
}
