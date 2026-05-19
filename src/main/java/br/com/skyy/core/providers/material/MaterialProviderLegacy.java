package br.com.skyy.core.providers.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Material provider for 1.8–1.12 (uses Material + short data values).
 */
@SuppressWarnings("deprecation")
public class MaterialProviderLegacy implements MaterialProvider {

    private static class LegacyMaterial {
        final Material material;
        final short data;

        LegacyMaterial(Material material, short data) {
            this.material = material;
            this.data = data;
        }
    }

    private final Map<String, LegacyMaterial> legacyMap = new HashMap<>();

    public MaterialProviderLegacy() {
        register("GRAY_STAINED_GLASS_PANE",  "STAINED_GLASS_PANE", (short) 7);
        register("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 15);
        register("WHITE_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 0);
        register("LIME_STAINED_GLASS_PANE",  "STAINED_GLASS_PANE", (short) 5);
        register("RED_STAINED_GLASS_PANE",   "STAINED_GLASS_PANE", (short) 14);
        register("GLASS_PANE",               "THIN_GLASS",          (short) 0);
        register("PLAYER_HEAD",              "SKULL_ITEM",          (short) 3);
        register("SKELETON_SKULL",           "SKULL_ITEM",          (short) 0);
        // Blocks
        register("IRON_BLOCK",              "IRON_BLOCK",          (short) 0);
        register("GOLD_BLOCK",              "GOLD_BLOCK",          (short) 0);
        register("DIAMOND_BLOCK",           "DIAMOND_BLOCK",       (short) 0);
        register("COAL_BLOCK",              "COAL_BLOCK",          (short) 0);
        register("EMERALD_BLOCK",           "EMERALD_BLOCK",       (short) 0);
        // Items
        register("IRON_INGOT",             "IRON_INGOT",          (short) 0);
        register("GOLD_INGOT",             "GOLD_INGOT",          (short) 0);
        register("DIAMOND",               "DIAMOND",             (short) 0);
        register("EMERALD",               "EMERALD",             (short) 0);
        register("COAL",                  "COAL",                (short) 0);
        register("ARROW",                 "ARROW",               (short) 0);
        register("BARRIER",               "BARRIER",             (short) 0);
        register("ANVIL",                 "ANVIL",               (short) 0);
        register("AIR",                   "AIR",                 (short) 0);
        register("STONE",                 "STONE",               (short) 0);
        // Menu items used by sMaquinas
        register("FIREBALL",              "FIREBALL",            (short) 0);
        register("FIRE_CHARGE",           "FIREBALL",            (short) 0);
        register("EYE_OF_ENDER",          "EYE_OF_ENDER",        (short) 0);
        register("ENDER_EYE",             "EYE_OF_ENDER",        (short) 0);
        register("EXP_BOTTLE",            "EXP_BOTTLE",          (short) 0);
        register("EXPERIENCE_BOTTLE",     "EXP_BOTTLE",          (short) 0);
        register("SULPHUR",               "SULPHUR",             (short) 0);
        register("GUNPOWDER",             "SULPHUR",             (short) 0);
        register("GLASS_BOTTLE",          "GLASS_BOTTLE",        (short) 0);
        register("POTION",                "POTION",              (short) 0);
        register("CHEST",                 "CHEST",               (short) 0);
        register("BUCKET",                "BUCKET",              (short) 0);
        register("FEATHER",               "FEATHER",             (short) 0);
        register("CLOCK",                 "WATCH",               (short) 0);
        register("WATCH",                 "WATCH",               (short) 0);
        register("COMPASS",               "COMPASS",             (short) 0);
        register("NETHER_STAR",           "NETHER_STAR",         (short) 0);
        register("BLAZE_POWDER",          "BLAZE_POWDER",        (short) 0);
        register("BLAZE_ROD",             "BLAZE_ROD",           (short) 0);
        register("PAPER",                 "PAPER",               (short) 0);
        register("BOOK",                  "BOOK",                (short) 0);
        register("ENCHANTED_BOOK",        "ENCHANTED_BOOK",      (short) 0);
    }

    private void register(String modernName, String legacyName, short data) {
        try {
            Material mat = Material.valueOf(legacyName);
            legacyMap.put(modernName.toUpperCase(), new LegacyMaterial(mat, data));
        } catch (IllegalArgumentException ignored) {
            // Material not available on this version, skip
        }
    }

    @Override
    public Material get(String name) {
        if (name == null) return Material.STONE;
        LegacyMaterial lm = legacyMap.get(name.toUpperCase());
        if (lm != null) return lm.material;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    @Override
    public Material get(String name, String fallback) {
        if (name == null) return get(fallback);
        LegacyMaterial lm = legacyMap.get(name.toUpperCase());
        if (lm != null) return lm.material;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return get(fallback);
        }
    }

    @Override
    public short getData(String name) {
        if (name == null) return 0;
        LegacyMaterial lm = legacyMap.get(name.toUpperCase());
        return lm != null ? lm.data : 0;
    }

    @Override
    public ItemStack createItem(String name, int amount) {
        if (name == null) return new ItemStack(Material.STONE, amount);
        LegacyMaterial lm = legacyMap.get(name.toUpperCase());
        if (lm != null) {
            return new ItemStack(lm.material, amount, lm.data);
        }
        return new ItemStack(get(name), amount);
    }
}
