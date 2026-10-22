package dev.yu.ironspatches;

/**
 * Marker for entities whose fluid scan should skip chunk sections that cannot contain fluids.
 * Applied to Iron's Spells' Black Hole, whose bounding box is (2 x radius)^3 blocks (up to 96^3).
 */
public interface SectionFilteredFluidScan {
}
