package offline;

import com.senko.gunfirelight.source.LightRegistry;
import java.util.*;

public final class RegistryCheck {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) {
        LightRegistry registry = new LightRegistry(4); float[] out = new float[32];
        long a = registry.create(10000000.125, 23.375, -10000000.25, 3, 1,.5f,.2f,2,0,1,true,1);
        check(a != 0, "create");
        check(registry.collect(0,10000000,23,-10000000,256,out,4)==1,"single");
        check(out[0]==.125f && out[1]==.375f && out[2]==-.25f,"sub-block precision at far world coordinate");
        check(out[3]==3 && out[4]==2 && out[5]==1 && out[7]==0,"packed radiance and range");
        registry.collect(.5,10000000,23,-10000000,256,out,4);
        check(out[4]==1,"smooth midpoint decay");
        registry.collect(.75,10000000,23,-10000000,256,out,4); float phase=out[4];
        for(int i=0;i<1000;i++) {registry.collect(.75,10000000,23,-10000000,256,out,4);check(out[4]==phase&&registry.size()==1,"paused phase/storage stability");}
        check(registry.collect(1,10000000,23,-10000000,256,out,4)==0 && registry.size()==0,"expiration exact");
        long b = registry.create(1,2,3,2,1,1,1,1,1,Double.POSITIVE_INFINITY,false,0);
        check(b!=a&&!registry.update(a,0,0,0,2,1,1,1,1)&&!registry.remove(a),"stale generation cannot mutate reused slot");
        check(registry.contains(b),"new handle intact");
        check(registry.create(Double.NaN,0,0,1,1,1,1,1,0,1,false,0)==0,"invalid coordinate rejected");
        check(registry.create(0,0,0,Float.POSITIVE_INFINITY,1,1,1,1,0,1,false,0)==0,"infinite range rejected");
        check(registry.create(0,0,0,1,Float.NaN,1,1,1,0,1,false,0)==0,"nan radiance rejected");
        check(registry.create(0,0,0,1,1,1,1,1,0,Double.NaN,false,0)==0,"nan lifetime rejected");
        registry.clear(); check(registry.size()==0&&!registry.contains(b),"clear removes handles");
        long local=registry.create(0,0,0,10,1,.5f,.2f,64,0,Double.POSITIVE_INFINITY,false,8);
        long other=registry.create(1,0,0,4,1,.5f,.2f,40,0,Double.POSITIVE_INFINITY,false,1);
        check(registry.collect(0,0,0,0,256,out,4)==2&&out[4]==64,"full configuration intensity range survives packing");
        check(registry.collectExcluding(0,0,0,0,256,out,4,local)==1&&out[4]==40,"hand view excludes only exact local muzzle token");
        check(registry.contains(local)&&registry.contains(other)&&registry.size()==2,"alternate view selection preserves world sources");
        registry.clear();
        for(int i=0;i<4;i++)check(registry.create(i,0,0,1,1,1,1,1,0,Double.POSITIVE_INFINITY,false,0)!=0,"capacity accepted");
        check(registry.create(0,0,0,1,1,1,1,1,0,1,false,0)==0&&registry.rejected()==1,"bounded overflow");
        check(registry.collect(0,0,0,0,1,out,4)==3,"finite distance includes source reach");
        registry.clear();
        Random random=new Random(630387L); LightRegistry many=new LightRegistry(8192);
        record Row(float x,float intensity,double score) {}
        List<Row> expected=new ArrayList<>();
        for(int i=0;i<4096;i++) {
            float x=random.nextFloat()*128;float intensity=.1f+random.nextFloat()*3;
            int priority=i%7;float radius=1+random.nextFloat()*5;
            many.create(x,0,0,radius,1,1,1,intensity,0,Double.POSITIVE_INFINITY,false,priority);
            expected.add(new Row(x,intensity,(1.0+priority*4.0)*radius*radius*intensity/(1.0+(double)x*x)));
        }
        expected.sort(Comparator.comparingDouble(Row::score).reversed());
        float[] packed=new float[256*8];
        check(many.collect(0,0,0,0,256,packed,256)==256,"global GPU budget");
        for(int i=0;i<256;i++)check(packed[i*8]==expected.get(i).x&&packed[i*8+4]==expected.get(i).intensity,"correct descending topK at "+i);
        for(int frame=0;frame<1000;frame++)check(many.collect(frame*.01,0,0,0,256,packed,256)==256,"sustained 4096-source budget");
        check(many.size()==4096,"selection does not destroy offscreen/unselected sources");
        many.clear();
        for(int iteration=0;iteration<10000;iteration++) {
            long id=many.create(0,0,0,3,1,.5f,.2f,1,iteration,Double.POSITIVE_INFINITY,false,8);
            check(many.update(id,1.25,2.5,3.75,3,1,.5f,.2f,1),"source movement");
            check(many.remove(id)&&!many.contains(id)&&many.size()==0,"balanced lifecycle");
        }
        System.out.println("PASS registry checks="+checks+"; sub-block positions, finite range, pause, generation, topK order, 4096-bullet pressure, 10000 lifecycle cycles");
    }
}
