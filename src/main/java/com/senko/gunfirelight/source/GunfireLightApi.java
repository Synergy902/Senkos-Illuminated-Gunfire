package com.senko.gunfirelight.source;

/**
 * Reusable client-thread point-light API. Coordinates/radius are world blocks, colors
 * are linear RGB, strength is relative radiance. A positive lifetime is in seconds;
 * infinity means explicitly remove the handle. Do not call from a server thread.
 */
public final class GunfireLightApi {
    static final LightRegistry SOURCES = new LightRegistry(8192);
    static double clock;
    private GunfireLightApi() {}

    public static long create(double x, double y, double z, float radius,
                              float red, float green, float blue, float intensity,
                              double lifetimeSeconds, boolean fade, int priority) {
        return SOURCES.create(x, y, z, radius, red, green, blue, intensity,
            clock, lifetimeSeconds, fade, priority);
    }

    public static boolean update(long handle, double x, double y, double z, float radius,
                                 float red, float green, float blue, float intensity) {
        return SOURCES.update(handle, x, y, z, radius, red, green, blue, intensity);
    }

    public static boolean remove(long handle) { return SOURCES.remove(handle); }
    public static boolean exists(long handle) { return SOURCES.contains(handle); }
}
