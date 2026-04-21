package pl.lublin.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lublin.dashboard.model.EntityImportBatch;
import pl.lublin.dashboard.service.EntityImportService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImportController {

    @Autowired private EntityImportService entityImportService;

    @PostMapping("/import/{sourceCode}")
    public ResponseEntity<?> importSource(@PathVariable String sourceCode) {
        try {
            EntityImportBatch batch = entityImportService.runImport(sourceCode);
            return ResponseEntity.status("running".equals(batch.getStatus()) ? 202 : 200).body(toResponse(batch));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(error(ex.getMessage(), "IMPORT_SOURCE_NOT_FOUND"));
        }
    }

    @GetMapping("/import-runs")
    public ResponseEntity<?> getImportRuns(@RequestParam(required = false) String source) {
        return ResponseEntity.ok(entityImportService.getRecentRuns(source).stream().map(this::toResponse).toList());
    }

    private Map<String, Object> toResponse(EntityImportBatch batch) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", batch.getId());
        response.put("source_code", batch.getSourceCode());
        response.put("status", batch.getStatus());
        response.put("started_at", format(batch.getStartedAt()));
        response.put("finished_at", format(batch.getFinishedAt()));
        response.put("records_total", batch.getRecordsTotal());
        response.put("records_new", batch.getRecordsNew());
        response.put("records_updated", batch.getRecordsUpdated());
        response.put("records_skipped", batch.getRecordsSkipped());
        response.put("error_log", batch.getErrorLog());
        return response;
    }

    private String format(OffsetDateTime value) {
        return value != null ? value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }

    private Map<String, Object> error(String message, String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", message);
        response.put("code", code);
        response.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return response;
    }
}
