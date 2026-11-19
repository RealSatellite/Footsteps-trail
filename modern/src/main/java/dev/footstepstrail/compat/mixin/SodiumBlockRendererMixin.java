package dev.footstepstrail.compat.mixin;

import dev.footstepstrail.compat.SodiumQuadAccess;
import dev.footstepstrail.deformation.SodiumQuads;
import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.9 (Minecraft 26.x) builds chunks with its own block renderer, so the vanilla hook
 * never runs. Deformed quads are written back into Sodium's quad and passed through its own
 * writer, so Sodium's lighting, render passes and translucency sorting (and Iris) handle them as
 * usual. Same approach as the 1.21.1 hook; Sodium 0.9 moved the quad class and renamed its
 * accessors.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
abstract class SodiumBlockRendererMixin {
    @Unique private static final float[] footsteps$FULL_BRIGHTNESS = {1, 1, 1, 1};
    @Unique private BlockPos footsteps$pos;
    @Unique private BlockState footsteps$state;
    @Unique private boolean footsteps$emitting;

    @Shadow
    private void bufferQuad(MutableQuadViewImpl quad, float[] brightness, Material material) {
        throw new AssertionError();
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void footsteps$block(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        footsteps$pos = pos;
        footsteps$state = state;
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
    private void footsteps$surface(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
        if (footsteps$emitting || footsteps$pos == null || SodiumQuadAccess.lightFace(quad) == Direction.DOWN) return;
        TerrainContacts.Surface surface = TerrainFootprints.surface(footsteps$pos, footsteps$state);
        if (surface == null) return;
        TerrainQuad source = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            source.x[i] = SodiumQuadAccess.x(quad, i); source.y[i] = SodiumQuadAccess.y(quad, i);
            source.z[i] = SodiumQuadAccess.z(quad, i);
            source.u[i] = SodiumQuadAccess.u(quad, i); source.v[i] = SodiumQuadAccess.v(quad, i);
            source.color[i] = SodiumQuads.shadeArgb(quad.baseColor(i), brightness[i]);
            source.light[i] = SodiumQuadAccess.light(quad, i);
        }
        ci.cancel();
        footsteps$emitting = true;
        try {
            surface.emit(source, out -> {
                for (int i = 0; i < 4; i++) {
                    quad.setPos(i, out.x[i], out.y[i], out.z[i]);
                    quad.setUV(i, out.u[i], out.v[i]);
                    quad.setColor(i, out.color[i]);
                    quad.setLight(i, out.light[i]);
                }
                bufferQuad(quad, footsteps$FULL_BRIGHTNESS, material);
            });
        } finally {
            footsteps$emitting = false;
        }
        TerrainFootprints.emitted();
    }
}
