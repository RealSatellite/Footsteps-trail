package dev.footstepstrail.deformation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import java.lang.reflect.Field;

/** Captures the existing renderer's lit quads, including Forge's optional light pipeline. */
final class TerrainBlockRenderer extends BlockModelRenderer {
    private final BlockModelRenderer delegate;
    private final ThreadLocal<BufferBuilder> scratch = ThreadLocal.withInitial(() -> new BufferBuilder(256));

    private TerrainBlockRenderer(Minecraft mc, BlockModelRenderer delegate) {
        super(mc.getBlockColors());
        this.delegate = delegate;
    }

    static void install(Minecraft mc) {
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        if (dispatcher == null || dispatcher.getBlockModelRenderer() instanceof TerrainBlockRenderer) return;
        try {
            // The dispatcher has no public setter in 1.12.2. Support both MCP development
            // names and the SRG name present in production, without a coremod/bootstrap dependency.
            Field field;
            try { field = BlockRendererDispatcher.class.getDeclaredField("blockModelRenderer"); }
            catch (NoSuchFieldException ignored) { field = BlockRendererDispatcher.class.getDeclaredField("field_175027_c"); }
            field.setAccessible(true);
            field.set(dispatcher, new TerrainBlockRenderer(mc, dispatcher.getBlockModelRenderer()));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to install the 1.12.2 footprint terrain adapter", error);
        }
    }

    @Override public boolean renderModel(IBlockAccess level, IBakedModel model, IBlockState state,
            BlockPos pos, BufferBuilder output, boolean checkSides, long seed) {
        TerrainContacts.Surface surface = TerrainFootprints.surface(pos, state);
        if (surface == null) return delegate.renderModel(level, model, state, pos, output, checkSides, seed);
        BufferBuilder buffer = scratch.get();
        buffer.begin(output.getDrawMode(), output.getVertexFormat());
        buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
        boolean rendered;
        try {
            rendered = delegate.renderModel(level, model, state, pos, buffer, checkSides, seed);
        } finally {
            buffer.finishDrawing();
        }
        TerrainBuffer.emit(buffer.getVertexState().getRawBuffer(), buffer.getVertexFormat(), pos, surface, output);
        buffer.reset();
        if (rendered) TerrainFootprints.emitted();
        return rendered;
    }
}
