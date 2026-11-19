package dev.footstepstrail.deformation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainVertexOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @Redirect(method = "putQuadData", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void footsteps$surface(VertexConsumer output, PoseStack.Pose matrix, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha, int[] light, int overlay, boolean colorize,
            BlockAndTintGetter world, BlockState state, BlockPos pos, VertexConsumer originalOutput,
            PoseStack.Pose originalMatrix, BakedQuad originalQuad, float b0, float b1, float b2, float b3,
            int l0, int l1, int l2, int l3, int originalOverlay) {
        TerrainContacts.Surface surface = quad.getDirection() == Direction.DOWN ? null : TerrainFootprints.surface(pos, state);
        if (surface == null) {
            output.putBulkData(matrix, quad, brightness, red, green, blue, alpha, light, overlay, colorize);
            return;
        }
        surface.emit(TerrainVertexOutput.read(quad, brightness, red, green, blue, light, colorize),
                result -> TerrainVertexOutput.write(output, matrix, overlay, result));
        TerrainFootprints.emitted();
    }
}
