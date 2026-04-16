package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.lublin.dashboard.model.IkeResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IkeResultRepository extends JpaRepository<IkeResult, String> {

    @Query("SELECT r FROM IkeResult r ORDER BY r.ikeScore DESC NULLS LAST")
    List<IkeResult> findAllOrderByIkeScoreDesc();

    @Query("SELECT r FROM IkeResult r WHERE r.ikeKategoria = :kategoria ORDER BY r.ikeScore DESC NULLS LAST")
    List<IkeResult> findByKategoriaOrderByIkeScoreDesc(String kategoria);

    Optional<IkeResult> findByPlacowkaKod(String placowkaKod);

    @Query("SELECT MAX(r.obliczoneO) FROM IkeResult r")
    Optional<OffsetDateTime> findLastCalculationTime();

    @Query("SELECT r.correlationId FROM IkeResult r WHERE r.obliczoneO = (SELECT MAX(r2.obliczoneO) FROM IkeResult r2) ORDER BY r.placowkaKod ASC")
    List<String> findLastCorrelationIds();
}
