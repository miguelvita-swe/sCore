package br.com.skyy.core.providers.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public interface MaterialProvider {
    Material get(String name);
    Material get(String name, String fallback);
    /** For legacy versions returns the data value; modern always returns 0 */
    short getData(String name);
    /** Creates ItemStack already with correct data value */
    ItemStack createItem(String name, int amount);
}
