package com.senko.gunfirelight.compat;

import com.atsuishio.superbwarfare.client.lighting.ProjectileLightHelper;
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties;
import com.atsuishio.superbwarfare.entity.projectile.IAdvancedHitDetection;
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.config.server.ProjectileConfig;
import com.atsuishio.superbwarfare.init.ModTags;
import com.senko.gunfirelight.GunfireConfig;
import com.senko.gunfirelight.source.GunfireLightApi;
import com.senko.gunfirelight.source.BulletLightEnvelope;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

/** One interpolated GPU emitter per bullet, hot shell or rocket; no block-light updates. */
public final class SbwProjectileLights {
    private static final IdentityHashMap<Entity, Flight> FLIGHTS = new IdentityHashMap<>();
    private static final int MAX_PROJECTILES = 2048;
    private static long clipTick = Long.MIN_VALUE;
    private static int clipsThisTick;
    // Match released SBW's ordinary-bullet collision policy, including its iron-door exceptions.
    private static final Predicate<BlockState> IGNORE = state -> state.m_204336_(ModTags.Blocks.BULLET_IGNORE)
        && !state.m_60713_(Blocks.f_50166_) && !state.m_60713_(Blocks.f_50376_);
    private static final Predicate<BlockState> IGNORE_WITH_DESTRUCTION = IGNORE.and(
        state -> !state.m_204336_(ModTags.Blocks.BULLET_CAN_DESTROY));
    private SbwProjectileLights() {}

    public static void join(EntityJoinLevelEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        Entity entity = event.getEntity();
        if (event.isCanceled() || event.getLevel() != mc.f_91073_ || FLIGHTS.containsKey(entity)
                || FLIGHTS.size() >= MAX_PROJECTILES || !GunfireConfig.BULLET_ENABLED.get()) return;
        if (!(entity instanceof ProjectileEntity) && ProjectileLightHelper.getTrailLight(entity) == null) return;
        Flight flight = new Flight(entity); flight.pendingJoin = event;
        refreshColor(flight);
        FLIGHTS.put(entity, flight);
    }
    private static void refreshColor(Flight flight) {
        if (flight.colorTick == flight.entity.f_19797_) return;
        flight.colorTick = flight.entity.f_19797_;
        if (flight.entity instanceof IBulletProperties properties) {
            float[] rgb = properties.getRGB();
            if (rgb != null && rgb.length >= 3 && valid(rgb[0]) && valid(rgb[1]) && valid(rgb[2])) {
                flight.r = rgb[0]; flight.g = rgb[1]; flight.b = rgb[2];
            }
        }
    }
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() != Minecraft.m_91087_().f_91073_) return;
        Flight flight = FLIGHTS.get(event.getEntity());
        if (flight != null) finish(flight, Minecraft.m_91087_().m_91296_());
    }
    public static void update(float partial) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null || !GunfireConfig.BULLET_ENABLED.get()) { clear(); return; }
        double now = (mc.f_91073_.m_46467_() + partial) * .05;
        float radius = GunfireConfig.BULLET_RADIUS.get().floatValue();
        float intensity = GunfireConfig.BULLET_INTENSITY.get().floatValue();
        var iterator = FLIGHTS.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next(); Entity entity = flight.entity;
            if (entity.m_9236_() != mc.f_91073_ || flight.pendingJoin != null && flight.pendingJoin.isCanceled()) {
                GunfireLightApi.remove(flight.handle); iterator.remove(); continue;
            }
            flight.pendingJoin = null;
            if (!flight.finished) refreshColor(flight); // Entity-data colors arrive after the spawn packet.
            if (!prepareVisualSegment(flight, mc)) {
                // A spent ray budget must not display an unchecked light beyond a wall.
                GunfireLightApi.remove(flight.handle); flight.handle = 0; continue;
            }
            if (entity.m_213877_() && !flight.finished) finish(flight, partial);
            float energy = intensity;
            if (flight.impactKnown) {
                if (now >= flight.impactAt) {
                    flight.x=flight.hx; flight.y=flight.hy; flight.z=flight.hz;
                    energy *= BulletLightEnvelope.terminal(now-flight.impactAt, GunfireConfig.BULLET_FADE_SECONDS.get().floatValue());
                } else {
                    // Keep the approach continuous until the within-tick impact time.
                    flight.x=lerp(flight.sx,flight.ex,partial);
                    flight.y=lerp(flight.sy,flight.ey,partial);
                    flight.z=lerp(flight.sz,flight.ez,partial);
                }
            } else if (flight.finished) energy *= BulletLightEnvelope.terminal(now-flight.finishedAt, GunfireConfig.BULLET_FADE_SECONDS.get().floatValue());
            else {
                // Ordinary bullets are clipped visually above; SBW owns all gameplay/collision events.
                flight.x = lerp(entity.f_19854_, entity.m_20185_(), entity.f_19797_ == 0 ? 1 : partial);
                flight.y = lerp(entity.f_19855_, entity.m_20186_(), entity.f_19797_ == 0 ? 1 : partial);
                flight.z = lerp(entity.f_19856_, entity.m_20189_(), entity.f_19797_ == 0 ? 1 : partial);
            }
            if (energy <= 0) { GunfireLightApi.remove(flight.handle); iterator.remove(); continue; }
            if (!pastMuzzle(flight)) {
                // Gameplay may spawn a coax bullet behind its visible outlet. Its light
                // begins at the model's exit plane; projectile physics remain SBW's.
                GunfireLightApi.remove(flight.handle); flight.handle=0; continue;
            }
            if (!GunfireLightApi.update(flight.handle,flight.x,flight.y,flight.z,radius,flight.r,flight.g,flight.b,energy))
                flight.handle = GunfireLightApi.create(flight.x,flight.y,flight.z,radius,flight.r,flight.g,flight.b,energy,Double.POSITIVE_INFINITY,false,1);
        }
    }
    private static boolean pastMuzzle(Flight flight) {
        if (flight.exitedMuzzle) return true;
        if (flight.muzzle == null) {
            Entity owner=flight.entity instanceof Projectile projectile?projectile.m_19749_():null;
            flight.muzzle=SbwVehicleLights.projectileMuzzle(owner,flight.spawn);
            if(flight.muzzle==null) {
                // Give fire packets/model rendering two client ticks to supply the
                // visual outlet, without flashing at a vehicle's buried spawn point.
                // Unknown/handheld owners retain normal behavior; offscreen or custom
                // vehicles without a usable model point resume native lighting.
                if(flight.entity.f_19797_>1)flight.exitedMuzzle=true;
                boolean vehicleOwned=owner instanceof VehicleEntity
                    ||owner!=null&&owner.m_20202_() instanceof VehicleEntity;
                return flight.exitedMuzzle||!vehicleOwned;
            }
            Vec3 velocity=flight.entity.m_20184_();
            double length=Math.sqrt(velocity.f_82479_*velocity.f_82479_+velocity.f_82480_*velocity.f_82480_+velocity.f_82481_*velocity.f_82481_);
            // A spawn packet can precede motion data. Wait for a usable direction
            // instead of permanently allowing a light at the buried spawn point.
            if(!Double.isFinite(length)||length<1e-8){flight.muzzle=null;return false;}
            flight.nx=velocity.f_82479_/length;flight.ny=velocity.f_82480_/length;flight.nz=velocity.f_82481_/length;
        }
        double dot=(flight.x-flight.muzzle.f_82479_)*flight.nx+(flight.y-flight.muzzle.f_82480_)*flight.ny+(flight.z-flight.muzzle.f_82481_)*flight.nz;
        if(dot<0)return false;
        flight.exitedMuzzle=true;flight.muzzle=null;
        return true;
    }
    private static boolean prepareVisualSegment(Flight flight, Minecraft mc) {
        Entity entity = flight.entity;
        if (flight.impactKnown || flight.finished || !(entity instanceof ProjectileEntity bullet)
                || bullet.isPenetrating() || bullet.isBeast() || entity.f_19797_ == 0) return true;
        Vec3 start = new Vec3(entity.f_19854_,entity.f_19855_,entity.f_19856_);
        Vec3 end = entity.m_20182_();
        if (flight.checkedTick == entity.f_19797_ && flight.ex == end.f_82479_
                && flight.ey == end.f_82480_ && flight.ez == end.f_82481_) return true;
        double length2 = start.m_82557_(end);
        if (!Double.isFinite(length2) || length2 > 256.0*256.0) return false;
        Vec3 camera = mc.f_91063_.m_109153_().m_90583_();
        double reach = GunfireConfig.MAX_DISTANCE.get()+GunfireConfig.BULLET_RADIUS.get();
        if (camera.m_82557_(start)>reach*reach && camera.m_82557_(end)>reach*reach) return false;
        if (length2 > 1e-8) {
            long tick = mc.f_91073_.m_46467_();
            if (clipTick != tick) { clipTick=tick; clipsThisTick=0; }
            if (clipsThisTick >= 64) return false;
            clipsThisTick++;
            var hit = IAdvancedHitDetection.rayTraceBlocksWithFluid(mc.f_91073_,
                new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,entity),
                ProjectileConfig.PROJECTILE_DESTROY_BLOCKS.get() ? IGNORE_WITH_DESTRUCTION : IGNORE).getFirst();
            if (hit.m_6662_() != HitResult.Type.MISS) {
                Vec3 at = hit.m_82450_(); var normal=hit.m_82434_();
                flight.impactKnown=true;
                // Keep the bulb outside the collider, including its final fade.
                flight.hx=at.f_82479_+normal.m_122429_()*.06;
                flight.hy=at.f_82480_+normal.m_122430_()*.06;
                flight.hz=at.f_82481_+normal.m_122431_()*.06;
                double fraction=Math.max(0,Math.min(1,Math.sqrt(start.m_82557_(at)/length2)));
                flight.impactAt=(mc.f_91073_.m_46467_()+fraction)*.05;
            }
        }
        flight.checkedTick=entity.f_19797_;
        flight.sx=start.f_82479_; flight.sy=start.f_82480_; flight.sz=start.f_82481_;
        flight.ex=end.f_82479_; flight.ey=end.f_82480_; flight.ez=end.f_82481_;
        return true;
    }
    private static void finish(Flight flight,float partial) {
        if (flight.finished || flight.impactKnown) return;
        boolean checked=prepareVisualSegment(flight,Minecraft.m_91087_());
        if (flight.impactKnown) return; // A late removal packet cannot move the terminal light through the wall.
        flight.finished = true;
        flight.finishedAt = (flight.entity.m_9236_().m_46467_()+partial)*.05;
        if (checked) {
            flight.x=flight.entity.m_20185_(); flight.y=flight.entity.m_20186_(); flight.z=flight.entity.m_20189_();
        }
    }
    public static int tracked() { return FLIGHTS.size(); }
    public static void clear() {
        for(Flight flight:FLIGHTS.values())GunfireLightApi.remove(flight.handle);
        FLIGHTS.clear(); clipTick=Long.MIN_VALUE; clipsThisTick=0;
    }
    private static double lerp(double a,double b,float t) { return a+(b-a)*t; }
    private static boolean valid(float value) { return Float.isFinite(value)&&value>=0&&value<=1; }
    private static final class Flight {
        final Entity entity;
        final Vec3 spawn;
        Vec3 muzzle;
        double nx,ny,nz;
        boolean exitedMuzzle;
        long handle; double x,y,z,finishedAt; boolean finished;
        double sx,sy,sz,ex,ey,ez,hx,hy,hz,impactAt;
        boolean impactKnown;
        int checkedTick = -1;
        float r=1,g=.5f,b=.18f;
        int colorTick = -1;
        EntityJoinLevelEvent pendingJoin;
        Flight(Entity entity) {
            this.entity=entity; x=entity.m_20185_(); y=entity.m_20186_(); z=entity.m_20189_();
            spawn=new Vec3(x,y,z);
        }
    }
}
