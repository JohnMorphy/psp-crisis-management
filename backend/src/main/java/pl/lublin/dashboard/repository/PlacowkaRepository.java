package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.Placowka;

import java.util.List;
import java.util.Optional;

public interface PlacowkaRepository extends JpaRepository<Placowka, Integer> {

    Optional<Placowka> findByKod(String kod);

    List<Placowka> findByPowiat(String powiat);
}
