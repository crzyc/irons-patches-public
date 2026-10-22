package dev.yu.ironspatches.mixin;

import dev.yu.ironspatches.SectionFilteredFluidScan;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Forge's fluid scan calls {@code level.getFluidState} for every block inside the entity's bounding box,
 * every tick. Iron's Spells' Black Hole has a (2 x radius)^3 box (up to 96^3 blocks), so that scan dominates
 * client and server tick time.
 *
 * For entities tagged {@link SectionFilteredFluidScan}, this runs the exact same algorithm in the exact same
 * block order, but jumps over 16^3 chunk sections whose block palette contains no fluid. Every skipped block
 * has an empty fluid state, which the original loop ignores anyway, so the results are identical.
 * Logic mirrors Forge 47.4.10 {@code Entity#updateFluidHeightAndDoFluidPushing(Predicate)}.
 */
@Mixin(Entity.class)
public abstract class EntityFluidScanMixin {

    @Shadow(remap = false) // Forge-added method, not obfuscated
    protected abstract void setFluidTypeHeight(FluidType type, double height);

    @Inject(method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ironspatches$sectionFilteredScan(Predicate<FluidState> shouldUpdate, CallbackInfo ci) {
        if (!(this instanceof SectionFilteredFluidScan)) {
            return;
        }
        ci.cancel();

        Entity self = (Entity) (Object) this;
        if (self.touchingUnloadedChunk()) {
            return;
        }
        Level level = self.level();
        AABB aabb = self.getBoundingBox().deflate(0.001);
        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.ceil(aabb.maxX);
        int minY = Mth.floor(aabb.minY);
        int maxY = Mth.ceil(aabb.maxY);
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.ceil(aabb.maxZ);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return;
        }

        // Which 16^3 sections inside the box may contain a fluid (conservative: unknown => true).
        int secMinX = minX >> 4, secMinY = minY >> 4, secMinZ = minZ >> 4;
        int secCountX = ((maxX - 1) >> 4) - secMinX + 1;
        int secCountY = ((maxY - 1) >> 4) - secMinY + 1;
        int secCountZ = ((maxZ - 1) >> 4) - secMinZ + 1;
        boolean[] mayHaveFluid = new boolean[secCountX * secCountY * secCountZ];
        boolean anyFluid = false;
        for (int sx = 0; sx < secCountX; sx++) {
            for (int sz = 0; sz < secCountZ; sz++) {
                LevelChunk chunk = level.getChunk(secMinX + sx, secMinZ + sz);
                if (chunk instanceof EmptyLevelChunk) {
                    continue; // getFluidState is always empty here
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = 0; sy < secCountY; sy++) {
                    int sectionY = secMinY + sy;
                    if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
                        continue; // outside build height: Level#getFluidState returns empty
                    }
                    LevelChunkSection section = sections[level.getSectionIndexFromSectionY(sectionY)];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    if (section.getStates().maybeHas(state -> !state.getFluidState().isEmpty())) {
                        mayHaveFluid[(sx * secCountY + sy) * secCountZ + sz] = true;
                        anyFluid = true;
                    }
                }
            }
        }
        if (!anyFluid) {
            return; // the original loop would find nothing and change nothing
        }

        // ---- Forge's algorithm, unchanged except for the section skip in the innermost loop ----
        boolean pushedByFluid = self.isPushedByFluid();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Object2ObjectMap<FluidType, MutableTriple<Double, Vec3, Integer>> interimCalcs =
                new Object2ObjectArrayMap<>(FluidType.SIZE.get() - 1);

        for (int x = minX; x < maxX; x++) {
            int sx = (x >> 4) - secMinX;
            for (int y = minY; y < maxY; y++) {
                int rowBase = (sx * secCountY + ((y >> 4) - secMinY)) * secCountZ;
                int z = minZ;
                while (z < maxZ) {
                    int sz = (z >> 4) - secMinZ;
                    int segmentEnd = Math.min(maxZ, ((z >> 4) + 1) << 4);
                    if (!mayHaveFluid[rowBase + sz]) {
                        z = segmentEnd;
                        continue;
                    }
                    for (; z < segmentEnd; z++) {
                        pos.set(x, y, z);
                        FluidState fluidstate = level.getFluidState(pos);
                        FluidType fluidType = fluidstate.getFluidType();
                        if (!fluidType.isAir() && shouldUpdate.test(fluidstate)) {
                            double height = (double) ((float) y + fluidstate.getHeight(level, pos));
                            if (height >= aabb.minY) {
                                MutableTriple<Double, Vec3, Integer> interim =
                                        interimCalcs.computeIfAbsent(fluidType, t -> MutableTriple.of(0.0D, Vec3.ZERO, 0));
                                interim.setLeft(Math.max(height - aabb.minY, interim.getLeft()));
                                if (self.isPushedByFluid(fluidType)) {
                                    Vec3 flow = fluidstate.getFlow(level, pos);
                                    if (interim.getLeft() < 0.4D) {
                                        flow = flow.scale(interim.getLeft());
                                    }
                                    interim.setMiddle(interim.getMiddle().add(flow));
                                    interim.setRight(interim.getRight() + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        interimCalcs.forEach((fluidType, interim) -> {
            if (interim.getMiddle().length() > 0.0D) {
                if (interim.getRight() > 0) {
                    interim.setMiddle(interim.getMiddle().scale(1.0D / (double) interim.getRight()));
                }
                if (!(self instanceof Player)) {
                    interim.setMiddle(interim.getMiddle().normalize());
                }
                Vec3 motion = self.getDeltaMovement();
                interim.setMiddle(interim.getMiddle().scale(self.getFluidMotionScale(fluidType)));
                if (Math.abs(motion.x) < 0.003D && Math.abs(motion.z) < 0.003D && interim.getMiddle().length() < 0.0045000000000000005D) {
                    interim.setMiddle(interim.getMiddle().normalize().scale(0.0045000000000000005D));
                }
                self.setDeltaMovement(self.getDeltaMovement().add(interim.getMiddle()));
            }
            this.setFluidTypeHeight(fluidType, interim.getLeft());
        });
    }
}
