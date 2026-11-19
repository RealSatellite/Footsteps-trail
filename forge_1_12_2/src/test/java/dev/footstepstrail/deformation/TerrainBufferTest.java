package dev.footstepstrail.deformation;

import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TerrainBufferTest {
    @Test public void legacyPackedTerrainRetainsColorUvLightAndChunkOffsets() {
        VertexFormat format = DefaultVertexFormats.BLOCK;
        TerrainQuad original = TerrainContactsTest.top();
        int[] packed = TerrainBuffer.write(new int[format.getIntegerSize() * 4], format, original);
        TerrainQuad decoded = TerrainBuffer.read(packed, format);
        assertArrayEquals(original.color, decoded.color);
        assertArrayEquals(original.light, decoded.light);
        List<SurfaceImprint> stamps = Collections.singletonList(new SurfaceImprint(0.5, 1, 0.5, 0, 0.2, 1, 0));
        BufferBuilder output = new BufferBuilder(256);
        output.begin(7, format);
        output.setTranslation(-1600, -64, 3200);
        QuadDeformation.emit(decoded, 0, 0, 0, DeformationMaterial.SAND, stamps,
                (x, y, z) -> SurfaceHeights.sample(x, y, z, 0, DeformationMaterial.SAND, stamps), quad -> {
                    output.addVertexData(TerrainBuffer.write(packed, format, quad));
                    output.putPosition(1600, 64, -3200);
                });
        output.finishDrawing();
        int[] data = output.getVertexState().getRawBuffer();
        int stride = format.getIntegerSize() * 4;
        float low = 1, high = 1;
        for (int start = 0; start < data.length; start += stride) {
            TerrainQuad quad = TerrainBuffer.read(Arrays.copyOfRange(data, start, start + stride), format);
            for (int i = 0; i < 4; i++) {
                low = Math.min(low, quad.y[i]); high = Math.max(high, quad.y[i]);
                assertEquals(quad.x[i], quad.u[i], 0.000001);
                assertEquals(quad.z[i], quad.v[i], 0.000001);
                assertEquals(255, quad.color[i] >>> 24);
                assertEquals(0x00f000f0, quad.light[i]);
            }
        }
        assertTrue(low < 0.985);
        assertTrue(high > 1.002);
        assertArrayEquals(packed, TerrainBuffer.write(packed, format, decoded));
    }

    @Test public void dispatcherAndRendererExposeTheVerifiedLegacyHook() throws Exception {
        assertEquals(BlockModelRenderer.class, BlockRendererDispatcher.class.getDeclaredField("blockModelRenderer").getType());
        assertNotNull(TerrainBlockRenderer.class.getDeclaredMethod("renderModel", net.minecraft.world.IBlockAccess.class,
                net.minecraft.client.renderer.block.model.IBakedModel.class, net.minecraft.block.state.IBlockState.class,
                BlockPos.class, BufferBuilder.class, boolean.class, long.class));
        assertEquals(net.minecraft.client.renderer.ViewFrustum.class, LegacyTerrainRebuilds.fieldOfType(
                net.minecraft.client.renderer.RenderGlobal.class, net.minecraft.client.renderer.ViewFrustum.class).getType());
        assertEquals(net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.class, LegacyTerrainRebuilds.fieldOfType(
                net.minecraft.client.renderer.RenderGlobal.class, net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.class).getType());
    }
}
