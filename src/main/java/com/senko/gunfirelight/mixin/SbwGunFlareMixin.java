package com.senko.gunfirelight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.compat.SbwGunLights;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.animation.AnimationHelper", remap=false)
abstract class SbwGunFlareMixin {
    @Inject(method="handleShootFlare(Ljava/lang/String;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/MultiBufferSource;IDDDD)V", at=@At("HEAD"), require=1)
    private static void senko$flare(String name, PoseStack pose, ItemStack stack, GeoBone bone, MultiBufferSource buffers, int light, double x, double y, double z, double size, CallbackInfo ci) {
        SbwGunLights.explicitFlare(pose, stack, bone, x, y, z);
    }
}
