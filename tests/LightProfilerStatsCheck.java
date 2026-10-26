package offline;

import com.senko.gunfirelight.render.LightPerformanceProfiler.Stats;

/** Pure statistics checks: no GL context, Forge bootstrap, or Minecraft launch. */
public final class LightProfilerStatsCheck {
    private static int checks;
    public static void main(String[] args) {
        Stats empty=new Stats();
        check(empty.count()==0&&empty.retained()==0&&empty.mean()==0&&empty.max()==0&&empty.p95()==0,"empty capture");
        empty.add(-1);check(empty.count()==0,"invalid elapsed time ignored");
        for(int i=1;i<=100;i++)empty.add(i);
        check(empty.count()==100&&empty.retained()==100,"small capture count");
        check(empty.mean()==50.5&&empty.max()==100&&empty.p95()==95,"exact mean/max and nearest-rank p95");
        Stats bounded=new Stats();
        for(int i=1;i<=70000;i++)bounded.add(i);
        check(bounded.count()==70000&&bounded.retained()==65536,"fixed sample storage after wrap");
        check(bounded.mean()==35000.5&&bounded.max()==70000,"all-call aggregate survives wrap");
        check(bounded.p95()==66724,"p95 uses latest bounded window");
        check(bounded.describe().contains("p95 last 65536"),"report discloses bounded percentile window");
        check(bounded.count()==70000&&bounded.retained()==65536,"sorting report preserves live counters");
        System.out.println("PASS lighting profiler statistics: "+checks+" checks; bounded storage, percentile window and aggregation");
    }
    private static void check(boolean value,String label) { checks++;if(!value)throw new AssertionError(label); }
}
