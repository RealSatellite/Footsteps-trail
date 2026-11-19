package dev.footstepstrail.deformation;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TerrainGeometryTest {
    @Test public void everyMaterialHasTexturedReliefWithBoundedSubdivision() {
        for (DeformationMaterial material : DeformationMaterial.values()) {
            TerrainQuad source = TerrainContactsTest.top();
            List<SurfaceImprint> stamps = Collections.singletonList(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 0));
            List<TerrainQuad> output = new ArrayList<>();
            QuadDeformation.emit(source, 0, 0, 0, material, stamps,
                    (x, y, z) -> SurfaceHeights.sample(x, y, z, 0, material, stamps), output::add);
            float low = 1, high = 1;
            for (TerrainQuad quad : output) for (int i = 0; i < 4; i++) {
                low = Math.min(low, quad.y[i]); high = Math.max(high, quad.y[i]);
                assertEquals(quad.x[i], quad.u[i], 0.000001);
                assertEquals(quad.z[i], quad.v[i], 0.000001);
                assertEquals(255, quad.color[i] >>> 24);
                assertEquals(0x00f000f0, quad.light[i]);
            }
            assertTrue(material + " needs a real cavity", low < 0.999);
            assertTrue(material + " needs displaced material", high > 1);
            assertTrue(output.size() <= 1024);
            assertArrayEquals(new float[]{1, 1, 1, 1}, source.y, 0);
        }
    }

    /** Brightness of the emitted vertex at the sole centre, relative to the source quad (1 = unchanged). */
    private static double centreShade(DeformationMaterial material) {
        List<SurfaceImprint> stamps = Collections.singletonList(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 0));
        int[] red = {-1};
        QuadDeformation.emit(TerrainContactsTest.top(), 0, 0, 0, material, stamps,
                (x, y, z) -> SurfaceHeights.sample(x, y, z, 0, material, stamps), q -> {
                    for (int i = 0; i < 4; i++)
                        if (q.x[i] == 0.5f && q.z[i] == 0.5f) red[0] = (q.color[i] >>> 16) & 255;
                });
        assertTrue("The emitted mesh must include the sole centre", red[0] >= 0);
        return red[0] / (double) ((TerrainContactsTest.top().color[0] >>> 16) & 255);
    }

    @Test public void softGroundDentFloorIsNotPaintedDark() {
        for (DeformationMaterial material : new DeformationMaterial[]{DeformationMaterial.SAND,
                DeformationMaterial.ORGANIC, DeformationMaterial.EARTH, DeformationMaterial.SNOW}) {
            double shade = centreShade(material);
            assertTrue(material + " floor should keep the block colour, got " + shade, shade >= 0.93);
        }
    }

    @Test public void saturationProtectsThinSurfacesAndZeroStrengthRestoresExactly() {
        List<SurfaceImprint> stamps = new ArrayList<>();
        for (int i = 0; i < 24; i++) stamps.add(new SurfaceImprint(0.5, 0.0625, 0.5, 0, 0.2, 2, i));
        double dent = SurfaceHeights.sample(0.5, 0.0625, 0.5, 0, DeformationMaterial.SNOW, stamps);
        assertTrue(0.0625 + dent > 0.02);
        List<SurfaceImprint> zero = Collections.singletonList(stamps.get(0).withStrength(0));
        assertEquals(0, SurfaceHeights.sample(0.5, 0.0625, 0.5, 0, DeformationMaterial.SNOW, zero), 0);
    }

    @Test public void mixedMaterialEdgesShareIdenticalHeights() {
        List<SurfaceImprint> stamps = Collections.singletonList(new SurfaceImprint(1, 1, 0.5, 35, 0.3, 1, 0));
        SurfaceHeights.Sampler heights = (x, y, z) -> SurfaceHeights.weight(x, 0)
                * SurfaceHeights.sample(x, y, z, 0, DeformationMaterial.SAND, stamps)
                + SurfaceHeights.weight(x, 1) * SurfaceHeights.sample(x, y, z, 0, DeformationMaterial.STONE, stamps);
        Map<Float, Float> edge = new HashMap<>();
        QuadDeformation.emit(TerrainContactsTest.top(), 0, 0, 0, DeformationMaterial.SAND, stamps, heights, q -> {
            for (int i = 0; i < 4; i++) if (q.x[i] == 1) edge.put(q.z[i], q.y[i]);
        });
        QuadDeformation.emit(TerrainContactsTest.top(), 1, 0, 0, DeformationMaterial.STONE, stamps, heights, q -> {
            for (int i = 0; i < 4; i++) if (q.x[i] == 0) {
                assertNotNull(edge.get(q.z[i]));
                assertEquals(edge.get(q.z[i]), q.y[i], 0.000001);
            }
        });
        assertFalse(edge.isEmpty());
    }
}
