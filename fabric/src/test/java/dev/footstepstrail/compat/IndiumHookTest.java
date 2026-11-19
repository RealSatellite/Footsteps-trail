package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The Indium hook's injection point and fields exist in the build it targets. */
public class IndiumHookTest {
    private static final String I = "link/infra/indium/renderer/";
    private static final String S = "me/jellysquid/mods/sodium/client/";

    @Test public void terrainContextHasTheHookedMethodAndFields() throws Exception {
        ClassNode context = read(I + "render/TerrainRenderContext");
        String desc = "(L" + I + "mesh/MutableQuadViewImpl;L" + S + "render/chunk/terrain/material/Material;)V";
        assertTrue(context.methods.stream().anyMatch(m -> m.name.equals("bufferQuad") && m.desc.equals(desc)));
        assertField(context, "vertices", "[L" + S + "render/chunk/vertex/format/ChunkVertexEncoder$Vertex;");
        assertField(context, "buffers", "L" + S + "render/chunk/compile/ChunkBuildBuffers;");
        assertField(context, "origin", "Lorg/joml/Vector3fc;");
        assertField(context, "modelOffset", "Lnet/minecraft/util/math/Vec3d;");
    }

    @Test public void blockContextHasBlockInfo() throws Exception {
        assertField(read(I + "render/AbstractBlockRenderContext"), "blockInfo", "L" + I + "render/BlockRenderInfo;");
    }

    private static void assertField(ClassNode node, String name, String desc) {
        assertTrue(name, node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc)));
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
