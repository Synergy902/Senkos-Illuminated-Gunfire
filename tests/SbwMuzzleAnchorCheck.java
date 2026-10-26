package com.senko.gunfirelight.compat;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Checks authored vehicle flare bindings without starting Minecraft. */
public final class SbwMuzzleAnchorCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(SbwMuzzleAnchors.animationKey("MachineGun",0).equals("animation.machine_gun.fire"),"coax name");
        check(SbwMuzzleAnchors.animationKey("AAMissile",2).equals("animation.a_a_missile.fire.2"),"native acronym and tube index");
        var synthetic=SbwMuzzleAnchors.parse(JsonParser.parseString("""
            {"animations":{
              "animation.main.fire":{"bones":{"barrel":{},"flare":{}}},
              "animation.main.fire.1":{"bones":{"flare_right":{}}},
              "animation.main.fire.2":{"bones":{"flare_left":{},"flare_right":{}}},
              "animation.other.idle":{"bones":{"flare":{}}}
            }}
            """).getAsJsonObject());
        check("flare".equals(synthetic.get("animation.main.fire")),"default tube");
        check("flare_right".equals(synthetic.get("animation.main.fire.1")),"indexed addon tube");
        check(!synthetic.containsKey("animation.main.fire.2"),"ambiguous addon keeps native point");
        check(!synthetic.containsKey("animation.other.idle"),"idle pose is not a muzzle");
        for(String vehicle:new String[]{"t_90a","ztz_99a","m_1a_2"}) {
            String path="/assets/superbwarfare/animations/bedrock/vehicle/"+vehicle+".animation.json";
            try(var stream=SbwMuzzleAnchorCheck.class.getResourceAsStream(path)) {
                if(stream==null)throw new AssertionError("Missing released SBW resource: "+path);
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8);
                var map=SbwMuzzleAnchors.parse(JsonParser.parseReader(reader).getAsJsonObject());
                check("flare2".equals(map.get("animation.machine_gun.fire")),vehicle+" authored coax outlet");
                check(!map.containsKey("animation.main.fire"),vehicle+" cannon native point retained");
            }
        }
        System.out.println("SBW muzzle anchors: "+checks+" checks passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
