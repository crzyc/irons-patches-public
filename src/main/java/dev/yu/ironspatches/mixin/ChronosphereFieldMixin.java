package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.IronsPatchesConfig;
import dev.yu.ironspatches.SummonOwners;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Antarchy's Chronosphere enchantment is an Anthology time-dilation field owned by its wearer. The field
 * already leaves its owner alone; this also leaves the owner's own pets and summons alone. Other fields
 * (time dilation spells, other players' Chronospheres) are unchanged.
 * Config: compatibility.chronosphereSparesOwnSummons.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.anthology.time.TimeDilationFieldEntity", remap = false)
public abstract class ChronosphereFieldMixin {

    @Shadow public abstract boolean isChronosphere();
    @Shadow public abstract boolean isOwnedBy(Entity entity);

    @Inject(
            method = "affects",
            at = @At("HEAD"),
            cancellable = true,
            require = 0 // don't crash the game if a future Anthology version changes this method
    )
    private void ironspatches$spareOwnSummons(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || !this.isChronosphere()
                || !IronsPatchesConfig.enabled(IronsPatchesConfig.CHRONOSPHERE_SPARES_OWN_SUMMONS)) return;
        Entity owner = SummonOwners.ownerOf(entity);
        if (owner != null && this.isOwnedBy(owner)) cir.setReturnValue(false);
    }
}
