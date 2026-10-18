package com.senko.gunfirelight.mixin;

import com.senko.gunfirelight.GunfireConfig;
import com.senko.gunfirelight.source.GunfireSources;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe real client physics independently of tracer visibility. Never changes the bullet. */
@Mixin(value=EntityKineticBullet.class,remap=false)
abstract class BulletLightTickMixin {
    @Inject(method="m_8119_()V",at=@At("HEAD"),require=1,remap=false)
    private void senko$lightStart(CallbackInfo ci){senko$sample(false);}
    @Inject(method="m_8119_()V",at=@At("TAIL"),require=1,remap=false)
    private void senko$lightEnd(CallbackInfo ci){senko$sample(true);}
    private void senko$sample(boolean after){
        EntityKineticBullet bullet=(EntityKineticBullet)(Object)this;
        if(bullet.m_9236_().m_5776_()&&GunfireConfig.ENABLED.get()&&GunfireConfig.BULLET_ENABLED.get())
            GunfireSources.onBulletTick(bullet,after);
    }
}
