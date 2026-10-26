# Changelog

## 0.3.0-sbw.2
- Added support for SBW vehicles and weapons.
- Optimized GPU usage further.
- Reduced CPU overhead when tracking gunfire lights.
- Fixed muzzle-light positioning on the ZTZ and T-90.
- Fixed bullet lights appearing inside vehicle armor.
- Added /gunfirelight profile for CPU/GPU lighting measurements.
- Renamed the mod to [Tacz/Sbw] Gunfire light.
- Preserved existing lighting settings.

## 0.3.0-sbw.1 (testing build)
- Optional Superb Warfare 0.8.9.2 Forge integration for animated handheld muzzles and vehicle firing points.
- Vehicle lights follow the named weapon and fired barrel, including interpolated turret transforms.
- SBW projectile lighting and first-person model illumination use the existing GPU pipeline and settings.
- Suppress duplicate SBW muzzle/trail block lights while preserving native explosion and wreck effects.
- Add released-bytecode Mixin validation, optional-mod/server checks, and shared-library compatibility checks.
- Preserve 0.2.7 settings; explicit profile installation provides a rollback backup.

## 0.2.7
- Default first-person gun/hand brightness 0.1; overall exposure 3.0.
- Stable 0.2.5 rendering behavior preserved.
- Standalone distribution renamed Senkos Illuminated Gunfire; internal IDs unchanged.
- No benchmark component included.

## 0.2.6 (rejected experiment)
- Tile-membership upload caching tested, then rolled back following worse gameplay results.
- Not present in the current code.

## 0.2.5
- Overall brightness default 3.0.

## 0.2.4
- Suppressor/flash-hider muzzle profiles and configurable gunpack overrides.
- Bullet brightness remains independent of muzzle attachment type.

## 0.2.3
- Correct world-view matrix capture and shader rendering order for bullet lights.
