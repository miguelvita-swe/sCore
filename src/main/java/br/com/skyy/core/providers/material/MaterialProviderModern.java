package br.com.skyy.core.providers.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Material provider for 1.13+ (no data values).
 */
public class MaterialProviderModern implements MaterialProvider {

    @Override
    public Material get(String name) {
        if (name == null) return Material.STONE;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : Material.STONE;
    }

    @Override
    public Material get(String name, String fallback) {
        if (name == null) return get(fallback);
        Material mat = Material.matchMaterial(name);
        if (mat != null) return mat;
        return get(fallback);
    }

    @Override
    public short getData(String name) {
        return 0;
    }

    @Override
    public ItemStack createItem(String name, int amount) {
        return new ItemStack(get(name), amount);
    }
}
