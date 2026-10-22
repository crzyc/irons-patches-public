package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.CachedMethodLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/**
 * Arcane Manifestation's Compat.owner runs for every entity every tick (from its time-stop skipTick
 * mixin) and Compat.boss once per server tick, and both call Class.getMethod uncached. Most entities
 * lack the probed methods, so each call also builds a NoSuchMethodException. Routing those lookups
 * through a per-class cache keeps the results identical while removing the reflection cost.
 */
@Pseudo
@Mixin(targets = "net.triplebomb.arcane.Compat", remap = false)
public abstract class ArcaneCompatMixin {

    @Redirect(
            method = {"owner", "boss"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"
            ),
            require = 0 // don't crash the game if a future Arcane Manifestation version changes these methods
    )
    private static Method ironspatches$cachedGetMethod(Class<?> type, String name, Class<?>[] params) throws NoSuchMethodException {
        return CachedMethodLookup.getMethod(type, name, params);
    }
}
