package dev.footstepstrail.deformation.mixin;

import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainVertexOutput;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockModelRenderer.class)
abstract class BlockModelRendererMixin {
    @Redirect(method = "renderQuad", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[IIZ)V"))
    private void footsteps$surface(VertexConsumer output, MatrixStack.Entry matrix, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha, int[] light, int overlay, boolean colorize,
            BlockRenderView world, BlockState state, BlockPos pos, VertexConsumer originalOutput,
            MatrixStack.Entry originalMatrix, BakedQuad originalQuad, float b0, float b1, float b2, float b3,
            int l0, int l1, int l2, int l3, int originalOverlay) {
        TerrainContacts.Surface surface = quad.getFace() == Direction.DOWN ? null : TerrainFootprints.surface(pos, state);
        if (surface == null) {
            output.quad(matrix, quad, brightness, red, green, blue, alpha, light, overlay, colorize);
            return;
        }
        surface.emit(TerrainVertexOutput.read(quad, brightness, red, green, blue, light, colorize),
                result -> TerrainVertexOutput.write(output, matrix, overlay, result));
        TerrainFootprints.emitted();
    }
}
