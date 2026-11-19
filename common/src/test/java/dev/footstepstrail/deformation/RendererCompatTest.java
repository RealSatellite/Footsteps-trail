package dev.footstepstrail.deformation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class RendererCompatTest {
    private static final String TARGET = "example.Renderer";

    private static RendererCompat.Environment env(String... mods) {
        Set<String> loaded = new HashSet<>(Arrays.asList(mods));
        return new RendererCompat.Environment() {
            @Override public boolean modLoaded(String id) { return loaded.contains(id); }
            @Override public boolean hasClass(String name) { return false; }
            @Override public void load(String name) {}
        };
    }

    private static RendererCompat.Hook sodium(String target) {
        return new RendererCompat.Hook("sodium", "Sodium", target);
    }

    @After public void vanilla() { TerrainSupport.hooked("vanilla"); }

    @Test public void vanillaKeepsTerrain() {
        RendererCompat.detect(env(), sodium(TARGET));
        assertTrue(TerrainSupport.available());
    }

    @Test public void attachedHookKeepsTerrain() {
        RendererCompat.attached(TARGET);
        RendererCompat.detect(env("sodium"), sodium(TARGET));
        assertTrue(TerrainSupport.available());
        assertEquals("Sodium", TerrainSupport.renderer());
    }

    @Test public void hookThatDidNotAttachFallsBack() {
        RendererCompat.detect(env("sodium"), sodium("example.ChangedRenderer"));
        assertFalse(TerrainSupport.available());
    }

    @Test public void rendererWithoutHookFallsBack() {
        RendererCompat.detect(env("embeddium"), sodium(TARGET));
        assertEquals("Embeddium", TerrainSupport.unsupportedRenderer());
    }

    @Test public void optifineAlwaysFallsBack() {
        RendererCompat.attached(TARGET);
        RendererCompat.detect(env("sodium", "optifabric"), sodium(TARGET));
        assertEquals("OptiFine", TerrainSupport.unsupportedRenderer());
    }

    @Test public void colorsRoundTripThroughSodiumOrder() {
        int argb = 0xff336699;
        assertEquals(0xff996633, SodiumQuads.swapRedBlue(argb));
        assertEquals(argb, SodiumQuads.abgrToShadedArgb(SodiumQuads.swapRedBlue(argb), 1f));
        assertEquals(0xff1a334d, SodiumQuads.shadeArgb(argb, 0.5f));
    }
}
