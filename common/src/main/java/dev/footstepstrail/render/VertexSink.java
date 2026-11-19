package dev.footstepstrail.render;

/**
 * Minimal abstraction over the per-loader vertex buffer, so the footprint
 * geometry can live in shared common code without depending on any
 * Minecraft rendering API. Each loader wraps its buffer in a lambda.
 */
public interface VertexSink {

    /**
     * Emit one vertex. Coordinates are world-relative (the caller has already
     * subtracted the camera position).
     */
    void vertex(float x, float y, float z, int r, int g, int b, int a);
}
