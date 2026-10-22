package dev.yu.ironspatches.harness;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import dev.shadowsoffire.placebo.events.GetEnchantmentLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TEST HARNESS ONLY (not shipped): sets up the scenarios and stops the server when done. */
@Mod.EventBusSubscriber(modid = "ironspatches")
public final class HarnessEvents {
    private static final Logger LOG = LoggerFactory.getLogger("ironspatches-harness");
    private static boolean boostEnabled;
    private static int ticks;
    private static Enchantment mastery;
    private static Entity blackHole;

    @SubscribeEvent
    public static void onEnchLevel(GetEnchantmentLevelEvent e) {
        // stands in for an Apotheosis gem: the same Placebo event Apotheosis uses (AdventureEvents#enchLevels)
        if (boostEnabled && mastery != null && e.getEnchantments().containsKey(mastery)) {
            e.getEnchantments().merge(mastery, 5, Integer::sum);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        // ---- ISE test ----
        mastery = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("irons_spells_enchantments", "eldritch_spell_power"));
        Attribute eldritch = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", "eldritch_spell_power"));
        if (mastery == null || eldritch == null) {
            LOG.error("ISE TEST: enchant or attribute missing ({} / {})", mastery, eldritch);
        } else {
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.enchant(mastery, 10);
            LOG.info("ISE TEST stored level 10, no boost -> {}", sum(helmet, eldritch));
            boostEnabled = true;
            LOG.info("ISE TEST stored level 10, +5 via GetEnchantmentLevelEvent -> {}", sum(helmet, eldritch));
            boostEnabled = false;
            LOG.info("ISE TEST boost removed again -> {}", sum(helmet, eldritch));
        }

        // ---- Black Hole test: radius 40 near water and lava ----
        ServerLevel level = server.overworld();
        BlockPos c = level.getSharedSpawnPos().above(20);
        // fluid sources inside the box (box spans y-40..y+40); they flow down, creating flowing states across sections
        for (int dx = -30; dx <= 30; dx += 15) {
            level.setBlockAndUpdate(c.offset(dx, 10, 5), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(c.offset(dx, 25, -20), Blocks.WATER.defaultBlockState());
        }
        level.setBlockAndUpdate(c.offset(3, -1, 2), Blocks.WATER.defaultBlockState()); // touches the BH's own position
        level.setBlockAndUpdate(c.offset(12, 15, 12), Blocks.LAVA.defaultBlockState());
        // platforms so water/lava spread horizontally (non-zero flow vectors -> fluid pushing changes motion)
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            level.setBlockAndUpdate(c.offset(x, -3, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(c.offset(x + 20, 4, z + 20), Blocks.STONE.defaultBlockState());
        }
        // sources in platform corners so the spread is one-sided and flow vectors don't cancel out
        level.setBlockAndUpdate(c.offset(-12, -2, -12), Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(c.offset(8, 5, 8), Blocks.LAVA.defaultBlockState());
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("irons_spellbooks", "black_hole"));
        Entity bh = type == null ? null : type.create(level);
        if (bh == null) {
            LOG.error("BH TEST: could not create black hole");
            return;
        }
        CompoundTag tag = new CompoundTag();
        bh.saveWithoutId(tag);
        tag.putFloat("Radius", 40f);
        tag.putInt("Duration", 100000);
        tag.putFloat("Damage", 0f);
        bh.load(tag);
        bh.setPos(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        level.addFreshEntity(bh);
        blackHole = bh;
        LOG.info("BH TEST spawned at {} bbox={}", c, bh.getBoundingBox());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++ticks % 100 == 0 && blackHole != null) {
            LOG.info("TICK {} blackHole removed={} reason={} pos={} age={}", ticks, blackHole.isRemoved(), blackHole.getRemovalReason(), blackHole.position(), blackHole.tickCount);
        }
        if (e.phase == TickEvent.Phase.END && ticks == 800) {
            LOG.info("HARNESS DONE, stopping server");
            e.getServer().halt(false);
        }
    }

    private static double sum(ItemStack stack, Attribute attr) {
        double s = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlot.HEAD).get(attr)) s += m.getAmount();
        return s;
    }
}
