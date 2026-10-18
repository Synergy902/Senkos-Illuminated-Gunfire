package com.senko.gunfirelight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.senko.gunfirelight.render.GunfireLightRenderer;
import com.senko.gunfirelight.render.HandLighting;
import com.senko.gunfirelight.source.GunfireSources;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import net.minecraftforge.fml.DistExecutor;

@Mod(SenkosGunfireLight.ID)
public final class SenkosGunfireLight {
    public static final String ID = "senkosgunfirelight";
    public static final Logger LOG = LogManager.getLogger("SenkosGunfireLight");
    public SenkosGunfireLight() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GunfireConfig.SPEC, "senkos-gunfire-light-client.toml");
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientConfigRegistration::register);
        LOG.info("Senkos Illuminated Gunfire 0.2.7: continuous GPU point lighting; client-only TaCZ integration");
    }
    @Mod.EventBusSubscriber(modid=ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Client.reset());
        }
    }
    @Mod.EventBusSubscriber(modid=ID, value=Dist.CLIENT)
    public static final class Client {
        private static final GunfireLightRenderer RENDERER = new GunfireLightRenderer();
        private static final float[] LIGHTS = new float[256 * 8];
        private static final Matrix4f PROJECTION=new Matrix4f(), VIEW=new Matrix4f();
        private static boolean shaderPending;
        private static Object world;
        private static Object cameraWorld;
        private static boolean failed;
        private static int submitted;
        private static String lastStatus = "idle";
        private static long lastStatusAt;
        private static void configure() {
            GunfireSources.configure(GunfireConfig.MUZZLE_ENABLED.get(),GunfireConfig.MUZZLE_RADIUS.get().floatValue(),GunfireConfig.MUZZLE_INTENSITY.get().floatValue(),
                GunfireConfig.SUPPRESSED_RADIUS.get().floatValue(),GunfireConfig.SUPPRESSED_INTENSITY.get().floatValue(),GunfireConfig.MUZZLE_SECONDS.get().floatValue(),
                GunfireConfig.SUPPRESSED_SECONDS.get().floatValue(),GunfireConfig.BULLET_ENABLED.get(),GunfireConfig.BULLET_RADIUS.get().floatValue(),
                GunfireConfig.BULLET_INTENSITY.get().floatValue(),GunfireConfig.BULLET_FADE_SECONDS.get().floatValue(),GunfireConfig.MAX_DISTANCE.get().floatValue());
            GunfireSources.configureFlashHider(GunfireConfig.FLASH_HIDER_RADIUS.get().floatValue(),GunfireConfig.FLASH_HIDER_INTENSITY.get().floatValue(),GunfireConfig.FLASH_HIDER_SECONDS.get().floatValue());
            com.senko.gunfirelight.source.MuzzleProfiles.configure(GunfireConfig.FLASH_HIDER_IDS.get(),GunfireConfig.INTEGRAL_GUN_IDS.get());
            RENDERER.configure(GunfireConfig.EXPOSURE.get().floatValue(),GunfireConfig.NIGHT_VISIBILITY.get().floatValue(),
                GunfireConfig.DISTANCE_FALLOFF.get().floatValue(),GunfireConfig.CONTACT_SHADOWS.get(),GunfireConfig.LIGHTS_PER_TILE.get());
            HandLighting.configure(GunfireConfig.ENABLED.get() && GunfireConfig.MODEL_LIGHTING.get(),GunfireConfig.MODEL_INTENSITY.get().floatValue(),
                GunfireConfig.EXPOSURE.get().floatValue(),GunfireConfig.NIGHT_VISIBILITY.get().floatValue(),GunfireConfig.DISTANCE_FALLOFF.get().floatValue());
        }
        @SubscribeEvent public static void shot(GunFireEvent event) {
            if (!event.getLogicalSide().isClient() || !GunfireConfig.ENABLED.get()) return;
            configure(); GunfireSources.onGunFire(event);
        }
        @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
            if(GunfireConfig.ENABLED.get() && GunfireConfig.BULLET_ENABLED.get())GunfireSources.onEntityJoin(event);
        }
        @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
            if(GunfireConfig.ENABLED.get())GunfireSources.onEntityLeave(event);
        }
        @SubscribeEvent public static void livingPre(RenderLivingEvent.Pre<?,?> event) { GunfireSources.onLivingPre(event); }
        @SubscribeEvent public static void livingPost(RenderLivingEvent.Post<?,?> event) { GunfireSources.onLivingPost(event); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderLevelStageEvent event) {
            if(OculusCompat.isRenderShadow())return;
            if (event.getStage()==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
                shaderPending=false;
                cameraWorld=null;
                configure();HandLighting.beginFrame();
                if(!GunfireConfig.ENABLED.get()){GunfireSources.clear();return;}
                // Forge 1.20.1 AFTER_LEVEL carries GameRenderer's projection
                // PoseStack, NOT LevelRenderer's camera/view stack. Capture the
                // actual world matrices here, while terrain is being rendered.
                PROJECTION.set(event.getProjectionMatrix());
                VIEW.set(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().m_85850_().m_252922_());
                cameraWorld=Minecraft.m_91087_().f_91073_;
                GunfireSources.captureWorldView(event.getPoseStack().m_85850_().m_252922_());
                return;
            }
            boolean shaders=OculusCompat.isUsingRenderPack();
            // Oculus finalizes inside LevelRenderer, before Forge AFTER_LEVEL.
            // Prepare depth/sources before that callback; apply only after its
            // final composite. Native rendering still applies at AFTER_LEVEL.
            if (event.getStage()!=(shaders?RenderLevelStageEvent.Stage.AFTER_WEATHER:RenderLevelStageEvent.Stage.AFTER_LEVEL)) return;
            shaderPending=false;
            Minecraft mc=Minecraft.m_91087_();
            if (mc.f_91073_==null) return;
            if (world!=mc.f_91073_) { world=mc.f_91073_; failed=false; RENDERER.invalidate(); }
            if (!GunfireConfig.ENABLED.get() || failed) {submitted=0;return;}
            if(cameraWorld!=mc.f_91073_) {submitted=0;return;}
            cameraWorld=null; // A captured world view is consumed once, never reused across frames.
            try {
                configure();
                if(shaders)HandLighting.captureShaderDepth();
                var camera=event.getCamera().m_90583_();
                submitted=GunfireSources.collect(event.getPartialTick(),camera.f_82479_,camera.f_82480_,camera.f_82481_,LIGHTS,256);
                if (submitted==0) return;
                if(shaders) {
                    // Preserve world depth before Presence/post effects. Shader packs
                    // finish their material/composite stages before Forge AFTER_LEVEL.
                    shaderPending=RENDERER.captureDepth();
                } else RENDERER.render(PROJECTION,VIEW,LIGHTS,submitted);
                if(!shaderPending)logStatus();
            } catch (RuntimeException | LinkageError ex) {
                fail(ex);
            }
        }
        /** Optional Oculus callback: resolved color is now ready, before scope capture. */
        public static void finishShaderFrame(int noHandDepth) {
            boolean renderWorld=shaderPending;
            shaderPending=false;
            try {
                if(renderWorld){RENDERER.renderCaptured(PROJECTION,VIEW,LIGHTS,submitted,noHandDepth);logStatus();}
                HandLighting.finishShaderFrame(noHandDepth);
            }
            catch(RuntimeException|LinkageError ex){fail(ex);}
        }
        private static void logStatus(){
            String status=RENDERER.status();
            long now=System.nanoTime();
            if(now-lastStatusAt<1_000_000_000L)return;
            if(!status.equals(lastStatus)||now-lastStatusAt>=5_000_000_000L){
                lastStatus=status;lastStatusAt=now;
                LOG.info("Gunfire lighting renderer: {}; tracked bullets={}, joined/finished={}/{}, surface hits={}, submitted={}, uploaded={}, culled={}",
                    status,GunfireSources.trackedBullets(),GunfireSources.joinedBullets(),GunfireSources.finishedBullets(),
                    GunfireSources.clippedBulletEnds(),submitted,RENDERER.uploadedLights(),RENDERER.rejectedLights());
            }
        }
        private static void fail(Throwable ex){
            shaderPending=false;failed=true;LOG.error("Gunfire lighting disabled until resource reload to preserve rendering",ex);
        }
        @SubscribeEvent public static void unload(LevelEvent.Unload event) {if(event.getLevel().m_5776_())reset();}
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {reset();}
        public static void reset() {
            if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(Client::reset);return;}
            GunfireSources.clear();world=null;cameraWorld=null;submitted=0;failed=false;shaderPending=false;lastStatus="idle";lastStatusAt=0;
            RENDERER.invalidate();
            HandLighting.reset();
        }
        @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.m_82127_("gunfirelight")
                .then(Commands.m_82127_("status").executes(ctx->{
                    ctx.getSource().m_288197_(() -> Component.m_237113_("Senkos Illuminated Gunfire: enabled="+GunfireConfig.ENABLED.get()+", tracked bullets="+GunfireSources.trackedBullets()+", joined/finished="+GunfireSources.joinedBullets()+"/"+GunfireSources.finishedBullets()+", submitted="+submitted+", tile overflow="+RENDERER.tileOverflow()+", world="+RENDERER.status()+", gun/hands="+HandLighting.status()+(failed?", suspended after error":"")),false);return 1;
                })));
        }
    }
}
