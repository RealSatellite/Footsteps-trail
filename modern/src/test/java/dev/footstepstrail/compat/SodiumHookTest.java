package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The Sodium hook's injection points exist, with the signatures it uses, in the Sodium build it targets. */
public class SodiumHookTest {
    private static final String QUAD = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;";

    @Test public void blockRendererHasTheHookedMethods() throws Exception {
        ClassNode renderer = read("net/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer");
        MethodNode buffer = method(renderer, "bufferQuad",
                "(" + QUAD + "[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V");
        assertNotNull(buffer);
        assertTrue((buffer.access & Opcodes.ACC_PRIVATE) != 0);
        assertEquals(1, renderer.methods.stream().filter(m -> m.name.equals("renderModel")).count());
        assertNotNull(method(renderer, "renderModel",
                "(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"));
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
