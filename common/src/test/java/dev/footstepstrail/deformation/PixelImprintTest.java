package dev.footstepstrail.deformation;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PixelImprintTest {
    private static final float TOP = 1f, FLOOR = 1f - 1 / 16f;
    private static final float HALF = 1f - 0.5f / 16f, RIM = 1f + 0.25f / 16f;

    private static List<TerrainQuad> emit(TerrainQuad source, List<SurfaceImprint> stamps) {
        return emit(source, stamps, DeformationMaterial.STONE, 1);
    }

    private static List<TerrainQuad> emit(TerrainQuad source, List<SurfaceImprint> stamps,
            DeformationMaterial material, double thickness) {
        List<TerrainQuad> out = new ArrayList<>();
        PixelImprint.emit(source, 0, 0, 0, stamps, (x, y, z) -> {
            // Single-block world: the blocks around it draw no pixel prints.
            if (x < 0 || x >= 1 || z < 0 || z >= 1) return null;
            return PixelImprint.level(stamps, material, thickness, x, y, z);
        }, out::add);
        return out;
    }

    private static boolean cut(List<SurfaceImprint> stamps, double x, double z) {
        return PixelImprint.level(stamps, DeformationMaterial.STONE, 1, x, 1, z) < 0;
    }

    private static List<SurfaceImprint> stamp(double strength, double yaw) {
        return Collections.singletonList(new SurfaceImprint(0.5, 1, 0.5, yaw, 0.2, strength, 0));
    }

    private static boolean horizontal(TerrainQuad q) {
        return q.y[0] == q.y[1] && q.y[1] == q.y[2] && q.y[2] == q.y[3];
    }

    private static double area(TerrainQuad q) {
        double ax = q.x[1] - q.x[0], az = q.z[1] - q.z[0], bx = q.x[3] - q.x[0], bz = q.z[3] - q.z[0];
        return Math.abs(ax * bz - az * bx);
    }

    private static Set<Float> floorHeights(List<TerrainQuad> quads) {
        Set<Float> heights = new HashSet<>();
        for (TerrainQuad q : quads) if (horizontal(q)) heights.add(q.y[0]);
        return heights;
    }

    /** The texels a stamp cuts, as rows of '#' and '.', toe (+z) first, x increasing left to right. */
    private static List<String> sprite(SurfaceImprint stamp) {
        List<String> rows = new ArrayList<>();
        for (int tz = 15; tz >= 0; tz--) {
            StringBuilder row = new StringBuilder();
            for (int tx = 0; tx < 16; tx++) {
                boolean cut = cut(Collections.singletonList(stamp), (tx + 0.5) / 16, (tz + 0.5) / 16);
                row.append(cut ? '#' : '.');
            }
            if (row.indexOf("#") >= 0 || !rows.isEmpty()) rows.add(row.toString());
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).indexOf('#') < 0) rows.remove(rows.size() - 1);
        return rows;
    }

    private static final List<String> LEFT_BOOT = Arrays.asList(
            ".......##.......",
            "......####......",
            "......####......",
            "......###.......",
            "................",
            ".......##.......",
            ".......##.......");

    @Test public void printIsAPixelBootNotABox() {
        assertEquals(LEFT_BOOT, sprite(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 0)));
        List<String> right = sprite(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 1));
        assertEquals("Right feet mirror the arch notch", "......####......", right.get(2));
        assertEquals(".......###......", right.get(3));
        List<String> big = sprite(new SurfaceImprint(0.5, 1, 0.5, 0, 0.4, 1, 0));
        assertEquals("The print scales with the print size setting", 14, big.size());
    }

    @Test public void fadingPrintsKeepTheirOutline() {
        for (double strength : new double[] {0.9, 0.4, 0.1, 0.05})
            assertEquals("strength " + strength, LEFT_BOOT, sprite(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, strength, 0)));
    }

    @Test public void recessIsOneTexelDeepOnTheTexelGrid() {
        List<TerrainQuad> out = emit(TerrainContactsTest.top(), stamp(1, 30));
        int floors = 0;
        double covered = 0;
        for (TerrainQuad q : out) {
            if (!horizontal(q)) continue;
            covered += area(q);
            assertTrue(q.y[0] == TOP || q.y[0] == FLOOR || q.y[0] == RIM);
            if (q.y[0] != FLOOR) continue;
            floors++;
            for (int i = 0; i < 4; i++) {
                assertEquals("x on the texel grid", 0, (q.x[i] * 16) % 1, 1e-4);
                assertEquals("z on the texel grid", 0, (q.z[i] * 16) % 1, 1e-4);
                assertEquals("Texture follows position", q.x[i], q.u[i], 1e-5);
                assertEquals(q.z[i], q.v[i], 1e-5);
            }
        }
        assertTrue("A full-strength sole cuts a print of several texels, got " + floors, floors >= 9);
        assertEquals("Top and floor pieces tile the face exactly once", 1, covered, 1e-5);
    }

    @Test public void everyWallIsVerticalAndFacesIntoTheRecess() {
        List<TerrainQuad> out = emit(TerrainContactsTest.top(), stamp(1, 0));
        int walls = 0;
        for (TerrainQuad q : out) {
            // Walls into the recess; the rim's outer walls face away from it.
            if (horizontal(q) || Math.min(q.y[0], q.y[2]) != FLOOR) continue;
            walls++;
            int tops = 0, floors = 0;
            for (int i = 0; i < 4; i++) { if (q.y[i] == TOP || q.y[i] == RIM) tops++; if (q.y[i] == FLOOR) floors++; }
            assertEquals("Walls run from the surface or rim to the floor", 2, tops);
            assertEquals(2, floors);
            // Vertical: the two edges at each end share x and z.
            Set<String> columns = new HashSet<>();
            for (int i = 0; i < 4; i++) columns.add(Math.round(q.x[i] * 1e5) + "," + Math.round(q.z[i] * 1e5));
            assertEquals(2, columns.size());
            // Front normal with the source quad's winding (source normal = cross(e1, e3) faces up).
            double ax = q.x[1] - q.x[0], ay = q.y[1] - q.y[0], az = q.z[1] - q.z[0];
            double bx = q.x[3] - q.x[0], by = q.y[3] - q.y[0], bz = q.z[3] - q.z[0];
            TerrainQuad s = TerrainContactsTest.top();
            double sy = (s.z[1] - s.z[0]) * (s.x[3] - s.x[0]) - (s.x[1] - s.x[0]) * (s.z[3] - s.z[0]);
            double sign = sy >= 0 ? 1 : -1;
            double nx = (ay * bz - az * by) * sign, nz = (ax * by - ay * bx) * sign;
            double cx = (q.x[0] + q.x[1] + q.x[2] + q.x[3]) / 4, cz = (q.z[0] + q.z[1] + q.z[2] + q.z[3]) / 4;
            double mx = cx + nx * 1e-3 / Math.hypot(nx, nz);
            double mz = cz + nz * 1e-3 / Math.hypot(nx, nz);
            assertTrue("Wall must face the recessed texel", cut(stamp(1, 0), mx, mz));
            int red = (q.color[0] >>> 16) & 255;
            int expected = (int) (0xab * (nx != 0 ? PixelImprint.WALL_SHADE_X : PixelImprint.WALL_SHADE_Z));
            assertEquals("Walls use vanilla side-face shading", expected, red);
        }
        assertTrue("A recess needs walls", walls >= 4);
    }

    @Test public void fadingPrintsGetShallower() {
        Set<Float> faded = floorHeights(emit(TerrainContactsTest.top(), stamp(0.25, 0)));
        assertTrue(faded.toString(), faded.contains(HALF));
        assertFalse(faded.contains(FLOOR));
        assertEquals("Spent prints leave the face untouched", 1, emit(TerrainContactsTest.top(), stamp(0, 0)).size());
    }

    @Test public void softGroundIsCutHalfATexelWithALowRim() {
        List<SurfaceImprint> stamps = stamp(1, 0);
        List<TerrainQuad> out = emit(TerrainContactsTest.top(), stamps, DeformationMaterial.SAND, 1);
        assertEquals(new HashSet<>(Arrays.asList(TOP, HALF, RIM)), floorHeights(out));
        for (TerrainQuad q : out) {
            if (!horizontal(q)) continue;
            double cx = (q.x[0] + q.x[2]) / 2, cz = (q.z[0] + q.z[2]) / 2;
            if (q.y[0] == HALF) {
                assertEquals("A shallow cut is shaded less", (int) (0xab * (1 - (1 - PixelImprint.FLOOR_SHADE) * 0.5)),
                        (q.color[0] >>> 16) & 255);
            }
            if (q.y[0] != RIM) continue;
            assertFalse("The rim is outside the sole", cut(stamps, cx, cz));
            boolean next = false;
            for (int d = -1; d <= 1; d += 2)
                next |= cut(stamps, cx + d / 16.0, cz) || cut(stamps, cx, cz + d / 16.0);
            assertTrue("The rim follows the outline", next);
        }
        assertTrue("Grass and dirt get the rim too",
                floorHeights(emit(TerrainContactsTest.top(), stamps, DeformationMaterial.EARTH, 1)).contains(RIM));
        assertTrue("Hard blocks get the rim too", floorHeights(emit(TerrainContactsTest.top(), stamps)).contains(RIM));
    }

    @Test public void thinLayersAreNeverCutThrough() {
        // A one-texel layer (carpet) takes a half-texel print, and half of it always stays.
        List<TerrainQuad> carpet = emit(TerrainContactsTest.top(), stamp(1, 0), DeformationMaterial.ORGANIC, 1 / 16.0);
        assertTrue("A carpet is marked", carpet.size() > 1);
        for (TerrainQuad q : carpet) {
            if (horizontal(q)) assertTrue("Half the carpet stays", q.y[0] >= TOP - 0.5 * PixelImprint.PIXEL - 1e-6);
        }
        assertTrue(floorHeights(emit(TerrainContactsTest.top(), stamp(1, 0), DeformationMaterial.SNOW, 2 / 16.0))
                .contains(HALF));
    }

    @Test public void stepsAlwaysGetAWall() {
        // Every height change between neighbouring texels needs exactly one wall, or a gap shows.
        List<TerrainQuad> out = emit(TerrainContactsTest.top(), stamp(1, 0), DeformationMaterial.SAND, 1);
        Map<String, Float> cells = new HashMap<>();
        for (TerrainQuad q : out) {
            if (!horizontal(q)) continue;
            int tx = (int) Math.floor((q.x[0] + q.x[2]) / 2 * 16), tz = (int) Math.floor((q.z[0] + q.z[2]) / 2 * 16);
            cells.put(tx + "," + tz, q.y[0]);
        }
        int steps = 0;
        for (Map.Entry<String, Float> e : cells.entrySet()) {
            String[] p = e.getKey().split(",");
            int tx = Integer.parseInt(p[0]), tz = Integer.parseInt(p[1]);
            Float east = cells.get((tx + 1) + "," + tz), south = cells.get(tx + "," + (tz + 1));
            if (east != null && !east.equals(e.getValue())) steps++;
            if (south != null && !south.equals(e.getValue())) steps++;
        }
        int walls = 0;
        for (TerrainQuad q : out) if (!horizontal(q)) walls++;
        assertTrue(steps > 0);
        assertEquals(steps, walls);
    }

    @Test public void gelIsNotAPixelMaterial() {
        assertFalse(DeformationMaterial.GEL.isPixel());
        assertTrue(DeformationMaterial.SAND.isPixel());
        assertTrue(DeformationMaterial.STONE.isPixel());
    }

    @Test public void sideFacesAndUntouchedTopsPassThrough() {
        TerrainQuad side = new TerrainQuad();
        float[] x = {0, 1, 1, 0}, y = {1, 1, 0, 0};
        for (int i = 0; i < 4; i++) { side.x[i] = x[i]; side.y[i] = y[i]; side.z[i] = 0; side.color[i] = -1; }
        List<TerrainQuad> out = emit(side, stamp(1, 0));
        assertEquals(1, out.size());
        assertSame(side, out.get(0));
        List<SurfaceImprint> far = Collections.singletonList(new SurfaceImprint(0.5, 3, 0.5, 0, 0.2, 1, 0));
        assertEquals("A stamp at another height does not cut this face", 1, emit(TerrainContactsTest.top(), far).size());
    }
}
