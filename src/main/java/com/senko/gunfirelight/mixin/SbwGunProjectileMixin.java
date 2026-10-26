package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwGunLights;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.lighting.ClientLightingHandler", remap=false)
abstract class SbwGunProjectileMixin {
    @Inject(method="handleProjectileAdded(Lnet/minecraft/world/entity/Entity;)V", at=@At("HEAD"), require=1)
    private static void senko$remoteShot(Entity entity, CallbackInfo ci) { SbwGunLights.projectileAdded(entity); }
}
