package dev.footstepstrail.render;

import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.Footstep;
import dev.footstepstrail.tracking.FootstepsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Iterator;

/**
 * Forge 1.12.2 renderer for footstep markers.
 */
@SideOnly(Side.CLIENT)
public class FootstepsRenderer {
    private static FootstepsRenderer INSTANCE;

    // Height offset above ground
    private static final float HEIGHT_OFFSET = 0.02f;

    private static final int CIRCLE_SEGMENTS = 16;

    private FootstepsRenderer() {
    }

    public static FootstepsRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FootstepsRenderer();
        }
        return INSTANCE;
    }

    public void render(float partialTicks) {
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

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        String currentDim = String.valueOf(mc.world.provider.getDimensionType().getName());

        RenderManager renderManager = mc.getRenderManager();

        double camX = renderManager.viewerPosX;
        double camY = renderManager.viewerPosY;
        double camZ = renderManager.viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableCull();
        GlStateManager.enableDepth();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

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

            float x = (float) (footstep.getX() + offsetX);
            float y = (float) (footstep.getY() + HEIGHT_OFFSET);
            float z = (float) (footstep.getZ() + offsetZ);

            int a = realistic
                    ? (int) (opacity * profile.getOpacityScale() * 255.0f)
                    : (int) (opacity * 255.0f);

            float cos = 1.0f;
            float sin = 0.0f;
            if (config.isEnableRotation()) {
                float yawRad = (float) Math.toRadians(-footstep.getYaw());
                cos = (float) Math.cos(yawRad);
                sin = (float) Math.sin(yawRad);
            }

            if (realistic) {
                FootprintGeometry.emit((vx, vy, vz, vr, vg, vb, va) ->
                        buffer.pos(vx, vy, vz).color(vr, vg, vb, va).endVertex(),
                        x, y, z, markerHalfSize, cos, sin, profile,
                        footstep.getBaseColor(), a, footstep.getStepIndex());
            } else {
                if (glow) {
                    GlowGeometry.emitHalo((vx, vy, vz, vr, vg, vb, va) -> buffer.pos(vx, vy, vz).color(vr, vg, vb, va).endVertex(),
                            x, y, z, markerHalfSize, r, g, b, a);
                }
                drawShape(buffer, shape, x, y, z, markerHalfSize, cos, sin, sr, sg, sb, a);
            }
        }

        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();
    }

    private static void writeVertex(BufferBuilder buffer,
            float cx, float cy, float cz, float rx, float rz,
            float cos, float sin, int r, int g, int b, int a) {
        float rotX = rx * cos - rz * sin;
        float rotZ = rx * sin + rz * cos;
        buffer.pos(cx + rotX, cy, cz + rotZ).color(r, g, b, a).endVertex();
    }

    private static void drawShape(BufferBuilder buffer, FootstepShape shape,
            float x, float y, float z, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        switch (shape) {
            case CIRCLE:
                drawCircle(buffer, x, y, z, 0.0f, 0.0f, halfSize, halfSize, cos, sin, r, g, b, a);
                break;
            case PAW:
                drawPaw(buffer, x, y, z, halfSize, cos, sin, r, g, b, a);
                break;
            case CROSS:
                drawCross(buffer, x, y, z, halfSize, cos, sin, r, g, b, a);
                break;
            case SQUARE:
            default:
                writeVertex(buffer, x, y, z, -halfSize, -halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, x, y, z, -halfSize, halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, x, y, z, halfSize, halfSize, cos, sin, r, g, b, a);
                writeVertex(buffer, x, y, z, halfSize, -halfSize, cos, sin, r, g, b, a);
                break;
        }
    }

    private static void drawCircle(BufferBuilder buffer,
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

            writeVertex(buffer, cx, cy, cz, offsetX, offsetZ, cos, sin, r, g, b, a);
            writeVertex(buffer, cx, cy, cz, offsetX + x1, offsetZ + z1, cos, sin, r, g, b, a);
            writeVertex(buffer, cx, cy, cz, offsetX + x2, offsetZ + z2, cos, sin, r, g, b, a);
            writeVertex(buffer, cx, cy, cz, offsetX + x2, offsetZ + z2, cos, sin, r, g, b, a);
        }
    }

    private static void drawPaw(BufferBuilder buffer,
            float cx, float cy, float cz, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        drawCircle(buffer, cx, cy, cz, 0.0f, halfSize * 0.2f, halfSize * 0.65f, halfSize * 0.5f, cos, sin, r, g, b, a);
        float toeRadius = halfSize * 0.22f;
        float toeRowZ = -halfSize * 0.45f;

        drawCircle(buffer, cx, cy, cz, -halfSize * 0.45f, toeRowZ, toeRadius, toeRadius, cos, sin, r, g, b, a);
        drawCircle(buffer, cx, cy, cz, halfSize * 0.45f, toeRowZ, toeRadius, toeRadius, cos, sin, r, g, b, a);
        drawCircle(buffer, cx, cy, cz, -halfSize * 0.18f, toeRowZ - halfSize * 0.2f, toeRadius, toeRadius, cos, sin, r, g, b, a);
        drawCircle(buffer, cx, cy, cz, halfSize * 0.18f, toeRowZ - halfSize * 0.2f, toeRadius, toeRadius, cos, sin, r, g, b, a);
    }

    private static void drawCross(BufferBuilder buffer,
            float cx, float cy, float cz, float halfSize, float cos, float sin,
            int r, int g, int b, int a) {
        float armWidth = halfSize * 0.35f;

        writeVertex(buffer, cx, cy, cz, -halfSize, -armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, -halfSize, armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, halfSize, armWidth, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, halfSize, -armWidth, cos, sin, r, g, b, a);

        writeVertex(buffer, cx, cy, cz, -armWidth, -halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, -armWidth, halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, armWidth, halfSize, cos, sin, r, g, b, a);
        writeVertex(buffer, cx, cy, cz, armWidth, -halfSize, cos, sin, r, g, b, a);
    }
}
