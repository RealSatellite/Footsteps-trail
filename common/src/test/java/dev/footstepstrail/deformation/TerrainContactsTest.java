package dev.footstepstrail.deformation;

import com.google.gson.Gson;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.Footstep;
import dev.footstepstrail.tracking.FootstepsTracker;
import org.junit.Before;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** These contracts execute against the same core in all five version modules. */
public class TerrainContactsTest {
    private TerrainContacts<Object> terrain;
    private FakeWorld world;
    private FootstepsConfig config;
    private long now;

    @Before public void setup() {
        terrain = new TerrainContacts<>();
        world = new FakeWorld();
        config = new Gson().fromJson("{}", FootstepsConfig.class);
        config.setEnabled(true);
        config.setType(FootstepType.TERRAIN);
        config.setEnableAlternatingFeet(false);
        config.setMinDistanceForStep(0.5);
        config.setTrackingMode(TrackingMode.STEPS);
        config.setMaxSteps(500);
        now = System.currentTimeMillis();
        FootstepsTracker.getInstance().clear();
    }

    /** Airborne ticks are at jump height, so touching down again is a real landing. */
    private void tick(double x, boolean ground) {
        terrain.tick(world, new TerrainContacts.Player(x, ground ? 1 : 1.6, 0.5, 0, ground, false, false), config, now += 150);
    }

    @Test public void contactsUseTravelStrideAndLandingPressure() {
        tick(0.5, true);
        assertEquals(1, FootstepsTracker.getInstance().getCount());
        tick(0.5, true);
        assertEquals(1, FootstepsTracker.getInstance().getCount());
        tick(0.5, false);
        tick(0.5, true);
        assertEquals("Landing records both feet", 3, FootstepsTracker.getInstance().getCount());
        tick(10.5, true);
        assertEquals("Teleporting is not a stride", 3, FootstepsTracker.getInstance().getCount());
    }

    /**
     * Walking on slime makes the player hop a few hundredths of a block every
     * other tick. Those hops are not landings: the trail must get the same
     * number of prints per distance as on solid ground, or it burns through
     * the step budget and older prints vanish early.
     */
    @Test public void slimeMicroBouncesAreNotLandings() {
        int onGround = walk(DeformationMaterial.SAND, false);
        int onSlime = walk(DeformationMaterial.GEL, true);
        assertTrue("expected ~" + onGround + " prints on slime, got " + onSlime,
                Math.abs(onSlime - onGround) <= 2);
    }

    private int walk(DeformationMaterial material, boolean bouncing) {
        FootstepsTracker.getInstance().clear();
        terrain = new TerrainContacts<>();
        world.material = material;
        double x = 0.5;
        for (int i = 0; i < 100; i++) {
            boolean ground = !bouncing || i % 2 == 0;
            terrain.tick(world, new TerrainContacts.Player(x, ground ? 1 : 1.04, 0.5, 0, ground, false, false),
                    config, now += 50);
            x += 0.2;
        }
        return FootstepsTracker.getInstance().getCount();
    }

    @Test public void alternatingOffsetIsResolvedOnceAndStealthPreventsContacts() {
        config.setEnableAlternatingFeet(true);
        tick(0.5, true);
        Footstep first = FootstepsTracker.getInstance().getFootsteps().next();
        assertEquals(0.5 - config.getAlternatingFootOffset(), first.getX(), 0.000001);
        terrain.tick(world, new TerrainContacts.Player(1.5, 1, 0.5, 0, true, false, true), config, now += 150);
        assertEquals(1, FootstepsTracker.getInstance().getCount());
    }

    @Test public void clearingAndDisablingRestoreNeighbourSections() {
        tick(15.99, true);
        assertNotNull(terrain.surface(15, 0, 0, world.state));
        world.dirty.clear();
        FootstepsTracker.getInstance().clear();
        tick(15.99, true);
        assertNull(terrain.surface(15, 0, 0, world.state));
        assertTrue(world.dirty.contains(new TerrainContacts.Position(0, 0, 0)));
        assertTrue(world.dirty.contains(new TerrainContacts.Position(1, 0, 0)));
        tick(15.99, false);
        tick(15.99, true);
        config.setEnabled(false);
        tick(15.99, true);
        assertNull(terrain.surface(15, 0, 0, world.state));
    }

    @Test public void replacedAndUnloadedSurfacesNeverResurrect() {
        tick(0.5, true);
        Object original = world.state;
        world.state = new Object();
        tick(0.5, true);
        world.state = original;
        tick(0.5, true);
        assertNull(terrain.surface(0, 0, 0, world.state));
        tick(0.5, false);
        tick(0.5, true);
        world.loaded = false;
        tick(0.5, true);
        world.loaded = true;
        tick(0.5, true);
        assertNull(terrain.surface(0, 0, 0, world.state));
    }

    @Test public void disconnectedWorldAndModeSwitchClearGeometry() {
        tick(0.5, true);
        terrain.tick(null, null, config, now += 150);
        assertNull(terrain.surface(0, 0, 0, world.state));
        assertEquals(0, FootstepsTracker.getInstance().getCount());
        tick(0.5, true);
        config.setType(FootstepType.CLASSIC);
        assertFalse(terrain.tick(world, new TerrainContacts.Player(0.5, 1, 0.5, 0, true, false, false), config, now += 150));
        assertNull(terrain.surface(0, 0, 0, world.state));
    }

    @Test public void snapshotIsImmutableWhileNextTickRestoresTerrain() {
        tick(0.5, true);
        TerrainContacts.Surface captured = terrain.surface(0, 0, 0, world.state);
        config.setEnabled(false);
        tick(0.5, true);
        assertNull(terrain.surface(0, 0, 0, world.state));
        List<TerrainQuad> quads = new ArrayList<>();
        captured.emit(top(), quads::add);
        assertTrue(quads.stream().anyMatch(q -> q.y[0] < 0.99));
    }

    @Test public void timeModeAndContactStorageHaveAHardCeiling() {
        config.setTrackingMode(TrackingMode.TIME);
        config.setMaxDurationSeconds(300);
        for (int i = 0; i < 550; i++) tick(i * 0.8 + 0.5, true);
        assertEquals(500, FootstepsTracker.getInstance().getCount());
        assertNull(terrain.surface(0, 0, 0, world.state));
    }

    /** Hard blocks get pixel prints: a recess exactly one texel deep and a low raised edge. */
    @Test public void hardBlocksAreCutOnePixelDeep() {
        world.material = DeformationMaterial.STONE;
        tick(0.5, true);
        TerrainContacts.Surface surface = terrain.surface(0, 0, 0, world.state);
        assertNotNull(surface);
        Set<Float> heights = new HashSet<>();
        surface.emit(top(), q -> { for (int i = 0; i < 4; i++) heights.add(q.y[i]); });
        assertEquals(new HashSet<>(Arrays.asList(1f, 1f - 1 / 16f, 1f + 0.25f / 16f)), heights);
    }

    /** Sand gets pixel prints too, cut half a texel deep. */
    @Test public void sandGetsAShallowPixelPrint() {
        tick(0.5, true);
        TerrainContacts.Surface surface = terrain.surface(0, 0, 0, world.state);
        assertNotNull(surface);
        Set<Float> heights = new HashSet<>();
        surface.emit(top(), q -> { for (int i = 0; i < 4; i++) heights.add(q.y[i]); });
        assertTrue(heights.toString(), heights.contains(1f - 0.5f / 16f));
        for (float h : heights) assertTrue("Never deeper than half a texel: " + h, h >= 1f - 0.5f / 16f - 1e-6);
    }

    /** Smooth slime meets a pixel-print block at the block's flat top height, so no seam opens. */
    @Test public void softGroundSettlesFlatAtAHardNeighbour() {
        world.material = DeformationMaterial.GEL;
        world.materials.put(new TerrainContacts.Position(1, 0, 0), DeformationMaterial.STONE);
        tick(1.0, true);
        TerrainContacts.Surface sand = terrain.surface(0, 0, 0, world.state);
        assertNotNull(sand);
        float[] edge = {Float.NaN, Float.NaN};
        sand.emit(top(), q -> {
            for (int i = 0; i < 4; i++) {
                if (q.x[i] == 1) edge[0] = Float.isNaN(edge[0]) ? q.y[i] : Math.min(edge[0], q.y[i]);
                if (q.x[i] == 1) edge[1] = Float.isNaN(edge[1]) ? q.y[i] : Math.max(edge[1], q.y[i]);
            }
        });
        assertEquals("Slime edge touching stone stays at the block top", 1, edge[0], 0.000001);
        assertEquals(1, edge[1], 0.000001);
    }

    @Test public void hardScuffsAreOptional() {
        world.material = DeformationMaterial.STONE;
        config.setHardSurfaceScuffs(false);
        tick(0.5, true);
        assertNull(terrain.surface(0, 0, 0, world.state));
        config.setHardSurfaceScuffs(true);
        tick(0.5, true);
        assertNotNull(terrain.surface(0, 0, 0, world.state));
    }

    /** Slime/honey prints follow the normal trail lifetime like every other surface. */
    @Test public void gelPrintsLastAsLongAsSandPrints() {
        config.setTrackingMode(TrackingMode.TIME);
        config.setMaxDurationSeconds(30);
        for (DeformationMaterial material : new DeformationMaterial[] {
                DeformationMaterial.SAND, DeformationMaterial.GEL }) {
            FootstepsTracker.getInstance().clear();
            terrain = new TerrainContacts<>();
            world.material = material;
            now = System.currentTimeMillis(); // prints are stamped with the real clock
            tick(0.5, true);
            now += 15000;
            tick(0.5, true);
            assertNotNull(material.name() + " still visible at 15 s", terrain.surface(0, 0, 0, world.state));
            now += 20000;
            tick(0.5, true);
            assertNull(material.name() + " gone after the trail duration", terrain.surface(0, 0, 0, world.state));
        }
    }

    @Test public void slimeAndHoneyShareOneStyle() {
        assertEquals(DeformationMaterial.GEL, DeformationMaterial.resolve("minecraft:slime_block",
                SurfaceProfile.HARD, null));
        assertEquals(DeformationMaterial.GEL, DeformationMaterial.resolve("minecraft:honey_block",
                SurfaceProfile.HARD, null));
        // Configs written before the merge still resolve.
        assertEquals(DeformationMaterial.GEL, DeformationMaterial.byName("slime"));
        assertEquals(DeformationMaterial.GEL, DeformationMaterial.byName("HONEY"));
    }

    @Test public void gelPrintIsARoundSmoothSquashThatStaysInsideItsRadius() {
        SurfaceImprint gel = new SurfaceImprint(0, 1, 0, 0, 0.2, 1, 0, DeformationMaterial.GEL.softness);
        SurfaceImprint boot = new SurfaceImprint(0, 1, 0, 0, 0.2, 1, 0);
        // At the sole's corner the boot is still at full depth; the gel pad is rounded off.
        double cx = 0.2 * 0.55, cz = 0.2 * 0.62;
        double bootCorner = boot.height(cx, cz, DeformationMaterial.SAND) / boot.height(0, 0, DeformationMaterial.SAND);
        double gelCorner = gel.height(cx, cz, DeformationMaterial.GEL) / gel.height(0, 0, DeformationMaterial.GEL);
        assertTrue(gelCorner < bootCorner);
        double outside = gel.radius() + 0.001;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            assertEquals(0, gel.height(Math.cos(a) * outside, Math.sin(a) * outside, DeformationMaterial.GEL), 0);
        }
    }

    static TerrainQuad top() {
        TerrainQuad quad = new TerrainQuad();
        float[] x = {0, 0, 1, 1}, z = {0, 1, 1, 0};
        for (int i = 0; i < 4; i++) {
            quad.x[i] = quad.u[i] = x[i]; quad.z[i] = quad.v[i] = z[i]; quad.y[i] = 1;
            quad.color[i] = 0xffabcdef; quad.light[i] = 0x00f000f0;
        }
        return quad;
    }

    private static final class FakeWorld implements TerrainContacts.World<Object> {
        Object state = new Object();
        boolean loaded = true;
        DeformationMaterial material = DeformationMaterial.SAND;
        /** Per-block materials; blocks not listed use {@link #material}. */
        final Map<TerrainContacts.Position, DeformationMaterial> materials = new HashMap<>();
        final Set<TerrainContacts.Position> dirty = new HashSet<>();
        public TerrainContacts.Hit floor(double x, double y, double z) { return new TerrainContacts.Hit((int) Math.floor(x), 0, (int) Math.floor(z), 1); }
        public Object state(TerrainContacts.Position pos) { return state; }
        public boolean loaded(TerrainContacts.Position pos) { return loaded; }
        public String dimension() { return "test:world"; }
        public SurfaceProfile profile(Object state) { return SurfaceProfile.LOOSE_GRANULAR; }
        public int color(Object state, TerrainContacts.Position pos) { return 0xffffff; }
        public DeformationMaterial material(Object state, TerrainContacts.Position pos) { return materials.getOrDefault(pos, material); }
        public void particle(Object state, double x, double y, double z, double vx, double vy, double vz) {}
        public void dirtySection(int x, int y, int z) { dirty.add(new TerrainContacts.Position(x, y, z)); }
    }
}
