package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.EntityCategory;

public interface EntityCategoryRepository extends JpaRepository<EntityCategory, String> {
}
