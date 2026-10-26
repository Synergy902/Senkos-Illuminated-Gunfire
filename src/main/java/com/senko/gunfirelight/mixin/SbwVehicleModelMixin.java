package com.senko.gunfirelight.mixin;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.VehicleModelEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.compat.SbwIntegration;
import com.senko.gunfirelight.compat.SbwVehicleLights;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer",remap=false)
abstract class SbwVehicleModelMixin {
    @Inject(method="render(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at=@At(value="INVOKE",target="Lcom/atsuishio/superbwarfare/client/renderer/entity/GeoVehicleRenderer;transformCustomModelPart(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;Lcom/atsuishio/superbwarfare/client/model/entity/VehicleModelInstance;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",shift=At.Shift.AFTER),
        locals=LocalCapture.CAPTURE_FAILHARD,require=1)
    private void senko$visualMuzzles(VehicleEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci,VehicleModelEntry entry,VehicleModelInstance model){
        // Capture after virtual dispatch, including each renderer's post-super edits.
        if(SbwIntegration.active())SbwVehicleLights.captureModel(entity,model,pose,partial);
    }
}
