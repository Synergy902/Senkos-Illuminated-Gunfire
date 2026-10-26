package com.senko.gunfirelight.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.MortarEntity;
import com.senko.gunfirelight.compat.SbwIntegration;
import com.senko.gunfirelight.compat.SbwVehicleLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.entity.vehicle.MortarEntity", remap=false)
public abstract class SbwVehicleMortarMixin {
    @Inject(method="m_6075_()V", at=@At("RETURN"), require=1)
    private void senko$mortarTick(CallbackInfo ci) {
        if (SbwIntegration.active()) SbwVehicleLights.onMortarTick((MortarEntity)(Object)this);
    }
}
