package dev.footstepstrail.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** Forgified Fabric API's Indigo still has the method and fields the Indigo hook uses. */
public class IndigoHookTest {
    private static final String I = "net/fabricmc/fabric/impl/client/indigo/renderer/";

    @Test public void renderContextHasBufferQuadAndTransforms() throws Exception {
        ClassNode context = read(I + "render/AbstractRenderContext");
        assertTrue(context.methods.stream().anyMatch(m -> m.name.equals("bufferQuad")
                && m.desc.startsWith("(L" + I + "mesh/MutableQuadViewImpl;L") && m.desc.endsWith(";)V")));
        assertField(context, "matrix", "Lorg/joml/Matrix4f;");
        assertField(context, "normalMatrix", "Lorg/joml/Matrix3f;");
        assertField(context, "overlay", "I");
    }

    @Test public void blockContextExposesBlockPosAndState() throws Exception {
        assertField(read(I + "render/AbstractBlockRenderContext"), "blockInfo", "L" + I + "render/BlockRenderInfo;");
        ClassNode info = read(I + "render/BlockRenderInfo");
        assertTrue(info.fields.stream().anyMatch(f -> f.name.equals("blockPos")));
        assertTrue(info.fields.stream().anyMatch(f -> f.name.equals("blockState")));
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
