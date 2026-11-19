package dev.footstepstrail.deformation.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.footstepstrail.deformation.TerrainFootprints;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @Redirect(method = "putQuadWithTint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void footsteps$surface(BlockQuadOutput output, float x, float y, float z,
            BakedQuad quad, QuadInstance lighting, BlockQuadOutput originalOutput,
            float originalX, float originalY, float originalZ, BlockAndTintGetter world,
            BlockState state, BlockPos pos, BakedQuad originalQuad) {
        TerrainFootprints.emit(output, x, y, z, quad, lighting, state, pos);
    }
}
