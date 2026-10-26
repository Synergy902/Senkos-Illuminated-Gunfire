package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.lighting.VehicleLightingHandler", remap=false)
abstract class SbwNativeVehicleMixin {
    @Inject(method="onVehicleFire(Lcom/atsuishio/superbwarfare/api/event/ClientVehicleFireEvent;)V", at=@At("HEAD"), cancellable=true, require=1)
    private static void senko$gpuVehicleMuzzle(CallbackInfo ci) { if (SbwIntegration.active()) ci.cancel(); }
}
