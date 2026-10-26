package offline;

import com.atsuishio.superbwarfare.data.gun.ShootPos;
import com.senko.gunfirelight.compat.SbwVehicleLights;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Multi-barrel selection regression: packet index must never become a seat or next tube. */
public final class SbwVehicleCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Method select = SbwVehicleLights.class.getDeclaredMethod("barrelPosition", ShootPos.class, int.class);
        select.setAccessible(true);
        ShootPos mount = new ShootPos();
        Vec3 left = new Vec3(-2, .25, 5), middle = new Vec3(0, .25, 5), right = new Vec3(2, .25, 5);
        mount.setPositions(new ArrayList<>(List.of(left, middle, right)));
        check(select.invoke(null, mount, 0)==left, "first barrel");
        check(select.invoke(null, mount, 2)==right, "third barrel even with one passenger seat");
        check(select.invoke(null, mount, 1)==middle, "middle barrel remains independent");
        mount.setBoundUpWithAmmoAmount(true);
        check(select.invoke(null, mount, 2)==right, "ammo-bound tube uses transmitted index");
        check(select.invoke(null, mount, -1)==null, "negative packet index rejected");
        check(select.invoke(null, mount, 3)==null, "out-of-range packet index rejected, not wrapped");
        mount.setPositions(new ArrayList<>());
        check(select.invoke(null, mount, 0)==null, "missing muzzle never lights vehicle center");
        mount.setPositions(new ArrayList<>(List.of(new Vec3(Double.NaN, 0, 0))));
        check(select.invoke(null, mount, 0)==null, "nonfinite geometry rejected");
        check(select.invoke(null, null, 0)==null, "missing mount rejected");

        // A real barrel point keeps its relative separation after translated and
        // rotated vehicle transforms, including at large world coordinates.
        Matrix4d pose = new Matrix4d().translate(100_000, 70, -200_000).rotateY(Math.PI/2);
        Vector4d a = pose.transform(new Vector4d(left.f_82479_,left.f_82480_,left.f_82481_,1));
        Vector4d b = pose.transform(new Vector4d(right.f_82479_,right.f_82480_,right.f_82481_,1));
        check(Math.abs(a.x-100_005)<1e-8 && Math.abs(a.y-70.25)<1e-8 && Math.abs(a.z+199_998)<1e-8,
            "barrel transform expected world coordinates");
        check(Math.abs(a.distance(b)-4)<1e-8, "separate emitters do not collapse during rotation");
        System.out.println("PASS SBW multi-barrel geometry: assertions="+assertions);
    }
    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
}
