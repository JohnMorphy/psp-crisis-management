package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lublin.dashboard.model.StrefaZagrozen;

import java.util.List;

public interface StrefaZagrozenRepository extends JpaRepository<StrefaZagrozen, Integer> {

    List<StrefaZagrozen> findByTypZagrozenia(String typZagrozenia);

    List<StrefaZagrozen> findByPoziom(String poziom);

    void deleteByKodStartingWith(String kodPrefix);
}
