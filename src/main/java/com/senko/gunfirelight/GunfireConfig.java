package com.senko.gunfirelight;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings in world units, independent of Minecraft block-light levels. */
public final class GunfireConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, MUZZLE_ENABLED, BULLET_ENABLED, MODEL_LIGHTING, CONTACT_SHADOWS;
    public static final ForgeConfigSpec.DoubleValue MUZZLE_RADIUS, MUZZLE_INTENSITY, MUZZLE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BULLET_RADIUS, BULLET_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue SUPPRESSED_RADIUS, SUPPRESSED_INTENSITY, SUPPRESSED_SECONDS, BULLET_FADE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue FLASH_HIDER_RADIUS, FLASH_HIDER_INTENSITY, FLASH_HIDER_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FLASH_HIDER_IDS, INTEGRAL_GUN_IDS;
    public static final ForgeConfigSpec.DoubleValue MODEL_INTENSITY, EXPOSURE, NIGHT_VISIBILITY, DISTANCE_FALLOFF, MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue LIGHTS_PER_TILE;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Enable client GPU gunfire lighting.").define("enabled", true);
        b.push("muzzle");
        MUZZLE_ENABLED = b.comment("Enable the muzzle flash LIGHT. Does not change TaCZ's muzzle flash particles.").define("enabled", true);
        MUZZLE_RADIUS = b.comment("Finite radius in blocks. Light is zero outside this sphere.").defineInRange("radius", 10.0, 0.5, 32.0);
        MUZZLE_INTENSITY = b.comment("Unsuppressed muzzle brightness. Higher values make nearby surfaces brighter.").defineInRange("intensity", 8.0, 0.0, 64.0);
        MUZZLE_SECONDS = b.comment("Flash duration in seconds of simulation time.").defineInRange("durationSeconds", 0.12, 0.02, 0.5);
        SUPPRESSED_RADIUS = b.comment("Suppressed muzzle light range. Defaults approximate reduced flash; real devices vary.").defineInRange("suppressedRadius", 2.5, 0.25, 32.0);
        SUPPRESSED_INTENSITY = b.comment("Suppressed muzzle brightness. Default 0.4 is a game approximation, not a measured universal ratio. Zero removes this light.").defineInRange("suppressedIntensity", 0.4, 0.0, 64.0);
        SUPPRESSED_SECONDS = b.comment("Suppressed flash duration in seconds.").defineInRange("suppressedDurationSeconds", 0.04, 0.01, 0.5);
        b.pop().push("flashHider");
        FLASH_HIDER_RADIUS = b.comment("Light range for identified flash hiders, including flash-hiding hybrids.").defineInRange("radius", 3.0, 0.25, 32.0);
        FLASH_HIDER_INTENSITY = b.comment("Flash-hider muzzle brightness. Approximate game default; does not change bullet light or TaCZ particles.").defineInRange("intensity", 0.8, 0.0, 64.0);
        FLASH_HIDER_SECONDS = b.comment("Flash-hider muzzle light duration in seconds.").defineInRange("durationSeconds", 0.05, 0.01, 0.5);
        b.pop().push("gunpackOverrides");
        FLASH_HIDER_IDS = b.comment("Extra attachment IDs treated as flash hiders. Clear flash_hider/flash_suppressor IDs are also recognized; hider alone is not.")
            .defineListAllowEmpty(java.util.List.of("flashHiderAttachments"),java.util.List.of("suffuse:cslr4_flash_hider"),GunfireConfig::validId);
        INTEGRAL_GUN_IDS = b.comment("Gun IDs with integral suppression that their pack does not declare through TaCZ's suppression property. Existing equipped/built-in suppressors are detected separately.")
            .defineListAllowEmpty(java.util.List.of("integrallySuppressedGuns"),java.util.List.of("ccrp:aac_honeybadger","ccrp:honeybadger","cib:asval","eft:asval"),GunfireConfig::validId);
        b.pop().push("bullet");
        BULLET_ENABLED = b.comment("Light all TaCZ bullets, whether or not a tracer is visible.").define("enabled", true);
        BULLET_RADIUS = b.comment("Bullet light radius in blocks. Bullets farther from a surface than this do not light it.").defineInRange("radius", 4.0, 0.25, 16.0);
        BULLET_INTENSITY = b.comment("Bullet brightness, independent of muzzle brightness.").defineInRange("intensity", 4.0, 0.0, 64.0);
        BULLET_FADE_SECONDS = b.comment("Short visual fade after a bullet is removed, to keep brief flights visible. Zero disables it.").defineInRange("fadeSeconds", 0.06, 0.0, 0.25);
        b.pop().push("model");
        MODEL_LIGHTING = b.comment("Light the first-person gun and hands using their actual rendering projection.").define("enabled", true);
        MODEL_INTENSITY = b.comment("First-person gun/hand light multiplier. World and third-person lighting use the settings above.").defineInRange("intensity", 0.1, 0.0, 4.0);
        b.pop().push("rendering");
        EXPOSURE = b.comment("Overall light brightness multiplier for all light sources.").defineInRange("exposure", 3.0, 0.0, 8.0);
        NIGHT_VISIBILITY = b.comment("Recover visible light response on dark surfaces. Zero keeps their existing darkness; higher values brighten night lighting.").defineInRange("nightVisibility", 0.25, 0.0, 1.0);
        DISTANCE_FALLOFF = b.comment("How quickly light weakens with distance inside its finite radius. Smaller values carry more brightness farther.").defineInRange("distanceFalloff", 0.25, 0.01, 4.0);
        CONTACT_SHADOWS = b.comment("Use visible depth to reduce light bleeding behind nearby surfaces. Turning off is faster.").define("contactShadows", true);
        LIGHTS_PER_TILE = b.comment("Maximum simultaneous lights affecting a screen area. Lower values reduce GPU work during heavy gunfire.").defineInRange("lightsPerTile", 32, 1, 32);
        MAX_DISTANCE = b.comment("Maximum camera distance for selecting lights. Does not change any emitter's radius.").defineInRange("maxDistance", 256.0, 32.0, 512.0);
        b.pop();
        SPEC = b.build();
    }
    private GunfireConfig() {}
    private static boolean validId(Object value){return value instanceof String s&&s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
}
