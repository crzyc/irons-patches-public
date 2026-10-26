package dev.yu.ironspatches;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config (config/ironspatches-common.toml). It's a plain Forge config spec, so Configured can
 * edit it from the title screen. Every option is read live, so changes apply without a restart.
 */
public final class IronsPatchesConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue GUARD_NON_FINITE_HEALTH;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_BANISH_SAFE_RETURN;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_BANISH_SHARED_HEALTH;
    public static final ForgeConfigSpec.BooleanValue DEATH_WISH_IGNORES_KILL;
    public static final ForgeConfigSpec.BooleanValue CHRONOSPHERE_SPARES_OWN_SUMMONS;
    public static final ForgeConfigSpec.BooleanValue ISE_COUNTS_GEM_LEVELS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("glitch_fixes");
        GUARD_NON_FINITE_HEALTH = b
                .comment("Never store NaN or infinite health/absorption. Infinite damage (e.g. /kill boosted by a",
                        "damage multiplier) otherwise leaves a player at NaN health: unable to die or open menus.",
                        "Entities that load with NaN health are healed to full one second after loading.")
                .translation("config.ironspatches.guardNonFiniteHealth")
                .define("guardNonFiniteHealth", true);
        SPIRIT_BANISH_SAFE_RETURN = b
                .comment("Spirit Banish (ws_effect_lib) forgets who is banished when the world closes, leaving them",
                        "in Spectator with a body that does nothing. Remember each new banish's body position and game",
                        "mode, and rebuild the banish on rejoin so touching the body, recasting and the effect ending",
                        "all return the player.")
                .translation("config.ironspatches.spiritBanishSafeReturn")
                .define("spiritBanishSafeReturn", true);
        SPIRIT_BANISH_SHARED_HEALTH = b
                .comment("While a player is banished, the body and the spirit properly share a synced health pool.",
					"This should be default behavior, but there were a few edge cases that were fixed."
				)
                .translation("config.ironspatches.spiritBanishSharedHealth")
                .define("spiritBanishSharedHealth", true);
        b.pop();

        b.push("compatibility");
        DEATH_WISH_IGNORES_KILL = b
                .comment("Majrusz's Death Wish doesn't increase /kill or void damage, so /kill works normally.")
                .translation("config.ironspatches.deathWishIgnoresKill")
                .define("deathWishIgnoresKill", true);
        CHRONOSPHERE_SPARES_OWN_SUMMONS = b
                .comment("Antarchy's Chronosphere doesn't slow its wearer's own summons and pets.")
                .translation("config.ironspatches.chronosphereSparesOwnSummons")
                .define("chronosphereSparesOwnSummons", true);
        ISE_COUNTS_GEM_LEVELS = b
                .comment("Iron's Spell's Enchantments counts enchantment levels added by Apotheosis gems and affixes.",
                        "Takes effect the next time an item's attributes are recalculated (e.g. re-equipping it).")
                .translation("config.ironspatches.iseCountsGemLevels")
                .define("iseCountsGemLevels", true);
        b.pop();

        SPEC = b.build();
    }

    private IronsPatchesConfig() {}

    /** Reads an option, falling back to its default if anything asks before the config has loaded. */
    public static boolean enabled(ForgeConfigSpec.BooleanValue option) {
        try {
            return option.get();
        } catch (IllegalStateException notLoadedYet) {
            return option.getDefault();
        }
    }
}
