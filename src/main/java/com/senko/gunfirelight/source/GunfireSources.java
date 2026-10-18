package com.senko.gunfirelight.source;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** TaCZ client events feed continuous point emitters; no light-engine or terrain rebuild calls. */
public final class GunfireSources {
    private static final int MAX_BULLETS = 4096, MAX_MUZZLES = 512;
    private static final LightRegistry REGISTRY = GunfireLightApi.SOURCES;
    private static final ArrayList<Bullet> BULLETS = new ArrayList<>(256);
    private static final IdentityHashMap<EntityKineticBullet, Bullet> BULLET_INDEX = new IdentityHashMap<>();
    private static final IdentityHashMap<LivingEntity, Muzzle> MUZZLES = new IdentityHashMap<>();
    private static final Vector3f TRANSFORM = new Vector3f();
    private static final PoseStack FALLBACK_POSE = new PoseStack();
    private static final Matrix4f INVERSE_WORLD_VIEW = new Matrix4f();
    private static boolean worldViewValid;
    private static Object level;
    private static LivingEntity renderingOwner;
    private static boolean muzzleEnabled = true, bulletEnabled = true;
    private static float muzzleRadius = 10f, muzzleIntensity = 8f, muzzleDuration = .12f;
    private static float suppressedRadius = 2.5f, suppressedIntensity = .4f, suppressedDuration = .04f;
    private static float bulletRadius = 4f, bulletIntensity = 4f, bulletFadeSeconds = .06f, maximumDistance = 256;
    private static float flashRadius=3f, flashIntensity=.8f, flashDuration=.05f;
    private static long fallbackMuzzles, rejectedBullets, joinedBullets, finishedBullets, clippedBulletEnds;
    private static long clipTick = Long.MIN_VALUE;
    private static int clipsThisTick;

    private GunfireSources() {}
    public static void configureFlashHider(float radius,float intensity,float seconds){
        flashRadius=bounded(radius,.25f,32f,3f);flashIntensity=bounded(intensity,0,64,.8f);flashDuration=bounded(seconds,.01f,.5f,.05f);
    }

    public static void configure(float mr, float mi, float seconds, float br, float bi) {
        configure(true, mr, mi, suppressedRadius, suppressedIntensity, seconds, suppressedDuration,
            true, br, bi, bulletFadeSeconds, maximumDistance);
    }

    public static void configure(boolean me, float mr, float mi, float sr, float si, float seconds,
                                 float suppressedSeconds, boolean be, float br, float bi,
                                 float fadeSeconds, float maxDistance) {
        muzzleEnabled = me; bulletEnabled = be;
        muzzleRadius = bounded(mr, .25f, 32f, 10f);
        muzzleIntensity = bounded(mi, 0f, 64f, 8f);
        muzzleDuration = bounded(seconds, .01f, .5f, .12f);
        suppressedRadius = bounded(sr, .25f, 32f, 2.5f);
        suppressedIntensity = bounded(si, 0f, 64f, .4f);
        suppressedDuration = bounded(suppressedSeconds, .01f, .5f, .04f);
        bulletRadius = bounded(br, .1f, 16f, 4f);
        bulletIntensity = bounded(bi, 0f, 64f, 4f);
        bulletFadeSeconds = bounded(fadeSeconds, 0f, .25f, .06f);
        maximumDistance = bounded(maxDistance, 16f, 1024f, 256f);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        if (event.isCanceled() || event.getLevel() != mc.f_91073_ || !(event.getEntity() instanceof EntityKineticBullet entity)) return;
        ensureLevel(mc);
        if (BULLET_INDEX.containsKey(entity)) return;
        if (BULLETS.size() >= MAX_BULLETS) { rejectedBullets++; return; }
        Bullet b = new Bullet(entity); b.joinEvent = event;
        BULLETS.add(b); BULLET_INDEX.put(entity, b);
        joinedBullets++;
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().m_5776_() || event.getLevel() != Minecraft.m_91087_().f_91073_) return;
        if (event.getEntity() instanceof EntityKineticBullet entity) {
            Bullet b = BULLET_INDEX.get(entity);
            if (b != null) finishBullet(b, Minecraft.m_91087_());
        }
        if (event.getEntity() instanceof LivingEntity entity) {
            Muzzle m = MUZZLES.remove(entity);
            if (m != null) REGISTRY.remove(m.handle);
        }
    }

    /** Keep the light on the actual flight segment, even when a tracer renderer skips it. */
    public static void onBulletTick(EntityKineticBullet entity,boolean after) {
        Minecraft mc=Minecraft.m_91087_();
        if(mc.f_91073_==null||entity.m_9236_()!=mc.f_91073_)return;
        ensureLevel(mc);
        Bullet b=BULLET_INDEX.get(entity);
        if(b==null){
            if(entity.m_213877_()||BULLETS.size()>=MAX_BULLETS)return;
            b=new Bullet(entity);BULLETS.add(b);BULLET_INDEX.put(entity,b);joinedBullets++;
        }
        if(!after){b.sx=entity.m_20185_();b.sy=entity.m_20186_();b.sz=entity.m_20189_();b.sampled=true;return;}
        if(!b.sampled||b.finished)return;
        b.hit=false;
        Vec3 start=new Vec3(b.sx,b.sy,b.sz),end=entity.m_20182_();
        double length2=start.m_82557_(end);
        if(length2<.000001||length2>256*256||!clipBudget(mc,start))return;
        var hit=mc.f_91073_.m_45547_(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity));
        if(hit.m_6662_()==HitResult.Type.MISS)return;
        Vec3 p=hit.m_82450_();var face=hit.m_82434_();
        b.hit=true;b.hx=p.f_82479_+face.m_122429_()*.06;
        b.hy=p.f_82480_+face.m_122430_()*.06;b.hz=p.f_82481_+face.m_122431_()*.06;
        b.hitFraction=Math.min(1,Math.sqrt(start.m_82557_(p)/length2));
        b.hitAt=clock(mc,(float)b.hitFraction);b.lastHit=true;clippedBulletEnds++;
    }

    private static boolean clipBudget(Minecraft mc,Vec3 at){
        Vec3 camera=mc.f_91063_.m_109153_().m_90583_();double reach=maximumDistance+bulletRadius;
        if(camera.m_82557_(at)>reach*reach)return false;
        long tick=mc.f_91073_.m_46467_();
        if(clipTick!=tick){clipTick=tick;clipsThisTick=0;}
        if(clipsThisTick>=64)return false;
        clipsThisTick++;return true;
    }

    public static void onGunFire(GunFireEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        if (!muzzleEnabled || !event.getLogicalSide().isClient() || event.isCanceled() || mc.f_91073_ == null) return;
        LivingEntity shooter = event.getShooter();
        if (shooter == null || shooter.m_9236_() != mc.f_91073_) return;
        ensureLevel(mc);
        Muzzle m = muzzle(shooter);
        if (m == null) return;
        // Scheduled burst tasks can arrive between ticks. The last rendered
        // simulation time avoids both 20 Hz ignition quantization and using a
        // stale partial tick from before the world tick advanced.
        double now = Math.max(clock(mc, 0), GunfireLightApi.clock);
        m.shotAt = now; m.gun = event.getGunItemStack(); m.pendingEvent = event;
        m.suppressed = isSuppressed(shooter, m.gun, m);
        IGun gun=IGun.getIGunOrNull(m.gun);
        var attachment=gun==null?null:gun.getAttachmentId(m.gun,AttachmentType.MUZZLE);
        if(gun!=null&&(attachment==null||DefaultAssets.isEmptyAttachmentId(attachment)))attachment=gun.getBuiltInAttachmentId(m.gun,AttachmentType.MUZZLE);
        m.flashHidden=!m.suppressed&&attachment!=null&&MuzzleProfiles.flashHider(attachment.toString());
        REGISTRY.remove(m.handle); m.handle = 0;
        // The pose is resolved at render time, after interpolation. Sustained fire
        // refreshes one bulb per gun rather than accumulating same-position lights.
    }

    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) { renderingOwner = event.getEntity(); }
    public static void onLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (renderingOwner == event.getEntity()) renderingOwner = null;
    }

    /** Capture after solid terrain, before living renderers, including camera bob/hurt transforms. */
    public static void captureWorldView(Matrix4f view) {
        Minecraft mc = Minecraft.m_91087_();
        ensureLevel(mc);
        if (mc.f_91073_ == null) { worldViewValid = false; return; }
        GunfireLightApi.clock = clock(mc, mc.m_91296_());
        worldViewValid = view != null && view.isFinite() && Math.abs(view.determinant()) > 1e-8f;
        if (worldViewValid) INVERSE_WORLD_VIEW.set(view).invert();
    }

    /** Runs after TaCZ caches the actual animated muzzle and corrects its hand/world FOV. */
    public static void captureFirstPerson(BedrockGunModel model) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91073_ == null || model.getMuzzleFlashPosPath() == null
                || OculusCompat.isRenderShadow()) return;
        ensureLevel(mc);
        Muzzle m = muzzle(mc.f_91074_); if (m == null) return;
        Vector3f p = GunItemRendererWrapper.muzzleRenderOffset;
        if (!finite(p.x, p.y, p.z) || p.lengthSquared() > 64) return;
        m.ox = p.x; m.oy = p.y; m.oz = p.z; m.firstPerson = true;
        m.capturedAt = clock(mc, mc.m_91296_()); m.capturedGun = gunId(model.getCurrentGunItem());
    }

    /** Pose arrives at the native muzzle_flash node, including full model hierarchy. */
    public static void captureMuzzleNode(PoseStack pose, ItemDisplayContext context, BedrockGunModel model) {
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) return;
        Minecraft mc = Minecraft.m_91087_();
        LivingEntity owner = renderingOwner;
        if (owner == null || mc.f_91073_ == null || owner.m_9236_() != mc.f_91073_ || OculusCompat.isRenderShadow()) return;
        ensureLevel(mc);
        float partial = mc.m_91296_();
        TRANSFORM.set(pose.m_85850_().m_252922_().m30(), pose.m_85850_().m_252922_().m31(), pose.m_85850_().m_252922_().m32());
        if (!finite(TRANSFORM.x, TRANSFORM.y, TRANSFORM.z)) return;
        if (worldViewValid) INVERSE_WORLD_VIEW.transformPosition(TRANSFORM);
        else TRANSFORM.rotate(mc.f_91063_.m_109153_().m_253121_());
        Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
        double ox = camera.f_82479_ + TRANSFORM.x - lerp(owner.f_19854_, owner.m_20185_(), partial);
        double oy = camera.f_82480_ + TRANSFORM.y - lerp(owner.f_19855_, owner.m_20186_(), partial);
        double oz = camera.f_82481_ + TRANSFORM.z - lerp(owner.f_19856_, owner.m_20189_(), partial);
        if (ox * ox + oy * oy + oz * oz > 100) return; // Reject GUI/non-world pose contamination.
        Muzzle m = muzzle(owner); if (m == null) return;
        m.ox = (float)ox; m.oy = (float)oy; m.oz = (float)oz; m.firstPerson = false;
        m.capturedAt = clock(mc, partial); m.capturedGun = gunId(model.getCurrentGunItem());
    }

    public static int collect(float partial, double cx, double cy, double cz, float[] output, int limit) {
        updateSources(partial, cx, cy, cz);
        return REGISTRY.collect(GunfireLightApi.clock, cx, cy, cz, maximumDistance, output, limit);
    }

    public static int collectForHand(float partial, double cx, double cy, double cz, float[] output, int limit) {
        updateSources(partial, cx, cy, cz);
        Muzzle own = MUZZLES.get(Minecraft.m_91087_().f_91074_);
        return REGISTRY.collectExcluding(GunfireLightApi.clock, cx, cy, cz, maximumDistance,
            output, limit, own == null ? 0 : own.handle);
    }

    /** Radius and RGB radiance of the current first-person flash; caller supplies its exact current pose. */
    public static boolean localMuzzle(float partial, float[] output) {
        Minecraft mc = Minecraft.m_91087_();
        if (output == null || output.length < 4 || !muzzleEnabled || mc.f_91073_ == null || mc.f_91074_ == null) return false;
        Muzzle m = MUZZLES.get(mc.f_91074_);
        if (m == null || m.pendingEvent != null && m.pendingEvent.isCanceled()) return false;
        float energy = muzzleEnergy(m, clock(mc, bounded(partial, 0, 1, 0)));
        if (energy <= 0) return false;
        output[0] = muzzleRadius(m);
        output[1] = energy; output[2] = energy * .57f; output[3] = energy * .2f;
        return true;
    }

    private static void updateSources(float partial, double cx, double cy, double cz) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) { clear(); return; }
        ensureLevel(mc);
        partial = bounded(partial, 0, 1, 0);
        double now = clock(mc, partial); GunfireLightApi.clock = now;
        for (int i = BULLETS.size() - 1; i >= 0; i--) {
            Bullet b = BULLETS.get(i); EntityKineticBullet e = b.entity;
            boolean canceledJoin = b.joinEvent != null && b.joinEvent.isCanceled();
            b.joinEvent = null;
            if (canceledJoin || e.m_9236_() != mc.f_91073_) {
                REGISTRY.remove(b.handle); BULLET_INDEX.remove(e); BULLETS.remove(i); continue;
            }
            if (e.m_213877_() && !b.finished) finishBullet(b, mc);
            float energy = bulletIntensity;
            if (b.finished) {
                energy *= BulletLightEnvelope.terminal(now - b.finishedAt, bulletFadeSeconds);
                if (energy <= 0) {
                    REGISTRY.remove(b.handle); BULLET_INDEX.remove(e); BULLETS.remove(i); continue;
                }
            }
            if (!bulletEnabled || energy <= 0) { REGISTRY.remove(b.handle); b.handle = 0; continue; }
            double x = lerp(e.f_19854_, e.m_20185_(), partial);
            double y = lerp(e.f_19855_, e.m_20186_(), partial);
            double z = lerp(e.f_19856_, e.m_20189_(), partial);
            if(b.sampled){x=lerp(b.sx,e.m_20185_(),partial);y=lerp(b.sy,e.m_20186_(),partial);z=lerp(b.sz,e.m_20189_(),partial);}
            if (e.f_19797_ == 0) { x = e.m_20185_(); y = e.m_20186_(); z = e.m_20189_(); }
            if(!b.finished&&b.hit&&partial>=b.hitFraction){
                x=b.hx;y=b.hy;z=b.hz;
                energy*=BulletLightEnvelope.terminal(Math.max(0,now-b.hitAt),bulletFadeSeconds);
            }
            if (b.finished) { x = b.x; y = b.y; z = b.z; }
            else { b.x = x; b.y = y; b.z = z; b.rendered = true; }
            if (!REGISTRY.update(b.handle, x, y, z, bulletRadius, 1f, .50f, .18f, energy))
                b.handle = REGISTRY.create(x, y, z, bulletRadius, 1f, .50f, .18f, energy,
                    now, Double.POSITIVE_INFINITY, false, 1);
        }
        Iterator<Map.Entry<LivingEntity, Muzzle>> it = MUZZLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Muzzle> entry = it.next(); LivingEntity owner = entry.getKey(); Muzzle m = entry.getValue();
            if (m.pendingEvent != null) {
                if (m.pendingEvent.isCanceled()) m.shotAt = Double.NEGATIVE_INFINITY;
                m.pendingEvent = null;
            }
            if (owner.m_213877_() || owner.m_9236_() != mc.f_91073_
                    || now - Math.max(m.capturedAt, m.shotAt) > 5) {
                REGISTRY.remove(m.handle); it.remove(); continue;
            }
            float energy = muzzleEnabled ? muzzleEnergy(m, now) : 0;
            if (energy <= 0) { REGISTRY.remove(m.handle); m.handle = 0; continue; }
            boolean ownFirst = owner == mc.f_91074_ && mc.f_91066_.m_92176_().m_90612_();
            if (m.firstPerson == ownFirst && m.capturedGun != null && m.capturedGun.equals(gunId(m.gun))
                    && now - m.capturedAt >= 0 && now - m.capturedAt <= .15) {
                if (ownFirst) {
                    TRANSFORM.set(m.ox, m.oy, m.oz).rotate(mc.f_91063_.m_109153_().m_253121_());
                    m.x = cx + TRANSFORM.x; m.y = cy + TRANSFORM.y; m.z = cz + TRANSFORM.z;
                } else {
                    m.x = lerp(owner.f_19854_, owner.m_20185_(), partial) + m.ox;
                    m.y = lerp(owner.f_19855_, owner.m_20186_(), partial) + m.oy;
                    m.z = lerp(owner.f_19856_, owner.m_20189_(), partial) + m.oz;
                }
            } else fallback(owner, m, partial);
            float radius = muzzleRadius(m);
            if (!REGISTRY.update(m.handle, m.x, m.y, m.z, radius, 1f, .57f, .2f, energy))
                m.handle = REGISTRY.create(m.x, m.y, m.z, radius, 1f, .57f, .2f, energy,
                    now, Double.POSITIVE_INFINITY, false, ownFirst ? 8 : 4);
        }
    }

    public static void clear() {
        BULLETS.clear(); BULLET_INDEX.clear(); MUZZLES.clear(); REGISTRY.clear();
        level = null; renderingOwner = null; worldViewValid = false; GunfireLightApi.clock = 0;
        clipTick = Long.MIN_VALUE; clipsThisTick = 0;
    }
    public static int trackedBullets() { return BULLETS.size(); }
    public static int trackedSources() { return REGISTRY.size(); }
    public static long fallbackMuzzles() { return fallbackMuzzles; }
    public static long rejectedBullets() { return rejectedBullets; }
    public static long joinedBullets() { return joinedBullets; }
    public static long finishedBullets() { return finishedBullets; }
    public static long clippedBulletEnds() { return clippedBulletEnds; }

    private static float muzzleEnergy(Muzzle m, double now) {
        float duration = m.suppressed ? suppressedDuration : m.flashHidden ? flashDuration : muzzleDuration;
        float intensity = m.suppressed ? suppressedIntensity : m.flashHidden ? flashIntensity : muzzleIntensity;
        return intensity * BulletLightEnvelope.terminal(now - m.shotAt, duration);
    }
    private static float muzzleRadius(Muzzle m){return m.suppressed?suppressedRadius:m.flashHidden?flashRadius:muzzleRadius;}

    private static boolean isSuppressed(LivingEntity shooter, ItemStack stack, Muzzle muzzle) {
        Object id=gunId(stack);
        if(id!=null&&MuzzleProfiles.integral(id.toString()))return true;
        // TaCZ's SilenceModifier boolean drives silence sound and its own muzzle
        // flash suppression. A muzzle brake alone is not a suppressed weapon.
        var cache = IGunOperator.fromLivingEntity(shooter).getCacheProperty();
        if (cache != null) {
            var silence = cache.getCache(GunProperties.SILENCE);
            if (silence != null && Boolean.TRUE.equals(silence.right())) return true;
        }
        // Remote packets can arrive without a local ShooterDataHolder property
        // cache. Read the packet's selected/built-in muzzle exactly as TaCZ's
        // muzzle renderer does, including a gunpack's exclusive override.
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        var gunId = gun.getGunId(stack);
        var attachmentId = gun.getAttachmentId(stack, AttachmentType.MUZZLE);
        if (attachmentId == null || DefaultAssets.isEmptyAttachmentId(attachmentId))
            attachmentId = gun.getBuiltInAttachmentId(stack, AttachmentType.MUZZLE);
        if (attachmentId == null || DefaultAssets.isEmptyAttachmentId(attachmentId)) return false;
        if (gunId.equals(muzzle.suppressionGun) && attachmentId.equals(muzzle.suppressionAttachment))
            return muzzle.fallbackSuppressed;
        AttachmentData data = null;
        var commonGun = TimelessAPI.getCommonGunIndex(gunId);
        if (commonGun.isPresent()) data = commonGun.get().getGunData().getExclusiveAttachments().get(attachmentId);
        if (data == null) {
            var commonAttachment = TimelessAPI.getCommonAttachmentIndex(attachmentId);
            if (commonAttachment.isPresent()) data = commonAttachment.get().getData();
        }
        boolean suppressed = false;
        if (data != null) {
            var property = data.getModifier().get(SilenceModifier.ID);
            if (property != null && property.getValue() instanceof it.unimi.dsi.fastutil.Pair<?,?> pair)
                suppressed = Boolean.TRUE.equals(pair.right());
        }
        muzzle.suppressionGun = gunId; muzzle.suppressionAttachment = attachmentId;
        muzzle.fallbackSuppressed = suppressed;
        return suppressed;
    }

    private static void finishBullet(Bullet b, Minecraft mc) {
        if (b.finished) return;
        b.finished = true; finishedBullets++;
        b.finishedAt = Math.max(clock(mc, 0), GunfireLightApi.clock);
        EntityKineticBullet e = b.entity;
        Vec3 start = b.rendered ? new Vec3(b.x, b.y, b.z)
            : e.f_19797_ == 0 ? e.m_20182_() : new Vec3(e.f_19854_, e.f_19855_, e.f_19856_);
        b.x = e.m_20185_(); b.y = e.m_20186_(); b.z = e.m_20189_();
        // The removal packet can arrive after client extrapolation has crossed a
        // wall. Reuse the observed surface instead of raycasting from inside it.
        if(b.lastHit){b.x=b.hx;b.y=b.hy;b.z=b.hz;return;}
        if (!bulletEnabled || bulletFadeSeconds <= 0 || mc.f_91073_ == null) return;
        Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
        double dx = b.x - camera.f_82479_, dy = b.y - camera.f_82480_, dz = b.z - camera.f_82481_;
        double reach = maximumDistance + bulletRadius;
        if (dx * dx + dy * dy + dz * dz > reach * reach) return;
        long tick = mc.f_91073_.m_46467_();
        if (clipTick != tick) { clipTick = tick; clipsThisTick = 0; }
        // TaCZ collision runs only server-side. Removal may arrive before the
        // client renders even one frame of a short flight. Only then resolve the
        // final swept segment, never one raycast per active bullet per frame.
        // The extra endpoint reaches a server impact one simulation tick ahead;
        // on a miss, keep the actual final position rather than extrapolating.
        if (clipsThisTick >= 64) return;
        clipsThisTick++;
        Vec3 end = e.m_20182_().m_82549_(e.m_20184_());
        if (start.m_82557_(end) > 256 * 256) return;
        var hit = mc.f_91073_.m_45547_(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        if (hit.m_6662_() != HitResult.Type.MISS) {
            Vec3 p = hit.m_82450_(); var face = hit.m_82434_();
            // Keep the bulb just outside the surface; otherwise screen-space
            // contact shadows correctly reject a source inside the wall.
            b.x = p.f_82479_ + face.m_122429_() * .06;
            b.y = p.f_82480_ + face.m_122430_() * .06;
            b.z = p.f_82481_ + face.m_122431_() * .06;
            clippedBulletEnds++;
        }
    }

    private static void ensureLevel(Minecraft mc) {
        if (level != mc.f_91073_) { clear(); level = mc.f_91073_; }
    }

    private static Muzzle muzzle(LivingEntity owner) {
        Muzzle existing = MUZZLES.get(owner); if (existing != null) return existing;
        if (MUZZLES.size() >= MAX_MUZZLES) return null;
        Muzzle m = new Muzzle(); MUZZLES.put(owner, m); return m;
    }

    private static void fallback(LivingEntity owner, Muzzle m, float partial) {
        // Offscreen/not-yet-rendered weapons have no animated view transform. Use
        // their own muzzle node reach, not the addon's universal eye+0.8 offset.
        // Once a native capture arrives, it replaces this bounded approximation.
        fallbackMuzzles++;
        if (m.fallbackGun != m.gun) {
            m.fallbackGun = m.gun; m.reach = .7f;
            if (m.gun != null) TimelessAPI.getGunDisplay(m.gun).ifPresent(display -> {
                BedrockGunModel model = display.getGunModel();
                if (model == null || model.getMuzzleFlashPosPath() == null) return;
                FALLBACK_POSE.m_85850_().m_252922_().identity(); FALLBACK_POSE.m_85850_().m_252943_().identity();
                for (BedrockPart part : model.getMuzzleFlashPosPath()) part.translateAndRotateAndScale(FALLBACK_POSE);
                float reach = Math.abs(FALLBACK_POSE.m_85850_().m_252922_().m32());
                m.reach = bounded(reach, .25f, 2.5f, .7f);
            });
        }
        Vec3 eye = owner.m_20299_(partial), direction = owner.m_20252_(partial);
        m.x = eye.f_82479_ + direction.f_82479_ * m.reach;
        m.y = eye.f_82480_ + direction.f_82480_ * m.reach - .12;
        m.z = eye.f_82481_ + direction.f_82481_ * m.reach;
    }

    private static double clock(Minecraft mc, float partial) { return (mc.f_91073_.m_46467_() + partial) * .05; }
    private static Object gunId(ItemStack stack) {
        if (stack == null) return null;
        IGun gun = IGun.getIGunOrNull(stack); return gun == null ? null : gun.getGunId(stack);
    }
    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static boolean finite(float x, float y, float z) { return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z); }
    private static float bounded(float f, float min, float max, float fallback) { return Float.isFinite(f) ? Math.max(min, Math.min(max, f)) : fallback; }

    private static final class Bullet {
        final EntityKineticBullet entity; long handle; boolean rendered, finished, sampled, hit, lastHit;
        double sx,sy,sz,hx,hy,hz,hitFraction,hitAt;
        double x, y, z, finishedAt; EntityJoinLevelEvent joinEvent;
        Bullet(EntityKineticBullet entity) { this.entity = entity; }
    }
    private static final class Muzzle {
        long handle; double shotAt = Double.NEGATIVE_INFINITY, capturedAt = Double.NEGATIVE_INFINITY;
        double x, y, z; float ox, oy, oz, reach; boolean firstPerson, suppressed, flashHidden;
        ItemStack gun, fallbackGun; Object capturedGun; GunFireEvent pendingEvent;
        Object suppressionGun, suppressionAttachment; boolean fallbackSuppressed;
    }
}
