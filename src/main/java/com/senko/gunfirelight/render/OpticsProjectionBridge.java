package com.senko.gunfirelight.render;

import java.lang.reflect.Method;

/** Optional one-time linkage; never ends or changes the scope renderer's world phase. */
final class OpticsProjectionBridge {
    private boolean searched;
    private Method active,center,band,gain;
    final float[] values={.5f,.5f,.3f,.4f,1};
    float[] read(){
        if(!searched){searched=true;try{Class<?> c=Class.forName("com.senko.optics.FoveatedProjection",false,getClass().getClassLoader());active=c.getMethod("sourceWarped");center=c.getMethod("centerUV");band=c.getMethod("bandUV");gain=c.getMethod("gain");}catch(ReflectiveOperationException|LinkageError ignored){active=null;}}
        values[4]=1;
        if(active!=null)try{if(Boolean.TRUE.equals(active.invoke(null))){float[] c=(float[])center.invoke(null),b=(float[])band.invoke(null);float g=((Number)gain.invoke(null)).floatValue();if(c.length>=2&&b.length>=2&&g>=1&&g<=64&&valid(c[0],b[0])&&valid(c[1],b[1])){values[0]=c[0];values[1]=c[1];values[2]=b[0];values[3]=b[1];values[4]=g;}}}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){values[4]=1;}
        return values;
    }
    private boolean valid(float c,float b){return Float.isFinite(c)&&Float.isFinite(b)&&c>.01f&&c<.99f&&b>0&&b<Math.min(c,1-c);}
}
