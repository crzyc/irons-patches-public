package dev.yu.ironspatches;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * ws_effect_lib keeps Spirit Banish sessions (the banished player's body and previous game mode) only in
 * memory, so a world reload mid-banish loses them: the body is discarded, touching it or recasting does
 * nothing, and the player stays in Spectator. This stores the same return data on the player when a banish
 * starts. If the session is gone while the effect is still on, the session and body are rebuilt from it, so
 * ws_effect_lib's own return paths (touch, recast, expiry, body death) work again. Players without the stored
 * data (e.g. stuck before this fix) are left alone.
 * Config: glitch_fixes.spiritBanishSafeReturn. Hooked in from SpiritBanishEventsMixin.
 */
@Mod.EventBusSubscriber(modid = IronsPatches.MOD_ID)
public final class SpiritBanishReturn {
    private static final String TAG = IronsPatches.MOD_ID + ":spirit_banish_return";
    private static final float DEFAULT_FLY_SPEED = 0.05F;
    private static final ResourceLocation SPIRIT_BANISH = new ResourceLocation("ws_effect_lib", "spirit_banish");
    private static final String SYNCED_HEALTH = IronsPatches.MOD_ID + ":synced_health";

    private SpiritBanishReturn() {}

    /**
     * Called when ws_effect_lib is about to start a session. Only a banish that starts from no effect is new;
     * with the effect already on, the player is a spirit (e.g. a session rebuild), and the record from the real
     * start must be kept.
     */
    public static void remember(ServerPlayer player, ResourceLocation source) {
        if (!IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN) || banish(player) != null) return;
        CompoundTag data = new CompoundTag();
        data.putString("GameMode", player.gameMode.getGameModeForPlayer().getName());
        data.putString("Dimension", player.level().dimension().location().toString());
        data.putDouble("X", player.getX());
        data.putDouble("Y", player.getY());
        data.putDouble("Z", player.getZ());
        if (source != null) data.putString("Source", source.toString());
        player.getPersistentData().put(TAG, data);
    }

    public static boolean hasRecord(ServerPlayer player) {
        return player.getPersistentData().contains(TAG, CompoundTag.TAG_COMPOUND);
    }

    /**
     * ws_effect_lib has no session for a player with a stored record (the world was reloaded mid-banish).
     * With the effect still on, start a new session the way ws_effect_lib does, but from the stored body spot
     * and game mode, then put the spirit back where it was. ws_effect_lib's 1.5 s touch grace restarts.
     */
    public static void restoreSession(ServerPlayer player, BiConsumer<Integer, ResourceLocation> startOrRefresh) {
        if (!IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN)) return;
        MobEffectInstance effect = banish(player);
        if (effect == null) {
            returnWithoutSession(player); // left over with no effect: the player would stay in Spectator
            return;
        }
        CompoundTag data = player.getPersistentData().getCompound(TAG);
        double x = data.getDouble("X"), y = data.getDouble("Y"), z = data.getDouble("Z");
        // Wait until the body's spot is loaded in this dimension. shortcut: a body in another dimension is never
        // rebuilt; recast or expiry still returns the player there via returnWithoutSession.
        if (!player.level().dimension().location().toString().equals(data.getString("Dimension"))
                || !player.level().isLoaded(BlockPos.containing(x, y, z))) return;

        Vec3 spirit = player.position();
        player.setGameMode(gameMode(data, player)); // ws_effect_lib records the current mode as the one to restore
        player.moveTo(x, y, z, player.getYRot(), player.getXRot()); // and spawns the body where the player stands
        ResourceLocation source = data.contains("Source") ? ResourceLocation.tryParse(data.getString("Source")) : null;
        startOrRefresh.accept(effect.getAmplifier(), source);
        player.moveTo(spirit.x, spirit.y, spirit.z, player.getYRot(), player.getXRot());
    }

    /**
     * Keeps the body and the spirit on one health pool. Changes on either side since the last sync are added
     * together and written to both, so a hit on the body shows on the player and the reverse. The last synced
     * value lives on the body, so it survives the body being saved and reloaded with its chunk.
     */
    public static void syncHealth(ServerPlayer player, LivingEntity body) {
        if (!IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SHARED_HEALTH)
                || body.isRemoved() || body.isDeadOrDying() || player.isDeadOrDying()) return;
        matchMaxHealth(player, body);
        CompoundTag data = body.getPersistentData();
        float bodyHealth = body.getHealth(), spiritHealth = player.getHealth();
        float last = data.contains(SYNCED_HEALTH, CompoundTag.TAG_FLOAT) ? data.getFloat(SYNCED_HEALTH) : bodyHealth;
        float shared = bodyHealth + spiritHealth - last;
        // shortcut: two hits in one tick that are only lethal together leave both at the lower health; the next hit kills.
        if (!(shared > 0)) shared = Math.min(bodyHealth, spiritHealth);
        shared = Math.min(shared, Math.min(body.getMaxHealth(), player.getMaxHealth()));
        if (bodyHealth != shared) body.setHealth(shared);
        if (spiritHealth != shared) player.setHealth(shared);
        data.putFloat(SYNCED_HEALTH, shared);
    }

    /**
     * ws_effect_lib sets the body's base max health to the player's full max health (gear and effects included),
     * then copies the player's armor, held items and effects onto the body, so their bonuses count twice. Set the
     * body's base so its total max health equals the player's.
     */
    private static void matchMaxHealth(ServerPlayer player, LivingEntity body) {
        AttributeInstance maxHealth = body.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || Math.abs(body.getMaxHealth() - player.getMaxHealth()) < 0.01F) return;
        maxHealth.setBaseValue(maxHealth.getBaseValue() + player.getMaxHealth() - body.getMaxHealth());
    }

    /** ws_effect_lib still has the session and will return the player itself. */
    public static void forget(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    /** The effect ended but ws_effect_lib lost the session: return the player to their body. */
    public static void returnWithoutSession(ServerPlayer player) {
        CompoundTag data = take(player);
        if (data == null || !player.isSpectator()
                || !IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN)) return;

        player.setGameMode(gameMode(data, player));
        player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
        player.onUpdateAbilities();

        ServerLevel level = player.serverLevel();
        ResourceLocation dimension = ResourceLocation.tryParse(data.getString("Dimension"));
        if (dimension != null) {
            ServerLevel stored = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (stored != null) level = stored;
        }
        player.teleportTo(level, data.getDouble("X"), data.getDouble("Y"), data.getDouble("Z"), player.getYRot(), player.getXRot());
    }

    /** Dying with no session left (e.g. /kill after a restart) would otherwise respawn the player in Spectator. */
    /**
     * ws_effect_lib's session state is static, so in singleplayer it outlives the world: after save-and-quit and
     * rejoin, the old session (old server's start tick, old level and body) is still there. Its touch check
     * compares the new server's tick count against the old start tick and stays off until the new world has run
     * longer than the old one had, and restoreSession never runs because a session exists. Clear it with the
     * world so a reload always goes through restoreSession. RESPAWN_MODES is kept: it is keyed by player and
     * still applies to a player who quit on the death screen.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (!IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN)) return;
        Class<?> events;
        try {
            events = Class.forName("dev.modmind.ws_effect_lib.events.SpiritBanishEvents");
        } catch (ClassNotFoundException e) {
            return; // ws_effect_lib not installed
        }
        try {
            for (String name : new String[] {"SESSIONS", "ADJUSTING", "PENDING_ADJUST", "PENDING_SOURCE"}) {
                Field field = events.getDeclaredField(name);
                field.setAccessible(true);
                Object state = field.get(null);
                if (state instanceof Map<?, ?> map) map.clear();
                else if (state instanceof Collection<?> collection) collection.clear();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogUtils.getLogger().warn("Could not clear ws_effect_lib Spirit Banish state on world close", e);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = take(event.getOriginal());
        if (data == null || !player.isSpectator()
                || !IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN)) return;
        player.setGameMode(gameMode(data, player));
        player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
        player.onUpdateAbilities();
    }

    private static CompoundTag take(net.minecraft.world.entity.player.Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(TAG, CompoundTag.TAG_COMPOUND)) return null;
        CompoundTag data = persistent.getCompound(TAG);
        persistent.remove(TAG);
        return data;
    }

    private static MobEffectInstance banish(ServerPlayer player) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(SPIRIT_BANISH);
        return effect == null ? null : player.getEffect(effect);
    }

    private static GameType gameMode(CompoundTag data, ServerPlayer player) {
        GameType fallback = player.server.getDefaultGameType();
        GameType mode = GameType.byName(data.getString("GameMode"), fallback);
        return mode == GameType.SPECTATOR ? fallback : mode;
    }
}
