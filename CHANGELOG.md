# Changelog

## 0.2.3

### Changes
- **Spirit Banish shared health:** while a player is banished, the body and the spirit share one health pool. Damage or healing on either applies to both, so the player's health always matches the body's, including after a save-and-quit, when the body is rebuilt from the player's health. The body's max health is also set to match the player's. ws_effect_lib counted the player's armor, held items and effects twice on the body, which gave it far more max health. After joining, both the rebuild and the sync wait one second, so armor, held-item and curio bonuses are applied and the player's health has been restored first. Absorption is not shared. Option: `glitch_fixes.spiritBanishSharedHealth`.

## 0.2.2

### Fixes
- **Spirit Banish after a second save-and-quit:** ws_effect_lib keeps its banish sessions in static fields, so in singleplayer they outlive the world. After rejoining, the previous world's session was still there, so 0.2.1's rebuild never ran, and ws_effect_lib's touch check compared the new world's tick count with the old world's start tick. That left touch-to-return off until the new world had run longer than the old one had (the first reload usually worked because the banish had started early). ws_effect_lib's per-world banish state is now cleared when the world closes, so every reload goes through the rebuild. Option: `glitch_fixes.spiritBanishSafeReturn`.

## 0.2.1

### Fixes
- **Spirit Banish after a world reload:** 0.2 only used its stored data once the effect ended, so after save-and-quit mid-banish the body was gone, touching its spot did nothing, and a new banish (for example a recast that started over) replaced the stored body position with the spirit's. Now, if ws_effect_lib has lost the session while the effect is still on, the session and body are rebuilt at the stored spot and game mode, so touching the body, recasting, the effect ending and the body dying all work as before the reload. The stored data is only written when a banish starts from no effect, so it is never overwritten mid-banish. If the old body also loads, only one body is kept. Option: `glitch_fixes.spiritBanishSafeReturn`.

## 0.2

Adds `config/ironspatches-common.toml`, a standard Forge config that Configured can edit from the title screen. Every option defaults to on and is read live.

### Fixes
- **NaN health ("can't die, can't open inventory" after /kill):** infinite damage made vanilla's absorption math store NaN, which then spread to health. Health and absorption are now kept finite. Entities saved with NaN health are healed to full one second after loading. Option: `glitch_fixes.guardNonFiniteHealth`.
- **Spirit Banish (ws_effect_lib):** banish sessions only lived in memory, so a world restart mid-banish left the player in Spectator. Each new banish now stores its body position and game mode on the player, and that data is used only when ws_effect_lib has lost the session (the effect ending, or death followed by respawn). Players already stuck before this version are not changed. Option: `glitch_fixes.spiritBanishSafeReturn`.

### Compatibility
- **Majrusz's Enchantments Death Wish:** the "take up to 1.2x damage at high health" penalty no longer applies to damage that bypasses invulnerability (/kill and the void). That penalty turned /kill's damage into Infinity. Option: `compatibility.deathWishIgnoresKill`.
- **Antarchy Chronosphere:** the wearer's own pets and summons (vanilla tamed mobs and Iron's Spells-style summons) are no longer slowed by their own Chronosphere field. Other time fields are unchanged. Option: `compatibility.chronosphereSparesOwnSummons`.
- **Iron's Spell's Enchantments + Apotheosis:** the 0.1 gem-level patch can now be turned off. Option: `compatibility.iseCountsGemLevels`.

## 0.1 (prerelease)

First release. Forge 47.x, Minecraft 1.20.1. No config files; every patch skips itself if its target mod is missing or has changed.

### Compatibility
- **Iron's Spell's Enchantments + Apotheosis:** enchantment levels added by Apotheosis gems and affixes now count toward Iron's Spell's Enchantments' attribute bonuses. It now reads levels through Forge's `getAllEnchantments()`, which includes Placebo's `GetEnchantmentLevelEvent`, instead of the raw item NBT.

### Performance
- **Iron's Spells Black Hole:** the per-tick fluid scan over the Black Hole's hitbox skips chunk sections that contain no fluid. It scans in the same order as Forge, so results are identical. Measured: about 5.4 ms to 0.02 ms per tick without fluid nearby, and about 12 ms to 2.1 ms with fluid.
- **Arcane Manifestation:** `Compat.owner` (run for every entity every tick by its time-stop check) and `Compat.boss` now look up `getSummonerUUID`/`getOwnerUUID`/`getSummoner`/`getOwner`/`hasBossBar` through a per-class cache instead of calling `Class.getMethod` and building a new `NoSuchMethodException` on every miss. Results are identical. A missed lookup drops from about 365 ns to about 12 ns, and this was the largest mod cost on the server thread in a spark profile.

### Testing
- `gradlew build -Pharness -PplaceboJar=<path>` builds a harness that brute-force compares the patched and vanilla fluid scans and runs a server for 800 ticks.
