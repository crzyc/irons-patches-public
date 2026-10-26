package dev.yu.ironspatches;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/** Most functionality lives in mixins (see ironspatches.mixins.json). */
@Mod(IronsPatches.MOD_ID)
public final class IronsPatches {
    public static final String MOD_ID = "ironspatches";

    public IronsPatches() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, IronsPatchesConfig.SPEC);
    }
}
