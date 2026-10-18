package com.senko.gunfirelight.render;

import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Allocation-free conservative sphere binning. Inputs are in visual-priority order. */
public final class LightTiles {
    public static final int MAX_LIGHTS=256, PER_TILE=32, COLUMNS=32, ROWS=18, STRIDE=PER_TILE+1;
    public final short[] indices=new short[COLUMNS*ROWS*STRIDE];
    public final float[] lights=new float[MAX_LIGHTS*8];
    public int lightCount, references, overflow, rejected, left, bottom, right, top;
    private final Vector4f point=new Vector4f();

    public void build(Matrix4f projection, Matrix4f view, float[] input,int count,float[] warp) {
        build(projection,view,input,count,warp,PER_TILE);
    }
    public void build(Matrix4f projection, Matrix4f view, float[] input,int count,float[] warp,int lightsPerTile) {
        lightsPerTile=Math.max(1,Math.min(PER_TILE,lightsPerTile));
        Arrays.fill(indices,(short)0);lightCount=references=overflow=rejected=0;
        left=COLUMNS;bottom=ROWS;right=top=0;
        if(input==null)return;
        count=Math.max(0,Math.min(MAX_LIGHTS,Math.min(count,input.length/8)));
        for(int n=0;n<count;n++) {
            int at=n*8;boolean finite=true;for(int k=0;k<7;k++)finite&=Float.isFinite(input[at+k]);
            float radius=input[at+3];
            if(!finite||radius<=.001f||radius>128||Math.max(input[at+4],Math.max(input[at+5],input[at+6]))<=0){rejected++;continue;}
            point.set(input[at],input[at+1],input[at+2],1);view.transform(point);
            float x=point.x,y=point.y,z=point.z;
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||z-radius>=-.02f){rejected++;continue;}
            float ax=0,ay=0,bx=1,by=1;
            if(z+radius<-.02f){
                ax=ay=Float.POSITIVE_INFINITY;bx=by=Float.NEGATIVE_INFINITY;
                // The eight corners of the enclosing view-space cube conservatively cover a sphere.
                for(int c=0;c<8;c++) {
                    point.set(x+((c&1)==0?-radius:radius),y+((c&2)==0?-radius:radius),z+((c&4)==0?-radius:radius),1);
                    projection.transform(point);
                    if(point.w<=0||!Float.isFinite(point.w)){ax=ay=0;bx=by=1;break;}
                    float u=.5f+.5f*point.x/point.w,v=.5f+.5f*point.y/point.w;
                    ax=Math.min(ax,u);ay=Math.min(ay,v);bx=Math.max(bx,u);by=Math.max(by,v);
                }
                if(bx<0||by<0||ax>1||ay>1){rejected++;continue;}
                if(warp!=null&&warp.length>=5&&warp[4]>1.001f){ax=warp(ax,warp[0],warp[2],warp[4]);bx=warp(bx,warp[0],warp[2],warp[4]);ay=warp(ay,warp[1],warp[3],warp[4]);by=warp(by,warp[1],warp[3],warp[4]);}
            }
            int x0=Math.max(0,Math.min(COLUMNS-1,(int)Math.floor(ax*COLUMNS))),y0=Math.max(0,Math.min(ROWS-1,(int)Math.floor(ay*ROWS)));
            int x1=Math.max(0,Math.min(COLUMNS-1,(int)Math.floor(bx*COLUMNS))),y1=Math.max(0,Math.min(ROWS-1,(int)Math.floor(by*ROWS)));
            int index=lightCount++,out=index*8;
            lights[out]=x;lights[out+1]=y;lights[out+2]=z;lights[out+3]=radius;
            for(int k=4;k<7;k++)lights[out+k]=Math.min(64,Math.max(0,input[at+k]));lights[out+7]=0;
            boolean used=false;
            for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){
                int tile=(ty*COLUMNS+tx)*STRIDE,items=indices[tile];
                if(items<lightsPerTile){indices[tile+1+items]=(short)index;indices[tile]=(short)(items+1);references++;used=true;}
                else overflow++;
            }
            if(used){left=Math.min(left,x0);bottom=Math.min(bottom,y0);right=Math.max(right,x1+1);top=Math.max(top,y1+1);}
        }
    }
    public static float warp(float value,float center,float band,float gain){float lo=center-band/gain,hi=center+band/gain;if(value<lo)return value*(center-band)/Math.max(lo,.00001f);if(value>hi)return center+band+(value-hi)*(1-center-band)/Math.max(1-hi,.00001f);return center+(value-center)*gain;}
    public static float unwarp(float value,float center,float band,float gain){float lo=center-band,hi=center+band;if(value<lo)return value*(center-band/gain)/Math.max(lo,.00001f);if(value>hi)return center+band/gain+(value-hi)*(1-center-band/gain)/Math.max(1-hi,.00001f);return center+(value-center)/gain;}
}
