package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The Sodium hook's injection points and quad setters exist in the Sodium build it targets. */
public class SodiumHookTest {
    private static final String QUAD = "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;";

    @Test public void blockRendererHasTheHookedMethods() throws Exception {
        ClassNode renderer = read("net/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer");
        MethodNode buffer = method(renderer, "bufferQuad",
                "(" + QUAD + "[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V");
        assertNotNull(buffer);
        assertTrue((buffer.access & Opcodes.ACC_PRIVATE) != 0);
        assertEquals(1, renderer.methods.stream().filter(m -> m.name.equals("renderModel")).count());
        assertNotNull(method(renderer, "renderModel", "(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;"
                + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)V"));
    }

    @Test public void quadHasTheAccessorsTheHookUses() throws Exception {
        ClassNode quad = read("net/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl");
        String self = "(I" ;
        assertNotNull(method(quad, "pos", self + "FFF)" + QUAD));
        assertNotNull(method(quad, "uv", self + "FF)" + QUAD));
        assertNotNull(method(quad, "color", self + "I)" + QUAD));
        assertNotNull(method(quad, "lightmap", self + "I)" + QUAD));
        ClassNode view = read(quad.superName);
        assertNotNull(method(view, "x", "(I)F"));
        assertNotNull(method(view, "u", "(I)F"));
        assertNotNull(method(view, "color", "(I)I"));
        assertNotNull(method(view, "lightmap", "(I)I"));
        assertNotNull(method(view, "lightFace", "()Lnet/minecraft/util/math/Direction;"));
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
