package com.senko.gunfirelight.render;

import org.lwjgl.opengl.*;

/** Core and ARB indexed-blend entry points have different names on GL 3.2. */
final class BlendDispatch {
    private BlendDispatch() {}
    static boolean core(GLCapabilities c) {
        return c.OpenGL40 && c.glBlendEquationSeparatei != 0 && c.glBlendFuncSeparatei != 0;
    }
    static boolean arb(GLCapabilities c) {
        return c.GL_ARB_draw_buffers_blend && c.glBlendEquationSeparateiARB != 0 && c.glBlendFuncSeparateiARB != 0;
    }
    static boolean indexed() { var c=GL.getCapabilities();return core(c)||arb(c); }
    static void set(int equationRGB,int equationAlpha,int srcRGB,int dstRGB,int srcAlpha,int dstAlpha) {
        var c=GL.getCapabilities();
        if(core(c)) {
            GL40.glBlendEquationSeparatei(0,equationRGB,equationAlpha);
            GL40.glBlendFuncSeparatei(0,srcRGB,dstRGB,srcAlpha,dstAlpha);
        } else if(arb(c)) {
            ARBDrawBuffersBlend.glBlendEquationSeparateiARB(0,equationRGB,equationAlpha);
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(0,srcRGB,dstRGB,srcAlpha,dstAlpha);
        } else {
            GL20.glBlendEquationSeparate(equationRGB,equationAlpha);
            GL14.glBlendFuncSeparate(srcRGB,dstRGB,srcAlpha,dstAlpha);
        }
    }
}
