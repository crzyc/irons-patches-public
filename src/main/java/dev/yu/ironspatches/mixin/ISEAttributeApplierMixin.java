package dev.yu.ironspatches.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Iron's Spell's Enchantments reads enchantment levels straight from the item's NBT, so levels added
 * by Apotheosis gems/affixes never reach its attribute modifiers. Apotheosis adds those levels through
 * Placebo's GetEnchantmentLevelEvent, which Placebo fires from Forge's IForgeItemStack#getAllEnchantments().
 * Switching to that call is the only change; everything else in the applier is left untouched.
 * (Without Placebo installed, getAllEnchantments() returns the same map as before.)
 */
@Pseudo
@Mixin(targets = "com.inolia_zaicek.irons_spells_enchantments.event.ISEAttributeApplier", remap = false)
public abstract class ISEAttributeApplierMixin {

    @Redirect(
            method = "onItemAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantments(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;",
                    remap = true
            ),
            require = 0 // don't crash the game if a future ISE version changes this method
    )
    private static Map<Enchantment, Integer> ironspatches$withGemLevels(ItemStack stack) {
        return stack.getAllEnchantments();
    }
}
