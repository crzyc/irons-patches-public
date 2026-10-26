package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.IronsPatchesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Infinite damage (e.g. /kill's Float.MAX_VALUE times a damage multiplier) makes vanilla's absorption math
 * compute Infinity - Infinity = NaN. The absorption setters only clamp values below zero, so the NaN is
 * stored, and the next hit carries it into health. Mth.clamp lets NaN through, and NaN is never <= 0, so
 * the entity can no longer die. This keeps both values finite, and heals entities that were saved at NaN
 * health back to full once their equipment bonuses have been applied.
 * Config: glitch_fixes.guardNonFiniteHealth. See also {@link PlayerAbsorptionMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class NonFiniteHealthMixin {
    /** Ticks to wait after loading before healing, so curio/armor max-health bonuses are counted. */
    @Unique
    private static final int ironspatches$REPAIR_DELAY_TICKS = 20;

    @Unique
    private int ironspatches$healthRepairTicks = -1;

    @Shadow public abstract float getHealth();
    @Shadow public abstract float getMaxHealth();
    @Shadow public abstract void setHealth(float health);

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float ironspatches$rejectNaNHealth(float health) {
        if (!Float.isNaN(health) || !IronsPatchesConfig.enabled(IronsPatchesConfig.GUARD_NON_FINITE_HEALTH)) return health;
        // Keep the current health; +/-Infinity is already clamped to max/0 by vanilla.
        float current = this.getHealth();
        return Float.isFinite(current) ? current : this.getMaxHealth();
    }

    @ModifyVariable(method = "setAbsorptionAmount", at = @At("HEAD"), argsOnly = true)
    private float ironspatches$rejectNonFiniteAbsorption(float amount) {
        if (Float.isFinite(amount) || !IronsPatchesConfig.enabled(IronsPatchesConfig.GUARD_NON_FINITE_HEALTH)) return amount;
        return 0.0F;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void ironspatches$noteNaNHealthOnLoad(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("Health", Tag.TAG_ANY_NUMERIC)
                && Float.isNaN(tag.getFloat("Health"))
                && IronsPatchesConfig.enabled(IronsPatchesConfig.GUARD_NON_FINITE_HEALTH)) {
            this.ironspatches$healthRepairTicks = ironspatches$REPAIR_DELAY_TICKS;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ironspatches$healAfterNaNLoad(CallbackInfo ci) {
        if (this.ironspatches$healthRepairTicks < 0 || ((LivingEntity) (Object) this).level().isClientSide) return;
        if (this.ironspatches$healthRepairTicks-- == 0) {
            this.setHealth(this.getMaxHealth());
        }
    }
}
