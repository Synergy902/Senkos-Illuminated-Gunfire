package com.senko.gunfirelight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.source.GunfireSources;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class CaptureFirstPersonMuzzleMixin {
    @Inject(method = "cacheMuzzlePosition(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/tacz/guns/client/model/BedrockGunModel;)V", at = @At("RETURN"), require = 1)
    private static void senko$capture(PoseStack pose, BedrockGunModel model, CallbackInfo ci) {
        GunfireSources.captureFirstPerson(model);
    }
}
