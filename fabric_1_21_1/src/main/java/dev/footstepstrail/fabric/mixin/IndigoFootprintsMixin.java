package dev.footstepstrail.fabric.mixin;

import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainQuad;
import dev.footstepstrail.deformation.TerrainVertexOutput;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Indigo has a separate output path for Fabric Renderer API block models. */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractRenderContext", remap = false)
abstract class IndigoFootprintsMixin {
    @Shadow protected Matrix4f matrix;
    @Shadow protected Matrix3f normalMatrix;
    @Shadow protected int overlay;

    @Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
    private void footsteps$surface(MutableQuadViewImpl quad, VertexConsumer output, CallbackInfo ci) {
        if (!((Object) this instanceof IndigoBlockInfoAccessor)) return;
        BlockRenderInfo blockInfo = ((IndigoBlockInfoAccessor) (Object) this).footsteps$blockInfo();
        TerrainContacts.Surface surface = quad.lightFace() == Direction.DOWN ? null
                : TerrainFootprints.surface(blockInfo.blockPos, blockInfo.blockState);
        if (surface == null) return;
        TerrainQuad source = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            source.x[i] = quad.x(i); source.y[i] = quad.y(i); source.z[i] = quad.z(i);
            source.u[i] = quad.u(i); source.v[i] = quad.v(i);
            source.color[i] = quad.color(i); source.light[i] = quad.lightmap(i);
        }
        surface.emit(source, result -> TerrainVertexOutput.write(output, matrix, normalMatrix, overlay, result));
        TerrainFootprints.emitted();
        ci.cancel();
    }
}
