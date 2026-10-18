package com.senko.gunfirelight.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.senko.gunfirelight.source.GunfireSources;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The hand uses a different projection from terrain. Keep both coordinate systems separate. */
public final class HandLighting {
    private static final HandLightRenderer GPU=new HandLightRenderer();
    private static final Matrix4f PROJECTION=new Matrix4f(), BASE=new Matrix4f();
    private static final Vector3f MUZZLE=new Vector3f(), TEMP=new Vector3f();
    private static final Quaternionf INVERSE_CAMERA=new Quaternionf();
    private static final float[] LIGHTS=new float[16*8], LOCAL=new float[4];
    private static boolean enabled=true, gunSeen, muzzleSeen, failed;
    private static float strength=1, partial;
    private static int capturedCount;
    private static String status="idle";

    private HandLighting(){}
    public static void configure(boolean on,float multiplier,float exposure,float nightVisibility,float distanceFalloff){
        enabled=on;strength=Math.max(0,Math.min(4,multiplier));GPU.configure(exposure,nightVisibility,distanceFalloff);
    }
    public static void beginFrame(){gunSeen=muzzleSeen=false;capturedCount=0;GPU.cancelCapture();}
    public static void captureBase(PoseStack pose,float tick){
        if(!enabled||failed||OculusCompat.isRenderShadow())return;
        BASE.set(pose.m_85850_().m_252922_());partial=tick;
    }
    public static void captureGun(PoseStack pose,BedrockGunModel model,ItemDisplayContext context){
        if(!enabled||failed||OculusCompat.isRenderShadow()||
            (context!=ItemDisplayContext.FIRST_PERSON_RIGHT_HAND&&context!=ItemDisplayContext.FIRST_PERSON_LEFT_HAND))return;
        gunSeen=true;PROJECTION.set(RenderSystem.getProjectionMatrix());
        if(model.getMuzzleFlashPosPath()==null)return;
        pose.m_85836_();
        try{
            for(BedrockPart node:model.getMuzzleFlashPosPath())node.translateAndRotateAndScale(pose);
            Matrix4f m=pose.m_85850_().m_252922_();MUZZLE.set(m.m30(),m.m31(),m.m32());
            muzzleSeen=Float.isFinite(MUZZLE.x)&&Float.isFinite(MUZZLE.y)&&Float.isFinite(MUZZLE.z)&&MUZZLE.lengthSquared()<100;
        }finally{pose.m_85849_();}
    }
    private static int collect(){
        Minecraft mc=Minecraft.m_91087_();
        if(mc.f_91073_==null||mc.f_91074_==null||!gunSeen||!enabled||failed||strength<=0)return 0;
        var camera=mc.f_91063_.m_109153_();var at=camera.m_90583_();
        boolean own=muzzleSeen&&GunfireSources.localMuzzle(partial,LOCAL);
        int count=own?GunfireSources.collectForHand(partial,at.f_82479_,at.f_82480_,at.f_82481_,LIGHTS,15)
            :GunfireSources.collect(partial,at.f_82479_,at.f_82480_,at.f_82481_,LIGHTS,16);
        INVERSE_CAMERA.set(camera.m_253121_()).conjugate();
        int kept=0;
        for(int i=0;i<count;i++){
            int o=i*8;TEMP.set(LIGHTS[o],LIGHTS[o+1],LIGHTS[o+2]).rotate(INVERSE_CAMERA);BASE.transformPosition(TEMP);
            // The shader supports hand geometry only within eight view-space
            // units. Distant bullets cannot affect it and must not trigger copies.
            float reach=LIGHTS[o+3]+8;
            if(!Float.isFinite(TEMP.lengthSquared())||TEMP.lengthSquared()>reach*reach)continue;
            int target=kept++*8;
            LIGHTS[target]=TEMP.x;LIGHTS[target+1]=TEMP.y;LIGHTS[target+2]=TEMP.z;LIGHTS[target+3]=LIGHTS[o+3];
            LIGHTS[target+4]=LIGHTS[o+4]*strength;LIGHTS[target+5]=LIGHTS[o+5]*strength;LIGHTS[target+6]=LIGHTS[o+6]*strength;LIGHTS[target+7]=0;
        }
        count=kept;
        if(own){int o=count++*8;LIGHTS[o]=MUZZLE.x;LIGHTS[o+1]=MUZZLE.y;LIGHTS[o+2]=MUZZLE.z;LIGHTS[o+3]=LOCAL[0];
            LIGHTS[o+4]=LOCAL[1]*strength;LIGHTS[o+5]=LOCAL[2]*strength;LIGHTS[o+6]=LOCAL[3]*strength;LIGHTS[o+7]=0;}
        return count;
    }
    /** Main hand draw has completed and flushed, before fire/water/full-screen overlays. */
    public static void renderNative(){
        try{int count=collect();if(count>0){GPU.render(PROJECTION,LIGHTS,count);status=GPU.status();}}
        catch(RuntimeException|LinkageError ex){fail(ex);}
        gunSeen=muzzleSeen=false;
    }
    /** Oculus hands have already been drawn into the G-buffer at this stage. */
    public static void captureShaderDepth(){
        capturedCount=0;
        try{int count=collect();if(count>0){if(GPU.captureDepth())capturedCount=count;status=GPU.status();}}
        catch(RuntimeException|LinkageError ex){fail(ex);}
    }
    public static void finishShaderFrame(int noHandDepth){
        // No simulation/selection work here: this frame's hand geometry, projection
        // and source positions were frozen together when its depth was captured.
        try{if(capturedCount>0&&GPU.hasCapture()){GPU.renderCaptured(PROJECTION,LIGHTS,capturedCount,noHandDepth);status=GPU.status();}}
        catch(RuntimeException|LinkageError ex){fail(ex);}
        capturedCount=0;
        gunSeen=muzzleSeen=false;
    }
    private static void fail(Throwable ex){failed=true;status="disabled after hand lighting failure: "+ex.getMessage();System.err.println("[Senkos Illuminated Gunfire] "+status);}
    public static String status(){return status;}
    public static void reset(){GPU.close();gunSeen=muzzleSeen=failed=false;capturedCount=0;status="idle";}
}
