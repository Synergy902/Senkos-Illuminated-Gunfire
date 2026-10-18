package com.senko.gunfirelight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.render.HandLighting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ItemInHandRenderer.class,remap=false)
abstract class HandLightBaseMixin {
    @Inject(method="m_109314_(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",at=@At("HEAD"),remap=false,require=1)
    private void senko$handBase(float partial,PoseStack pose,MultiBufferSource.BufferSource buffers,LocalPlayer player,int light,CallbackInfo ci){HandLighting.captureBase(pose,partial);}
}
