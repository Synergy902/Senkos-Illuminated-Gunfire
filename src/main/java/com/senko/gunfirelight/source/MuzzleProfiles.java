package com.senko.gunfirelight.source;

import java.util.List;
import java.util.Set;
import java.util.Locale;

/** Conservative gunpack classification, evaluated on shots rather than per light/pixel. */
public final class MuzzleProfiles {
    private static Set<String> flashIds=Set.of(), integralIds=Set.of();
    private static List<? extends String> lastFlash=List.of(),lastIntegral=List.of();
    private MuzzleProfiles(){}
    public static void configure(List<? extends String> flashes,List<? extends String> integrals){
        if(!lastFlash.equals(flashes)){lastFlash=List.copyOf(flashes);flashIds=Set.copyOf(flashes);}
        if(!lastIntegral.equals(integrals)){lastIntegral=List.copyOf(integrals);integralIds=Set.copyOf(integrals);}
    }
    public static boolean integral(String gun){return gun!=null&&integralIds.contains(gun);}
    public static boolean flashHider(String attachment){
        if(attachment==null)return false;
        if(flashIds.contains(attachment))return true;
        String path=attachment.substring(attachment.indexOf(':')+1).toLowerCase(Locale.ROOT).replace('-','_');
        // "hider" alone is not reliable: hare:muzzle_hider_dragon is a brake.
        return path.contains("flash_hider")||path.contains("flashhider")
            ||path.contains("flash_suppressor")||path.contains("flashsuppressor");
    }
}
