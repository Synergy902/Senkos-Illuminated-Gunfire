"""Install the tested client adapter and its official SBW dependencies with rollback files.

Separate from build.py: invoke explicitly with the profile that should be changed.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import io
import json
import shutil
import tomllib
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--profile', required=True, type=Path)
p.add_argument('--dependencies', required=True, type=Path)
p.add_argument('--jar', required=True, type=Path)
p.add_argument('--check', action='store_true', help='Validate the requested installation without changing the profile.')
a = p.parse_args()
profile = a.profile.resolve(strict=True)
mods = (profile / 'mods').resolve(strict=True)
if mods.parent != profile:
    raise SystemExit('The mods folder must be inside the requested profile.')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def jar_ids(z):
    found = set()
    names = z.namelist()
    if 'META-INF/mods.toml' in names:
        found.update(m['modId'] for m in tomllib.loads(z.read('META-INF/mods.toml').decode()).get('mods', []))
    for name in names:
        if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(z.read(name))) as nested:
                found.update(jar_ids(nested))
    return found

def ids(path):
    with zipfile.ZipFile(path) as z:
        return jar_ids(z)

sources = [a.jar.resolve(strict=True)] + [
    (a.dependencies / name).resolve(strict=True) for name in [
        'superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar',
        'kotlinforforge-4.11.0-all.jar',
        'Meshbench-Armor-Diagnostic-Host-1.20.1-0.1.0.jar',
    ]
]
assert 'senkosgunfirelight' in ids(sources[0])
assert 'superbwarfare' in ids(sources[1])
assert 'taczmeshpilotcompat' in ids(sources[3])
old_lighting = [f for f in mods.glob('*.jar') if 'senkosgunfirelight' in ids(f)]
if len(old_lighting) != 1:
    raise SystemExit('Expected exactly one existing lighting mod; inspect this profile before installation.')
replace_ids = {'superbwarfare', 'taczmeshpilotcompat'}
for f in mods.glob('*.jar'):
    if f in old_lighting:
        continue
    if ids(f) & replace_ids and not any(f.name == source.name and sha(f) == sha(source) for source in sources):
        raise SystemExit('A different dependency is already installed: ' + f.name)
for source in sources:
    dest = mods / source.name
    if dest.exists() and dest not in old_lighting and sha(source) != sha(dest):
        raise SystemExit('Refusing to overwrite an unrecognized dependency: ' + dest.name)

if a.check:
    print(json.dumps({'profile': str(profile), 'replace': [f.name for f in old_lighting],
                      'install': [f.name for f in sources]},indent=2))
    raise SystemExit(0)

backup = profile / ('gunfire-sbw-backup-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir()
(backup/'mods').mkdir()
(backup/'config').mkdir()
(backup/'staged').mkdir()
config = profile/'config/senkos-gunfire-light-client.toml'
original_config = sha(config) if config.exists() else None
if config.exists():
    shutil.copy2(config,backup/'config'/config.name)
manifest = {'profile': str(profile), 'replaced': [], 'installed': [], 'config_sha256': original_config}
for f in old_lighting:
    shutil.copy2(f,backup/'mods'/f.name)
    assert sha(f) == sha(backup/'mods'/f.name)
    manifest['replaced'].append({'name': f.name, 'sha256': sha(f)})
for source in sources:
    if not (mods/source.name).exists() or mods/source.name in old_lighting:
        staged = backup/'staged'/source.name
        shutil.copy2(source,staged)
        assert sha(source) == sha(staged)
owned = []
try:
    for f in old_lighting:
        assert f.resolve().parent == mods
        f.unlink()
    for source in sources:
        dest = mods/source.name
        existed = dest.exists()
        if not existed:
            owned.append(dest)
            (backup/'staged'/source.name).replace(dest)
        assert sha(source) == sha(dest)
        manifest['installed'].append({'name': dest.name, 'sha256': sha(dest), 'already_present': existed})
    assert original_config == (sha(config) if config.exists() else None), 'Existing settings changed'
    assert sum('senkosgunfirelight' in ids(f) for f in mods.glob('*.jar')) == 1
except BaseException:
    try:
        for dest in owned:
            if dest.exists(): dest.unlink()
    finally:
        for item in manifest['replaced']:
            shutil.copy2(backup/'mods'/item['name'],mods/item['name'])
    raise
(backup/'manifest.json').write_text(json.dumps(manifest,indent=2))
(backup/'RESTORE.txt').write_text(
    'Close Minecraft. Remove the four newly installed JARs listed in manifest.json '\
    '(keep any marked already_present). Copy mods/*.jar from this backup back into '\
    'the profile mods folder. The lighting config was not changed. No worlds were modified.\n')
print(json.dumps({'backup': str(backup), **manifest},indent=2))
