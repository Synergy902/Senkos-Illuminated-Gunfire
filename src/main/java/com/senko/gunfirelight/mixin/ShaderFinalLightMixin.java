package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.SenkosGunfireLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Oculus final color, after composites and before the single-world scope capture. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
abstract class ShaderFinalLightMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Inject(method="finalizeLevelRendering()V",at=@At("RETURN"),require=1,remap=false)
    private void senko$illuminateResolvedScene(CallbackInfo ci) {
        SenkosGunfireLight.Client.finishShaderFrame(renderTargets.getDepthTextureNoHand().getTextureId());
    }
}
