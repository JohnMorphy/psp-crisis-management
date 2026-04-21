package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lublin.dashboard.service.EntityRegistryService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EntityController {

    @Autowired private EntityRegistryService entityRegistryService;

    @GetMapping("/entity-categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(entityRegistryService.getCategories());
    }

    @GetMapping("/entities")
    public ResponseEntity<?> getEntities(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(name = "kod_woj", required = false) String kodWoj,
            @RequestParam(name = "kod_pow", required = false) String kodPow,
            @RequestParam(name = "kod_gmina", required = false) String kodGmina,
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(entityRegistryService.getEntities(category, source, kodWoj, kodPow, kodGmina, bbox, q));
    }

    @GetMapping("/entities/{id}")
    public ResponseEntity<?> getEntity(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(entityRegistryService.getEntity(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(error(ex.getMessage(), "ENTITY_NOT_FOUND"));
        }
    }

    @GetMapping("/entities/summary")
    public ResponseEntity<?> getSummary(@RequestParam(name = "kod_teryt") String kodTeryt) {
        return ResponseEntity.ok(entityRegistryService.getSummary(kodTeryt));
    }

    private Map<String, Object> error(String message, String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", message);
        response.put("code", code);
        response.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return response;
    }
}
