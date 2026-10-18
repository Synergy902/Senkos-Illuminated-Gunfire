package com.senko.gunfirelight.source;

/** Shared finite temporal envelope; remains stable while simulation is paused. */
public final class BulletLightEnvelope {
    private BulletLightEnvelope() {}

    public static float terminal(double age, float duration) {
        if (!Double.isFinite(age) || !Float.isFinite(duration) || duration <= 0 || age < 0 || age >= duration) return 0;
        float t = (float) (age / duration);
        return (1 - t) * (1 - t) * (1 + 2 * t);
    }
}
