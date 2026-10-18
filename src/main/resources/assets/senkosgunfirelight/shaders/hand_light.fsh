#version 150
uniform sampler2D Scene;
uniform sampler2D Depth;
uniform sampler2D NoHandDepth;
uniform int Captured;
uniform mat4 InverseProjection;
uniform vec2 Size;
uniform vec4 Lights[32];
uniform int Count;
uniform float Exposure;
uniform float NightVisibility;
uniform float DistanceFalloff;
out vec4 fragColor;
vec3 position(vec2 uv,float d){vec4 p=InverseProjection*vec4(uv*2.-1.,d*2.-1.,1.);return p.xyz/p.w;}
vec3 samplePosition(vec2 uv){return position(uv,texture(Depth,uv).r);}
void main(){
    vec2 uv=gl_FragCoord.xy/Size;float depth=texture(Depth,uv).r;
    if(depth>=.9999999){fragColor=vec4(0);return;}
    // In an Oculus frame the same depth contains world and compressed hand pixels.
    // Only changed pixels in Iris' hand Z band belong to the hand projection.
    if(Captured!=0&&(depth<.43749||depth>.56251||abs(depth-texture(NoHandDepth,uv).r)<.000001)){fragColor=vec4(0);return;}
    vec3 p=position(uv,depth);
    if(any(isnan(p))||any(isinf(p))||dot(p,p)>64.){fragColor=vec4(0);return;}
    vec2 pixel=1./Size;
    vec3 l=p-samplePosition(uv-vec2(pixel.x,0)),r=samplePosition(uv+vec2(pixel.x,0))-p;
    vec3 d=p-samplePosition(uv-vec2(0,pixel.y)),u=samplePosition(uv+vec2(0,pixel.y))-p;
    vec3 n=cross(dot(l,l)<dot(r,r)?l:r,dot(d,d)<dot(u,u)?d:u);
    float n2=dot(n,n);n=n2>1e-20?n*inversesqrt(n2):normalize(-p);if(dot(n,-p)<0.)n=-n;
    vec3 energy=vec3(0);
    for(int i=0;i<16;i++){
        if(i>=Count)break;vec4 source=Lights[2*i];vec3 delta=source.xyz-p;float d2=dot(delta,delta),r2=source.w*source.w;
        if(d2>=r2)continue;
        // A soft angular wrap gives curved, low-poly gun/arm surfaces a smooth
        // flash response without turning the entire weapon into fullbright.
        float facing=clamp((dot(n,delta*inversesqrt(max(d2,.00001)))+.22)/1.22,0.,1.);
        float cut=1.-d2/r2;energy+=Lights[2*i+1].rgb*(facing*cut*cut/(1.+d2*DistanceFalloff));
    }
    vec3 scene=max(texture(Scene,uv).rgb,vec3(0));float luminance=dot(scene,vec3(.2126,.7152,.0722));
    vec3 chroma=sqrt(clamp(scene/max(luminance,.035),vec3(0),vec3(2)));
    vec3 material=scene+NightVisibility*(.35+.65*chroma)*(1.-clamp(luminance,0.,1.));
    fragColor=vec4(1.-exp(-energy*Exposure*material),0);
}
