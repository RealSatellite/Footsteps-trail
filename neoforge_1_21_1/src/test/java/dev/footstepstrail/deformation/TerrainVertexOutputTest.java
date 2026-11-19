package dev.footstepstrail.deformation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.Assert.*;

public class TerrainVertexOutputTest {
    @Test public void packedQuadsKeepTextureLightingColorAndTransformThroughTheAdapter() {
        TerrainQuad input = TerrainContactsTest.top();
        int[] packed = new int[32];
        for (int i = 0; i < 4; i++) {
            int start = i * 8;
            packed[start] = Float.floatToRawIntBits(input.x[i]);
            packed[start + 1] = Float.floatToRawIntBits(input.y[i]);
            packed[start + 2] = Float.floatToRawIntBits(input.z[i]);
            packed[start + 3] = 0xffefcdab;
            packed[start + 4] = Float.floatToRawIntBits(input.u[i]);
            packed[start + 5] = Float.floatToRawIntBits(input.v[i]);
        }
        BakedQuad baked = new BakedQuad(packed, -1, Direction.UP, null, true);
        TerrainQuad decoded = TerrainVertexOutput.read(baked, new float[]{1, 1, 1, 1}, 1, 1, 1, input.light, true);
        assertArrayEquals(input.color, decoded.color);
        List<SurfaceImprint> stamps = Collections.singletonList(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 0));
        PoseStack matrix = new PoseStack();
        matrix.translate(8, 4, 16);
        Capture capture = new Capture();
        QuadDeformation.emit(decoded, 0, 0, 0, DeformationMaterial.SAND, stamps,
                (x, y, z) -> SurfaceHeights.sample(x, y, z, 0, DeformationMaterial.SAND, stamps),
                quad -> TerrainVertexOutput.write(capture, matrix.last(), 0, quad));
        assertTrue(capture.low < 4.985);
        assertTrue(capture.high > 5.002);
        assertTrue(capture.vertices > 4);
        assertTrue(capture.vertices <= 4096);
    }

    @Test public void mappedRendererContainsExactlyTheRequiredOutputHook() throws Exception {
        ClassNode type = new ClassNode();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("net/minecraft/client/renderer/block/ModelBlockRenderer.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        int calls = 0;
        for (MethodNode method : type.methods) if (method.name.equals("putQuadData")) {
            for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("com/mojang/blaze3d/vertex/VertexConsumer") && call.name.equals("putBulkData")) {
                    assertEquals("(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V", call.desc);
                    calls++;
                }
            }
        }
        assertEquals("The required terrain mixin must match this exact Minecraft build", 1, calls);
    }

    private static final class Capture implements VertexConsumer {
        double x, y, z, low = 5, high = 5;
        int vertices;
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices++;
            this.x = x; this.y = y; this.z = z;
            low = Math.min(low, y); high = Math.max(high, y); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { assertEquals(255, a); return this; }
        public VertexConsumer setUv(float u, float v) { assertEquals(x - 8, u, 0.000001); assertEquals(z - 16, v, 0.000001); return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { assertEquals(240, u); assertEquals(240, v); return this; }
        public VertexConsumer setNormal(float x, float y, float z) { assertTrue(Float.isFinite(x + y + z)); return this; }
    }
}
