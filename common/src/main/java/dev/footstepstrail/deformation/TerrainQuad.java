package dev.footstepstrail.deformation;

/** A block-local quad at the terrain adapter boundary. Colors are ARGB; UVs are atlas coordinates. */
public final class TerrainQuad {
    public final float[] x = new float[4], y = new float[4], z = new float[4];
    public final float[] u = new float[4], v = new float[4];
    public final int[] color = new int[4], light = new int[4];
}
