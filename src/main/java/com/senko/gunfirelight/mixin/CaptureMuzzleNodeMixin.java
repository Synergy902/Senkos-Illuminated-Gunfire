package com.senko.gunfirelight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senko.gunfirelight.source.GunfireSources;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MuzzleFlashRender.class, remap = false)
public abstract class CaptureMuzzleNodeMixin {
    @Shadow @Final private BedrockGunModel bedrockGunModel;
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V", at = @At("HEAD"), require = 1)
    private void senko$capture(PoseStack pose, VertexConsumer consumer, ItemDisplayContext context, int light, int overlay, CallbackInfo ci) {
        GunfireSources.captureMuzzleNode(pose, context, bedrockGunModel);
    }
}
