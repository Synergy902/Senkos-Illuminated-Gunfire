# [Tacz/Sbw] Gunfire light

Client-side finite-range muzzle and bullet lighting for TaCZ and Superb Warfare on Minecraft 1.20.1.
GPU shaders illuminate already rendered surfaces and first-person guns/hands.
No second world render and no Minecraft block-light rebuilds. MeshBench is not included.

## Runtime requirements

- Java 17; Minecraft 1.20.1; Forge 47.4 or newer in the 47.x series.
- TaCZ 1.1.8 through compatible 1.1.x versions.
- Cloth Config 11.x for the in-game settings screen (optional for lighting).
- Optional Superb Warfare **0.8.9.2 Forge**, release commit `65d985a64`, with Kotlin for Forge 4.11.0 or a compatible 4.x release.
- Install on the client; the server does not need this mod.

Superb Warfare itself is a gameplay mod and must also be installed on a multiplayer server.
The lighting adapter remains optional and client-side; TaCZ remains a requirement of this distribution.

Select **[Tacz/Sbw] Gunfire light** in the Mods screen to open its configuration.
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

## Superb Warfare adapter (0.3.0-sbw.2 testing build)

The existing GPU surface shader also lights SBW weapons, hands, vehicles and nearby terrain.
Handheld muzzle positions come from animated SBW model bones and flare offsets, including
attachment-specific positions. Launchers without flare bones use their model's front rim.
Vehicle firing packets identify a weapon and barrel index; the light follows that barrel's
animated visual muzzle bone when the fire animation identifies one, otherwise its native
vehicle/turret transform. This separates visible gun outlets from gameplay projectile origins
inside armor, including the T-90 and ZTZ coaxial machine guns. Multiple barrels retain separate emitters.
Custom firing paths use synchronized firing state, the mortar firing countdown, or native
flare positions. Particle-based fallback paths depend on SBW emitting those native particles.

SBW bullets, hot shells and rockets feed the shared bounded light registry. Ordinary thrown
grenades and mines are not treated as luminous projectiles. Native muzzle/flight block lights
are suppressed while the GPU adapter is enabled to avoid duplicate illumination. SBW explosion,
burning-vehicle and wreck lighting remain native. Disable the addon to restore native firing lights.
Existing muzzle, suppressor, flash-hider, bullet and model settings control both integrations;
no approved defaults or existing profile configuration values are replaced.

Run `python test_sbw.py` with the same JDK/classpath options after building. It applies the
packaged mixins to the actual released SBW bytecode, checks client/server and absent-SBW cases,
and verifies TaCZ references against the shared SimpleBedrockModel library. These checks do
not launch Minecraft or prove visual alignment in a running client.

For an in-game check, use a creative test world at night: fire an SBW rifle beside a wall,
aim and reload, switch views, then fire a tank's main cannon and secondary gun while rotating
the turret. Check a multi-tube launcher and a mortar as well. `/gunfirelight status` reports
adapter activity. Check shader-enabled and ordinary rendering separately. This build needs
that visual test, especially the exceptional launcher model anchors and particle fallbacks.

`tools/install_sbw_profile.py` is a separate, explicit installation step. It backs up the old
lighting bundle, preserves its MeshBench component separately, installs the new adapter plus
SBW/Kotlin dependencies, and verifies that the existing lighting settings remain byte-identical.
It does not edit worlds or start Minecraft.

## Measuring lighting cost

`/gunfirelight profile` records 30 seconds of lighting source-collection CPU time,
world-pass CPU/GPU time and hand-pass CPU/GPU time. Fire weapons during that period.
The report appears in chat and the game log. Times are per measured invocation, including
culled calls, rather than FPS or whole-frame time; shader depth captures and final applies
are separate invocations. Event handlers and model-bone capture are outside these timers, so
this is not the total CPU cost of the mod. GPU timestamps are collected only when available,
without waiting for the GPU. This instrumentation is off by default.

`test_gpu.py --jar <built-jar> --baseline <previous-jar>` runs hidden OpenGL fixtures for
pixel equivalence, state restoration and synthetic GPU pass timings. It never starts Minecraft.
The 0.3.0-sbw.2 optimization reduces color-copy bandwidth only when lights affect less than
half the screen. Full depth, shadows, brightness, range, tile uploads and broad-coverage passes
are preserved. Synthetic pass improvements should not be presented as in-game FPS gains.

## Rendering limits

Lighting uses visible color and depth, not true material albedo or global illumination.
Contact shadows cannot fully reconstruct hidden or off-screen geometry.
GPU passes consume frame time; performance varies with resolution and scene.

## License

All rights reserved. See LICENSE. Third-party dependencies keep their own licenses.
