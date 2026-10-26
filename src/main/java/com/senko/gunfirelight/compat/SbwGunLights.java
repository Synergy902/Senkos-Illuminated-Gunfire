package com.senko.gunfirelight.compat;

import com.atsuishio.superbwarfare.client.lighting.MuzzleFlashHelper;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.atsuishio.superbwarfare.resource.gun.GunResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.GunfireConfig;
import com.senko.gunfirelight.render.HandLighting;
import com.senko.gunfirelight.source.GunfireLightApi;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Optional SBW 0.8.9.2 small-arms adapter. It never edits SBW or Minecraft light data. */
public final class SbwGunLights {
    private static final int LIMIT = 512;
    private static final IdentityHashMap<LivingEntity, Muzzle> MUZZLES = new IdentityHashMap<>();
    private static final Matrix4f INVERSE_VIEW = new Matrix4f();
    private static final Vector3f POINT = new Vector3f();
    private static Object level;
    private static LivingEntity renderingOwner, modelOwner;
    private static ItemStack modelStack;
    private static ItemDisplayContext modelContext;
    private static String modelGunId;
    private static int modelScope;
    private static boolean viewValid, explicitFlare;
    private static float worldFocal = 1;
    private static long shots, captured;

    private SbwGunLights() {}
    public static void register() {} // Events are wired by the optional bridge, not auto-subscribed.
    private static boolean active() { return GunfireConfig.ENABLED.get() && GunfireConfig.MUZZLE_ENABLED.get(); }
    private static double clock(float partial) {
        Minecraft mc = Minecraft.m_91087_();
        return mc.f_91073_ == null ? 0 : (mc.f_91073_.m_46467_() + Math.max(0, Math.min(1, partial))) / 20.0;
    }
    private static void ensureLevel() {
        Object next = Minecraft.m_91087_().f_91073_;
        if (level != next) { clear(); level = next; }
    }
    private static Muzzle muzzle(LivingEntity owner) {
        Muzzle m = MUZZLES.get(owner);
        if (m == null && MUZZLES.size() < LIMIT) { m = new Muzzle(); MUZZLES.put(owner, m); }
        return m;
    }

    public static void localShot() {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null) shot(mc.f_91074_, mc.f_91074_.m_21205_(), null);
    }
    public static void toolShot(LivingEntity owner, ItemStack stack) {
        if (!(stack.m_41720_() instanceof GunItem)) return;
        String id = GunData.from(stack).id;
        if (id.equals("superbwarfare:repair_tool") || id.equals("superbwarfare:taser")) shot(owner, stack, null);
    }
    /** SBW invokes this only for its own projectile families, after the owner/spawn position are known. */
    public static void projectileAdded(Entity entity) {
        Minecraft mc = Minecraft.m_91087_();
        if (!active() || entity.m_9236_() != mc.f_91073_ || !(entity instanceof Projectile p)) return;
        if (!(p.m_19749_() instanceof LivingEntity owner)) return;
        if (owner.m_20202_() instanceof VehicleEntity) return; // Mounted weapons have their own per-barrel adapter.
        if (owner != mc.f_91074_) shot(owner, owner.m_21205_(), entity.m_20182_());
        else {
            // Server-controlled/special guns may not run the normal local-shot callback.
            Muzzle m = MUZZLES.get(owner);
            if (m == null || clock(mc.m_91296_()) - m.shotAt > .2) shot(owner, owner.m_21205_(), entity.m_20182_());
        }
    }
    private static void shot(LivingEntity owner, ItemStack stack, Vec3 launch) {
        Minecraft mc = Minecraft.m_91087_();
        if (!active() || owner == null || owner.m_9236_() != mc.f_91073_ || !(stack.m_41720_() instanceof GunItem)) return;
        if (MuzzleFlashHelper.calculateFromStack(stack) == null) return; // Bow and non-firing tools.
        ensureLevel();
        Muzzle m = muzzle(owner); if (m == null) return;
        double now = clock(mc.m_91296_());
        // Multiple pellets from one server shot must not create additional emitters.
        if (launch != null && now - m.shotAt < .001 && m.gun == stack.m_41720_()) return;
        if (m.gun != stack.m_41720_()) m.launchKnown = false;
        m.gun = stack.m_41720_(); m.shotAt = now;
        int barrel = GunData.from(stack).attachment.get(AttachmentType.BARREL);
        m.radius = (barrel == 2 ? GunfireConfig.SUPPRESSED_RADIUS : barrel == 1 ? GunfireConfig.FLASH_HIDER_RADIUS : GunfireConfig.MUZZLE_RADIUS).get().floatValue();
        m.intensity = (barrel == 2 ? GunfireConfig.SUPPRESSED_INTENSITY : barrel == 1 ? GunfireConfig.FLASH_HIDER_INTENSITY : GunfireConfig.MUZZLE_INTENSITY).get().floatValue();
        m.duration = (barrel == 2 ? GunfireConfig.SUPPRESSED_SECONDS : barrel == 1 ? GunfireConfig.FLASH_HIDER_SECONDS : GunfireConfig.MUZZLE_SECONDS).get().floatValue();
        if (launch != null) {
            m.fallbackX = launch.f_82479_ - owner.m_20185_();
            m.fallbackY = launch.f_82480_ - owner.m_20186_();
            m.fallbackZ = launch.f_82481_ - owner.m_20189_();
            m.launchKnown = true;
        }
        shots++;
    }

    public static void livingPre(RenderLivingEvent.Pre<?, ?> event) { renderingOwner = event.getEntity(); }
    public static void livingPost(RenderLivingEvent.Post<?, ?> event) {
        if (renderingOwner == event.getEntity()) renderingOwner = null;
    }
    public static void captureWorldView(Matrix4f view) {
        ensureLevel();
        viewValid = view != null && view.isFinite() && Math.abs(view.determinant()) > 1e-8f;
        if (viewValid) INVERSE_VIEW.set(view).invert();
        worldFocal = RenderSystem.getProjectionMatrix().m11();
    }
    /** CustomGunRenderer's defaultRender encloses both its normal and emissive bone passes. */
    public static void beginModel(ItemStack stack, ItemDisplayContext context) {
        modelOwner = null; modelStack = null; modelGunId = null; modelScope = 0; modelContext = context; explicitFlare = false;
        Minecraft mc = Minecraft.m_91087_();
        if (!GunfireConfig.ENABLED.get() || OculusCompat.isRenderShadow() || mc.f_91073_ == null || stack == null || !(stack.m_41720_() instanceof GunItem)) return;
        boolean first = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean third = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        LivingEntity owner = first ? mc.f_91074_ : third ? renderingOwner : null;
        if (owner == null || owner.m_9236_() != mc.f_91073_ || owner.m_21205_().m_41720_() != stack.m_41720_()) return;
        ensureLevel(); modelOwner = owner; modelStack = stack; modelContext = context;
        // Constant for this render invocation; avoid Guava weak-cache lookups for every model bone.
        GunData data = GunData.from(stack);
        modelGunId = data.id; modelScope = data.attachment.get(AttachmentType.SCOPE);
    }
    public static void endModel() { modelOwner = null; modelStack = null; modelGunId = null; modelScope = 0; modelContext = null; explicitFlare = false; }

    /** Captures SBW's custom attachment-specific flare coordinates before any random sprite scaling. */
    public static void explicitFlare(PoseStack pose, ItemStack stack, GeoBone bone, double x, double y, double z) {
        if (modelOwner == null || modelStack == null || modelStack.m_41720_() != stack.m_41720_() || !bone.getName().equals("flare")) return;
        captureFlare(pose, bone, x, y, z); explicitFlare = true;
    }
    /** The emissive pass supplies the same complete animated parent hierarchy for remote weapons too. */
    public static void captureBone(PoseStack pose, GeoBone bone) {
        if (modelOwner == null || modelStack == null || explicitFlare) return;
        if (bone.getName().equals("flare")) {
            Vec3 p = GunResource.compute(modelStack).flarePosition;
            if (p != null) {
                // RpkItemRenderer's explicit flare differs from its generic resource definition.
                if (modelGunId.equals("superbwarfare:rpk")) captureFlare(pose, bone, 0, .02, 1.475);
                else captureFlare(pose, bone, p.f_82479_, p.f_82480_, p.f_82481_);
            }
            return;
        }
        // These stock tools/launcher have no FlarePosition. Use their authored muzzle bone pivots.
        String id = modelGunId;
        boolean anchor = id.equals("superbwarfare:repair_tool") && bone.getName().equals("flame_illuminated")
            || id.equals("superbwarfare:taser") && bone.getName().equals("head")
            || id.equals("superbwarfare:javelin") && bone.getName().equals("bone18")
            || id.equals("superbwarfare:igla_9k38") && bone.getName().equals("octagon9")
            || id.equals("superbwarfare:super_star_shooter") && bone.getName().equals("bone1");
        if (!anchor) return;
        pose.m_85836_();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            RenderUtils.translateToPivotPoint(pose, bone);
            // 0.8.9.2 Javelin's front rim extends 3.82 model pixels beyond this pivot.
            if (id.equals("superbwarfare:javelin")) pose.m_85837_(0, 0, -3.82 / 16.0);
            // Igla's front tube ring begins 6.75 pixels ahead of the authored ring pivot.
            if (id.equals("superbwarfare:igla_9k38")) pose.m_85837_(0, 0, -6.75 / 16.0);
            // Super Star Shooter supplies FlarePosition but no flare bone; use its front barrel ring.
            if (id.equals("superbwarfare:super_star_shooter")) pose.m_85837_(0, 0, -2.007 / 16.0);
            capturePose(pose.m_85850_().m_252922_());
        } finally { pose.m_85849_(); }
    }
    private static void captureFlare(PoseStack pose, GeoBone bone, double x, double y, double z) {
        int scope = modelScope;
        boolean first = firstPerson();
        double scopeOffset = first && (scope == 2 || scope == 3) && ClientEventHandler.zoom ? -.07 : 0;
        pose.m_85836_();
        try {
            pose.m_85837_(x, y + .02 + scopeOffset, -z);
            RenderUtils.prepMatrixForBone(pose, bone);
            capturePose(pose.m_85850_().m_252922_());
        } finally { pose.m_85849_(); }
    }
    private static boolean firstPerson() {
        return modelContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || modelContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }
    private static void capturePose(Matrix4f pose) {
        if (!pose.isFinite() || OculusCompat.isRenderShadow()) return;
        Minecraft mc = Minecraft.m_91087_();
        Muzzle m = muzzle(modelOwner); if (m == null) return;
        float partial = mc.m_91296_();
        POINT.set(pose.m30(), pose.m31(), pose.m32());
        boolean first = firstPerson();
        if (first) {
            if (POINT.lengthSquared() > 100) return;
            float handFocal = RenderSystem.getProjectionMatrix().m11();
            float ratio = Math.abs(handFocal) > 1e-6f ? worldFocal / handFocal : 1;
            if (Float.isFinite(ratio) && ratio > .01 && ratio < 100) POINT.z *= ratio;
            m.ox = POINT.x; m.oy = POINT.y; m.oz = POINT.z;
        } else {
            if (!viewValid) return;
            INVERSE_VIEW.transformPosition(POINT);
            Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
            m.ox = camera.f_82479_ + POINT.x - lerp(modelOwner.f_19854_, modelOwner.m_20185_(), partial);
            m.oy = camera.f_82480_ + POINT.y - lerp(modelOwner.f_19855_, modelOwner.m_20186_(), partial);
            m.oz = camera.f_82481_ + POINT.z - lerp(modelOwner.f_19856_, modelOwner.m_20189_(), partial);
            if (m.ox * m.ox + m.oy * m.oy + m.oz * m.oz > 100) return;
        }
        m.first = first; m.capturedAt = clock(partial); m.capturedGun = modelStack.m_41720_(); captured++;
        updateMuzzle(modelOwner, m, partial);
        if (first) {
            float energy = energy(m, clock(partial));
            HandLighting.captureExternalGun(pose, partial, m.handle, m.radius, energy, energy * .57f, energy * .2f);
        }
    }
    public static void update(float partial) {
        ensureLevel();
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) return;
        double now = clock(partial);
        Iterator<Map.Entry<LivingEntity, Muzzle>> it = MUZZLES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next(); LivingEntity owner = entry.getKey(); Muzzle m = entry.getValue();
            if (owner.m_213877_() || owner.m_9236_() != mc.f_91073_ || now - Math.max(m.capturedAt, m.shotAt) > 5) {
                GunfireLightApi.remove(m.handle); it.remove(); continue;
            }
            updateMuzzle(owner, m, partial);
        }
    }
    private static void updateMuzzle(LivingEntity owner, Muzzle m, float partial) {
        Minecraft mc = Minecraft.m_91087_();
        float energy = energy(m, clock(partial));
        if (energy <= 0 || m.gun != owner.m_21205_().m_41720_()) { GunfireLightApi.remove(m.handle); m.handle = 0; return; }
        Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
        boolean first = owner == mc.f_91074_ && mc.f_91066_.m_92176_().m_90612_();
        double x, y, z;
        if (m.first == first && m.capturedGun == m.gun && clock(partial) - m.capturedAt <= .15) {
            if (first) {
                POINT.set((float)m.ox, (float)m.oy, (float)m.oz).rotate(mc.f_91063_.m_109153_().m_253121_());
                x = camera.f_82479_ + POINT.x; y = camera.f_82480_ + POINT.y; z = camera.f_82481_ + POINT.z;
            } else {
                x = lerp(owner.f_19854_, owner.m_20185_(), partial) + m.ox;
                y = lerp(owner.f_19855_, owner.m_20186_(), partial) + m.oy;
                z = lerp(owner.f_19856_, owner.m_20189_(), partial) + m.oz;
            }
        } else if (m.launchKnown) {
            x = lerp(owner.f_19854_, owner.m_20185_(), partial) + m.fallbackX;
            y = lerp(owner.f_19855_, owner.m_20186_(), partial) + m.fallbackY;
            z = lerp(owner.f_19856_, owner.m_20189_(), partial) + m.fallbackZ;
        } else { GunfireLightApi.remove(m.handle); m.handle = 0; return; }
        double reach = GunfireConfig.MAX_DISTANCE.get() + m.radius;
        double dx = x - camera.f_82479_, dy = y - camera.f_82480_, dz = z - camera.f_82481_;
        if (!Double.isFinite(x + y + z) || dx * dx + dy * dy + dz * dz > reach * reach) { GunfireLightApi.remove(m.handle); m.handle = 0; return; }
        if (!GunfireLightApi.update(m.handle, x, y, z, m.radius, 1, .57f, .2f, energy))
            m.handle = GunfireLightApi.create(x, y, z, m.radius, 1, .57f, .2f, energy, Double.POSITIVE_INFINITY, false, first ? 8 : 4);
    }
    private static float energy(Muzzle m, double now) {
        if (!active() || m.duration <= 0) return 0;
        double age = Math.max(0, now - m.shotAt);
        if (age >= m.duration) return 0;
        float remaining = (float)(1 - age / m.duration);
        return m.intensity * remaining * remaining;
    }
    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    public static String status() { return "SBW gun shots=" + shots + ", muzzle captures=" + captured + ", tracked=" + MUZZLES.size(); }
    public static int trackedMuzzles() { return MUZZLES.size(); }
    public static void clear() {
        for (Muzzle m : MUZZLES.values()) GunfireLightApi.remove(m.handle);
        MUZZLES.clear(); level = null; renderingOwner = null; endModel(); viewValid = false;
    }
    private static final class Muzzle {
        long handle;
        Object gun, capturedGun;
        double shotAt = Double.NEGATIVE_INFINITY, capturedAt = Double.NEGATIVE_INFINITY;
        double ox, oy, oz, fallbackX, fallbackY, fallbackZ;
        boolean first, launchKnown;
        float radius, intensity, duration;
    }
}
