package dev.footstepstrail.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.Footstep;
import dev.footstepstrail.tracking.FootstepsTracker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.Iterator;

/**
 * Handles rendering of footstep markers in the world.
 */
public class FootstepsRenderer {
    private static FootstepsRenderer INSTANCE;

    // Height offset above ground
    private static final float HEIGHT_OFFSET = 0.02f;

    // Number of segments for circle/oval approximation
    private static final int CIRCLE_SEGMENTS = 16;

    private FootstepsRenderer() {
    }

    public static FootstepsRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FootstepsRenderer();
        }
        return INSTANCE;
    }

    /**
     * Render all footsteps in the world.
     */
    public void render(PoseStack matrices, Camera camera) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        if (config.isTerrain()) return; // Terrain footprints are part of the terrain mesh.

        if (!config.isEnabled()) {
            return;
        }

        FootstepsTracker tracker = FootstepsTracker.getInstance();
        int totalFootsteps = tracker.getCount();

        if (totalFootsteps == 0) {
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String currentDim = mc.level.dimension().location().toString();

        // Get camera position for offset calculation
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        matrices.pushPose();

        // Set up rendering state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Build buffer with all footstep quads
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        long now = System.currentTimeMillis();
        int maxDurationMs = config.getMaxDurationSeconds() * 1000;
        float markerHalfSize = config.getMarkerSize();
        float maxOpacity = config.getMaxOpacity();
        int r = (int) (config.getColorR() * 255);
        int g = (int) (config.getColorG() * 255);
        int b = (int) (config.getColorB() * 255);
        boolean realistic = config.isSurface();
        boolean glow = config.isClassic() && config.isGlow();
        int sr = glow ? GlowGeometry.brighten(r) : r;
        int sg = glow ? GlowGeometry.brighten(g) : g;
        int sb = glow ? GlowGeometry.brighten(b) : b;
        FootstepShape shape = config.getShape();
        boolean hardScuffs = config.isHardSurfaceScuffs();
        double maxDist = config.getMaxRenderDistance();
        double maxDistSq = maxDist * maxDist;

        Iterator<Footstep> iterator = tracker.getFootsteps();
        int index = 0;
        while (iterator.hasNext()) {
            Footstep footstep = iterator.next();

            // Dimension filtering
            if (footstep.getDimension() != null && !footstep.getDimension().equals(currentDim)) {
                continue;
            }

            // Calculate opacity based on mode
            float opacity;
            TrackingMode trackingMode = config.getTrackingMode();
            float normalizedAge = Math.min(1.0f, Math.max(0.0f,
                    (float) (now - footstep.getTimestamp()) / maxDurationMs));
            float normalizedIndex = (float) index / totalFootsteps;
            // Whichever limit is nearer decides how faded the mark is.
            opacity = (1.0f - trackingMode.progress(normalizedAge, normalizedIndex)) * maxOpacity;
            // Let the surface decide how long a mark lingers: dust scuffs
            // fade faster than snow prints.
            if (realistic && trackingMode.usesTime()) {
                opacity *= Math.max(0.0f, 1.0f - normalizedAge
                        * (1.0f - footstep.getSurfaceProfile().getLifetimeScale()));
            }
            index++;

            // Skip if too faded
            if (opacity < 0.01f) {
                continue;
            }

            SurfaceProfile profile = footstep.getSurfaceProfile();

            if (realistic && footstep.distanceSquaredTo(camX, camY, camZ) > maxDistSq) {
                continue;
            }

            if (realistic && profile == SurfaceProfile.HARD && !hardScuffs) {
                continue;
            }

            // Calculate alternating foot offset
            double offsetX = 0;
            double offsetZ = 0;
            if (config.isEnableAlternatingFeet()) {
                boolean isLeft = (footstep.getStepIndex() % 2 == 0);
                double yawRad = Math.toRadians(footstep.getYaw());
                double offsetFactor = isLeft ? -config.getAlternatingFootOffset() : config.getAlternatingFootOffset();
                offsetX = Math.cos(yawRad) * offsetFactor;
                offsetZ = Math.sin(yawRad) * offsetFactor;
            }

            // Calculate position relative to camera
            float x = (float) (footstep.getX() + offsetX - camX);
            float y = (float) (footstep.getY() - camY + HEIGHT_OFFSET);
            float z = (float) (footstep.getZ() + offsetZ - camZ);

            Matrix4f matrix = matrices.last().pose();
            int a = realistic
                    ? (int) (opacity * profile.getOpacityScale() * 255)
                    : (int) (opacity * 255);

            float cos = 1.0f;
            float sin = 0.0f;
            if (config.isEnableRotation()) {
                float yawRad = (float) Math.toRadians(-footstep.getYaw());
                cos = (float) Math.cos(yawRad);
                sin = (float) Math.sin(yawRad);
            }

            if (realistic) {
                FootprintGeometry.emit((vx, vy, vz, vr, vg, vb, va) ->
                        buffer.addVertex(matrix, vx, vy, vz).setColor(vr, vg, vb, va),
                        x, y, z, markerHalfSize, cos, sin, profile,
                        footstep.getBaseColor(), a, footstep.getStepIndex());
            } else {
                if (glow) {
                    GlowGeometry.emitHalo((vx, vy, vz, vr, vg, vb, va) -> buffer.addVertex(matrix, vx, vy, vz).setColor(vr, vg, vb, va),
                            x, y, z, markerHalfSize, r, g, b, a);
                }
                drawShape(buffer, matrix, shape, x, y, z, markerHalfSize, cos, sin, sr, sg, sb, a);
            }
        }

        MeshData built = buffer.build();
        if (built != null) {
            BufferUploader.drawWithShader(built);
        }

        // Restore rendering state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.popPose();
    }

    private static void writeVertex(BufferBuilder buffer, Matrix4f matrix,
            float cx, float cy, float cz, float rx, float rz,
            float cos, float sin, int r, int g, int b, int a) {
        float rotX = rx * cos - rz * sin;
        float rotZ = rx * sin + rz * cos;
        buffer.addVertex(matrix, cx + rotX, cy, cz + rotZ).setColor(r, g, b, a);
    }

    private static void drawShape(BufferBuilder buffer, Matrix4f matrix, FootstepShape shape,
            float x, float y, float z, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        switch (shape) {
            case CIRCLE:
                drawCircle(buffer, matrix, x, y, z, 0.0f, 0.0f, halfSize, halfSize, cos, sin, r, g, b, a);
                break;
            case PAW:
                drawPaw(buffer, matrix, x, y, z, halfSize, cos, sin, r, g, b, a);
                break;
            case CROSS:
                drawCross(buffer, matrix, x, y, z, halfSize, cos, sin, r, g, b, a);
                break;
            case SQUARE:
            default:
                writeVertex(buffer, matrix, x, y, z, -halfSize, -halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, matrix, x, y, z, -halfSize, halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, matrix, x, y, z, halfSize, halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, matrix, x, y, z, halfSize, -halfSize, cos, sin, r, g, b, a);
                break;
        }
    }

    private static void drawCircle(BufferBuilder buffer, Matrix4f matrix,
            float cx, float cy, float cz, float offsetX, float offsetZ,
            float radiusX, float radiusZ, float cos, float sin,
            int r, int g, int b, int a) {
        float angleStep = (float) (2.0 * Math.PI / CIRCLE_SEGMENTS);
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;

            float x1 = (float) Math.cos(angle1) * radiusX;
            float z1 = (float) Math.sin(angle1) * radiusZ;
            float x2 = (float) Math.cos(angle2) * radiusX;
            float z2 = (float) Math.sin(angle2) * radiusZ;

            writeVertex(buffer, matrix, cx, cy, cz, offsetX, offsetZ, cos, sin, r, g, b, a);
            writeVertex(buffer, matrix, cx, cy, cz, offsetX + x1, offsetZ + z1, cos, sin, r, g, b, a);
            writeVertex(buffer, matrix, cx, cy, cz, offsetX + x2, offsetZ + z2, cos, sin, r, g, b, a);
            writeVertex(buffer, matrix, cx, cy, cz, offsetX + x2, offsetZ + z2, cos, sin, r, g, b, a);
        }
    }

    private static void drawPaw(BufferBuilder buffer, Matrix4f matrix,
            float cx, float cy, float cz, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        // Main pad (oval)
        float padRadiusX = halfSize * 0.65f;
        float padRadiusZ = halfSize * 0.5f;
        float padOffsetZ = halfSize * 0.2f;
        drawCircle(buffer, matrix, cx, cy, cz, 0.0f, padOffsetZ, padRadiusX, padRadiusZ, cos, sin, r, g, b, a);

        // Four toe beans
        float toeRadius = halfSize * 0.22f;
        float toeRowZ = -halfSize * 0.45f;

        // Outer toes
        drawCircle(buffer, matrix, cx, cy, cz, -halfSize * 0.45f, toeRowZ, toeRadius, toeRadius, cos, sin, r, g, b, a);
        drawCircle(buffer, matrix, cx, cy, cz, halfSize * 0.45f, toeRowZ, toeRadius, toeRadius, cos, sin, r, g, b, a);
        // Inner toes
        drawCircle(buffer, matrix, cx, cy, cz, -halfSize * 0.18f, toeRowZ - halfSize * 0.2f, toeRadius, toeRadius, cos, sin, r, g,
                b, a);
        drawCircle(buffer, matrix, cx, cy, cz, halfSize * 0.18f, toeRowZ - halfSize * 0.2f, toeRadius, toeRadius, cos, sin, r, g,
                b, a);
    }

    private static void drawCross(BufferBuilder buffer, Matrix4f matrix,
            float cx, float cy, float cz, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        float armWidth = halfSize * 0.35f;

        // Horizontal bar
        writeVertex(buffer, matrix, cx, cy, cz, -halfSize, -armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, -halfSize, armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, halfSize, armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, halfSize, -armWidth, cos, sin, r, g, b, a);

        // Vertical bar
        writeVertex(buffer, matrix, cx, cy, cz, -armWidth, -halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, -armWidth, halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, armWidth, halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, matrix, cx, cy, cz, armWidth, -halfSize, cos, sin, r, g, b, a);
    }
}
