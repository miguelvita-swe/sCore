package br.com.skyy.core.providers.nbt;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class NBTProviderPDC implements NBTProvider {

    private final Plugin plugin;

    public NBTProviderPDC(Plugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(String key) {
        return new NamespacedKey("score", key);
    }

    @Override
    public void setString(ItemStack item, String key, String value) {
        if (item == null || key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value == null ? "" : value);
        item.setItemMeta(meta);
    }

    @Override
    public void setInt(ItemStack item, String key, int value) {
        if (item == null || key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key(key), PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    @Override
    public String getString(ItemStack item, String key) {
        if (item == null || key == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = key(key);
        return pdc.has(nk, PersistentDataType.STRING) ? pdc.get(nk, PersistentDataType.STRING) : null;
    }

    @Override
    public Integer getInt(ItemStack item, String key) {
        if (item == null || key == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = key(key);
        return pdc.has(nk, PersistentDataType.INTEGER) ? pdc.get(nk, PersistentDataType.INTEGER) : null;
    }

    @Override
    public boolean has(ItemStack item, String key) {
        if (item == null || key == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = key(key);
        return pdc.has(nk, PersistentDataType.STRING) || pdc.has(nk, PersistentDataType.INTEGER);
    }

    @Override
    public void remove(ItemStack item, String key) {
        if (item == null || key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(key(key));
        item.setItemMeta(meta);
    }
}
