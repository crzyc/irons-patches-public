package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.SectionFilteredFluidScan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Tags Iron's Spells' Black Hole so {@link EntityFluidScanMixin} uses the section-filtered scan for it. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole", remap = false)
public abstract class BlackHoleMixin implements SectionFilteredFluidScan {
}
