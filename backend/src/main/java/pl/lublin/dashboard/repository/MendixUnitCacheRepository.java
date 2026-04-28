package pl.lublin.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lublin.dashboard.model.MendixUnitCache;
import java.util.List;

public interface MendixUnitCacheRepository extends JpaRepository<MendixUnitCache, String> {

    @Query(value = """
        SELECT m.mendix_id FROM mendix_unit_cache m
        WHERE ST_DWithin(m.geom::geography,
                         ST_MakePoint(:lon, :lat)::geography,
                         :radiusMeters)
        """, nativeQuery = true)
    List<String> findMendixIdsWithinRadius(
        @Param("lat") double lat,
        @Param("lon") double lon,
        @Param("radiusMeters") double radiusMeters
    );
}
