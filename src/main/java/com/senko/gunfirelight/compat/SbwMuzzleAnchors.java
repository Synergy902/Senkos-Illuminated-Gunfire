package com.senko.gunfirelight.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource;
import com.atsuishio.superbwarfare.tools.FormatTool;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/** Resolve the visual muzzle using the same authored fire animation as SBW. */
public final class SbwMuzzleAnchors {
    private static final Map<ResourceLocation, Map<String,String>> CACHE = new HashMap<>();
    private SbwMuzzleAnchors() {}

    public static String bone(VehicleEntity vehicle,String weapon,int barrel) {
        ResourceLocation animation=VehicleResource.compute(vehicle).getAnimation();
        if(animation==null)return null;
        Map<String,String> mapping=CACHE.get(animation);
        if(mapping==null){
            if(CACHE.size()>=512)return null;
            mapping=load(animation);CACHE.put(animation,mapping);
        }
        return mapping.get(animationKey(weapon,barrel));
    }
    private static Map<String,String> load(ResourceLocation location){
        try{
            var resource=Minecraft.m_91087_().m_91098_().m_213713_(location);
            if(resource.isPresent())try(var reader=resource.get().m_215508_()){
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }catch(java.io.IOException|RuntimeException ignored){
            // Packs without ordinary animation JSON retain their native firing point.
        }
        return Map.of();
    }
    static String animationKey(String weapon,int barrel){
        String snake=FormatTool.INSTANCE.camelToSnake(weapon);
        return "animation."+snake+".fire"+(barrel==0?"":"."+barrel);
    }
    static Map<String,String> parse(JsonObject root){
        Map<String,String> result=new HashMap<>();
        if(!root.has("animations")||!root.get("animations").isJsonObject())return result;
        for(var entry:root.getAsJsonObject("animations").entrySet()){
            if(!entry.getKey().matches("animation\\..+\\.fire(?:\\.\\d+)?")||!entry.getValue().isJsonObject())continue;
            JsonObject animation=entry.getValue().getAsJsonObject();
            JsonElement bones=animation.get("bones");
            if(bones==null||!bones.isJsonObject())continue;
            String selected=null;boolean ambiguous=false;
            for(String name:bones.getAsJsonObject().keySet())if(name.startsWith("flare")){
                if(selected!=null){ambiguous=true;break;}
                selected=name;
            }
            // Never select another barrel when an addon has an ambiguous animation.
            if(selected!=null&&!ambiguous)result.put(entry.getKey(),selected);
        }
        return result;
    }
    public static void clear(){CACHE.clear();}
}
