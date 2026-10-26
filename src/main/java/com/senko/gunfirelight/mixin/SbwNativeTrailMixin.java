package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.lighting.ProjectileLightHelper", remap=false)
abstract class SbwNativeTrailMixin {
    @Inject(method="emitTrailLight(Lnet/minecraft/world/entity/Entity;)V", at=@At("HEAD"), cancellable=true, require=1)
    private static void senko$gpuTrail(CallbackInfo ci) { if (SbwIntegration.active()) ci.cancel(); }
}
