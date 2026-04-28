package pl.lublin.dashboard.model;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class MendixUnitCacheTest {

    @Test
    void mendixUnitCache_setsFieldsCorrectly() {
        MendixUnitCache unit = new MendixUnitCache();
        unit.setMendixId("MX-001");
        unit.setCategoryCode("psp");
        unit.setSyncedAt(OffsetDateTime.now());

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        Point point = gf.createPoint(new Coordinate(21.0, 52.0));
        unit.setGeom(point);

        assertThat(unit.getMendixId()).isEqualTo("MX-001");
        assertThat(unit.getCategoryCode()).isEqualTo("psp");
        assertThat(unit.getSyncedAt()).isNotNull();
        assertThat(unit.getGeom()).isNotNull();
        assertThat(unit.getGeom().getX()).isEqualTo(21.0);
        assertThat(unit.getGeom().getY()).isEqualTo(52.0);
    }
}
