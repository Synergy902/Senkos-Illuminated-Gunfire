package com.senko.gunfirelight;

import me.shedaniel.clothconfig2.api.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Ordinary player controls, applied on Save without reloading graphics resources. */
public final class GunfireConfigScreen {
    public static Screen create(Screen parent) {
        var b=ConfigBuilder.create().setParentScreen(parent).setTitle(text("Senkos Illuminated Gunfire"))
            .setSavingRunnable(GunfireConfig.SPEC::save).setShouldListSmoothScroll(true);
        var e=b.entryBuilder();
        var general=b.getOrCreateCategory(text("General"));
        toggle(general,e,"Enable gunfire lighting",GunfireConfig.ENABLED,"Changes take effect after saving. These are visual client settings.");
        number(general,e,"Overall brightness",GunfireConfig.EXPOSURE,0,8,"Multiplier for all gunfire lights. Default: 3.0.");
        number(general,e,"Night visibility",GunfireConfig.NIGHT_VISIBILITY,0,1,"Helps lights reveal dark surfaces. Increase for stronger night lighting.");
        var muzzle=b.getOrCreateCategory(text("Muzzle flash"));
        toggle(muzzle,e,"Muzzle flash lighting",GunfireConfig.MUZZLE_ENABLED,"Turn barrel light on or off. TaCZ's visible flash particles are unchanged.");
        number(muzzle,e,"Muzzle brightness",GunfireConfig.MUZZLE_INTENSITY,0,64,"Peak brightness of an unsuppressed shot. Default: 8.");
        number(muzzle,e,"Muzzle light range (blocks)",GunfireConfig.MUZZLE_RADIUS,.5,32,"Light fades smoothly and reaches zero at this distance from the barrel.");
        number(muzzle,e,"Flash duration (seconds)",GunfireConfig.MUZZLE_SECONDS,.02,.5,"How long each muzzle light fades. Default: 0.12 seconds.");
        var suppressed=b.getOrCreateCategory(text("Suppressed shots"));
        number(suppressed,e,"Suppressed brightness",GunfireConfig.SUPPRESSED_INTENSITY,0,64,"Used only when TaCZ identifies the gun as suppressed. Zero disables suppressed muzzle light.");
        number(suppressed,e,"Suppressed range (blocks)",GunfireConfig.SUPPRESSED_RADIUS,.25,32,"Separate light range for suppressed shots.");
        number(suppressed,e,"Suppressed duration (seconds)",GunfireConfig.SUPPRESSED_SECONDS,.01,.5,"Separate flash duration for suppressed shots.");
        var flash=b.getOrCreateCategory(text("Flash hiders"));
        number(flash,e,"Flash-hider brightness",GunfireConfig.FLASH_HIDER_INTENSITY,0,64,"Reduced muzzle light for flash hiders and identified hybrids. Does not dim bullets.");
        number(flash,e,"Flash-hider range (blocks)",GunfireConfig.FLASH_HIDER_RADIUS,.25,32,"Default: 3 blocks. A muzzle brake alone keeps the normal light profile.");
        number(flash,e,"Flash-hider duration (seconds)",GunfireConfig.FLASH_HIDER_SECONDS,.01,.5,"Default: 0.05 seconds. These defaults approximate real effects; devices vary.");
        var packs=b.getOrCreateCategory(text("Gunpack overrides"));
        ids(packs,e,"Extra flash-hider attachment IDs",GunfireConfig.FLASH_HIDER_IDS,"For custom attachments without an identifiable flash-hider ID. Use namespace:attachment_id.");
        ids(packs,e,"Integrally suppressed gun IDs",GunfireConfig.INTEGRAL_GUN_IDS,"For built-in suppressors whose gunpack lacks suppression metadata. Use namespace:gun_id. These settings apply to everyone seen on your screen.");
        var bullet=b.getOrCreateCategory(text("Bullets"));
        toggle(bullet,e,"Bullet lighting",GunfireConfig.BULLET_ENABLED,"Every tracked TaCZ bullet can emit light, with or without a visible tracer.");
        number(bullet,e,"Bullet brightness",GunfireConfig.BULLET_INTENSITY,0,64,"Brightness of moving bullet lights. Independent of muzzle brightness.");
        number(bullet,e,"Bullet light range (blocks)",GunfireConfig.BULLET_RADIUS,.25,16,"A bullet farther than this from a surface does not illuminate it.");
        number(bullet,e,"Removal fade (seconds)",GunfireConfig.BULLET_FADE_SECONDS,0,.25,"Brief fade at the end of a bullet's flight keeps fast shots visible. Zero disables the fade.");
        var model=b.getOrCreateCategory(text("Gun and hands"));
        toggle(model,e,"Light first-person gun and hands",GunfireConfig.MODEL_LIGHTING,"Adds spatial lighting to the held gun and hands. Other players use normal world-surface lighting.");
        number(model,e,"Gun and hand brightness",GunfireConfig.MODEL_INTENSITY,0,4,"Multiplier for first-person model lighting. World brightness is unaffected.");
        var performance=b.getOrCreateCategory(text("Light behavior"));
        number(performance,e,"Distance falloff",GunfireConfig.DISTANCE_FALLOFF,.01,4,"Smaller values keep light brighter farther from its source, within the same range.");
        toggle(performance,e,"Contact shadows",GunfireConfig.CONTACT_SHADOWS,"Reduce light bleeding behind visible surfaces. Disabling can improve performance.");
        performance.addEntry(e.startIntSlider(text("Lights per screen area"),GunfireConfig.LIGHTS_PER_TILE.get(),1,32)
            .setDefaultValue(GunfireConfig.LIGHTS_PER_TILE.getDefault()).setSaveConsumer(GunfireConfig.LIGHTS_PER_TILE::set)
            .setTooltip(text("Limits overlapping lights during heavy gunfire. Lower values save GPU work; the strongest lights are prioritized.")).build());
        number(performance,e,"Light selection distance (blocks)",GunfireConfig.MAX_DISTANCE,32,512,"Maximum distance from your camera to track visible light contributions. Does not extend individual light range.");
        return b.build();
    }
    private static Component text(String s){return Component.m_237113_(s);}
    private static void ids(ConfigCategory c,ConfigEntryBuilder e,String label,ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> value,String help){
        c.addEntry(e.startStrList(text(label),new java.util.ArrayList<>(value.get()))
            .setDefaultValue(new java.util.ArrayList<>(value.getDefault())).setSaveConsumer(value::set)
            .setCellErrorSupplier(s->s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")?java.util.Optional.empty():java.util.Optional.of(text("Use namespace:id (lowercase).")))
            .setTooltip(text(help)).build());
    }
    private static void toggle(ConfigCategory c,ConfigEntryBuilder e,String label,ForgeConfigSpec.BooleanValue value,String help){
        c.addEntry(e.startBooleanToggle(text(label),value.get()).setDefaultValue(value.getDefault())
            .setTooltip(text(help)).setSaveConsumer(value::set).build());
    }
    private static void number(ConfigCategory c,ConfigEntryBuilder e,String label,ForgeConfigSpec.DoubleValue value,double min,double max,String help){
        c.addEntry(e.startDoubleField(text(label),value.get()).setDefaultValue(value.getDefault()).setMin(min).setMax(max)
            .setTooltip(text(help)).setSaveConsumer(value::set).build());
    }
    private GunfireConfigScreen() {}
}
