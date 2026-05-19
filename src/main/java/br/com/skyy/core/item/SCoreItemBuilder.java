package br.com.skyy.core.item;

import br.com.skyy.core.SCore;
import br.com.skyy.core.utils.ColorUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Universal ItemBuilder for all MC versions (1.8–1.21).
 * Uses SCore providers for NBT, skull, materials and colors.
 *
 * Migration guide for sMaquinas:
 *   ANTES:  new ItemBuilder(Material.IRON_BLOCK).setName("&fMáquina").setNBT("tipo", "ferro")
 *   DEPOIS: new SCoreItemBuilder("IRON_BLOCK").name("&fMáquina").nbt("tipo", "ferro").build()
 *
 * Placeholders dinâmicos (ex: lore com {stack}, {combustivel}):
 *   new SCoreItemBuilder("IRON_BLOCK")
 *       .name("&fMáquina de Ferro")
 *       .lore("&7Stack: &f{stack}", "&7Combustível: &f{combustivel}")
 *       .placeholder("stack", String.valueOf(stack))
 *       .placeholder("combustivel", fuelName)
 *       .build();
 */
public class SCoreItemBuilder {

    public static final String NAMESPACE = "score";
    private static final Logger log = Logger.getLogger("sCore-ItemBuilder");

    private ItemStack item;
    private ItemMeta  meta;

    /** Placeholders pendentes: {chave} → valor. Aplicados em name+lore no build(). */
    private final Map<String, String> placeholders = new HashMap<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public SCoreItemBuilder(Material material) {
        this(material, 1);
    }

    @SuppressWarnings("deprecation")
    public SCoreItemBuilder(Material material, int legacyData) {
        this.item = new ItemStack(material, 1, (short) legacyData);
        this.meta = item.getItemMeta();
    }

    public SCoreItemBuilder(String materialName) {
        this(materialName, 1);
    }

    public SCoreItemBuilder(String materialName, int amount) {
        this.item = SCore.getMaterial().createItem(materialName, amount);
        this.meta = item.getItemMeta();
    }

    public SCoreItemBuilder(ItemStack existing) {
        this.item = existing.clone();
        this.meta = this.item.getItemMeta();
    }

    // ── Chainable setters ─────────────────────────────────────────────────────

    public SCoreItemBuilder name(String name) {
        if (meta != null && name != null) meta.setDisplayName(ColorUtil.colorize(name));
        return this;
    }

    public SCoreItemBuilder lore(List<String> lore) {
        if (meta == null || lore == null) return this;
        meta.setLore(ColorUtil.colorize(lore));
        return this;
    }

    public SCoreItemBuilder lore(String... lines) {
        List<String> list = new ArrayList<>();
        for (String l : lines) list.add(l);
        return lore(list);
    }

    public SCoreItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public SCoreItemBuilder glow(boolean glow) {
        if (meta != null && glow) applyGlow(meta);
        return this;
    }

    public SCoreItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null && enchantment != null) meta.addEnchant(enchantment, level, true);
        return this;
    }

    public SCoreItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) meta.addItemFlags(flags);
        return this;
    }

    public SCoreItemBuilder hideAll() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /** Sets a String NBT tag via SCore's NBTProvider */
    public SCoreItemBuilder nbt(String key, String value) {
        if (key == null) return this;
        item.setItemMeta(meta);
        SCore.getNBT().setString(item, key, value);
        meta = item.getItemMeta();
        return this;
    }

    /** Sets an int NBT tag via SCore's NBTProvider */
    public SCoreItemBuilder nbt(String key, int value) {
        if (key == null) return this;
        item.setItemMeta(meta);
        SCore.getNBT().setInt(item, key, value);
        meta = item.getItemMeta();
        return this;
    }

    /** Applies skull texture from skin URL */
    public SCoreItemBuilder skullTexture(String url) {
        if (url != null && meta instanceof SkullMeta) {
            SCore.getSkull().applyTexture((SkullMeta) meta, url);
        }
        return this;
    }

    /** Sets skull owner by player name */
    public SCoreItemBuilder skullOwner(String playerName) {
        if (playerName != null && meta instanceof SkullMeta) {
            SCore.getSkull().applyOwner((SkullMeta) meta, playerName);
        }
        return this;
    }

    // ── Placeholders ──────────────────────────────────────────────────────────

    /**
     * Registra um placeholder para substituição no build().
     * Suporta tanto {chave} quanto %chave% no nome e lore.
     *
     * Exemplo (sMaquinas):
     *   builder.placeholder("stack", "64")
     *          .placeholder("combustivel", "Carvão")
     *          .placeholder("tipo", "Ferro")
     */
    public SCoreItemBuilder placeholder(String key, String value) {
        if (key != null && value != null) {
            placeholders.put(key, value);
        }
        return this;
    }

    /**
     * Registra múltiplos placeholders de uma vez.
     * Conveniente para passar Map<String,String> de uma config.
     */
    public SCoreItemBuilder placeholders(Map<String, String> map) {
        if (map != null) placeholders.putAll(map);
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public ItemStack build() {
        if (meta != null) {
            // Aplica placeholders no displayName
            if (!placeholders.isEmpty() && meta.hasDisplayName()) {
                meta.setDisplayName(applyPlaceholders(meta.getDisplayName()));
            }
            // Aplica placeholders no lore
            if (!placeholders.isEmpty() && meta.hasLore()) {
                List<String> lore = meta.getLore();
                List<String> processed = new ArrayList<>();
                for (String line : lore) processed.add(applyPlaceholders(line));
                meta.setLore(processed);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Aplica todos os placeholders registrados em uma string. */
    private String applyPlaceholders(String text) {
        if (text == null || placeholders.isEmpty()) return text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            // Suporta {chave} e %chave%
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            text = text.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return text;
    }

    // ── Static readers ────────────────────────────────────────────────────────

    public static String getNBT(ItemStack item, String key) {
        if (item == null || key == null) return null;
        return SCore.getNBT().getString(item, key);
    }

    public static Integer getNBTInt(ItemStack item, String key) {
        if (item == null || key == null) return null;
        return SCore.getNBT().getInt(item, key);
    }

    /**
     * Tries namespace "score" first, then falls back to a legacy namespace.
     * Used for transparent migration of old sMaquinas items (namespace "smaquinas").
     */
    public static String getNBTWithFallback(ItemStack item, String key, String legacyNamespace) {
        if (item == null || key == null) return null;
        String value = SCore.getNBT().getString(item, key);
        if (value != null) return value;
        return getLegacyNBT(item, legacyNamespace + "_" + key);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void applyGlow(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
            m.invoke(meta, Boolean.TRUE);
            return;
        } catch (Exception ignored) {}
        try {
            Enchantment ench;
            try {
                ench = (Enchantment) Enchantment.class.getField("DURABILITY").get(null);
            } catch (NoSuchFieldException e) {
                ench = (Enchantment) Enchantment.class.getField("UNBREAKING").get(null);
            }
            meta.addEnchant(ench, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } catch (Exception e) {
            log.warning("[sCore] SCoreItemBuilder glow fallback failed: " + e.getMessage());
        }
    }

    private static String getLegacyNBT(ItemStack item, String rawKey) {
        try {
            String pkg = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String nmsVersion = pkg.substring(pkg.lastIndexOf('.') + 1);
            Class<?> craftItemClass;
            Class<?> nbtClass;
            try {
                craftItemClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".inventory.CraftItemStack");
                nbtClass = Class.forName("net.minecraft.server." + nmsVersion + ".NBTTagCompound");
            } catch (ClassNotFoundException e) {
                craftItemClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".inventory.CraftItemStack");
                nbtClass = Class.forName("net.minecraft.nbt.CompoundTag");
            }
            Object nmsItem = craftItemClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            Object tag = nmsItem.getClass().getMethod("getTag").invoke(nmsItem);
            if (tag == null) return null;
            boolean has = (boolean) nbtClass.getMethod("hasKey", String.class).invoke(tag, rawKey);
            if (!has) return null;
            return (String) nbtClass.getMethod("getString", String.class).invoke(tag, rawKey);
        } catch (Exception e) {
            return null;
        }
    }
}
