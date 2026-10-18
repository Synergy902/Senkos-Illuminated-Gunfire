package offline;

import com.senko.gunfirelight.GunfireConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.zip.ZipFile;

public class ConfigCheck {
 public static void main(String[] args)throws Exception {
  var memory=CommentedConfig.inMemory();GunfireConfig.SPEC.correct(memory);GunfireConfig.SPEC.setConfig(memory);
  if(!GunfireConfig.SPEC.isCorrect(memory))throw new AssertionError("default config invalid");
  Set<String> values=new HashSet<>(),screenValues=new HashSet<>();
  for(var field:GunfireConfig.class.getFields())if(ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
   var value=(ForgeConfigSpec.ConfigValue<?>)field.get(null);
   if(!Objects.equals(value.get(),value.getDefault()))throw new AssertionError(field.getName()+" default mismatch");
   values.add(field.getName());
  }
  if(GunfireConfig.MUZZLE_INTENSITY.get()!=8||GunfireConfig.BULLET_INTENSITY.get()!=4||!GunfireConfig.MODEL_LIGHTING.get())throw new AssertionError("stronger defaults");
  if(GunfireConfig.SUPPRESSED_INTENSITY.get()!=.4||GunfireConfig.FLASH_HIDER_INTENSITY.get()!=.8)throw new AssertionError("attachment defaults");
  memory.remove("flashHider");memory.remove("gunpackOverrides");
  memory.set("bullet.intensity",20.);memory.set("rendering.exposure",8.);memory.set("model.intensity",.3);memory.set("muzzle.suppressedIntensity",1.2);
  GunfireConfig.SPEC.correct(memory);GunfireConfig.SPEC.afterReload();
  if(GunfireConfig.BULLET_INTENSITY.get()!=20||GunfireConfig.EXPOSURE.get()!=8||GunfireConfig.MODEL_INTENSITY.get()!=.3||GunfireConfig.SUPPRESSED_INTENSITY.get()!=1.2||GunfireConfig.FLASH_HIDER_INTENSITY.get()!=.8)throw new AssertionError("upgrade overwrote existing values or failed to add new defaults");
  GunfireConfig.FLASH_HIDER_IDS.set(java.util.List.of());
  if(!GunfireConfig.SPEC.isCorrect(memory))throw new AssertionError("empty gunpack overrides rejected");
  memory.set("gunpackOverrides.flashHiderAttachments",java.util.List.of("invalid id"));
  if(GunfireConfig.SPEC.isCorrect(memory))throw new AssertionError("malformed attachment id accepted");
  GunfireConfig.SPEC.correct(memory);GunfireConfig.SPEC.afterReload();
  GunfireConfig.MUZZLE_ENABLED.set(false);GunfireConfig.BULLET_ENABLED.set(false);GunfireConfig.SUPPRESSED_INTENSITY.set(0.);
  if(GunfireConfig.MUZZLE_ENABLED.get()||GunfireConfig.BULLET_ENABLED.get()||GunfireConfig.SUPPRESSED_INTENSITY.get()!=0)throw new AssertionError("toggles did not apply");
  GunfireConfig.BULLET_INTENSITY.set(64.);GunfireConfig.MUZZLE_INTENSITY.set(64.);
  if(!GunfireConfig.SPEC.isCorrect(memory))throw new AssertionError("documented high brightness rejected");
  memory.set("rendering.lightsPerTile",1000);memory.set("bullet.radius",-1.);
  if(GunfireConfig.SPEC.isCorrect(memory))throw new AssertionError("invalid budget/radius accepted");
  GunfireConfig.SPEC.correct(memory);GunfireConfig.SPEC.afterReload();
  if(!GunfireConfig.SPEC.isCorrect(memory)||GunfireConfig.LIGHTS_PER_TILE.get()>32||GunfireConfig.BULLET_RADIUS.get()<.25)throw new AssertionError("range correction");
  try(var z=new ZipFile(args[0])) {
   var n=new ClassNode();new ClassReader(z.getInputStream(z.getEntry("com/senko/gunfirelight/GunfireConfigScreen.class"))).accept(n,0);
   for(var m:n.methods)for(var instruction:m.instructions)if(instruction instanceof FieldInsnNode f&&f.owner.equals("com/senko/gunfirelight/GunfireConfig"))screenValues.add(f.name);
   if(!screenValues.containsAll(values)){values.removeAll(screenValues);throw new AssertionError("settings missing from UI: "+values);}
  }
  try(var staged=CommentedFileConfig.of(new java.io.File(args[1]))) {
   staged.load();
   // Forge also considers comment wording in isCorrect; normalize comments in
   // memory while rejecting any missing/extra keys or value corrections.
   var corrections=new ArrayList<String>();
   GunfireConfig.SPEC.correct(staged,(action,path,oldValue,newValue)->corrections.add(path+": "+oldValue+" -> "+newValue));
   if(!corrections.isEmpty())throw new AssertionError("staged installation settings need correction: "+corrections);
   GunfireConfig.SPEC.setConfig(staged);GunfireConfig.SPEC.afterReload();
   for(var field:GunfireConfig.class.getFields())if(ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
    var value=(ForgeConfigSpec.ConfigValue<?>)field.get(null);
    if(!Objects.equals(value.get(),value.getDefault()))throw new AssertionError("staged default mismatch: "+field.getName());
   }
  }
  System.out.println("PASS "+values.size()+" settings: attachment defaults, existing-user migration, valid/empty ID overrides, independent toggles, brightness range, invalid-value correction, all settings bound in UI; no Minecraft initialization");
 }
}
