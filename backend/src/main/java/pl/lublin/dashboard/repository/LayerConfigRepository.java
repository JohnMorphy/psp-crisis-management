package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.LayerConfig;

import java.util.List;

public interface LayerConfigRepository extends JpaRepository<LayerConfig, String> {

    List<LayerConfig> findByAktywnaTrue();
}
