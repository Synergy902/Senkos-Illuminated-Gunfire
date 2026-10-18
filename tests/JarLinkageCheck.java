package offline;

import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public final class JarLinkageCheck {
    public static void main(String[] args) throws Exception {
        int classes=0,methods=0;
        try(var zip=new ZipFile(args[0])) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();
                if(!e.getName().endsWith(".class"))continue;
                byte[] bytes;try(var in=zip.getInputStream(e)){bytes=in.readAllBytes();}
                var node=new ClassNode();new ClassReader(bytes).accept(node,0);
                for(var method:node.methods)if(method.instructions.size()>0){new Analyzer<>(new BasicVerifier()).analyze(node.name,method);methods++;}
                Class<?> loaded=Class.forName(node.name.replace('/','.'),false,JarLinkageCheck.class.getClassLoader());
                loaded.getDeclaredMethods();loaded.getDeclaredFields();loaded.getDeclaredConstructors();classes++;
            }
        }
        System.out.println("PASS own packaged jar full JVM linkage without initialization: classes="+classes+", methods="+methods);
    }
}
