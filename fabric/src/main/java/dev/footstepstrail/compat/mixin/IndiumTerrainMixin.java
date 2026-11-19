package dev.footstepstrail.compat.mixin;

import dev.footstepstrail.deformation.SodiumQuads;
import dev.footstepstrail.deformation.TerrainContacts;
import dev.footstepstrail.deformation.TerrainFootprints;
import dev.footstepstrail.deformation.TerrainQuad;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import link.infra.indium.renderer.render.BlockRenderInfo;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With Sodium 0.5, Fabric Renderer API models (Continuity connected textures, randomised
 * resource-pack textures, many modded blocks) are meshed by Indium instead of Sodium's own
 * block renderer, so the Sodium hook never sees them.
 */
@Pseudo
@Mixin(targets = "link.infra.indium.renderer.render.TerrainRenderContext", remap = false)
abstract class IndiumTerrainMixin {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Shadow private ChunkBuildBuffers buffers;
    @Shadow private Vector3fc origin;
    @Shadow private Vec3d modelOffset;

    @Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
    private void footsteps$surface(MutableQuadViewImpl quad, Material material, CallbackInfo ci) {
        if (quad.lightFace() == Direction.DOWN) return;
        BlockRenderInfo blockInfo = ((IndiumBlockInfoAccessor) this).footsteps$blockInfo();
        TerrainContacts.Surface surface = TerrainFootprints.surface(blockInfo.blockPos, blockInfo.blockState);
        if (surface == null) return;
        TerrainQuad source = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            source.x[i] = quad.x(i); source.y[i] = quad.y(i); source.z[i] = quad.z(i);
            source.u[i] = quad.u(i); source.v[i] = quad.v(i);
            // Indium has already multiplied ambient occlusion and face shading into the colour.
            source.color[i] = quad.color(i);
            source.light[i] = quad.lightmap(i);
        }
        ci.cancel();
        float ox = origin.x() + (float) modelOffset.x, oy = origin.y() + (float) modelOffset.y,
                oz = origin.z() + (float) modelOffset.z;
        ChunkModelBuilder builder = buffers.get(material);
        // Dents tilt away from the block face, so they go in the bucket that is always drawn.
        ChunkMeshBufferBuilder buffer = builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED);
        surface.emit(source, out -> {
            for (int i = 0; i < 4; i++) {
                ChunkVertexEncoder.Vertex vertex = vertices[i];
                vertex.x = ox + out.x[i]; vertex.y = oy + out.y[i]; vertex.z = oz + out.z[i];
                vertex.color = SodiumQuads.swapRedBlue(out.color[i]);
                vertex.u = out.u[i]; vertex.v = out.v[i];
                vertex.light = out.light[i];
            }
            buffer.push(vertices, material);
        });
        Sprite sprite = quad.cachedSprite();
        if (sprite != null) builder.addSprite(sprite);
        TerrainFootprints.emitted();
    }
}
