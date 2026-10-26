from pathlib import Path
import argparse, os, shutil, subprocess, hashlib, json, zipfile
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--classpath',type=Path,default=root/'classpath.local.txt')
p.add_argument('--java-home',type=Path,default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
a=p.parse_args()
def tool(name):
    result=a.java_home/'bin'/(name+'.exe' if os.name=='nt' else name) if a.java_home else shutil.which(name)
    if not result: p.error('Set JAVA_HOME or --java-home to a Java 17 JDK.')
    return str(result)
jar=root/'build/libs/Tacz-Sbw-Gunfire-Light-1.20.1-0.3.0-sbw.2.jar'
record=json.loads((root/'build/build-record.json').read_text())
if hashlib.sha256(jar.read_bytes()).hexdigest()!=record['sha256']:
    raise SystemExit('Packaged jar differs from build record; rebuild first.')
current={str(f.relative_to(root)): hashlib.sha256(f.read_bytes()).hexdigest() for f in (root/'src/main/java').rglob('*.java')}
if current!=record['source_sha256']:
    raise SystemExit('Java sources changed since the successful build; rebuild before testing.')
with zipfile.ZipFile(jar) as packaged:
    for resource in (root/'src/main/resources').rglob('*'):
        if resource.is_file() and packaged.read(resource.relative_to(root/'src/main/resources').as_posix())!=resource.read_bytes():
            raise SystemExit('Packaged resources are stale; rebuild before testing.')
classes=root/'build/test-classes';classes.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(jar),str(classes),a.classpath.read_text().strip()])
subprocess.run([tool('javac'),'-proc:none','-cp',cp,'-d',str(classes),*map(str,(root/'tests').glob('*.java'))],check=True)
for cls,args in [('ConfigCheck',[str(jar),str(root/'config/senkos-gunfire-light-client.toml')]),('JarLinkageCheck',[str(jar)]),('RegistryCheck',[]),('SbwVehicleCheck',[]),('LightProfilerStatsCheck',[]),('SbwMuzzleAnchorCheck',[])]:
    package='com.senko.gunfirelight.compat.' if cls=='SbwMuzzleAnchorCheck' else 'offline.'
    subprocess.run([tool('java'),'-Xverify:all','-cp',cp,package+cls,*args],check=True)
