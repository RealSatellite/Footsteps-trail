package dev.footstepstrail.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.core.Direction;

/**
 * Reads Sodium's quad accessors under whichever name the installed Sodium uses. Sodium 0.9.3
 * renamed them during its betas ({@code getX} became {@code sodium$getX}, and so on), and packs
 * ship both, so calling either name directly crashes on the other with a NoSuchMethodError.
 * Each handle is looked up once; static final handles are inlined by the JIT like normal calls.
 */
public final class SodiumQuadAccess {
    private static final MethodHandle X = find("getX", float.class, int.class);
    private static final MethodHandle Y = find("getY", float.class, int.class);
    private static final MethodHandle Z = find("getZ", float.class, int.class);
    private static final MethodHandle U = find("getTexU", float.class, int.class);
    private static final MethodHandle V = find("getTexV", float.class, int.class);
    private static final MethodHandle LIGHT = find("getLight", int.class, int.class);
    private static final MethodHandle LIGHT_FACE = find("getLightFace", Direction.class);

    /** The accessor called {@code sodium$<name>} (newer Sodium) or {@code <name>} (older). */
    static MethodHandle find(String name, Class<?> returns, Class<?>... params) {
        MethodType type = MethodType.methodType(returns, params);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            return lookup.findVirtual(ModelQuadView.class, "sodium$" + name, type);
        } catch (ReflectiveOperationException renamed) {
            try {
                return lookup.findVirtual(ModelQuadView.class, name, type);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Sodium quad accessor not found: " + name, e);
            }
        }
    }

    public static float x(ModelQuadView quad, int i) { return invokeFloat(X, quad, i); }
    public static float y(ModelQuadView quad, int i) { return invokeFloat(Y, quad, i); }
    public static float z(ModelQuadView quad, int i) { return invokeFloat(Z, quad, i); }
    public static float u(ModelQuadView quad, int i) { return invokeFloat(U, quad, i); }
    public static float v(ModelQuadView quad, int i) { return invokeFloat(V, quad, i); }

    public static int light(ModelQuadView quad, int i) {
        try {
            return (int) LIGHT.invokeExact(quad, i);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static Direction lightFace(ModelQuadView quad) {
        try {
            return (Direction) LIGHT_FACE.invokeExact(quad);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static float invokeFloat(MethodHandle handle, ModelQuadView quad, int i) {
        try {
            return (float) handle.invokeExact(quad, i);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) return e;
        if (t instanceof Error e) throw e;
        return new IllegalStateException(t);
    }

    private SodiumQuadAccess() {}
}
