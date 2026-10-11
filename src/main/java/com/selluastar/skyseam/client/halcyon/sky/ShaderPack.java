package com.selluastar.skyseam.client.halcyon.sky;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a shader pack is drawing the world: Iris's public API, reached by reflection, so Iris stays a soft
 * dependency and Skyseam runs the same without it.
 */
public final class ShaderPack {
    private static MethodHandle inUse;
    private static boolean looked;

    private ShaderPack() {}

    /** True while Iris is installed and a shader pack is switched on. */
    public static boolean active() {
        if (!looked) {
            looked = true;
            inUse = find();
        }
        if (inUse == null) {
            return false;
        }
        try {
            return (boolean) inUse.invoke();
        } catch (Throwable broken) {
            inUse = null;
            return false;
        }
    }

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }
}
