package com.senko.gunfirelight.mixin;

import com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareOption;
import com.senko.gunfirelight.compat.SbwIntegration;
import com.senko.gunfirelight.compat.SbwVehicleLights;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareParticle$Provider", remap=false)
public abstract class SbwVehicleFlareParticleMixin {
    @Inject(method="createParticle(Lcom/atsuishio/superbwarfare/client/particle/CannonMuzzleFlareOption;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at=@At("HEAD"), require=1)
    private void senko$nativeMuzzle(CannonMuzzleFlareOption option, ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        // Both upstream cannon-muzzle routines emit exactly one life=45 sample
        // with zero spawn radius: read that point, not the randomized plume.
        if (SbwIntegration.active() && option.getLife()==45) SbwVehicleLights.onNativeFlare(x,y,z);
    }
}
