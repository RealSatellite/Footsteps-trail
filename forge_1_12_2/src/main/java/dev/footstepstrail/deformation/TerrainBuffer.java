package dev.footstepstrail.deformation;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.math.BlockPos;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Decodes the native BLOCK format after lighting; keeps buffer ownership with Minecraft. */
final class TerrainBuffer {
    static void emit(int[] data, VertexFormat format, BlockPos pos, TerrainContacts.Surface surface, BufferBuilder output) {
        int quadSize = format.getIntegerSize() * 4;
        for (int start = 0; start < data.length; start += quadSize) {
            int[] original = Arrays.copyOfRange(data, start, start + quadSize);
            TerrainQuad quad = read(original, format);
            float ax = quad.x[1] - quad.x[0], az = quad.z[1] - quad.z[0];
            float bx = quad.x[2] - quad.x[0], bz = quad.z[2] - quad.z[0];
            boolean bottom = quad.y[0] == quad.y[1] && quad.y[0] == quad.y[2] && quad.y[0] == quad.y[3]
                    && az * bx - ax * bz < 0;
            if (bottom) {
                output.addVertexData(original);
                output.putPosition(pos.getX(), pos.getY(), pos.getZ());
            } else {
                surface.emit(quad, result -> {
                    output.addVertexData(write(original, format, result));
                    output.putPosition(pos.getX(), pos.getY(), pos.getZ());
                });
            }
        }
    }

    static TerrainQuad read(int[] data, VertexFormat format) {
        TerrainQuad quad = new TerrainQuad();
        int stride = format.getIntegerSize();
        int color = format.getColorOffset() / 4, uv = format.getUvOffsetById(0) / 4, light = format.getUvOffsetById(1) / 4;
        for (int i = 0; i < 4; i++) {
            int start = i * stride;
            quad.x[i] = Float.intBitsToFloat(data[start]);
            quad.y[i] = Float.intBitsToFloat(data[start + 1]);
            quad.z[i] = Float.intBitsToFloat(data[start + 2]);
            quad.u[i] = Float.intBitsToFloat(data[start + uv]);
            quad.v[i] = Float.intBitsToFloat(data[start + uv + 1]);
            int rgba = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? data[start + color] : Integer.reverseBytes(data[start + color]);
            quad.color[i] = swapRedBlue(rgba);
            quad.light[i] = data[start + light];
        }
        return quad;
    }

    static int[] write(int[] original, VertexFormat format, TerrainQuad quad) {
        int[] data = original.clone();
        int stride = format.getIntegerSize();
        int color = format.getColorOffset() / 4, uv = format.getUvOffsetById(0) / 4, light = format.getUvOffsetById(1) / 4;
        for (int i = 0; i < 4; i++) {
            int start = i * stride;
            data[start] = Float.floatToRawIntBits(quad.x[i]);
            data[start + 1] = Float.floatToRawIntBits(quad.y[i]);
            data[start + 2] = Float.floatToRawIntBits(quad.z[i]);
            data[start + uv] = Float.floatToRawIntBits(quad.u[i]);
            data[start + uv + 1] = Float.floatToRawIntBits(quad.v[i]);
            int rgba = swapRedBlue(quad.color[i]);
            data[start + color] = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? rgba : Integer.reverseBytes(rgba);
            data[start + light] = quad.light[i];
        }
        return data;
    }

    private static int swapRedBlue(int color) {
        return (color & 0xff00ff00) | ((color >>> 16) & 255) | ((color & 255) << 16);
    }

    private TerrainBuffer() {}
}
