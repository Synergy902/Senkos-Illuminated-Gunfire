package com.senko.gunfirelight.render;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** A bounded GPU material-light pass over the already drawn hand depth, never another model draw. */
public final class HandLightRenderer implements AutoCloseable {
    public static final int MAX_LIGHTS=16;
    private final GpuState state=new GpuState();
    private final int[] viewport=new int[4];
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16),lights=BufferUtils.createFloatBuffer(MAX_LIGHTS*8);
    private final Matrix4f inverse=new Matrix4f();
    private int program,vao,fbo,color,depth,width,height,format;
    private int uInverse,uSize,uLights,uCount,uScene,uDepth,uNoHand,uCaptured,uExposure,uNight,uFalloff;
    private boolean captured;
    private float exposure=1,night=.25f,falloff=.25f;
    private String status="idle";
    public void configure(float e,float n,float f){exposure=Math.max(0,e);night=Math.max(0,n);falloff=Math.max(0,f);}
    public String status(){return status;}
    public boolean hasCapture(){return captured;}
    public void cancelCapture(){captured=false;}

    public boolean captureDepth(){
        captured=false;
        int source=target();if(source==0)return false;
        try(GpuState saved=state.capture()){
            unpack();int sourceFormat=depthFormat();if(sourceFormat==0){status="unsupported hand depth";return false;}
            initialize();resize(viewport[2],viewport[3],sourceFormat);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            captured=true;status="hand depth captured";return true;
        }
    }
    public boolean render(Matrix4f projection,float[] sources,int count){captured=false;return draw(projection,sources,count,0,false);}
    public boolean renderCaptured(Matrix4f projection,float[] sources,int count,int noHandDepth){
        if(!captured||noHandDepth==0||!GL11.glIsTexture(noHandDepth))return false;
        try{return draw(projection,sources,count,noHandDepth,true);}finally{captured=false;}
    }
    private boolean draw(Matrix4f projection,float[] sources,int count,int noHandDepth,boolean useCapture){
        if(count<=0||projection==null||!projection.isFinite()||Math.abs(projection.determinant())<1e-9)return false;
        int source=target();if(source==0)return false;
        if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)!=GL30.GL_COLOR_ATTACHMENT0){status="waiting for final hand color";return false;}
        int targets=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        for(int i=1;i<targets;i++)if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)!=GL11.GL_NONE){status="waiting for final hand color";return false;}
        if(useCapture&&(width!=viewport[2]||height!=viewport[3])){status="hand depth target resized";return false;}
        try(GpuState saved=state.capture()){
            unpack();int sourceFormat=useCapture?format:depthFormat();if(sourceFormat==0){status="unsupported hand depth";return false;}
            initialize();resize(viewport[2],viewport[3],sourceFormat);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT|(useCapture?0:GL11.GL_DEPTH_BUFFER_BIT),GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            for(int i=0;i<8;i++)GL11.glDisable(GL30.GL_CLIP_DISTANCE0+i);
            GL30.glColorMaski(0,true,true,true,false);GL30.glEnablei(GL11.GL_BLEND,0);
            BlendDispatch.set(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD,GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            GL20.glUseProgram(program);GL30.glBindVertexArray(vao);bind(0,color);bind(1,depth);if(useCapture)bind(2,noHandDepth);
            GL20.glUniform1i(uScene,0);GL20.glUniform1i(uDepth,1);GL20.glUniform1i(uNoHand,2);GL20.glUniform1i(uCaptured,useCapture?1:0);
            GL20.glUniform2f(uSize,width,height);GL20.glUniform1f(uExposure,exposure);GL20.glUniform1f(uNight,night);GL20.glUniform1f(uFalloff,falloff);
            count=Math.min(MAX_LIGHTS,Math.min(count,sources.length/8));lights.clear();lights.put(sources,0,count*8).flip();GL20.glUniform4fv(uLights,lights);GL20.glUniform1i(uCount,count);
            inverse.set(projection).invert();matrix.clear();inverse.get(matrix);GL20.glUniformMatrix4fv(uInverse,false,matrix);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);status="active";return true;
        }
    }
    private int target(){
        int target=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        if(target==0||viewport[0]!=0||viewport[1]!=0||viewport[2]<1||viewport[3]<1||viewport[2]>8192||viewport[3]>8192||GL11.glGetInteger(GL13.GL_SAMPLES)>0){status="unsupported hand target";return 0;}
        return target;
    }
    private int depthFormat(){
        int kind=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int id=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(id==0)return 0;int value,w,h;
        if(kind==GL30.GL_RENDERBUFFER){
            int prior=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,id);
            value=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            w=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_WIDTH);h=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_HEIGHT);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,prior);
        }else if(kind==GL11.GL_TEXTURE){
            if(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED)!=0)return 0;
            int level=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
            value=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_WIDTH);h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_HEIGHT);
        }else return 0;
        if(w!=viewport[2]||h!=viewport[3])return 0;
        return switch(value){case GL11.GL_DEPTH_COMPONENT,GL14.GL_DEPTH_COMPONENT16,GL14.GL_DEPTH_COMPONENT24,GL14.GL_DEPTH_COMPONENT32,GL30.GL_DEPTH_COMPONENT32F,GL30.GL_DEPTH24_STENCIL8,GL30.GL_DEPTH32F_STENCIL8->value;default->0;};
    }
    private void initialize(){
        if(program!=0)return;int vs=0,fs=0,p=0;
        try{vs=compile(GL20.GL_VERTEX_SHADER,read("point_light.vsh"));fs=compile(GL20.GL_FRAGMENT_SHADER,read("hand_light.fsh"));p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);
            if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));program=p;
        }catch(RuntimeException ex){if(p!=0)GL20.glDeleteProgram(p);throw ex;}finally{if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);}
        vao=GL30.glGenVertexArrays();uInverse=loc("InverseProjection");uSize=loc("Size");uLights=loc("Lights");uCount=loc("Count");uScene=loc("Scene");uDepth=loc("Depth");uNoHand=loc("NoHandDepth");uCaptured=loc("Captured");uExposure=loc("Exposure");uNight=loc("NightVisibility");uFalloff=loc("DistanceFalloff");
    }
    private void resize(int w,int h,int f){
        if(fbo!=0&&w==width&&h==height&&f==format)return;releaseSurfaces();width=w;height=h;format=f;
        color=texture(w,h,GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT);
        boolean stencil=f==GL30.GL_DEPTH24_STENCIL8||f==GL30.GL_DEPTH32F_STENCIL8;
        depth=texture(w,h,f,stencil?GL30.GL_DEPTH_STENCIL:GL11.GL_DEPTH_COMPONENT,f==GL30.GL_DEPTH24_STENCIL8?GL30.GL_UNSIGNED_INT_24_8:f==GL30.GL_DEPTH32F_STENCIL8?GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV:GL11.GL_FLOAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE);
        fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,stencil?GL30.GL_DEPTH_STENCIL_ATTACHMENT:GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete hand lighting snapshot");
    }
    private static int texture(int w,int h,int internal,int f,int type){int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,f,type,0L);return t;}
    private static void unpack(){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_FALSE);}
    private static void bind(int unit,int id){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);if(GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_sampler_objects)GL33.glBindSampler(unit,0);}
    private int loc(String name){return GL20.glGetUniformLocation(program,name);}
    private static String read(String file){try(var in=HandLightRenderer.class.getResourceAsStream("/assets/senkosgunfirelight/shaders/"+file)){if(in==null)throw new IOException(file);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException ex){throw new IllegalStateException(ex);}}
    private static int compile(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    private void releaseSurfaces(){if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(color!=0)GL11.glDeleteTextures(color);if(depth!=0)GL11.glDeleteTextures(depth);fbo=color=depth=width=height=format=0;}
    public void close(){captured=false;releaseSurfaces();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);program=vao=0;status="idle";}
}
