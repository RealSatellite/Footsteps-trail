package dev.footstepstrail.deformation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Converts already lit terrain vertices without applying tint or ambient occlusion twice. */
public final class TerrainVertexOutput {
    public static TerrainQuad read(BakedQuad quad, float[] brightness, float red, float green, float blue,
            int[] light, boolean colorize) {
        TerrainQuad source = new TerrainQuad();
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        for (int i = 0; i < 4; i++) {
            int start = i * stride;
            source.x[i] = Float.intBitsToFloat(data[start]);
            source.y[i] = Float.intBitsToFloat(data[start + 1]);
            source.z[i] = Float.intBitsToFloat(data[start + 2]);
            source.u[i] = Float.intBitsToFloat(data[start + 4]);
            source.v[i] = Float.intBitsToFloat(data[start + 5]);
            int color = colorize ? data[start + 3] : -1;
            int r = (int) (((color) & 255) * brightness[i] * red);
            int g = (int) (((color >>> 8) & 255) * brightness[i] * green);
            int b = (int) (((color >>> 16) & 255) * brightness[i] * blue);
            source.color[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            source.light[i] = light[i];
        }
        return source;
    }

    public static void write(VertexConsumer output, PoseStack.Pose matrices, int overlay, TerrainQuad quad) {
        write(output, matrices.pose(), matrices.normal(), overlay, quad);
    }

    public static void write(VertexConsumer output, Matrix4f matrix, Matrix3f normalMatrix, int overlay, TerrainQuad quad) {
        Vector3f normal = new Vector3f(quad.x[1] - quad.x[0], quad.y[1] - quad.y[0], quad.z[1] - quad.z[0])
                .cross(new Vector3f(quad.x[2] - quad.x[0], quad.y[2] - quad.y[0], quad.z[2] - quad.z[0]));
        if (normal.lengthSquared() > 1.0e-12f) normal.normalize();
        else normal.set(0, 1, 0);
        normal.mul(normalMatrix);
        for (int i = 0; i < 4; i++) {
            output.addVertex(matrix, quad.x[i], quad.y[i], quad.z[i]).setColor(quad.color[i])
                    .setUv(quad.u[i], quad.v[i]).setOverlay(overlay).setLight(quad.light[i])
                    .setNormal(normal.x, normal.y, normal.z);
        }
    }

    private TerrainVertexOutput() {}
}
