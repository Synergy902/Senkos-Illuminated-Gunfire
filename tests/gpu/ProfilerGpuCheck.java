package offline;

import com.senko.gunfirelight.render.LightPerformanceProfiler;
import java.lang.reflect.Field;
import java.util.Map;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** Real hidden-context timestamp lifecycle checks; never initializes a Minecraft client. */
public final class ProfilerGpuCheck extends GunfireGpuCheck {
    private static Object field(String name) throws Exception {
        Field field=LightPerformanceProfiler.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    private static int queryId(Object query,String name) throws Exception {
        Field field=query.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(query);
    }
    private static long count(String type,int kind) throws Exception {
        return ((LightPerformanceProfiler.Stats[])field(type))[kind].count();
    }
    private static void idle() {
        check(!LightPerformanceProfiler.active(),"inactive session");
        LightPerformanceProfiler.beginFrame();
        check(LightPerformanceProfiler.beginPass(0)==-1,"idle world pass");
        check(LightPerformanceProfiler.beginPass(1)==-1,"idle hand pass");
        LightPerformanceProfiler.endPass(-1);
        LightPerformanceProfiler.endPass(123);
        LightPerformanceProfiler.recordSources(1000);
        LightPerformanceProfiler.clear();
    }
    public static void main(String[] args) throws Exception {
        idle(); // No context exists: any accidental GL call would fail here.
        check(GLFW.glfwInit(),"GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(64,64,"Profiler GPU test",0,0);
        check(window!=0,"hidden context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        Map<String,String> original=state();
        LightPerformanceProfiler.start();
        check(LightPerformanceProfiler.active(),"session started");
        check((boolean)field("gpuAvailable"),"real GPU timestamps enabled");
        check(LightPerformanceProfiler.beginPass(-1)==-1 && LightPerformanceProfiler.beginPass(2)==-1,"invalid categories rejected");
        int[] first=new int[32];
        for(int i=0;i<32;i++){first[i]=LightPerformanceProfiler.beginPass(i%2);check(first[i]>=0,"bounded CPU slot accepted");}
        check(LightPerformanceProfiler.beginPass(0)==-1,"CPU pool overflow drops sample");
        for(int token:first){GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);LightPerformanceProfiler.endPass(token);}
        check(count("cpu",0)==16 && count("cpu",1)==16,"CPU categories measured once");
        LightPerformanceProfiler.endPass(first[0]);
        check(count("cpu",0)==16,"duplicate end rejected");
        for(int i=0;i<32;i++){int token=LightPerformanceProfiler.beginPass(0);LightPerformanceProfiler.endPass(first[i]);LightPerformanceProfiler.endPass(token);}
        check(count("cpu",0)==48,"stale tokens cannot close reused slots");
        int overflow=LightPerformanceProfiler.beginPass(1);LightPerformanceProfiler.endPass(overflow);
        check((long)field("gpuDropped")==1,"GPU pool overflow drops only timing, not CPU sample");
        check((long)field("cpuDropped")==1,"CPU overflow count");
        LightPerformanceProfiler.recordSources(12345);
        check(((LightPerformanceProfiler.Stats)field("sources")).count()==1,"source CPU sampling");
        Object[] queryObjects=(Object[])field("QUERIES");int[] ids=new int[128];
        for(int i=0;i<queryObjects.length;i++){ids[i*2]=queryId(queryObjects[i],"begin");ids[i*2+1]=queryId(queryObjects[i],"end");}
        GL11.glFlush();
        long until=System.nanoTime()+2_000_000_000L;
        while(count("gpu",0)+count("gpu",1)<64 && System.nanoTime()<until){LightPerformanceProfiler.beginFrame();Thread.sleep(1);}
        check(count("gpu",0)==48 && count("gpu",1)==16,"available timestamp pairs collected without lost samples");
        check(((LightPerformanceProfiler.Stats[])field("gpu"))[0].mean()>=0,"nonnegative measured GPU duration");
        assertState(original);error("timestamp profiling state");
        // Delete queries even while a newly-opened pass has no end timestamp.
        LightPerformanceProfiler.beginPass(0);
        LightPerformanceProfiler.clear();
        for(int id:ids)check(!GL15.glIsQuery(id),"query resource deleted on reset");
        check(!LightPerformanceProfiler.active(),"reset ended session");
        assertState(original);error("timestamp reset state");
        GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();GL.setCapabilities(null);
        idle(); // Cleared instrumentation must also stay inert after context destruction.
        System.out.println("PASS profiler hidden GPU checks="+checks+"; inactive-no-context, nested/overflow/stale tokens, availability drain, state preservation and query deletion");
    }
}
