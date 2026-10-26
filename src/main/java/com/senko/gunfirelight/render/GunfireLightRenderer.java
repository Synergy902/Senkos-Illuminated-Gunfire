package com.senko.gunfirelight.render;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** A single visible-surface lighting pass. No block light, world render, or CPU image readback. */
public final class GunfireLightRenderer implements AutoCloseable {
    public static final int MAX_LIGHTS=LightTiles.MAX_LIGHTS;
    private final LightTiles tiles=new LightTiles();
    private final OpticsProjectionBridge optics=new OpticsProjectionBridge();
    private final Matrix4f inverse=new Matrix4f();
    private final FloatBuffer matrices=BufferUtils.createFloatBuffer(16),lightBuffer=BufferUtils.createFloatBuffer(MAX_LIGHTS*8);
    private final ShortBuffer tileBuffer=BufferUtils.createShortBuffer(LightTiles.COLUMNS*LightTiles.ROWS*LightTiles.STRIDE);
    private final int[] viewport=new int[4];
    private final double[] depthRange=new double[2];
    private final GpuState state=new GpuState();
    private int program,vao,sceneFbo,sceneColor,sceneDepth,lightTexture,tileTexture,width,height,depthFormat;
    private boolean disabled,depthCaptured;
    private String status="idle";
    private long renderedFrames;
    private int uInverse,uProjection,uSize,uWarp,uGain,uLights,uScene,uDepth,uTiles,uNoHand,uHasNoHand,uExposure,uNight,uFalloff,uShadows;
    private float exposure=1,nightVisibility=.25f,distanceFalloff=.25f;
    private boolean contactShadows=true;
    private int lightsPerTile=LightTiles.PER_TILE;

    /** Uniform-only tuning; it does not rebuild surfaces or shaders. */
    public void configure(float exposure,float nightVisibility,float distanceFalloff,boolean contactShadows,int lightsPerTile){
        this.exposure=bounded(exposure,0,8,1);this.nightVisibility=bounded(nightVisibility,0,1,.25f);
        this.distanceFalloff=bounded(distanceFalloff,.01f,4,.25f);this.contactShadows=contactShadows;
        this.lightsPerTile=Math.max(1,Math.min(LightTiles.PER_TILE,lightsPerTile));
    }
    private static float bounded(float value,float min,float max,float fallback){return Float.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;}

    public String status(){return status;}
    public long renderedFrames(){return renderedFrames;}
    public int uploadedLights(){return tiles.lightCount;}
    public int tileOverflow(){return tiles.overflow;}
    public int rejectedLights(){return tiles.rejected;}
    /** Call on render thread at resource reload/world unload; objects are recreated lazily. */
    public void invalidate(){close();disabled=false;status="idle";}

    public boolean render(Matrix4f projection,Matrix4f viewRotation,float[] lights,int count){
        depthCaptured=false;return renderWithWarp(projection,viewRotation,lights,count,optics.read());
    }
    /** Save world depth before shader-pack composites or Presence overlays can replace it. */
    public boolean captureDepth(){
        depthCaptured=false;if(disabled)return false;
        int source=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(source==0||viewport[0]!=0||viewport[1]!=0||viewport[2]<1||viewport[3]<1||viewport[2]>8192||viewport[3]>8192||GL11.glGetInteger(GL13.GL_SAMPLES)>0){status="unsupported depth capture target";return false;}
        int format=sourceDepthFormat(viewport[2],viewport[3]);if(format==0){status="unsupported world depth capture";return false;}
        try(GpuState saved=state.capture()){
            unpackDefaults();initialize();resize(viewport[2],viewport[3],format);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,sceneFbo);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            depthCaptured=true;if(renderedFrames==0)status="waiting for shader-pack final color";return true;
        }catch(RuntimeException|LinkageError failure){close();disabled=true;status="depth capture unavailable: "+failure.getMessage();return false;}
    }
    /** Consume exactly one successful capture when Oculus has bound the completed main color target. */
    public boolean renderCaptured(Matrix4f projection,Matrix4f viewRotation,float[] lights,int count){
        return renderCaptured(projection,viewRotation,lights,count,0);
    }
    public boolean renderCaptured(Matrix4f projection,Matrix4f viewRotation,float[] lights,int count,int noHandDepthTexture){
        if(!depthCaptured){status="no current world-depth capture";return false;}
        try{return renderInternal(projection,viewRotation,lights,count,optics.read(),true,noHandDepthTexture);}finally{depthCaptured=false;}
    }
    /** Also used by offline probes to verify the exact nonlinear scope projection. */
    public boolean renderWithWarp(Matrix4f projection,Matrix4f viewRotation,float[] lights,int count,float[] warp){
        return renderInternal(projection,viewRotation,lights,count,warp,false,0);
    }
    private boolean renderInternal(Matrix4f projection,Matrix4f viewRotation,float[] lights,int count,float[] warp,boolean captured,int noHandDepthTexture){
        if(disabled||count<=0){status=disabled?"disabled after GPU failure":"idle";return false;}
        if(projection==null||viewRotation==null||!finite(projection)||!finite(viewRotation)||Math.abs(projection.determinant())<1e-10f){status="invalid camera";return false;}
        tiles.build(projection,viewRotation,lights,count,warp,lightsPerTile);
        if(tiles.references==0){status="no visible lights";return false;}
        int source=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(source==0||viewport[0]!=0||viewport[1]!=0||viewport[2]<1||viewport[3]<1||viewport[2]>8192||viewport[3]>8192||GL11.glGetInteger(GL13.GL_SAMPLES)>0){status="unsupported framebuffer or multisampling";return false;}
        // An MRT G-buffer is not a finished surface-color image. Never modify its material attachments.
        if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)!=GL30.GL_COLOR_ATTACHMENT0){status="waiting for resolved color target";return false;}
        int maxTargets=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        for(int i=1;i<maxTargets;i++)if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)!=GL11.GL_NONE){status="MRT renderer requires final-color integration";return false;}
        if(GL.getCapabilities().OpenGL45&&(GL11.glGetInteger(GL45.GL_CLIP_ORIGIN)!=GL45.GL_LOWER_LEFT||GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE)!=GL45.GL_NEGATIVE_ONE_TO_ONE)){status="unsupported depth convention";return false;}
        GL11.glGetDoublev(GL11.GL_DEPTH_RANGE,depthRange);if(depthRange[0]!=0||depthRange[1]!=1){status="unsupported depth range";return false;}
        if(captured&&(viewport[2]!=width||viewport[3]!=height)){status="captured depth size differs from final target";return false;}
        int format=captured?depthFormat:sourceDepthFormat(viewport[2],viewport[3]);
        if(format==0){status="unsupported or absent world depth";return false;}
        try(GpuState saved=state.capture()){
            unpackDefaults();
            initialize();resize(viewport[2],viewport[3],format);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,sceneFbo);
            int x0=tiles.left*width/LightTiles.COLUMNS,y0=tiles.bottom*height/LightTiles.ROWS,x1=(tiles.right*width+LightTiles.COLUMNS-1)/LightTiles.COLUMNS,y1=(tiles.top*height+LightTiles.ROWS-1)/LightTiles.ROWS;
            // Scene color is sampled only by fragments inside this exact scissor.
            // Keep full depth: contact shadows and normal reconstruction sample neighbors.
            // Large native passes retain the original single combined blit.
            if(captured || (long)(x1-x0)*(y1-y0)*2 < (long)width*height){
                if(!captured)GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
                GL30.glBlitFramebuffer(x0,y0,x1,y1,x0,y0,x1,y1,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
            }else{
                GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT|(captured?0:GL11.GL_DEPTH_BUFFER_BIT),GL11.GL_NEAREST);
            }
            bind(2,lightTexture);lightBuffer.clear();lightBuffer.put(tiles.lights,0,tiles.lightCount*8).flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,2,tiles.lightCount,GL11.GL_RGBA,GL11.GL_FLOAT,lightBuffer);
            bind(3,tileTexture);tileBuffer.clear();tileBuffer.put(tiles.indices).flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,LightTiles.STRIDE,LightTiles.COLUMNS*LightTiles.ROWS,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_SHORT,tileBuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source);
            GL11.glViewport(0,0,width,height);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            for(int i=0;i<8;i++)GL11.glDisable(GL30.GL_CLIP_DISTANCE0+i);
            GL30.glColorMaski(0,true,true,true,false);GL30.glEnablei(GL11.GL_BLEND,0);
            BlendDispatch.set(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD,GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x0,y0,x1-x0,y1-y0);
            GL20.glUseProgram(program);GL30.glBindVertexArray(vao);bind(0,sceneColor);bind(1,sceneDepth);
            GL20.glUniform1i(uScene,0);GL20.glUniform1i(uDepth,1);GL20.glUniform1i(uLights,2);GL20.glUniform1i(uTiles,3);GL20.glUniform2f(uSize,width,height);
            GL20.glUniform1f(uExposure,exposure);GL20.glUniform1f(uNight,nightVisibility);GL20.glUniform1f(uFalloff,distanceFalloff);GL20.glUniform1i(uShadows,contactShadows?1:0);
            boolean noHand=captured&&noHandDepthTexture!=0&&GL11.glIsTexture(noHandDepthTexture);
            GL20.glUniform1i(uHasNoHand,noHand?1:0);GL20.glUniform1i(uNoHand,4);if(noHand)bind(4,noHandDepthTexture);
            inverse.set(projection).invert();matrix(uInverse,inverse);matrix(uProjection,projection);
            float g=warp!=null&&warp.length>=5?warp[4]:1;
            GL20.glUniform4f(uWarp,g>1?warp[0]:.5f,g>1?warp[1]:.5f,g>1?warp[2]:.3f,g>1?warp[3]:.4f);GL20.glUniform1f(uGain,Math.max(1,g));
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            renderedFrames++;status="active";return true;
        }catch(RuntimeException|LinkageError failure){close();disabled=true;status="GPU pass unavailable: "+failure.getMessage();System.err.println("[[Tacz/Sbw] Gunfire light] "+status);return false;}
    }
    private static void unpackDefaults(){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_FALSE);}
    private static boolean finite(Matrix4f m){return m.isFinite();}
    private int sourceDepthFormat(int w,int h){
        int kind=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE),id=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(id==0)return 0;int format,sw,sh;
        if(kind==GL30.GL_RENDERBUFFER){int prior=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,id);format=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);sw=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_WIDTH);sh=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_HEIGHT);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,prior);}
        else if(kind==GL11.GL_TEXTURE&&(GL.getCapabilities().OpenGL45||GL.getCapabilities().GL_ARB_direct_state_access)){
            if(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED)!=0)return 0;
            int level=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            sw=GL45.glGetTextureLevelParameteri(id,level,GL11.GL_TEXTURE_WIDTH);sh=GL45.glGetTextureLevelParameteri(id,level,GL11.GL_TEXTURE_HEIGHT);format=GL45.glGetTextureLevelParameteri(id,level,GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }else return 0;
        if(sw!=w||sh!=h)return 0;
        return switch(format){case GL11.GL_DEPTH_COMPONENT,GL14.GL_DEPTH_COMPONENT16,GL14.GL_DEPTH_COMPONENT24,GL14.GL_DEPTH_COMPONENT32,GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH32F_STENCIL8->format;default->0;};
    }
    private void initialize(){
        if(program!=0)return;
        program=link(read("point_light.vsh"),read("point_light.fsh"));vao=GL30.glGenVertexArrays();
        uInverse=loc("InverseProjection");uProjection=loc("Projection");uSize=loc("Size");uWarp=loc("Warp");uGain=loc("WarpGain");uLights=loc("Lights");uScene=loc("Scene");uDepth=loc("Depth");uTiles=loc("Tiles");uNoHand=loc("NoHandDepth");uHasNoHand=loc("HasNoHandDepth");
        uExposure=loc("Exposure");uNight=loc("NightVisibility");uFalloff=loc("DistanceFalloff");uShadows=loc("ContactShadows");
        lightTexture=texture(2,MAX_LIGHTS,GL30.GL_RGBA32F,GL11.GL_RGBA,GL11.GL_FLOAT);tileTexture=texture(LightTiles.STRIDE,LightTiles.COLUMNS*LightTiles.ROWS,GL30.GL_R16UI,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_SHORT);
    }
    private void resize(int w,int h,int format){
        if(sceneFbo!=0&&width==w&&height==h&&depthFormat==format)return;releaseSurfaces();width=w;height=h;depthFormat=format;
        sceneColor=texture(w,h,GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT);
        boolean stencil=format==GL30.GL_DEPTH24_STENCIL8||format==GL30.GL_DEPTH32F_STENCIL8;
        sceneDepth=texture(w,h,format,stencil?GL30.GL_DEPTH_STENCIL:GL11.GL_DEPTH_COMPONENT,format==GL30.GL_DEPTH24_STENCIL8?GL30.GL_UNSIGNED_INT_24_8:format==GL30.GL_DEPTH32F_STENCIL8?GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV:GL11.GL_FLOAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE);
        sceneFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,sceneFbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,sceneColor,0);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,stencil?GL30.GL_DEPTH_STENCIL_ATTACHMENT:GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,sceneDepth,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("incomplete light snapshot framebuffer");
    }
    private static int texture(int w,int h,int internal,int format,int type){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,type,0L);return t;}
    private static void bind(int unit,int t){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);if(GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_sampler_objects)GL33.glBindSampler(unit,0);}
    private int loc(String name){return GL20.glGetUniformLocation(program,name);}
    private void matrix(int loc,Matrix4f m){matrices.clear();m.get(matrices);GL20.glUniformMatrix4fv(loc,false,matrices);}
    private static String read(String file){try(var in=GunfireLightRenderer.class.getResourceAsStream("/assets/senkosgunfirelight/shaders/"+file)){if(in==null)throw new IOException("Missing "+file);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException ex){throw new IllegalStateException(ex);}}
    private static int compile(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    private static int link(String vertex,String fragment){int vs=0,fs=0,p=0;try{vs=compile(GL20.GL_VERTEX_SHADER,vertex);fs=compile(GL20.GL_FRAGMENT_SHADER,fragment);p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));return p;}catch(RuntimeException ex){if(p!=0)GL20.glDeleteProgram(p);throw ex;}finally{if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);}}
    private void releaseSurfaces(){if(sceneFbo!=0)GL30.glDeleteFramebuffers(sceneFbo);if(sceneColor!=0)GL11.glDeleteTextures(sceneColor);if(sceneDepth!=0)GL11.glDeleteTextures(sceneDepth);sceneFbo=sceneColor=sceneDepth=width=height=depthFormat=0;}
    @Override public void close(){depthCaptured=false;releaseSurfaces();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(lightTexture!=0)GL11.glDeleteTextures(lightTexture);if(tileTexture!=0)GL11.glDeleteTextures(tileTexture);program=vao=lightTexture=tileTexture=0;}
}
