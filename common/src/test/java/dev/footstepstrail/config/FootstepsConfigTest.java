package dev.footstepstrail.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FootstepsConfigTest {
    private static FootstepsConfig parse(String json) {
        FootstepsConfig config = new Gson().fromJson(json, FootstepsConfig.class);
        config.migrate();
        return config;
    }

    @Test public void unsupportedRendererMakesSurfaceTheDefault() {
        FootstepsConfig config = parse("{}");
        try {
            dev.footstepstrail.deformation.TerrainSupport.unsupported("OptiFine");
            assertEquals("The settings show what is drawn", FootstepType.SURFACE, config.getType());
            assertTrue(config.isSurface());
            assertFalse(config.isTerrain());
            assertFalse(config.isTerrainUnsupported());
            config.setType(FootstepType.TERRAIN);
            assertEquals("A chosen type is used as is", FootstepType.TERRAIN, config.getType());
            assertTrue(config.isTerrain());
            assertFalse(config.isSurface());
            assertTrue("The player is told no terrain prints appear", config.isTerrainUnsupported());
            config.setType(FootstepType.CLASSIC);
            assertFalse(config.isTerrainUnsupported());
        } finally {
            dev.footstepstrail.deformation.TerrainSupport.hooked("vanilla");
        }
    }

    @Test public void existingConfigsWithTheOldDefaultFollowTheRenderer() {
        FootstepsConfig config = parse("{\"type\":\"TERRAIN\"}");
        try {
            dev.footstepstrail.deformation.TerrainSupport.unsupported("OptiFine");
            assertEquals(FootstepType.SURFACE, config.getType());
        } finally {
            dev.footstepstrail.deformation.TerrainSupport.hooked("vanilla");
        }
        assertEquals(FootstepType.TERRAIN, config.getType());
    }

    @Test public void chosenTypeSurvivesSaveAndLoad() {
        FootstepsConfig config = parse("{}");
        config.setType(FootstepType.TERRAIN);
        FootstepsConfig copy = parse(new Gson().toJson(config));
        try {
            dev.footstepstrail.deformation.TerrainSupport.unsupported("OptiFine");
            assertEquals(FootstepType.TERRAIN, copy.getType());
        } finally {
            dev.footstepstrail.deformation.TerrainSupport.hooked("vanilla");
        }
    }

    @Test public void freshConfigDefaultsToTerrain() {
        FootstepsConfig config = parse("{}");
        assertEquals(FootstepType.TERRAIN, config.getType());
        assertTrue(config.isTerrain());
        assertFalse(config.isGlow());
    }

    @Test public void legacyRealisticTrueBecomesTerrain() {
        assertEquals(FootstepType.TERRAIN, parse("{\"realistic\":true,\"shape\":\"PAW\"}").getType());
    }

    @Test public void legacyRealisticFalseBecomesClassicAndKeepsShape() {
        FootstepsConfig config = parse("{\"realistic\":false,\"shape\":\"PAW\"}");
        assertEquals(FootstepType.CLASSIC, config.getType());
        assertEquals(FootstepShape.PAW, config.getShape());
    }

    @Test public void removedRealisticShapeFallsBackToSquare() {
        FootstepsConfig config = parse("{\"shape\":\"REALISTIC\"}");
        assertEquals(FootstepShape.SQUARE, config.getShape());
        assertEquals(FootstepType.TERRAIN, config.getType());
    }

    @Test public void legacyFieldIsNotWrittenBack() {
        JsonObject saved = new Gson().toJsonTree(parse("{\"realistic\":false}")).getAsJsonObject();
        assertFalse(saved.has("realistic"));
        assertEquals("CLASSIC", saved.get("type").getAsString());
    }

    @Test public void typesAreExclusive() {
        FootstepsConfig config = parse("{}");
        config.setType(FootstepType.SURFACE);
        assertTrue(config.isSurface());
        assertFalse(config.isTerrain());
        assertFalse(config.isClassic());
    }

    @Test public void outOfRangeValuesAreClamped() {
        FootstepsConfig config = parse("{\"maxSteps\":99999,\"markerSize\":-3,\"maxDurationSeconds\":0}");
        assertEquals(500, config.getMaxSteps());
        assertEquals(0.05f, config.getMarkerSize(), 1e-6);
        assertEquals(1, config.getMaxDurationSeconds());
    }

    @Test public void missingAndUnknownKeysKeepDefaults() {
        FootstepsConfig config = parse("{\"someFutureKey\":42,\"placementMode\":\"CONTINUOUS\"}");
        assertEquals(50, config.getMaxSteps());
        assertEquals(TrackingMode.COMBINED, config.getTrackingMode());
        assertFalse(config.migrate());
    }
}
