package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.EntitySource;

public interface EntitySourceRepository extends JpaRepository<EntitySource, String> {
}
