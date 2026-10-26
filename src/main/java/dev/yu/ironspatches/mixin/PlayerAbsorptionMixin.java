package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.IronsPatchesConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Player overrides setAbsorptionAmount (synced data) without calling super; same guard as {@link NonFiniteHealthMixin}. */
@Mixin(Player.class)
public abstract class PlayerAbsorptionMixin {

    @ModifyVariable(method = "setAbsorptionAmount", at = @At("HEAD"), argsOnly = true)
    private float ironspatches$rejectNonFiniteAbsorption(float amount) {
        if (Float.isFinite(amount) || !IronsPatchesConfig.enabled(IronsPatchesConfig.GUARD_NON_FINITE_HEALTH)) return amount;
        return 0.0F;
    }
}
