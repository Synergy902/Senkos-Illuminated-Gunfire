from pathlib import Path
import argparse, os, shutil, subprocess
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--classpath',type=Path,default=root/'classpath.local.txt')
p.add_argument('--java-home',type=Path,default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
a=p.parse_args()
def tool(name):
    result=a.java_home/'bin'/(name+'.exe' if os.name=='nt' else name) if a.java_home else shutil.which(name)
    if not result: p.error('Set JAVA_HOME or --java-home to a Java 17 JDK.')
    return str(result)
jar=root/'build/libs/Senkos Illuminated Gunfire-1.20.1-0.2.7.jar'
classes=root/'build/test-classes';classes.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(jar),str(classes),a.classpath.read_text().strip()])
subprocess.run([tool('javac'),'-proc:none','-cp',cp,'-d',str(classes),*map(str,(root/'tests').glob('*.java'))],check=True)
for cls,args in [('ConfigCheck',[str(jar),str(root/'config/senkos-gunfire-light-client.toml')]),('JarLinkageCheck',[str(jar)]),('RegistryCheck',[])]:
    subprocess.run([tool('java'),'-Xverify:all','-cp',cp,'offline.'+cls,*args],check=True)
