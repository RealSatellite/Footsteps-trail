package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The Sodium and Embeddium hooks' injection points exist in the builds they target. */
public class RendererHookTest {
    private static final String SODIUM = "net/caffeinemc/mods/sodium/client/";
    private static final String QUAD = "L" + SODIUM + "render/frapi/mesh/MutableQuadViewImpl;";
    private static final String EMBEDDIUM = "org/embeddedt/embeddium/impl/";

    @Test public void sodiumBlockRendererHasTheHookedMethods() throws Exception {
        ClassNode renderer = read(SODIUM + "render/chunk/compile/pipeline/BlockRenderer");
        MethodNode buffer = method(renderer, "bufferQuad",
                "(" + QUAD + "[FL" + SODIUM + "render/chunk/terrain/material/Material;)V");
        assertNotNull(buffer);
        assertTrue((buffer.access & Opcodes.ACC_PRIVATE) != 0);
        assertNotNull(method(renderer, "renderModel", "(Lnet/minecraft/client/resources/model/BakedModel;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"));
        ClassNode quad = read(SODIUM + "render/frapi/mesh/MutableQuadViewImpl");
        assertNotNull(method(quad, "pos", "(IFFF)" + QUAD));
        assertNotNull(method(quad, "uv", "(IFF)" + QUAD));
        assertNotNull(method(quad, "color", "(II)" + QUAD));
        assertNotNull(method(quad, "lightmap", "(II)" + QUAD));
        ClassNode view = read(quad.superName);
        assertNotNull(method(view, "lightFace", "()Lnet/minecraft/core/Direction;"));
        assertNotNull(method(view, "lightmap", "(I)I"));
    }

    @Test public void embeddiumBlockRendererHasTheHookedMethodAndFields() throws Exception {
        ClassNode renderer = read(EMBEDDIUM + "render/chunk/compile/pipeline/BlockRenderer");
        assertNotNull(method(renderer, "writeGeometry", "(Lorg/embeddedt/embeddium/api/render/chunk/BlockRenderContext;"
                + "L" + EMBEDDIUM + "render/chunk/compile/buffers/ChunkModelBuilder;Lnet/minecraft/world/phys/Vec3;"
                + "L" + EMBEDDIUM + "render/chunk/terrain/material/Material;L" + EMBEDDIUM + "model/quad/BakedQuadView;"
                + "[IL" + EMBEDDIUM + "model/light/data/QuadLightData;)V"));
        assertTrue(renderer.fields.stream().anyMatch(f -> f.name.equals("vertices")
                && f.desc.equals("[L" + EMBEDDIUM + "render/chunk/vertex/format/ChunkVertexEncoder$Vertex;")));
        assertTrue(renderer.fields.stream().anyMatch(f -> f.name.equals("colorEncoder")
                && f.desc.equals("L" + EMBEDDIUM + "render/chunk/ChunkColorWriter;")));
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
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
