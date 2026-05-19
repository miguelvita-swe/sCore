package br.com.skyy.core.providers.nbt;

import org.bukkit.inventory.ItemStack;

public interface NBTProvider {
    void setString(ItemStack item, String key, String value);
    void setInt(ItemStack item, String key, int value);
    String getString(ItemStack item, String key);
    Integer getInt(ItemStack item, String key);
    boolean has(ItemStack item, String key);
    void remove(ItemStack item, String key);
}
