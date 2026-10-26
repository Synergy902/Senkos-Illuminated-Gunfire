package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwGunLights;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.lighting.MuzzleFlashHelper", remap=false)
abstract class SbwGunToolMixin {
    @Inject(method="spawnToolFlash(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at=@At("HEAD"), require=1)
    private static void senko$tool(Player player, ItemStack stack, CallbackInfo ci) { SbwGunLights.toolShot(player, stack); }
}
