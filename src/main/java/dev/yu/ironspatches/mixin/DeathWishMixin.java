package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.IronsPatchesConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Majrusz's Death Wish multiplies damage taken by up to 1.2x with no check on the source, so /kill's
 * Float.MAX_VALUE becomes Infinity (see {@link NonFiniteHealthMixin}). Damage that bypasses invulnerability
 * (/kill and the void) now skips that multiplier; everything else is unchanged.
 * Config: compatibility.deathWishIgnoresKill.
 */
@Pseudo
@Mixin(targets = "com.majruszsenchantments.enchantments.DeathWishEnchantment", remap = false)
public abstract class DeathWishMixin {
    @Unique
    private static Field ironspatches$sourceField;

    @Inject(
            method = "increaseDamageReceived",
            at = @At("HEAD"),
            cancellable = true,
            require = 0 // don't crash the game if a future Majrusz's Enchantments version changes this method
    )
    private void ironspatches$skipKillDamage(@Coerce Object event, CallbackInfo ci) {
        if (!IronsPatchesConfig.enabled(IronsPatchesConfig.DEATH_WISH_IGNORES_KILL)) return;
        DamageSource source = ironspatches$source(event);
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) ci.cancel();
    }

    /** Reads OnEntityPreDamaged's public final "source" field; Majrusz's Library isn't a compile dependency. */
    @Unique
    private static DamageSource ironspatches$source(Object event) {
        try {
            Field field = ironspatches$sourceField;
            if (field == null || field.getDeclaringClass() != event.getClass()) {
                field = event.getClass().getField("source");
                ironspatches$sourceField = field;
            }
            return field.get(event) instanceof DamageSource source ? source : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
