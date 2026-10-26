"""Hidden-context GPU regression checks and optional before/after pass timings.

This runs OpenGL fixtures, never Minecraft. Timing results are synthetic GPU pass
costs, not whole-game FPS estimates. Nothing is installed into a modpack.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--jar', required=True, type=Path)
p.add_argument('--baseline', type=Path)
p.add_argument('--classpath', type=Path, default=ROOT/'classpath.local.txt')
p.add_argument('--java-home', type=Path, default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
a = p.parse_args()

def tool(name):
    value = a.java_home/'bin'/(name+'.exe' if os.name == 'nt' else name) if a.java_home else shutil.which(name)
    if not value:
        p.error('Provide a Java 17 JDK using --java-home or JAVA_HOME.')
    return str(value)

jar = a.jar.resolve(strict=True)
out = ROOT/'build/gpu-validation'
classes = out/'classes'
classes.mkdir(parents=True,exist_ok=True)
cp = os.pathsep.join([str(classes),str(jar),a.classpath.read_text().strip()])
arguments = ['--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,(ROOT/'tests/gpu').glob('*.java'))]
argfile = out/'javac.args'
argfile.write_text('\n'.join('"'+arg.replace('\\','/')+'"' for arg in arguments))
subprocess.run([tool('javac'),'@'+str(argfile)],check=True)
reports = []
for major, minor in [(3,2),(4,5)]:
    target = out/f'gl{major}{minor}'
    target.mkdir(exist_ok=True)
    result = subprocess.run([tool('java'),'-Xverify:all',f'-Dtest.major={major}',f'-Dtest.minor={minor}',
        '-Doutput='+str(target),'-cp',cp,'offline.GunfireGpuCheck'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (out/f'gl{major}{minor}.log').write_text(result.stdout)
    print(result.stdout)
    result.check_returncode()
    reports.append(json.loads((target/'gpu-report.json').read_text()))
report = {'status':'PASS','candidate_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
          'contexts':reports,'game_launched':False,
          'limitation':'Synthetic hidden OpenGL timings; not Minecraft FPS or a game CPU/GPU utilization measurement.'}
profiler = subprocess.run([tool('java'),'-Xverify:all','-cp',cp,'offline.ProfilerGpuCheck'],
    text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(out/'profiler.log').write_text(profiler.stdout)
print(profiler.stdout)
profiler.check_returncode()
report['profiler'] = 'PASS: inactive/context, bounded nesting/overflow, async result collection, GL state and resource cleanup'
if a.baseline:
    baseline = a.baseline.resolve(strict=True)
    result = subprocess.run([tool('java'),'-Xverify:all','-cp',cp,'offline.GpuPassComparison',
        str(out),str(baseline),str(jar)],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (out/'comparison.log').write_text(result.stdout)
    print(result.stdout)
    result.check_returncode()
    report['baseline_sha256'] = hashlib.sha256(baseline.read_bytes()).hexdigest()
    report['comparison'] = json.loads((out/'gpu-comparison.json').read_text())
(out/'results.json').write_text(json.dumps(report,indent=2))
