package dev.footstepstrail.fabric;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.InputStream;
import static org.junit.Assert.*;

/** The output lives on the superclass; block context belongs to the subclass. */
public class IndigoHookTest {
    @Test public void indigoHookAndAccessorsTargetTheDeclaringClasses() throws Exception {
        ClassNode output = read("AbstractRenderContext");
        assertTrue(output.methods.stream().anyMatch(m -> m.name.equals("bufferQuad") && m.desc.equals(
                "(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;Lnet/minecraft/client/render/VertexConsumer;)V")));
        assertTrue(output.fields.stream().anyMatch(f -> f.name.equals("matrix") && f.desc.equals("Lorg/joml/Matrix4f;")));
        assertTrue(output.fields.stream().anyMatch(f -> f.name.equals("normalMatrix") && f.desc.equals("Lorg/joml/Matrix3f;")));
        assertTrue(output.fields.stream().anyMatch(f -> f.name.equals("overlay") && f.desc.equals("I")));
        ClassNode block = read("AbstractBlockRenderContext");
        assertEquals(output.name, block.superName);
        assertTrue(block.fields.stream().anyMatch(f -> f.name.equals("blockInfo")));
    }

    private ClassNode read(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "net/fabricmc/fabric/impl/client/indigo/renderer/render/" + name + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
