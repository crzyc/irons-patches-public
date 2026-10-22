# Changelog

## 0.1 (prerelease)

First release. Forge 47.x, Minecraft 1.20.1. No config files; every patch skips itself if its target mod is missing or has changed.

### Compatibility
- **Iron's Spell's Enchantments + Apotheosis:** enchantment levels added by Apotheosis gems and affixes now count toward Iron's Spell's Enchantments' attribute bonuses. It now reads levels through Forge's `getAllEnchantments()`, which includes Placebo's `GetEnchantmentLevelEvent`, instead of the raw item NBT.

### Performance
- **Iron's Spells Black Hole:** the per-tick fluid scan over the Black Hole's hitbox skips chunk sections that contain no fluid. It scans in the same order as Forge, so results are identical. Measured: about 5.4 ms to 0.02 ms per tick without fluid nearby, and about 12 ms to 2.1 ms with fluid.
- **Arcane Manifestation:** `Compat.owner` (run for every entity every tick by its time-stop check) and `Compat.boss` now look up `getSummonerUUID`/`getOwnerUUID`/`getSummoner`/`getOwner`/`hasBossBar` through a per-class cache instead of calling `Class.getMethod` and building a new `NoSuchMethodException` on every miss. Results are identical. A missed lookup drops from about 365 ns to about 12 ns, and this was the largest mod cost on the server thread in a spark profile.

### Testing
- `gradlew build -Pharness -PplaceboJar=<path>` builds a harness that brute-force compares the patched and vanilla fluid scans and runs a server for 800 ticks.
