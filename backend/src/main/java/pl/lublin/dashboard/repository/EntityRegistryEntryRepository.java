package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.EntityRegistryEntry;

import java.util.Optional;

public interface EntityRegistryEntryRepository extends JpaRepository<EntityRegistryEntry, Long> {

    Optional<EntityRegistryEntry> findBySourceCodeAndSourceRecordId(String sourceCode, String sourceRecordId);
}
