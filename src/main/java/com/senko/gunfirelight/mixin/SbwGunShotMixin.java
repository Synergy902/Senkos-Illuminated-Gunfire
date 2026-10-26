package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.compat.SbwGunLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.event.ClientEventHandler", remap=false)
abstract class SbwGunShotMixin {
    // This invocation is past the client fire checks and the successful shooting packet.
    @Inject(method="handleClientShoot()V", at=@At(value="INVOKE", target="Lcom/atsuishio/superbwarfare/client/lighting/MuzzleFlashHelper;calculateFromStack(Lnet/minecraft/world/item/ItemStack;)Lcom/atsuishio/superbwarfare/client/lighting/MuzzleFlashHelper$FlashParams;"), require=1)
    private void senko$shot(CallbackInfo ci) { SbwGunLights.localShot(); }
}
