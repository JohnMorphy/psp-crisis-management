package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.ZasobTransportu;

public interface ZasobTransportuRepository extends JpaRepository<ZasobTransportu, Integer> {
}
