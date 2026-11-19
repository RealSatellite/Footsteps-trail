package dev.footstepstrail.deformation;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import java.util.List;

/** Minecraft 26.3 quad/lighting adapter for the shared deformation engine. */
final class DeformedQuad {
    static TerrainQuad read(BakedQuad quad, QuadInstance light) {
        TerrainQuad source = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            source.x[i] = quad.position(i).x();
            source.y[i] = quad.position(i).y();
            source.z[i] = quad.position(i).z();
            source.u[i] = UVPair.unpackU(quad.packedUV(i));
            source.v[i] = UVPair.unpackV(quad.packedUV(i));
            source.color[i] = light.getColor(i);
            source.light[i] = light.getLightCoords(i);
        }
        return source;
    }

    static void write(BlockQuadOutput output, float x, float y, float z, BakedQuad original,
            QuadInstance light, TerrainQuad quad) {
        QuadInstance instance = new QuadInstance();
        instance.setOverlayCoords(light.overlayCoords());
        Vector3f[] points = new Vector3f[4];
        long[] uv = new long[4];
        for (int i = 0; i < 4; i++) {
            points[i] = new Vector3f(quad.x[i], quad.y[i], quad.z[i]);
            uv[i] = UVPair.pack(quad.u[i], quad.v[i]);
            instance.setColor(i, quad.color[i]);
            instance.setLightCoords(i, quad.light[i]);
        }
        output.put(x, y, z, new BakedQuad(points[0], points[1], points[2], points[3],
                uv[0], uv[1], uv[2], uv[3], original.direction(), original.materialInfo()), instance);
    }

    static void emit(BlockQuadOutput output, float x, float y, float z, BakedQuad quad,
            QuadInstance light, BlockPos pos, DeformationMaterial material, List<SurfaceImprint> stamps) {
        emit(output, x, y, z, quad, light, pos, material, stamps,
                (wx, wy, wz) -> SurfaceHeights.sample(wx, wy, wz, pos.getY(), material, stamps));
    }

    static void emit(BlockQuadOutput output, float x, float y, float z, BakedQuad quad,
            QuadInstance light, BlockPos pos, DeformationMaterial material, List<SurfaceImprint> stamps,
            SurfaceHeights.Sampler heights) {
        QuadDeformation.emit(read(quad, light), pos.getX(), pos.getY(), pos.getZ(), material, stamps, heights,
                result -> write(output, x, y, z, quad, light, result));
    }

    private DeformedQuad() {}
}
