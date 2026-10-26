package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only firing illumination; particles, gameplay and wreck lighting stay native. */
@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.lighting.MuzzleFlashHelper", remap=false)
abstract class SbwNativeMuzzleMixin {
    @Inject(method="spawnFlashCone(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/atsuishio/superbwarfare/client/lighting/MuzzleFlashHelper$FlashParams;)V", at=@At("HEAD"), cancellable=true, require=1)
    private static void senko$gpuMuzzle(CallbackInfo ci) { if (SbwIntegration.active()) ci.cancel(); }
}
