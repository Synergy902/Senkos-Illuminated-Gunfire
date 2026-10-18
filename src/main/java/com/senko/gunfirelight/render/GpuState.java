package com.senko.gunfirelight.render;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Reusable snapshot: no per-frame arrays or direct buffers; exact state restoration. */
final class GpuState implements AutoCloseable {
    int draw,read,readBuffer,drawReadBuffer,program,vao,active,unpack,unpackAlign,unpackRow,unpackSkipRows,unpackSkipPixels,unpackSwap;
    int srcRGB,dstRGB,srcAlpha,dstAlpha,equationRGB,equationAlpha;
    final int[] viewport=new int[4],scissor=new int[4],textures=new int[5],samplers=new int[5],polygon=new int[2];
    final int[] caps={GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL30.GL_FRAMEBUFFER_SRGB,GL30.GL_RASTERIZER_DISCARD,GL11.GL_COLOR_LOGIC_OP};
    final boolean[] enabled=new boolean[caps.length],clip=new boolean[8];
    boolean depthMask,samplerSupport,indexedBlend,inUse;
    boolean[] blend=new boolean[0];byte[][] masks=new byte[0][4];
    final ByteBuffer mask=BufferUtils.createByteBuffer(4);
    GpuState capture(){
        if(inUse)throw new IllegalStateException("Reentrant gunfire GPU pass");inUse=true;
        draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        unpackAlign=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);unpackRow=GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);unpackSkipRows=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);unpackSkipPixels=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        unpackSwap=GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
        samplerSupport=GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_sampler_objects;indexedBlend=BlendDispatch.indexed();
        depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        srcRGB=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);dstRGB=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        equationRGB=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);drawReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);
        for(int i=0;i<caps.length;i++)enabled[i]=GL11.glIsEnabled(caps[i]);
        for(int i=0;i<8;i++)clip[i]=GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0+i);
        int targets=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);if(blend.length!=targets){blend=new boolean[targets];masks=new byte[targets][4];}
        for(int i=0;i<targets;i++){blend[i]=GL30.glIsEnabledi(GL11.GL_BLEND,i);mask.clear();GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,i,mask);mask.get(masks[i]);}
        for(int i=0;i<5;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(samplerSupport)samplers[i]=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}
        GL13.glActiveTexture(active);return this;
    }
    public void close(){
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);GL11.glReadBuffer(drawReadBuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(readBuffer);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        GL20.glUseProgram(program);GL30.glBindVertexArray(vao);
        GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
        for(int i=0;i<caps.length;i++)set(caps[i],enabled[i]);for(int i=0;i<8;i++)set(GL30.GL_CLIP_DISTANCE0+i,clip[i]);
        GL11.glDepthMask(depthMask);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygon[0]);
        BlendDispatch.set(equationRGB,equationAlpha,srcRGB,dstRGB,srcAlpha,dstAlpha);
        for(int i=0;i<blend.length;i++){if(blend[i])GL30.glEnablei(GL11.GL_BLEND,i);else GL30.glDisablei(GL11.GL_BLEND,i);byte[] m=masks[i];GL30.glColorMaski(i,m[0]!=0,m[1]!=0,m[2]!=0,m[3]!=0);}
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,unpackAlign);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,unpackRow);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,unpackSkipRows);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,unpackSkipPixels);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,unpackSwap);
        for(int i=0;i<5;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);if(samplerSupport)GL33.glBindSampler(i,samplers[i]);}GL13.glActiveTexture(active);inUse=false;
    }
    static void set(int cap,boolean on){if(on)GL11.glEnable(cap);else GL11.glDisable(cap);}
}
