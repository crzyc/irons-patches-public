package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.CachedMethodLookup;
import dev.yu.ironspatches.IronsPatchesConfig;
import dev.yu.ironspatches.SpiritBanishReturn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Feeds {@link SpiritBanishReturn}: records each new banish, rebuilds a session lost to a world reload, drops the
 * record whenever ws_effect_lib still has the session (its own return logic runs as before), and returns the player
 * itself only when the session is gone.
 */
@Pseudo
@Mixin(targets = "dev.modmind.ws_effect_lib.events.SpiritBanishEvents", remap = false)
public abstract class SpiritBanishEventsMixin {

    @Shadow @Final private static Map<UUID, ?> SESSIONS;

    private static Field ironspatches$sessionAvatar;
    private static final int SETTLE_TICKS = 20;

    @Shadow
    private static void startOrRefresh(ServerPlayer player, int level, ResourceLocation source) {
        throw new AssertionError();
    }

    @Inject(method = "startOrRefresh", at = @At("HEAD"), require = 0)
    private static void ironspatches$rememberReturn(ServerPlayer player, int level, ResourceLocation source, CallbackInfo ci) {
        if (!SESSIONS.containsKey(player.getUUID())) SpiritBanishReturn.remember(player, source);
    }

    @Inject(method = "onLivingTick", at = @At("HEAD"), require = 0)
    private static void ironspatches$restoreLostSession(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        // Right after a join, armor, held-item and curio max-health bonuses are not applied yet (and health may still be
        // clamped to the bare max until mods like MaxHealthFix restore it), so wait before rebuilding the body or syncing.
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount <= SETTLE_TICKS) return;
        Object session = SESSIONS.get(player.getUUID());
        if (session == null && SpiritBanishReturn.hasRecord(player)) {
            SpiritBanishReturn.restoreSession(player, (level, source) -> startOrRefresh(player, level, source));
            session = SESSIONS.get(player.getUUID());
        }
        if (session != null && ironspatches$body(session) instanceof LivingEntity body) {
            SpiritBanishReturn.syncHealth(player, body);
        }
    }

    /**
     * A body saved before the reload can load after the session was rebuilt with a new one. ws_effect_lib then
     * links the session to the loaded body, so whichever body the session does not point to is discarded.
     */
    @Inject(method = "avatarTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ironspatches$dropUnlinkedBody(@Coerce LivingEntity avatar, CallbackInfo ci) {
        if (avatar == null || avatar.isRemoved()
                || !IronsPatchesConfig.enabled(IronsPatchesConfig.SPIRIT_BANISH_SAFE_RETURN)) return;
        Object session;
        try {
            session = SESSIONS.get((UUID) CachedMethodLookup.getMethod(avatar.getClass(), "getOwnerUUID").invoke(avatar));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return; // ws_effect_lib changed: leave its own handling alone
        }
        if (session != null && ironspatches$body(session) instanceof Entity linked && linked != avatar && !linked.isRemoved()) {
            avatar.discard();
            ci.cancel();
        }
    }

    /** The body the session points to, or null if ws_effect_lib's session class changed. */
    private static Object ironspatches$body(Object session) {
        try {
            if (ironspatches$sessionAvatar == null) {
                Field field = session.getClass().getDeclaredField("avatar");
                field.setAccessible(true);
                ironspatches$sessionAvatar = field;
            }
            return ironspatches$sessionAvatar.get(session);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Inject(method = "finishForPlayer", at = @At("HEAD"), require = 0)
    private static void ironspatches$returnWithoutSession(LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (SESSIONS.containsKey(player.getUUID())) {
            SpiritBanishReturn.forget(player);
        } else {
            SpiritBanishReturn.returnWithoutSession(player);
        }
    }

    @Inject(method = "onPlayerDeath", at = @At("HEAD"), require = 0)
    private static void ironspatches$forgetOnTrackedDeath(LivingDeathEvent event, CallbackInfo ci) {
        // With a session, ws_effect_lib restores the respawn game mode itself; without one, SpiritBanishReturn.onClone does.
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            SpiritBanishReturn.forget(player);
        }
    }
}
