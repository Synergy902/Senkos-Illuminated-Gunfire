package offline;
import java.nio.file.*; import java.net.*; import java.lang.reflect.*; import java.util.*;
import org.lwjgl.glfw.GLFW; import org.lwjgl.opengl.*; import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*; import static org.lwjgl.opengl.GL15.*; import static org.lwjgl.opengl.GL20.*; import static org.lwjgl.opengl.GL30.*;
public final class GpuPassComparison extends GunfireGpuCheck {
 static class Isolated extends URLClassLoader {
  Isolated(Path path)throws Exception{super(new URL[]{path.toUri().toURL()},GpuPassComparison.class.getClassLoader());}
  protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
   if(!name.startsWith("com.senko.gunfirelight.render."))return super.loadClass(name,resolve);
   Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
  }
  public URL getResource(String name){return name.startsWith("assets/senkosgunfirelight/")?findResource(name):super.getResource(name);}
 }
 record Variant(String name,Isolated loader,Object renderer,Method render)implements AutoCloseable{
  static Variant create(Path root,String name)throws Exception {Isolated loader=new Isolated(root);Class<?> cls=loader.loadClass("com.senko.gunfirelight.render.GunfireLightRenderer");Object r=cls.getConstructor().newInstance();return new Variant(name,loader,r,cls.getMethod("renderWithWarp",Matrix4f.class,Matrix4f.class,float[].class,int.class,float[].class));}
  boolean draw(float[] lights,float[] warp)throws Exception{return (boolean)render.invoke(renderer,projection,identity,lights,lights.length/8,warp);}
  public void close()throws Exception{renderer.getClass().getMethod("close").invoke(renderer);loader.close();}
 }
 static float[] field(int count,float radius,float z){float[] lights=new float[count*8];for(int i=0;i<count;i++){float x=count==1?0:((i%8)-3.5f)*1.1f,y=count==1?0:((i/8)-1.5f)*.9f;System.arraycopy(light(x,y,z,radius),0,lights,i*8,8);}return lights;}
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[0]);
  check(GLFW.glfwInit(),"init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);long window=GLFW.glfwCreateWindow(64,64,"GPU benchmark",0,0);check(window!=0,"hidden");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();System.out.println("GPU "+glGetString(GL_RENDERER));
    sceneProgram=link("#version 150\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}","#version 150\nuniform vec2 Size;uniform mat4 Projection;uniform vec4 Warp;uniform float Gain;uniform int Mode;out vec4 color;float unwarp(float v,float c,float b){float lo=c-b,hi=c+b;if(v<lo)return v*(c-b/Gain)/max(lo,.00001);if(v>hi)return c+b/Gain+(v-hi)*(1.-c-b/Gain)/max(1.-hi,.00001);return c+(v-c)/Gain;}void main(){vec2 uv=gl_FragCoord.xy/Size;if(Gain>1.001)uv=vec2(unwarp(uv.x,Warp.x,Warp.z),unwarp(uv.y,Warp.y,Warp.w));float z=Mode==1?-.1:-6.;if(Mode==2&&uv.x>.585&&uv.x<.635&&uv.y>.45&&uv.y<.55)z=-5.3;vec4 projected=Projection*vec4(0,0,z,1);gl_FragDepth=projected.z/projected.w*.5+.5;if(uv.x>.95){gl_FragDepth=1.;color=vec4(.12,.2,.3,.37);return;}float checker=mod(floor(uv.x*24.)+floor(uv.y*24.),2.);color=vec4(.12+.025*checker,.10,.07,.37);if(Mode==3)color.rgb*=.05;}");vao=glGenVertexArrays();
  List<Variant> variants=List.of(Variant.create(Path.of(args[1]),"baseline"),Variant.create(Path.of(args[2]),"candidate"));
  Map<String,float[]> cases=new LinkedHashMap<>();cases.put("small",field(1,1.5f,-5));cases.put("single",field(1,4,-4));cases.put("near-large",field(1,10,-4));cases.put("sparse16",field(16,1,-5));cases.put("dense32",field(32,4,-5));cases.put("no-surface",field(1,2,-2));
  List<Map<String,Object>> rows=new ArrayList<>();
  for(int[] size:new int[][]{{1920,1080},{2560,1440}}){projection=new Matrix4f().perspective((float)Math.toRadians(70),(float)size[0]/size[1],.05f,256);try(Surface surf=surface(size[0],size[1],GL_DEPTH24_STENCIL8)){
   for(var testcase:cases.entrySet()){
    float[] lights=testcase.getValue();float[] expected=null;
    for(Variant v:variants){scene(surf,ordinary,2);v.draw(lights,ordinary);float[] actual=pixels(surf);if(expected==null)expected=actual;else{double delta=0;int different=0;for(int i=0;i<actual.length;i++){delta=Math.max(delta,Math.abs(actual[i]-expected[i]));if(actual[i]!=expected[i])different++;}check(delta<=.0011,"pixel equivalence "+v.name+" "+testcase.getKey()+" delta="+delta);System.out.println("PIXELS "+v.name+" "+testcase.getKey()+" delta="+delta+" different="+different);}}
    for(Variant v:variants){for(int i=0;i<16;i++){scene(surf,ordinary,2);v.draw(lights,ordinary);}}glFinish();
    Map<String,List<Long>> results=new LinkedHashMap<>();for(Variant v:variants)results.put(v.name,new ArrayList<>());
    int[] queries=new int[64];for(int i=0;i<queries.length;i++)queries[i]=glGenQueries();
    for(int round=0;round<4;round++)for(int j=0;j<variants.size();j++){Variant v=variants.get((j+round)%variants.size());for(int i=0;i<queries.length;i++){scene(surf,ordinary,2);glBeginQuery(GL33.GL_TIME_ELAPSED,queries[i]);v.draw(lights,ordinary);glEndQuery(GL33.GL_TIME_ELAPSED);}for(int q:queries)results.get(v.name).add(GL33.glGetQueryObjectui64(q,GL_QUERY_RESULT));}
    for(int q:queries)glDeleteQueries(q);
    for(Variant v:variants){List<Long> ns=results.get(v.name);Collections.sort(ns);Map<String,Object> row=new LinkedHashMap<>();row.put("width",size[0]);row.put("height",size[1]);row.put("case",testcase.getKey());row.put("variant",v.name);row.put("median_ms",ns.get(ns.size()/2)/1e6);row.put("p95_ms",ns.get((int)(ns.size()*.95))/1e6);rows.add(row);System.out.println("BENCH "+row);}error("benchmarkcase");
   }
  }}
  for(Variant v:variants)v.close();glDeleteProgram(sceneProgram);glDeleteVertexArrays(vao);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();Files.writeString(out.resolve("gpu-comparison.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));System.out.println("PASS checks="+checks);
 }
}
