package com.senko.gunfirelight.compat;

import com.atsuishio.superbwarfare.api.event.ClientVehicleFireEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootPos;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.AnnihilatorEntity;
import com.atsuishio.superbwarfare.entity.vehicle.MortarEntity;
import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.senko.gunfirelight.GunfireConfig;
import com.senko.gunfirelight.source.BulletLightEnvelope;
import com.senko.gunfirelight.source.GunfireLightApi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Vector4d;
import org.joml.Matrix4f;
import org.joml.Matrix4d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;

/**
 * Optional SBW client integration. Each emitted flash belongs to the weapon and
 * barrel identified by SBW's firing packet, rather than to a passenger's eyes.
 * The registry is fed on the render thread; no block-light updates are issued.
 */
public final class SbwVehicleLights {
    private static final int MAX_MUZZLES = 512;
    private static final Map<Barrel, Flash> FLASHES = new HashMap<>();
    private static final Map<VehicleEntity, Map<String, WeaponState>> STATES = new WeakHashMap<>();
    private static final ArrayList<WorldFlash> PARTICLE_FLASHES = new ArrayList<>();
    private static final Matrix4f INVERSE_VIEW = new Matrix4f(), MODEL_POSE = new Matrix4f();
    private static final Vector3f MODEL_POINT = new Vector3f();
    private static final Matrix4d MODEL_INVERSE = new Matrix4d();
    private static final Vector4d MODEL_LOCAL = new Vector4d();
    private static boolean viewValid;
    private static Object level;
    private static boolean registered;
    private static double lastFrameTime = Double.NEGATIVE_INFINITY;
    private static long shots, rejectedShots;

    private SbwVehicleLights() {}

    /** Called only after the optional SBW integration has passed its version gate. */
    public static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.addListener(SbwVehicleLights::onFire);
        registered = true;
    }

    public static void onFire(ClientVehicleFireEvent event) {
        if (!enabled()) return;
        Minecraft mc = Minecraft.m_91087_();
        VehicleEntity vehicle = event.getVehicle();
        // Mortar's packet starts its loading countdown, a full second before the shot.
        if (vehicle instanceof MortarEntity) return;
        if (mc.f_91073_ == null || vehicle.m_9236_() != mc.f_91073_ || vehicle.m_213877_()) return;
        ensureLevel(mc);

        String weapon = event.getWeaponName();
        if (weapon == null || weapon.isEmpty()) {
            int seat = vehicle.getSeatIndex(event.getShooter());
            if (seat < 0) { rejectedShots++; return; }
            weapon = vehicle.getGunName(seat);
        }
        if (weapon == null || weapon.isEmpty()) { rejectedShots++; return; }
        GunData firingGun = vehicle.getGunData(weapon);
        // The event starts delayed attacks; synchronized ammo/timer changes or
        // native flare packets below identify when their projectile actually fires.
        if (firingGun != null && firingGun.get(GunProp.SHOOT_DELAY_TIME) > 0) return;
        emit(vehicle, weapon, event.getIndex(), mc);
    }

    private static void emit(VehicleEntity vehicle, String weapon, int index, Minecraft mc) {
        GunData gun = vehicle.getGunData(weapon);
        if (gun == null) { rejectedShots++; return; }
        ShootPos mount = gun.get(GunProp.SHOOT_POS);
        // index is the server-selected barrel/launch tube, NOT the passenger seat.
        // Reading gun.firePosition() here can select the next barrel after sync.
        Vec3 local = barrelPosition(mount, index);
        if (local == null) { rejectedShots++; return; }
        Vector4d world = vehicle.transformPosition(vehicle.getTransformFromString(mount.getTransform(), 1f),
            local.f_82479_, local.f_82480_, local.f_82481_);
        if (!withinRange(mc, world)) { rejectedShots++; return; }

        record(vehicle, weapon, index, mount.getTransform(), local, mc);
        // This native vehicle fires all three beams in one Main event; its data
        // describes the middle emitter. Side offsets match its 0.8.9.2 shoot().
        if (vehicle instanceof AnnihilatorEntity && weapon.equals("Main")) {
            record(vehicle, weapon + ":left", index, mount.getTransform(),
                new Vec3(local.f_82479_ + 2.703, local.f_82480_, local.f_82481_), mc);
            record(vehicle, weapon + ":right", index, mount.getTransform(),
                new Vec3(local.f_82479_ - 2.703, local.f_82480_, local.f_82481_), mc);
        }
        shots++;
    }

    private static void record(VehicleEntity vehicle, String weapon, int index, String transform, Vec3 local, Minecraft mc) {
        Barrel barrel = new Barrel(vehicle, weapon, index);
        Flash flash = FLASHES.get(barrel);
        if (flash == null) {
            if (FLASHES.size() >= MAX_MUZZLES) { rejectedShots++; return; }
            flash = new Flash();
            FLASHES.put(barrel, flash);
        }
        flash.transform = transform;
        flash.local = local;
        flash.bone = SbwMuzzleAnchors.bone(vehicle,weapon,index);
        flash.shotAt = Math.max(clock(mc, 0), lastFrameTime);
        Vector4d origin = vehicle.transformPosition(vehicle.getTransformFromString(transform, 1f),
            local.f_82479_, local.f_82480_, local.f_82481_);
        flash.shotX = origin.x; flash.shotY = origin.y; flash.shotZ = origin.z;
    }

    /** Synced firing state supplies ownerless automated guns which send no fire event. */
    public static void onVehicleTick(VehicleEntity vehicle) {
        if (vehicle instanceof MortarEntity) return;
        Minecraft mc = Minecraft.m_91087_();
        if (!enabled() || mc.f_91073_ == null || vehicle.m_9236_() != mc.f_91073_) return;
        ensureLevel(mc);
        if (!withinRange(mc, new Vector4d(vehicle.m_20185_(), vehicle.m_20186_(), vehicle.m_20189_(), 1))) return;
        if (!STATES.containsKey(vehicle) && STATES.size() >= 1024) return;
        Map<String, WeaponState> states = STATES.computeIfAbsent(vehicle, ignored -> new HashMap<>());
        for (Map.Entry<String, GunData> entry : vehicle.getGunDataMap().entrySet()) {
            String name = entry.getKey(); GunData gun = entry.getValue();
            WeaponState state = states.get(name);
            int ammo=gun.ammo.get(), indexNow=gun.fireIndex.get(), timer=gun.shootTimer.get();
            double heat=gun.heat.get();
            boolean firing = timer > 0 && state != null &&
                (ammo < state.ammo || indexNow != state.index || heat > state.heat || timer > state.timer);
            if(state==null){state=new WeaponState();states.put(name,state);}
            state.ammo=ammo;state.index=indexNow;state.timer=timer;state.heat=heat;
            // Native lasers can bypass GunData.shoot entirely but synchronize beam scale.
            boolean beam = name.equals("Main") && vehicle.getLaserScale() > 0;
            if ((!firing && !beam) || recentlyEmitted(vehicle, name, mc)) continue;
            ShootPos mount = gun.get(GunProp.SHOOT_POS);
            int count = mount.getPositions().size();
            if (count == 0) continue;
            int index = beam ? 0 : mount.getBoundUpWithAmmoAmount()
                ? Math.min(count - 1, Math.max(0, ammo)) : Math.floorMod(indexNow - 1, count);
            emit(vehicle, name, index, mc);
        }
    }

    private static boolean recentlyEmitted(VehicleEntity vehicle, String name, Minecraft mc) {
        double now = Math.max(clock(mc, 0), lastFrameTime);
        for (Map.Entry<Barrel, Flash> entry : FLASHES.entrySet()) {
            Barrel key = entry.getKey();
            if (key.vehicle == vehicle && key.weapon.equals(name) && now - entry.getValue().shotAt <= .075) return true;
        }
        return false;
    }

    /** Mortar's specialized countdown reaches five exactly when its shell fires. */
    public static void onMortarTick(MortarEntity mortar) {
        Minecraft mc = Minecraft.m_91087_();
        if (!enabled() || mc.f_91073_ == null || mortar.m_9236_() != mc.f_91073_
                || mortar.m_20088_().m_135370_(MortarEntity.FIRE_TIME) != 5) return;
        ensureLevel(mc);
        Vec3 eye = mortar.m_20299_(1f), direction = mortar.m_20252_(1f);
        // Native MortarEntity's muzzle particles use eye + look * 1.5.
        recordWorld(eye.f_82479_ + direction.f_82479_ * 1.5,
            eye.f_82480_ + direction.f_82480_ * 1.5, eye.f_82481_ + direction.f_82481_ * 1.5, mc);
    }

    /** A zero-radius native muzzle sample covers specialized rocket launchers. */
    public static void onNativeFlare(double x, double y, double z) {
        Minecraft mc = Minecraft.m_91087_();
        if (!enabled() || mc.f_91073_ == null) return;
        ensureLevel(mc);
        recordWorld(x, y, z, mc);
    }

    private static void recordWorld(double x, double y, double z, Minecraft mc) {
        if (!withinRange(mc, new Vector4d(x, y, z, 1))) return;
        double now = Math.max(clock(mc, 0), lastFrameTime);
        for (WorldFlash flash : PARTICLE_FLASHES) {
            double dx = x - flash.x, dy = y - flash.y, dz = z - flash.z;
            if (now - flash.shotAt <= .075 && dx * dx + dy * dy + dz * dz < .0625) return;
        }
        if (PARTICLE_FLASHES.size() >= 128) return;
        PARTICLE_FLASHES.add(new WorldFlash(x, y, z, now));
    }

    /** Resolve exactly the transmitted barrel. Invalid packets never light a guessed location. */
    static Vec3 barrelPosition(ShootPos mount, int index) {
        if (mount == null || index < 0 || index >= mount.getPositions().size()) return null;
        Vec3 local = mount.getPositions().get(index);
        return local != null && finite(local.f_82479_, local.f_82480_, local.f_82481_) ? local : null;
    }

    public static void captureWorldView(Matrix4f view){
        ensureLevel(Minecraft.m_91087_());
        viewValid=view!=null&&view.isFinite()&&Math.abs(view.determinant())>1e-8f;
        if(viewValid)INVERSE_VIEW.set(view).invert();
    }

    /** Use the authored flare bone after SBW applies its full animated model hierarchy. */
    public static void captureModel(VehicleEntity vehicle,VehicleModelInstance model,PoseStack pose,float partial){
        if(!enabled()||!viewValid||FLASHES.isEmpty()||OculusCompat.isRenderShadow())return;
        Minecraft mc=Minecraft.m_91087_();
        if(vehicle.m_9236_()!=mc.f_91073_)return;
        Vec3 camera=mc.f_91063_.m_109153_().m_90583_();
        for(var entry:FLASHES.entrySet()){
            if(entry.getKey().vehicle()!=vehicle)continue;
            Flash flash=entry.getValue();
            if(flash.bone==null)continue;
            var bone=model.getBone(flash.bone);
            if(bone==null)continue;
            var definition=bone.definition();
            MODEL_POSE.set(pose.m_85850_().m_252922_()).mul(bone.getGlobalTransform(model));
            MODEL_POINT.set(definition.pivotX(),definition.pivotY(),definition.pivotZ());
            MODEL_POSE.transformPosition(MODEL_POINT);INVERSE_VIEW.transformPosition(MODEL_POINT);
            double x=camera.f_82479_+MODEL_POINT.x,y=camera.f_82480_+MODEL_POINT.y,z=camera.f_82481_+MODEL_POINT.z;
            if(!finite(x,y,z))continue;
            Matrix4d inverse=MODEL_INVERSE.set(vehicle.getTransformFromString(flash.transform,partial));
            if(!Double.isFinite(inverse.determinant())||Math.abs(inverse.determinant())<1e-10)continue;
            Vector4d local=inverse.invert().transform(MODEL_LOCAL.set(x,y,z,1));
            // Reject GUI/foreign view contamination while allowing large custom vehicles.
            double dx=local.x-flash.local.f_82479_,dy=local.y-flash.local.f_82480_,dz=local.z-flash.local.f_82481_;
            if(!finite(local.x,local.y,local.z)||dx*dx+dy*dy+dz*dz>64)continue;
            flash.visualX=local.x;flash.visualY=local.y;flash.visualZ=local.z;flash.visualKnown=true;
        }
    }

    /** Resolve a recent vehicle bullet's visual exit, independently from its buried gameplay spawn. */
    public static Vec3 projectileMuzzle(Entity owner,Vec3 spawn){
        VehicleEntity vehicle=owner instanceof VehicleEntity v?v:owner!=null&&owner.m_20202_() instanceof VehicleEntity v?v:null;
        if(vehicle==null||FLASHES.isEmpty())return null;
        Minecraft mc=Minecraft.m_91087_();
        if(mc.f_91073_==null||vehicle.m_9236_()!=mc.f_91073_)return null;
        double now=clock(mc,mc.m_91296_()),best=1.0;
        Vec3 result=null;
        for(var entry:FLASHES.entrySet()){
            Flash flash=entry.getValue();
            if(entry.getKey().vehicle()!=vehicle||!flash.visualKnown||now-flash.shotAt>.2)continue;
            double dx=spawn.f_82479_-flash.shotX,dy=spawn.f_82480_-flash.shotY,dz=spawn.f_82481_-flash.shotZ;
            double distance=dx*dx+dy*dy+dz*dz;
            if(distance>=best)continue;
            Vector4d point=vehicle.transformPosition(vehicle.getTransformFromString(flash.transform,mc.m_91296_()),
                flash.visualX,flash.visualY,flash.visualZ);
            result=new Vec3(point.x,point.y,point.z);best=distance;
        }
        return result;
    }

    /** Called after the shared registry clock is advanced, before lights are collected. */
    public static void update(float partial) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null || !enabled()) { clear(); return; }
        ensureLevel(mc);
        partial = Float.isFinite(partial) ? Math.max(0, Math.min(1, partial)) : 0;
        double now = clock(mc, partial);
        lastFrameTime = now;
        float radius = GunfireConfig.MUZZLE_RADIUS.get().floatValue();
        float intensity = GunfireConfig.MUZZLE_INTENSITY.get().floatValue();
        float seconds = GunfireConfig.MUZZLE_SECONDS.get().floatValue();
        Iterator<Map.Entry<Barrel, Flash>> it = FLASHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Barrel, Flash> entry = it.next();
            VehicleEntity vehicle = entry.getKey().vehicle();
            Flash flash = entry.getValue();
            float energy = intensity * BulletLightEnvelope.terminal(Math.max(0, now - flash.shotAt), seconds);
            if (vehicle.m_213877_() || vehicle.m_9236_() != mc.f_91073_ || energy <= 0) {
                GunfireLightApi.remove(flash.handle); it.remove(); continue;
            }
            // Re-evaluate SBW's own vehicle/turret/barrel transform every rendered
            // frame, retaining the selected tube even if another weapon fires.
            Vector4d world = vehicle.transformPosition(vehicle.getTransformFromString(flash.transform, partial),
                flash.visualKnown?flash.visualX:flash.local.f_82479_,
                flash.visualKnown?flash.visualY:flash.local.f_82480_,
                flash.visualKnown?flash.visualZ:flash.local.f_82481_);
            if (!withinRange(mc, world)) {
                GunfireLightApi.remove(flash.handle); flash.handle = 0; continue;
            }
            flash.x=world.x; flash.y=world.y; flash.z=world.z;
            if (!GunfireLightApi.update(flash.handle, world.x, world.y, world.z, radius, 1f, .57f, .2f, energy))
                flash.handle = GunfireLightApi.create(world.x, world.y, world.z, radius, 1f, .57f, .2f, energy,
                    Double.POSITIVE_INFINITY, false, 4);
        }
        Iterator<WorldFlash> particles = PARTICLE_FLASHES.iterator();
        while (particles.hasNext()) {
            WorldFlash flash = particles.next();
            float energy = intensity * BulletLightEnvelope.terminal(Math.max(0, now - flash.shotAt), seconds);
            boolean duplicate = false;
            for (Map.Entry<Barrel, Flash> entry : FLASHES.entrySet()) {
                Flash other = entry.getValue();
                // The packet particle stays at the launch point while the barrel
                // moves with the vehicle. Compare origins from the shot, not this frame.
                double dx = other.shotX - flash.x, dy = other.shotY - flash.y, dz = other.shotZ - flash.z;
                if (Math.abs(other.shotAt - flash.shotAt) <= .1 && dx * dx + dy * dy + dz * dz < .0625) { duplicate = true; break; }
            }
            if (energy <= 0 || duplicate) { GunfireLightApi.remove(flash.handle); particles.remove(); continue; }
            if (!GunfireLightApi.update(flash.handle, flash.x, flash.y, flash.z, radius, 1f, .57f, .2f, energy))
                flash.handle = GunfireLightApi.create(flash.x, flash.y, flash.z, radius, 1f, .57f, .2f, energy,
                    Double.POSITIVE_INFINITY, false, 3);
        }
    }

    public static void clear() {
        for (Flash flash : FLASHES.values()) GunfireLightApi.remove(flash.handle);
        for (WorldFlash flash : PARTICLE_FLASHES) GunfireLightApi.remove(flash.handle);
        FLASHES.clear(); STATES.clear(); PARTICLE_FLASHES.clear(); SbwMuzzleAnchors.clear();viewValid=false;
        level = null; lastFrameTime = Double.NEGATIVE_INFINITY;
    }

    public static int trackedMuzzles() { return FLASHES.size(); }
    public static long shots() { return shots; }
    public static long rejectedShots() { return rejectedShots; }

    private static boolean enabled() { return GunfireConfig.ENABLED.get() && GunfireConfig.MUZZLE_ENABLED.get(); }
    private static void ensureLevel(Minecraft mc) {
        if (level != mc.f_91073_) { clear(); level = mc.f_91073_; }
    }
    private static double clock(Minecraft mc, float partial) { return (mc.f_91073_.m_46467_() + partial) * .05; }
    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
    private static boolean withinRange(Minecraft mc, Vector4d world) {
        if (!finite(world.x, world.y, world.z)) return false;
        Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
        double dx = world.x - camera.f_82479_, dy = world.y - camera.f_82480_, dz = world.z - camera.f_82481_;
        double reach = GunfireConfig.MAX_DISTANCE.get() + GunfireConfig.MUZZLE_RADIUS.get();
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private record Barrel(VehicleEntity vehicle, String weapon, int index) {}
    private static final class WeaponState { int ammo,index,timer; double heat; }
    private static final class WorldFlash {
        final double x, y, z, shotAt;
        long handle;
        WorldFlash(double x, double y, double z, double shotAt) { this.x=x; this.y=y; this.z=z; this.shotAt=shotAt; }
    }
    private static final class Flash {
        long handle;
        double shotAt;
        double x, y, z;
        double shotX, shotY, shotZ;
        String transform;
        Vec3 local;
        String bone;
        boolean visualKnown;
        double visualX,visualY,visualZ;
    }
}
