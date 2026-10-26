"""Reproducible direct Java 17 build against the installed Forge SRG API."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile, argparse, os

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description="Build with Java 17 against a Forge 1.20.1 SRG classpath.")
parser.add_argument('--classpath', type=Path, default=ROOT/'classpath.local.txt')
parser.add_argument('--java-home', type=Path, default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
options = parser.parse_args()
compiler = options.java_home/'bin'/('javac.exe' if os.name=='nt' else 'javac') if options.java_home else shutil.which('javac')
if not compiler: parser.error('Set JAVA_HOME to a Java 17 JDK or pass --java-home.')
if not options.classpath.is_file(): parser.error('Provide --classpath FILE; see README.md for the required SRG development classpath.')
CLASSES = ROOT / 'build/classes'
if CLASSES.exists():
    assert CLASSES.resolve().is_relative_to(ROOT.resolve())
    shutil.rmtree(CLASSES)
CLASSES.mkdir(parents=True)
sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
cp = options.classpath.read_text().strip()
args = ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-classpath', cp,
        '-d', str(CLASSES), *map(str, sources)]
argfile = ROOT / 'build/javac.args'
argfile.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args), encoding='utf-8')
subprocess.run([str(compiler), '@' + str(argfile)], check=True)
manifest = ('Manifest-Version: 1.0\r\n'
            'MixinConfigs: senkosgunfirelight.mixins.json\r\n'
            'Implementation-Title: [Tacz/Sbw] Gunfire light\r\n'
            'Implementation-Version: 0.3.0-sbw.2\r\n\r\n')
out = ROOT / 'build/libs/Tacz-Sbw-Gunfire-Light-1.20.1-0.3.0-sbw.2.jar'
out.parent.mkdir(parents=True, exist_ok=True)
entries = {'META-INF/MANIFEST.MF': manifest.encode()}
for base in [CLASSES, ROOT / 'src/main/resources']:
    for f in sorted(base.rglob('*')):
        if f.is_file():
            name = f.relative_to(base).as_posix()
            if name in entries: raise RuntimeError('duplicate: ' + name)
            entries[name] = f.read_bytes()
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as jar:
    for name, data in sorted(entries.items()):
        info = zipfile.ZipInfo(name, (2026, 10, 12, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        jar.writestr(info, data)
record = {'jar': str(out), 'sha256': hashlib.sha256(out.read_bytes()).hexdigest(),
          'sources': len(sources), 'classes': len(list(CLASSES.rglob('*.class'))),
          'source_sha256': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sources}}
(ROOT / 'build/build-record.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
print(json.dumps(record, indent=2))
