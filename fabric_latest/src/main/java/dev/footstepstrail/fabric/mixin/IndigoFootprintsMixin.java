package dev.footstepstrail.fabric.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.footstepstrail.deformation.TerrainFootprints;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Indigo emits through FRAPI instead of ModelBlockRenderer's vanilla quad output. */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
abstract class IndigoFootprintsMixin {
    @ModifyVariable(method = "tesselateBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private QuadEmitter footsteps$surfaceOutput(QuadEmitter output, QuadEmitter original,
            float x, float y, float z, BlockAndTintGetter world, BlockPos mutablePos,
            BlockState state, BlockStateModel model, long seed) {
        if (!TerrainFootprints.hasSurface(mutablePos, state)) return output;
        BlockPos pos = mutablePos.immutable();
        return Renderer.get().quadEmitter(source -> {
            // Indigo has already applied section offsets, tint and AO at this point.
            Vector3f[] vertices = new Vector3f[4];
            long[] uv = new long[4];
            QuadInstance light = new QuadInstance();
            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vector3f(source.x(i)-x, source.y(i)-y, source.z(i)-z);
                uv[i] = UVPair.pack(source.u(i), source.v(i));
                light.setColor(i, source.color(i));
                light.setLightCoords(i, source.lightmap(i));
            }
            BakedQuad quad = new BakedQuad(vertices[0],vertices[1],vertices[2],vertices[3],
                    uv[0],uv[1],uv[2],uv[3],source.lightFace(),null);
            TerrainFootprints.emit((ox,oy,oz,deformed,lighting) -> {
                output.copyFrom(source);
                Vector3f normal = new Vector3f(deformed.position1()).sub(deformed.position0())
                        .cross(new Vector3f(deformed.position2()).sub(deformed.position0())).normalize();
                for (int i = 0; i < 4; i++) {
                    output.pos(i,deformed.position(i).x()+ox,deformed.position(i).y()+oy,deformed.position(i).z()+oz);
                    output.uv(i,UVPair.unpackU(deformed.packedUV(i)),UVPair.unpackV(deformed.packedUV(i)));
                    output.color(i,lighting.getColor(i));
                    output.lightmap(i,lighting.getLightCoords(i));
                    output.normal(i,normal);
                }
                output.emit();
            },x,y,z,quad,light,state,pos);
        });
    }
}
