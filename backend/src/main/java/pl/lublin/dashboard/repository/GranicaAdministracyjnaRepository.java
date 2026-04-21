package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lublin.dashboard.model.GranicaAdministracyjna;

import java.util.List;

public interface GranicaAdministracyjnaRepository extends JpaRepository<GranicaAdministracyjna, Integer> {

    java.util.Optional<GranicaAdministracyjna> findByKodTeryt(String kodTeryt);

    List<GranicaAdministracyjna> findByPoziom(String poziom);

    long countByPoziom(String poziom);

    @Query(value = """
            SELECT * FROM granice_administracyjne
            WHERE poziom = :poziom
            AND LEFT(kod_teryt, 2) = :kodWoj
            ORDER BY nazwa
            """, nativeQuery = true)
    List<GranicaAdministracyjna> findByPoziomAndKodWoj(
            @Param("poziom") String poziom,
            @Param("kodWoj") String kodWoj);

    @Query(value = """
            SELECT * FROM granice_administracyjne
            WHERE poziom = :poziom
            AND ST_Intersects(geom, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax, 4326))
            ORDER BY nazwa
            """, nativeQuery = true)
    List<GranicaAdministracyjna> findByPoziomAndBbox(
            @Param("poziom") String poziom,
            @Param("xmin") double xmin,
            @Param("ymin") double ymin,
            @Param("xmax") double xmax,
            @Param("ymax") double ymax);
}
