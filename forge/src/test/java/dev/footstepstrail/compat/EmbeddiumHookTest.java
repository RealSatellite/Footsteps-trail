package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The Embeddium hook's injection point and fields exist in the build it targets. */
public class EmbeddiumHookTest {
    private static final String S = "me/jellysquid/mods/sodium/client/";

    @Test public void blockRendererHasTheHookedMethodAndFields() throws Exception {
        ClassNode renderer = read(S + "render/chunk/compile/pipeline/BlockRenderer");
        String desc = "(L" + S + "render/chunk/compile/pipeline/BlockRenderContext;L" + S
                + "render/chunk/compile/buffers/ChunkModelBuilder;Lnet/minecraft/util/math/Vec3d;L" + S
                + "render/chunk/terrain/material/Material;L" + S + "model/quad/BakedQuadView;[IL" + S
                + "model/light/data/QuadLightData;)V";
        assertTrue(renderer.methods.stream().anyMatch(m -> m.name.equals("writeGeometry") && m.desc.equals(desc)));
        assertTrue(renderer.fields.stream().anyMatch(f -> f.name.equals("vertices")
                && f.desc.equals("[L" + S + "render/chunk/vertex/format/ChunkVertexEncoder$Vertex;")));
        assertTrue(renderer.fields.stream().anyMatch(f -> f.name.equals("colorEncoder")
                && f.desc.equals("Lorg/embeddedt/embeddium/render/chunk/ChunkColorWriter;")));
    }

    private ClassNode read(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
