package dev.footstepstrail.compat.mixin;

import dev.footstepstrail.deformation.SodiumQuads;
import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainQuad;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.embeddedt.embeddium.render.chunk.ChunkColorWriter;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium 0.3 builds chunks with its own block renderer, so the vanilla hook never runs.
 * Deformed quads are written with Embeddium's own vertex array and colour encoder, so shader
 * packs (Oculus) receive them like any other terrain quad.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
abstract class EmbeddiumBlockRendererMixin {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Shadow @Final private ChunkColorWriter colorEncoder;

    @Inject(method = "writeGeometry", at = @At("HEAD"), cancellable = true)
    private void footsteps$surface(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3d offset, Material material,
            BakedQuadView quad, int[] colors, QuadLightData light, CallbackInfo ci) {
        if (quad.getLightFace() == Direction.DOWN) return;
        TerrainContacts.Surface surface = TerrainFootprints.surface(ctx.pos(), ctx.state());
        if (surface == null) return;
        TerrainQuad source = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            source.x[i] = quad.getX(i); source.y[i] = quad.getY(i); source.z[i] = quad.getZ(i);
            source.u[i] = quad.getTexU(i); source.v[i] = quad.getTexV(i);
            source.color[i] = SodiumQuads.abgrToShadedArgb(
                    ModelQuadUtil.mixARGBColors(colors[i], quad.getColor(i)), light.br[i]);
            source.light[i] = ModelQuadUtil.mergeBakedLight(quad.getLight(i), light.lm[i]);
        }
        ci.cancel();
        Vector3fc origin = ctx.origin();
        float ox = origin.x() + (float) offset.x, oy = origin.y() + (float) offset.y, oz = origin.z() + (float) offset.z;
        // Dents tilt away from the block face, so they go in the bucket that is always drawn.
        ChunkMeshBufferBuilder buffer = builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED);
        surface.emit(source, out -> {
            for (int i = 0; i < 4; i++) {
                ChunkVertexEncoder.Vertex vertex = vertices[i];
                vertex.x = ox + out.x[i]; vertex.y = oy + out.y[i]; vertex.z = oz + out.z[i];
                vertex.color = colorEncoder.writeColor(SodiumQuads.swapRedBlue(out.color[i]), 1f);
                vertex.u = out.u[i]; vertex.v = out.v[i];
                vertex.light = out.light[i];
            }
            buffer.push(vertices, material);
        });
        TerrainFootprints.emitted();
    }
}
