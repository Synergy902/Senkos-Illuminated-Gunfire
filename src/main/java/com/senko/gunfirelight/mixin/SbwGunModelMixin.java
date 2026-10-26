package com.senko.gunfirelight.mixin;

import com.atsuishio.superbwarfare.item.gun.GunGeoItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.senko.gunfirelight.compat.SbwGunLights;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.renderer.CustomGunRenderer", remap=false)
abstract class SbwGunModelMixin<T extends GunGeoItem & GeoAnimatable> extends GeoItemRenderer<T> {
    protected SbwGunModelMixin(GeoModel<T> model) { super(model); }

    @Inject(method="defaultRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/atsuishio/superbwarfare/item/gun/GunGeoItem;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFI)V", at=@At("HEAD"), require=1)
    private void senko$begin(CallbackInfo ci) { SbwGunLights.beginModel(currentItemStack, renderPerspective); }

    @Inject(method="defaultRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/atsuishio/superbwarfare/item/gun/GunGeoItem;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFI)V", at=@At("RETURN"), require=1)
    private void senko$end(CallbackInfo ci) { SbwGunLights.endModel(); }

    @Inject(method="illuminatedRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/atsuishio/superbwarfare/item/gun/GunGeoItem;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FIIFFFF)V", at=@At("HEAD"), require=1)
    private void senko$bone(PoseStack pose, GunGeoItem item, GeoBone bone, RenderType type, MultiBufferSource buffers, VertexConsumer vertices, float partial, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        SbwGunLights.captureBone(pose, bone);
    }
}
