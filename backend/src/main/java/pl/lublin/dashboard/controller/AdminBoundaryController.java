package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lublin.dashboard.agent.AdminBoundaryImportAgent;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-boundaries")
public class AdminBoundaryController {

    @Autowired private AdminBoundaryImportAgent importAgent;

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> startImport() {
        if (!importAgent.tryAcquire()) {
            return ResponseEntity.status(409).body(error(
                "Import granic jest juz w toku",
                "IMPORT_IN_PROGRESS"));
        }

        String correlationId = UUID.randomUUID().toString();
        importAgent.importAll(correlationId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "started");
        resp.put("poziomy", List.of("wojewodztwo", "powiat", "gmina"));
        resp.put("correlation_id", correlationId);
        return ResponseEntity.status(202).body(resp);
    }

    private Map<String, Object> error(String message, String code) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("error", message);
        resp.put("code", code);
        resp.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return resp;
    }
}
