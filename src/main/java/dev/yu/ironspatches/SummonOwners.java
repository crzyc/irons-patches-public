package dev.yu.ironspatches;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

import java.lang.reflect.Method;

/**
 * Finds who owns a pet or summon: vanilla's OwnableEntity (tamed animals and most modded pets), then a
 * public no-arg getSummoner() (Iron's Spells' IMagicSummon and addons built on it). Iron's Spells isn't a
 * compile dependency, so the second lookup goes through {@link CachedMethodLookup}.
 */
public final class SummonOwners {
    private SummonOwners() {}

    public static Entity ownerOf(Entity entity) {
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null) return owner;
        }
        try {
            Method getSummoner = CachedMethodLookup.getMethod(entity.getClass(), "getSummoner");
            return getSummoner.invoke(entity) instanceof Entity summoner ? summoner : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
