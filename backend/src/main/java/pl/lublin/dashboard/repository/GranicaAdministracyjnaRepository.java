package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.GranicaAdministracyjna;

import java.util.List;

public interface GranicaAdministracyjnaRepository extends JpaRepository<GranicaAdministracyjna, Integer> {

    List<GranicaAdministracyjna> findByPoziom(String poziom);

    long countByPoziom(String poziom);
}
