package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.EntityAlias;

public interface EntityAliasRepository extends JpaRepository<EntityAlias, Long> {
}
