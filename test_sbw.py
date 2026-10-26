"""Validate release SBW integration bytecode with client, server, and missing-SBW cases."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--classpath', type=Path, default=ROOT / 'classpath.local.txt')
p.add_argument('--jar', type=Path)
p.add_argument('--sbw-jar', type=Path)
p.add_argument('--java-home', type=Path,
               default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
a = p.parse_args()

def tool(name):
    value = a.java_home / 'bin' / (name + '.exe' if os.name == 'nt' else name) if a.java_home else shutil.which(name)
    if not value:
        p.error('Set JAVA_HOME or --java-home to Java 17.')
    return str(value)

dependencies = [Path(v) for v in a.classpath.read_text().strip().split(os.pathsep)]
jar = a.jar or max((ROOT / 'build/libs').glob('Tacz-Sbw-Gunfire-Light-*.jar'), key=lambda f: f.stat().st_mtime)
sbw = a.sbw_jar or next((v for v in dependencies if v.name.startswith('superbwarfare-0.8.9.2-')), None)
if sbw is None or not sbw.is_file():
    p.error('Provide the released Superb Warfare 0.8.9.2 jar in the classpath or with --sbw-jar.')
out = ROOT / 'build/sbw-validation'
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(jar) as z:
    configs = []
    for filename in z.namelist():
        if filename.endswith('.mixins.json'):
            cfg = json.loads(z.read(filename))
            selected = [name for name in cfg.get('client', []) if name.split('.')[-1].startswith('Sbw')]
            if selected:
                configs.append((cfg, selected))
    if len(configs) != 1:
        raise AssertionError('Expected one packaged config containing Sbw* mixins; found ' + str(len(configs)))
    config, selected = configs[0]
    config['client'] = selected
    config.pop('mixins', None)
    config.pop('server', None)
(classes / 'offline-sbw.mixins.json').write_text(json.dumps(config, indent=2))
services = classes / 'META-INF/services'
services.mkdir(parents=True, exist_ok=True)
(services / 'org.spongepowered.asm.service.IMixinService').write_text('offline.OfflineMixinService\n')
(services / 'org.spongepowered.asm.service.IGlobalPropertyService').write_text('offline.OfflineProperties\n')
classpath = [classes, jar, sbw, *[d for d in dependencies if d != sbw]]
cp = os.pathsep.join(map(str, classpath))
compile_args = ['-proc:none', '--release', '17', '-classpath', cp, '-d', str(classes),
                *map(str, (ROOT / 'tests/sbw_mixin').glob('*.java'))]
argfile = out / 'javac.args'
argfile.write_text('\n'.join('"' + x.replace('\\', '/') + '"' for x in compile_args))
subprocess.run([tool('javac'), '@' + str(argfile)], check=True)
cases = []
for case in ['client', 'server', 'absent']:
    case_cp = classpath if case != 'absent' else [v for v in classpath if v != sbw]
    cmd = [tool('java'), '-Xverify:all', '-Doffline.targetJar=' + str(sbw),
           '-Doffline.server=' + str(case == 'server').lower(),
           '-Doffline.absent=' + str(case == 'absent').lower(),
           '-Doffline.output=' + str(out / case), '-cp', os.pathsep.join(map(str, case_cp)),
           'offline.SbwMixinCheck']
    result = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / (case + '.log')).write_text(result.stdout)
    print(result.stdout)
    cases.append({'case': case, 'exit_code': result.returncode})
report = {'addon_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
          'sbw_sha256': hashlib.sha256(sbw.read_bytes()).hexdigest(),
          'mixins': selected, 'cases': cases, 'game_launched': False}
tacz = next((v for v in dependencies if v.name == 'TaCZ Mesh Fix.jar'), None)
if tacz:
    result = subprocess.run([tool('java'), '-Xverify:all', '-cp', cp, 'offline.SbwDependencyCheck', str(tacz)],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / 'shared-library.log').write_text(result.stdout)
    print(result.stdout)
    cases.append({'case': 'shared-library', 'exit_code': result.returncode})
(out / 'results.json').write_text(json.dumps(report, indent=2))
if any(case['exit_code'] for case in cases):
    raise SystemExit('SBW validation failed: inspect build/sbw-validation logs.')
