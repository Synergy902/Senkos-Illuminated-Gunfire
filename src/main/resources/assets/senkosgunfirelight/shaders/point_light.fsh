#version 150
uniform sampler2D Scene;
uniform sampler2D Depth;
uniform sampler2D Lights;
uniform usampler2D Tiles;
uniform sampler2D NoHandDepth;
uniform int HasNoHandDepth;
uniform mat4 InverseProjection;
uniform mat4 Projection;
uniform vec2 Size;
uniform vec4 Warp;
uniform float WarpGain;
uniform float Exposure;
uniform float NightVisibility;
uniform float DistanceFalloff;
uniform int ContactShadows;
out vec4 fragColor;

float unwarp(float v,float c,float b){float lo=c-b,hi=c+b;if(v<lo)return v*(c-b/WarpGain)/max(lo,.00001);if(v>hi)return c+b/WarpGain+(v-hi)*(1.-c-b/WarpGain)/max(1.-hi,.00001);return c+(v-c)/WarpGain;}
float warp(float v,float c,float b){float lo=c-b/WarpGain,hi=c+b/WarpGain;if(v<lo)return v*(c-b)/max(lo,.00001);if(v>hi)return c+b+(v-hi)*(1.-c-b)/max(1.-hi,.00001);return c+(v-c)*WarpGain;}
vec2 rawUv(vec2 uv){return WarpGain>1.001?vec2(unwarp(uv.x,Warp.x,Warp.z),unwarp(uv.y,Warp.y,Warp.w)):uv;}
vec2 denseUv(vec2 uv){return WarpGain>1.001?vec2(warp(uv.x,Warp.x,Warp.z),warp(uv.y,Warp.y,Warp.w)):uv;}
vec3 position(vec2 uv,float depth){vec4 p=InverseProjection*vec4(rawUv(uv)*2.-1.,depth*2.-1.,1.);return p.xyz/p.w;}
vec3 samplePosition(vec2 uv){return position(uv,texture(Depth,uv).r);}

// Only visible depth can cast a contact shadow. Offscreen geometry is intentionally not invented.
float visibility(vec3 surface,vec3 light,vec3 normal){
    if(ContactShadows==0)return 1.;
    vec3 start=surface+normal*.035;
    for(int i=1;i<=4;i++){
        float t=float(i)/5.;vec3 p=mix(start,light,t);if(p.z>=-.04)continue;
        vec4 clip=Projection*vec4(p,1.);vec2 uv=denseUv(clip.xy/clip.w*.5+.5);
        if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))continue;
        float d=texture(Depth,uv).r;if(d>=.9999999)continue;
        vec3 found=position(uv,d);
        float gap=found.z-p.z;
        // Thickness avoids falsely treating unrelated foreground silhouettes as infinitely thick walls.
        if(gap>.08&&gap<.8)return 0.;
    }
    return 1.;
}
void main(){
    vec2 uv=gl_FragCoord.xy/Size;float depth=texture(Depth,uv).r;
    if(depth>=.9999999){fragColor=vec4(0);return;}
    // Oculus' first-person projection compresses NDC Z by .125. Only exclude
    // changed pixels in that band, preserving true close surfaces in the same range.
    if(HasNoHandDepth!=0&&depth>=.43749&&depth<=.56251&&abs(depth-texture(NoHandDepth,uv).r)>.000001){fragColor=vec4(0);return;}
    ivec2 tile=clamp(ivec2(uv*vec2(32,18)),ivec2(0),ivec2(31,17));int row=tile.y*32+tile.x;
    int count=int(texelFetch(Tiles,ivec2(0,row),0).r);if(count==0){fragColor=vec4(0);return;}
    vec3 p=position(uv,depth);vec2 pixel=1./Size;
    vec3 l=p-samplePosition(uv-vec2(pixel.x,0)),r=samplePosition(uv+vec2(pixel.x,0))-p;
    vec3 d=p-samplePosition(uv-vec2(0,pixel.y)),u=samplePosition(uv+vec2(0,pixel.y))-p;
    vec3 dx=dot(l,l)<dot(r,r)?l:r,dy=dot(d,d)<dot(u,u)?d:u;
    vec3 crossN=cross(dx,dy);float n2=dot(crossN,crossN);vec3 normal=n2>1e-20?crossN*inversesqrt(n2):normalize(-p);
    if(dot(normal,-p)<0.)normal=-normal;
    vec3 irradiance=vec3(0);
    for(int i=0;i<32;i++){
        if(i>=count)break;int index=int(texelFetch(Tiles,ivec2(i+1,row),0).r);
        vec4 source=texelFetch(Lights,ivec2(0,index),0);vec3 delta=source.xyz-p;float d2=dot(delta,delta),radius2=source.w*source.w;
        if(d2>=radius2)continue;
        float facing=max(dot(normal,delta*inversesqrt(max(d2,.00001))),0.);if(facing<=.001)continue;
        float cutoff=1.-d2/radius2;float attenuation=cutoff*cutoff/(1.+d2*DistanceFalloff);
        if(attenuation*facing<.0005)continue;
        vec3 energy=texelFetch(Lights,ivec2(1,index),0).rgb;
        irradiance+=energy*(attenuation*facing*visibility(p,source.xyz,normal));
    }
    vec3 scene=max(texture(Scene,uv).rgb,vec3(0));
    // Finished color is a material proxy, not albedo. Recover a bounded dark-material
    // response so night lighting is not capped by the old ambient illumination.
    // The chroma term retains the visible texture detail and warm/cool material colors.
    float luminance=dot(scene,vec3(.2126,.7152,.0722));
    vec3 chroma=sqrt(clamp(scene/max(luminance,.035),vec3(0),vec3(2)));
    vec3 material=scene+NightVisibility*(.35+.65*chroma)*(1.-clamp(luminance,0.,1.));
    vec3 contribution=1.-exp(-irradiance*Exposure*material);
    fragColor=vec4(contribution,0);
}
