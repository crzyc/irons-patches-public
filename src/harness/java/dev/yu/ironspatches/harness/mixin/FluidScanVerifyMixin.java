package dev.yu.ironspatches.harness.mixin;

import dev.yu.ironspatches.SectionFilteredFluidScan;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEST HARNESS ONLY (not shipped). Around each fluid update of a tagged entity, computes the result of Forge's
 * original full-box scan (verbatim copy, no section skipping, no side effects) and compares it bit-for-bit with
 * what the patched scan produced. Also times both.
 */
@Mixin(Entity.class)
public abstract class FluidScanVerifyMixin {
    @Unique private static final Logger IRONSPATCHES$LOG = LoggerFactory.getLogger("ironspatches-harness");
    @Unique private static long ironspatches$calls, ironspatches$mismatches, ironspatches$bruteNanos, ironspatches$patchedNanos, ironspatches$withFluid;

    @Shadow(remap = false) protected it.unimi.dsi.fastutil.objects.Object2DoubleMap<FluidType> forgeFluidTypeHeight;

    @Unique private Object2DoubleMap<FluidType> ironspatches$expectedHeights;
    @Unique private Vec3 ironspatches$expectedMotion;
    @Unique private long ironspatches$start;

    @Inject(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At("HEAD"))
    private void ironspatches$before(TagKey<Fluid> tag, double d, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof SectionFilteredFluidScan)) return;
        Entity self = (Entity) (Object) this;
        long t0 = System.nanoTime();
        ironspatches$expectedHeights = new Object2DoubleArrayMap<>(this.forgeFluidTypeHeight);
        ironspatches$expectedMotion = self.getDeltaMovement();
        if (!self.touchingUnloadedChunk()) {
            Level level = self.level();
            AABB aabb = self.getBoundingBox().deflate(0.001);
            int i = Mth.floor(aabb.minX), j = Mth.ceil(aabb.maxX), k = Mth.floor(aabb.minY), l = Mth.ceil(aabb.maxY), i1 = Mth.floor(aabb.minZ), j1 = Mth.ceil(aabb.maxZ);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            Object2ObjectMap<FluidType, MutableTriple<Double, Vec3, Integer>> interimCalcs = new Object2ObjectArrayMap<>(FluidType.SIZE.get() - 1);
            for (int l1 = i; l1 < j; ++l1) for (int i2 = k; i2 < l; ++i2) for (int j2 = i1; j2 < j1; ++j2) {
                pos.set(l1, i2, j2);
                FluidState fs = level.getFluidState(pos);
                FluidType ft = fs.getFluidType();
                if (!ft.isAir()) {
                    double d1 = (double) ((float) i2 + fs.getHeight(level, pos));
                    if (d1 >= aabb.minY) {
                        MutableTriple<Double, Vec3, Integer> in = interimCalcs.computeIfAbsent(ft, t -> MutableTriple.of(0.0D, Vec3.ZERO, 0));
                        in.setLeft(Math.max(d1 - aabb.minY, in.getLeft()));
                        if (self.isPushedByFluid(ft)) {
                            Vec3 v = fs.getFlow(level, pos);
                            if (in.getLeft() < 0.4D) v = v.scale(in.getLeft());
                            in.setMiddle(in.getMiddle().add(v));
                            in.setRight(in.getRight() + 1);
                        }
                    }
                }
            }
            if (!interimCalcs.isEmpty()) ironspatches$withFluid++;
            Vec3[] motion = {ironspatches$expectedMotion};
            interimCalcs.forEach((ft, in) -> {
                if (in.getMiddle().length() > 0.0D) {
                    if (in.getRight() > 0) in.setMiddle(in.getMiddle().scale(1.0D / (double) in.getRight()));
                    if (!(self instanceof Player)) in.setMiddle(in.getMiddle().normalize());
                    Vec3 vec32 = motion[0];
                    in.setMiddle(in.getMiddle().scale(self.getFluidMotionScale(ft)));
                    if (Math.abs(vec32.x) < 0.003D && Math.abs(vec32.z) < 0.003D && in.getMiddle().length() < 0.0045000000000000005D)
                        in.setMiddle(in.getMiddle().normalize().scale(0.0045000000000000005D));
                    motion[0] = motion[0].add(in.getMiddle());
                }
                ironspatches$expectedHeights.put(ft, (double) in.getLeft());
            });
            ironspatches$expectedMotion = motion[0];
        }
        long t1 = System.nanoTime();
        ironspatches$bruteNanos += t1 - t0;
        ironspatches$start = System.nanoTime();
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At("RETURN"))
    private void ironspatches$after(TagKey<Fluid> tag, double d, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof SectionFilteredFluidScan) || ironspatches$expectedMotion == null) return;
        ironspatches$patchedNanos += System.nanoTime() - ironspatches$start;
        Entity self = (Entity) (Object) this;
        ironspatches$calls++;
        boolean ok = self.getDeltaMovement().equals(ironspatches$expectedMotion)
                && new java.util.HashMap<>(this.forgeFluidTypeHeight).equals(new java.util.HashMap<>(ironspatches$expectedHeights));
        if (!ok) {
            ironspatches$mismatches++;
            IRONSPATCHES$LOG.error("MISMATCH tick={} side={} expectedMotion={} actualMotion={} expectedHeights={} actualHeights={}",
                    self.tickCount, self.level().isClientSide ? "client" : "server", ironspatches$expectedMotion, self.getDeltaMovement(),
                    ironspatches$expectedHeights, this.forgeFluidTypeHeight);
        }
        if (ironspatches$calls % 50 == 0) {
            IRONSPATCHES$LOG.info("VERIFY calls={} mismatches={} callsWithFluid={} avgBruteUs={} avgPatchedUs={} bbox={} pos={} motion={} heights={}",
                    ironspatches$calls, ironspatches$mismatches, ironspatches$withFluid,
                    ironspatches$bruteNanos / ironspatches$calls / 1000, ironspatches$patchedNanos / ironspatches$calls / 1000,
                    self.getBoundingBox().getXsize(), self.position(), self.getDeltaMovement(), this.forgeFluidTypeHeight);
        }
        ironspatches$expectedMotion = null;
    }
}
