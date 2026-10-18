package com.senko.gunfirelight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.render.HandLighting;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture this frame's animated barrel before the model consumes/cleans its animation. */
@Mixin(value=BedrockGunModel.class,remap=false)
abstract class CaptureHandLightPoseMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",at=@At("HEAD"),remap=false,require=1)
    private void senko$handBarrel(PoseStack pose,ItemStack stack,ItemDisplayContext context,RenderType type,int light,int overlay,CallbackInfo ci){
        HandLighting.captureGun(pose,(BedrockGunModel)(Object)this,context);
    }
}
