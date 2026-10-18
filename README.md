# Senkos Illuminated Gunfire

Client-side finite-range muzzle and bullet lighting for TaCZ on Minecraft 1.20.1.
GPU shaders illuminate already rendered surfaces and first-person guns/hands.
No second world render and no Minecraft block-light rebuilds. MeshBench is not included.

## Runtime requirements

- Java 17; Minecraft 1.20.1; Forge 47.4 or newer in the 47.x series.
- TaCZ 1.1.8 through compatible 1.1.x versions.
- Cloth Config 11.x for the in-game settings screen (optional for lighting).
- Install on the client; the server does not need this mod.

Select **Senkos Illuminated Gunfire** in the Mods screen to open its configuration.
Internal mod ID remains `senkosgunfirelight`; existing settings remain compatible.

## Source layout

- `src/main/java/`: event handling, bullet tracking, settings and rendering integration.
- `src/main/resources/`: GPU shaders, mixin declarations and Forge metadata.
- `config/`: approved default settings, including exposure 3.0 and model intensity 0.1.
- `build.py`: Java 17 compilation and deterministic jar packaging.
- `dependencies.lock.json`: exact dependency filenames and hashes used by the original build.

## Building

Install a Java 17 JDK and Python 3.11 or newer. No Python packages are required.

This source snapshot uses Forge's **SRG names** (`m_...`, `f_...`). It must compile
against the Forge **1.20.1-47.4.10 SRG development jar**, its runtime libraries,
TaCZ, Cloth Config, and the integration API dependencies recorded in the lock file.
An ordinary Minecraft installation or an official-name Forge development jar is
not a substitute. Dependencies are not redistributed in this repository.

Create an ignored `classpath.local.txt` containing the resolved dependency paths
separated by your operating system's Java classpath separator (`;` on Windows,
`:` on Linux/macOS). Set `JAVA_HOME` to your Java 17 JDK, then run:

```sh
python build.py
```

Or supply the development classpath and JDK explicitly:

```sh
python build.py --classpath /path/to/classpath.txt --java-home /path/to/jdk-17
```

The jar and source-hash build record are written to `build/`. This build does not
launch Minecraft. It does not bootstrap/download Forge dependencies; a compatible
SRG development environment is required. The lock file documents the original
build inputs, including optional integration libraries on that classpath.

## Offline checks

After building, run `python test.py` with the same optional `--classpath` and
`--java-home` arguments. Checks cover defaults, saved settings preservation,
source registry behavior and compiled class linkage without launching Minecraft.

## Editing and releases

Edit Java or shader sources, rebuild, then test in a separate client profile.
Keep prior jars for rollback. Store distributable jars as private GitHub Release
assets rather than committing generated classes or third-party binaries.
The initial release preserves the tested 0.2.7 behavior and defaults.

## Rendering limits

Lighting uses visible color and depth, not true material albedo or global illumination.
Contact shadows cannot fully reconstruct hidden or off-screen geometry.
GPU passes consume frame time; performance varies with resolution and scene.

## License

All rights reserved. See LICENSE. Third-party dependencies keep their own licenses.
