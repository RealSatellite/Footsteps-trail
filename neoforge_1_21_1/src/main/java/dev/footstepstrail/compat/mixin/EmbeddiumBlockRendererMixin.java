package dev.footstepstrail.compat.mixin;

import dev.footstepstrail.deformation.SodiumQuads;
import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.embeddium.api.render.chunk.BlockRenderContext;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.ModelQuadUtil;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium 1.0 builds chunks with its own block renderer, so the vanilla hook never runs.
 * Deformed quads are written with Embeddium's own vertex array and colour encoder, so shader
 * packs (Oculus) receive them like any other terrain quad.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer", remap = false)
abstract class EmbeddiumBlockRendererMixin {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Shadow @Final private ChunkColorWriter colorEncoder;

    @Inject(method = "writeGeometry", at = @At("HEAD"), cancellable = true)
    private void footsteps$surface(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3 offset, Material material,
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
