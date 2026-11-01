package com.suschunkfinder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns a block into a small class id. Only the block TYPE is used (plain client-side data).
 * Main thread only (the cache is not synchronized).
 */
public final class BlockClassifier {
    public static final byte AIR = 0;          // air or non-solid passable blocks
    public static final byte SOLID = 1;
    public static final byte LIQUID = 2;
    public static final byte STORAGE = 3;
    public static final byte UTILITY = 4;
    public static final byte REDSTONE = 5;
    public static final byte BUILD = 6;
    public static final byte DECOR = 7;
    public static final byte STRONG_MARKER = 8; // natural structure marker (spawner, sculk...)
    public static final byte WEAK_MARKER = 9;   // natural structure hint, needs several
    public static final byte STONE_BRICK = 10;  // stronghold hint, needs very many

    public static final int FIRST_INDICATOR = 3;
    /** Weight per class, indexed by class id. */
    public static final int[] WEIGHTS = {0, 0, 0, 6, 4, 2, 1, 1, 0, 0, 0};

    private static final Map<Block, Byte> CACHE = new HashMap<>();

    private static final Set<String> STRONG = Set.of(
            "spawner", "trial_spawner", "vault", "sculk_shrieker", "sculk_sensor",
            "calibrated_sculk_sensor", "sculk_catalyst", "reinforced_deepslate",
            "end_portal_frame", "end_portal");
    private static final Set<String> WEAK = Set.of(
            "tuff_bricks", "chiseled_tuff", "chiseled_tuff_bricks", "soul_lantern",
            "mossy_cobblestone", "sculk", "sculk_vein");
    private static final Set<String> STONE_BRICKS = Set.of(
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "stone_brick_stairs", "stone_brick_slab", "mossy_stone_brick_stairs", "mossy_stone_brick_slab");
    private static final Set<String> STORAGE_IDS = Set.of(
            "chest", "trapped_chest", "barrel", "ender_chest", "hopper", "dropper", "dispenser");
    private static final Set<String> UTILITY_IDS = Set.of(
            "furnace", "blast_furnace", "smoker", "crafting_table", "smithing_table", "cartography_table",
            "fletching_table", "loom", "grindstone", "stonecutter", "anvil", "chipped_anvil", "damaged_anvil",
            "enchanting_table", "brewing_stand", "beacon", "lectern", "composter", "jukebox", "note_block",
            "respawn_anchor", "conduit");
    private static final Set<String> REDSTONE_IDS = Set.of(
            "redstone_wire", "repeater", "comparator", "observer", "piston", "sticky_piston",
            "redstone_lamp", "redstone_block", "target", "daylight_detector", "redstone_torch",
            "redstone_wall_torch", "tnt", "slime_block", "honey_block");
    private static final Set<String> BUILD_IDS = Set.of(
            "bricks", "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block",
            "netherite_block", "coal_block", "sea_lantern", "glowstone", "shroomlight",
            "jack_o_lantern", "bone_block", "hay_block", "dried_kelp_block", "smooth_stone",
            "chiseled_quartz_block");
    private static final Set<String> DECOR_IDS = Set.of(
            "bookshelf", "chiseled_bookshelf", "lantern", "scaffolding", "end_rod", "lightning_rod",
            "flower_pot", "cake", "campfire", "soul_campfire");

    private BlockClassifier() {}

    public static byte classify(BlockState state) {
        if (state.isAir()) return AIR;
        Block block = state.getBlock();
        Byte cached = CACHE.get(block);
        if (cached != null) return cached;
        byte c = classifyBlock(block);
        CACHE.put(block, c);
        return c;
    }

    private static byte classifyBlock(Block block) {
        String id = Registries.BLOCK.getId(block).getPath();

        int indicator = classifyId(id);
        if (indicator >= 0) return (byte) indicator;

        BlockState def = block.getDefaultState();
        if (!def.getFluidState().isEmpty()) return LIQUID;
        if (!def.blocksMovement()) return AIR;
        return SOLID;
    }

    /** Returns a class id, or -1 if the block is not an indicator. */
    private static int classifyId(String id) {
        if (STRONG.contains(id)) return STRONG_MARKER;
        if (WEAK.contains(id) || id.contains("copper_bulb") || id.contains("copper_grate")) return WEAK_MARKER;
        if (STONE_BRICKS.contains(id)) return STONE_BRICK;

        if (STORAGE_IDS.contains(id) || id.endsWith("shulker_box") || id.contains("copper_chest")) return STORAGE;
        if (UTILITY_IDS.contains(id) || id.endsWith("_bed")) return UTILITY;
        if (REDSTONE_IDS.contains(id)) return REDSTONE;

        if (BUILD_IDS.contains(id)
                || id.contains("glass")
                || id.endsWith("_concrete")
                || id.endsWith("glazed_terracotta")
                || id.endsWith("_wool")
                || id.endsWith("_froglight")
                || id.startsWith("brick_")
                || id.startsWith("polished_andesite")
                || id.startsWith("polished_granite")
                || id.startsWith("polished_diorite")
                || id.startsWith("smooth_stone")
                || id.startsWith("smooth_sandstone")
                || id.startsWith("smooth_red_sandstone")
                || id.startsWith("smooth_quartz")
                || id.startsWith("quartz_")) return BUILD;

        if (DECOR_IDS.contains(id)
                || id.contains("_sign")
                || id.endsWith("_banner")
                || id.endsWith("_head")
                || id.endsWith("_skull")
                || id.startsWith("potted_")) return DECOR;

        return -1;
    }
}
