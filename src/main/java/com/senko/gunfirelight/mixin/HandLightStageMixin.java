package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.render.HandLighting;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after the normal hand buffers flush, including the scope mod's late hand path. */
@Mixin(value=GameRenderer.class,remap=false)
abstract class HandLightStageMixin {
    @Inject(method="m_109120_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LightTexture;m_109891_()V",shift=At.Shift.AFTER),remap=false,require=1)
    private void senko$lightHand(CallbackInfo ci){HandLighting.renderNative();}
}
