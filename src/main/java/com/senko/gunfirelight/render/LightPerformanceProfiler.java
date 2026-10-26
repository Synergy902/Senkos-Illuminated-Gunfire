package com.senko.gunfirelight.render;

import com.senko.gunfirelight.SenkosGunfireLight;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.Arrays;
import java.util.Locale;

/** Opt-in 30-second instrumentation. Timestamp results are consumed only after availability. */
public final class LightPerformanceProfiler {
    public static final int WORLD = 0, HAND = 1;
    private static final long DURATION = 30_000_000_000L, DRAIN = 2_000_000_000L;
    private static final int GPU_SLOTS = 64, CPU_SLOTS = 32;
    private static final Pass[] PASSES = new Pass[CPU_SLOTS];
    private static final Query[] QUERIES = new Query[GPU_SLOTS];
    private static Stats[] cpu, gpu;
    private static Stats sources;
    private static boolean session, recording, gpuAvailable;
    private static long started, deadline, gpuDropped, cpuDropped;
    private static int serial;
    private static String last = "No lighting profile recorded. Use /gunfirelight profile.";

    private LightPerformanceProfiler() {}
    public static boolean active() { return session; }

    public static void start() {
        clear();
        cpu = new Stats[]{new Stats(), new Stats()};
        gpu = new Stats[]{new Stats(), new Stats()};
        sources = new Stats();
        gpuDropped = cpuDropped = 0;
        gpuAvailable = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        if (gpuAvailable) {
            try {
                for (int i=0;i<GPU_SLOTS;i++) {
                    Query query = new Query(); QUERIES[i] = query;
                    query.begin = GL15.glGenQueries(); query.end = GL15.glGenQueries();
                }
            } catch (RuntimeException | LinkageError failure) {
                deleteQueries(); gpuAvailable = false;
                SenkosGunfireLight.LOG.warn("GPU lighting timestamps unavailable; profiling CPU only", failure);
            }
        }
        for (int i=0;i<CPU_SLOTS;i++) PASSES[i] = new Pass();
        started = System.nanoTime(); deadline = started+DURATION;
        session = recording = true;
        last = "Recording lighting costs for 30 seconds; " + (gpuAvailable ? "GPU timestamps enabled" : "GPU timestamps unavailable") + ".";
        SenkosGunfireLight.LOG.info("[GunfireLight profile] {}", last);
    }

    /** Called once per frame. No busy wait, glFinish, or unavailable result read. */
    public static void beginFrame() {
        if (!session) return;
        poll();
        long now = System.nanoTime();
        if (now >= deadline) recording = false;
        if (!recording && (pending()==0 || now-deadline >= DRAIN)) finish();
    }

    /** Returns -1 when idle, after the capture window, or when nesting exceeds the bounded pool. */
    public static int beginPass(int kind) {
        if (!session || !recording || kind<0 || kind>1) return -1;
        long now = System.nanoTime();
        if (now>=deadline) { recording=false; return -1; }
        int slot=-1;
        for (int i=0;i<CPU_SLOTS;i++) if (!PASSES[i].open) { slot=i; break; }
        if (slot<0) { cpuDropped++; return -1; }
        Pass pass=PASSES[slot];
        pass.open=true; pass.kind=kind; pass.started=now; pass.query=-1;
        pass.token=((++serial & 0x03ffffff)<<5)|slot;
        if (gpuAvailable) {
            try {
                for (int i=0;i<GPU_SLOTS;i++) if (!QUERIES[i].busy) {
                    Query query=QUERIES[i]; query.busy=true; query.pending=false; query.kind=kind;
                    pass.query=i; GL33.glQueryCounter(query.begin,GL33.GL_TIMESTAMP); break;
                }
                if (pass.query<0) gpuDropped++;
            } catch (RuntimeException | LinkageError failure) {
                disableGpu(failure); pass.query=-1;
            }
        }
        return pass.token;
    }

    public static void endPass(int token) {
        if (!session || token<0) return;
        Pass pass=PASSES[token & 31];
        if (!pass.open || pass.token!=token) return;
        if (pass.query>=0 && gpuAvailable) {
            Query query=QUERIES[pass.query];
            try { GL33.glQueryCounter(query.end,GL33.GL_TIMESTAMP); query.pending=true; }
            catch (RuntimeException | LinkageError failure) { disableGpu(failure); }
        }
        cpu[pass.kind].add(System.nanoTime()-pass.started);
        pass.open=false;
    }

    public static void recordSources(long nanos) {
        if (session && recording && System.nanoTime()<deadline) sources.add(nanos);
    }

    private static void poll() {
        if (!gpuAvailable) return;
        try {
            for (Query query:QUERIES) {
                if (!query.pending) continue;
                if (GL15.glGetQueryObjecti(query.end,GL15.GL_QUERY_RESULT_AVAILABLE)==0
                        || GL15.glGetQueryObjecti(query.begin,GL15.GL_QUERY_RESULT_AVAILABLE)==0) continue;
                long a=GL33.glGetQueryObjectui64(query.begin,GL15.GL_QUERY_RESULT);
                long b=GL33.glGetQueryObjectui64(query.end,GL15.GL_QUERY_RESULT);
                if (b>=a) gpu[query.kind].add(b-a); else gpuDropped++;
                query.pending=query.busy=false;
            }
        } catch (RuntimeException | LinkageError failure) { disableGpu(failure); }
    }

    private static void disableGpu(Throwable failure) {
        gpuDropped+=pending(); gpuAvailable=false;
        SenkosGunfireLight.LOG.warn("GPU lighting timestamps failed; profiling CPU only",failure);
        deleteQueries();
    }

    private static int pending() {
        if (!gpuAvailable) return 0;
        int count=0; for (Query query:QUERIES) if (query.busy) count++; return count;
    }

    private static void finish() {
        gpuDropped+=pending();
        String world="World "+summary(cpu[WORLD],gpu[WORLD]);
        String hand="Hand "+summary(cpu[HAND],gpu[HAND]);
        String source="Sources CPU "+sources.describe()+"; dropped GPU="+gpuDropped+", CPU="+cpuDropped+".";
        String note="30s lighting profile: milliseconds per measured invocation (including culled calls), not FPS or total frame time. World depth capture and lighting apply are separate calls.";
        last=note+"\n"+world+"\n"+hand+"\n"+source;
        session=recording=false;
        deleteQueries();
        for (String line:new String[]{note,world,hand,source}) {
            SenkosGunfireLight.LOG.info("[GunfireLight profile] {}",line);
            Minecraft mc=Minecraft.m_91087_();
            if (mc.f_91074_!=null) mc.f_91074_.m_213846_(Component.m_237113_("[GunfireLight] "+line));
        }
    }

    private static String summary(Stats cpu,Stats gpu) {
        return "CPU "+cpu.describe()+" | GPU "+(gpuAvailable?gpu.describe():"unavailable");
    }

    public static String status() {
        if (!session) return last;
        double left=Math.max(0,(deadline-System.nanoTime())/1e9);
        return String.format(Locale.ROOT,"Lighting profile: %.1fs remaining%s; world calls=%d, hand calls=%d, GPU pending=%d, GPU dropped=%d.",
            left,recording?"":" (collecting ready GPU results)",cpu[WORLD].count,cpu[HAND].count,pending(),gpuDropped);
    }

    /** Render-thread reset; pending measurements are discarded rather than blocking a world unload. */
    public static void clear() {
        if (session) last="Lighting profile canceled by renderer reset; no completed report.";
        session=recording=false;
        deleteQueries(); gpuAvailable=false;
        Arrays.fill(PASSES,null);
    }

    private static void deleteQueries() {
        for (int i=0;i<GPU_SLOTS;i++) {
            Query query=QUERIES[i]; if (query==null) continue;
            if (query.begin!=0) GL15.glDeleteQueries(query.begin);
            if (query.end!=0) GL15.glDeleteQueries(query.end);
            QUERIES[i]=null;
        }
    }

    private static final class Pass { int token,kind,query; long started; boolean open; }
    private static final class Query { int begin,end,kind; boolean busy,pending; }

    /** Exact mean/max over all calls; p95 over at most the most recent 65,536 bounded samples. */
    public static final class Stats {
        private final long[] samples=new long[65536];
        private long count,total,max;
        private int next,size;
        public void add(long nanos) {
            if (nanos<0) return;
            count++; total+=nanos; max=Math.max(max,nanos);
            samples[next]=nanos; next=(next+1)&65535; if(size<samples.length)size++;
        }
        public long count() { return count; }
        public int retained() { return size; }
        public double mean() { return count==0?0:(double)total/count; }
        public long max() { return max; }
        public long p95() {
            if(size==0)return 0;
            long[] sorted=Arrays.copyOf(samples,size); Arrays.sort(sorted);
            return sorted[(int)Math.ceil(size*.95)-1];
        }
        public String describe() {
            if(count==0)return "no samples";
            return String.format(Locale.ROOT,"avg %.3f / p95 %.3f / max %.3f ms (%d calls%s)",mean()/1e6,p95()/1e6,max/1e6,count,
                count>size?", p95 last "+size:"");
        }
    }
}
