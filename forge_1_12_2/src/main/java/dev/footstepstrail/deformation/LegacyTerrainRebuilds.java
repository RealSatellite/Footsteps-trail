package dev.footstepstrail.deformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.Field;

/** Queues footprint-only rebuilds on Minecraft's existing workers, including nearby sections. */
final class LegacyTerrainRebuilds {
    private static final Field FRUSTUM = fieldOfType(RenderGlobal.class, ViewFrustum.class);
    private static final Field DISPATCHER = fieldOfType(RenderGlobal.class, ChunkRenderDispatcher.class);

    static void queue(Minecraft mc, int x, int y, int z) {
        if (mc.world == null || mc.player == null || y < 0 || y >= 16) return;
        try {
            ViewFrustum frustum = (ViewFrustum) FRUSTUM.get(mc.renderGlobal);
            ChunkRenderDispatcher dispatcher = (ChunkRenderDispatcher) DISPATCHER.get(mc.renderGlobal);
            if (frustum == null || dispatcher == null) return;
            // The 1.12.2 frustum is a toroidal (2*distance+1) x 16 x (2*distance+1) grid.
            int width = (int) Math.sqrt(frustum.renderChunks.length / 16);
            int index = (Math.floorMod(z, width) * 16 + y) * width + Math.floorMod(x, width);
            RenderChunk chunk = frustum.renderChunks[index];
            if (!chunk.getPosition().equals(new BlockPos(x << 4, y << 4, z << 4))) return;
            // Ordinary block/light updates retain their own scheduling. Their pending
            // rebuild also sees our published snapshot, so it need not be queued twice.
            if (chunk.needsUpdate()) return;
            dispatcher.updateChunkLater(chunk);
            // Do not setNeedsUpdate: RenderGlobal forcibly rebuilds nearby dirty chunks
            // on the render thread, even when the "immediate" flag is false. Also avoid
            // markBlockRangeForRenderUpdate's one-block padding (27 sections per request).
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Unable to queue the 1.12.2 footprint rebuild", error);
        }
    }

    /** These field types are unique in vanilla/Forge and stay stable under MCP/SRG renaming. */
    static Field fieldOfType(Class<?> owner, Class<?> type) {
        Field result = null;
        for (Field field : owner.getDeclaredFields()) if (field.getType() == type) {
            if (result != null) throw new IllegalStateException("Ambiguous terrain field: " + type.getName());
            result = field;
        }
        if (result == null) throw new IllegalStateException("Missing terrain field: " + type.getName());
        result.setAccessible(true);
        return result;
    }

    private LegacyTerrainRebuilds() {}
}
