package com.senko.gunfirelight.compat;

import com.senko.gunfirelight.GunfireConfig;
import com.senko.gunfirelight.SenkosGunfireLight;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import org.joml.Matrix4f;

/** Entry point with no SBW types in its signatures: TaCZ-only installations remain valid. */
public final class SbwIntegration {
    private static boolean registered;
    private SbwIntegration() {}
    public static void register() {
        if (registered || !ModList.get().isLoaded("superbwarfare")) return;
        SbwGunLights.register();
        SbwVehicleLights.register();
        registered = true;
        SenkosGunfireLight.LOG.info("Superb Warfare GPU lighting adapter enabled (0.8.9.2 / Minecraft 1.20.1)");
    }
    public static boolean active() { return registered && GunfireConfig.ENABLED.get(); }
    public static void update(float partial) {
        if (!registered) return;
        if (!active()) { clear(); return; }
        SbwGunLights.update(partial);
        SbwVehicleLights.update(partial);
        SbwProjectileLights.update(partial);
    }
    public static void clear() {
        if (!registered) return;
        SbwGunLights.clear(); SbwVehicleLights.clear(); SbwProjectileLights.clear();
    }
    public static void captureWorldView(Matrix4f view) {
        if (active()) { SbwGunLights.captureWorldView(view); SbwVehicleLights.captureWorldView(view); }
    }
    public static void livingPre(RenderLivingEvent.Pre<?,?> event) { if (active()) SbwGunLights.livingPre(event); }
    public static void livingPost(RenderLivingEvent.Post<?,?> event) { if (active()) SbwGunLights.livingPost(event); }
    public static void join(EntityJoinLevelEvent event) { if (active()) SbwProjectileLights.join(event); }
    public static void leave(EntityLeaveLevelEvent event) { if (registered) SbwProjectileLights.leave(event); }
    public static String status() {
        return registered ? SbwGunLights.status() + ", vehicle muzzles="
            + SbwVehicleLights.trackedMuzzles() + ", projectiles=" + SbwProjectileLights.tracked()
            + ", vehicle shots=" + SbwVehicleLights.shots() : "SBW absent";
    }
}
