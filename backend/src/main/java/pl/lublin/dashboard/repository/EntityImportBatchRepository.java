package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.EntityImportBatch;

import java.util.List;

public interface EntityImportBatchRepository extends JpaRepository<EntityImportBatch, Long> {

    List<EntityImportBatch> findTop20ByOrderByStartedAtDesc();

    List<EntityImportBatch> findTop20BySourceCodeOrderByStartedAtDesc(String sourceCode);
}
