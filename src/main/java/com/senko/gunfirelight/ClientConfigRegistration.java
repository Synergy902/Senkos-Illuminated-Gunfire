package com.senko.gunfirelight;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

/** Isolated client registration; Cloth is optional outside this modpack. */
public final class ClientConfigRegistration {
    public static void register() {
        if(ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft,parent) -> GunfireConfigScreen.create(parent)));
        }
    }
    private ClientConfigRegistration() {}
}
