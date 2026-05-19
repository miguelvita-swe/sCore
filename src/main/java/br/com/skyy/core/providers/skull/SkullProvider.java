package br.com.skyy.core.providers.skull;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public interface SkullProvider {
    /** Aplica textura customizada (URL de skin) ao SkullMeta */
    void applyTexture(SkullMeta meta, String url);
    /** Aplica skull pelo nome do jogador */
    void applyOwner(SkullMeta meta, String playerName);

    /**
     * Cria um ItemStack de cabeça com a textura do URL informado.
     * Compatível com 1.8–1.21.
     */
    default ItemStack getSkull(String url) {
        // Determine material by version: 1.8-1.12 = SKULL_ITEM data 3, 1.13+ = PLAYER_HEAD
        ItemStack item;
        try {
            item = new ItemStack(org.bukkit.Material.valueOf("PLAYER_HEAD"));
        } catch (IllegalArgumentException e) {
            // Legacy (1.8-1.12): SKULL_ITEM with data 3
            @SuppressWarnings("deprecation")
            ItemStack legacy = new ItemStack(org.bukkit.Material.valueOf("SKULL_ITEM"), 1, (short) 3);
            item = legacy;
        }
        if (url == null || url.isEmpty()) return item;
        if (item.getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            applyTexture(meta, url);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Cria um ItemStack de cabeça pelo nome do jogador.
     */
    default ItemStack getSkullByName(String playerName) {
        ItemStack item;
        try {
            item = new ItemStack(org.bukkit.Material.valueOf("PLAYER_HEAD"));
        } catch (IllegalArgumentException e) {
            @SuppressWarnings("deprecation")
            ItemStack legacy = new ItemStack(org.bukkit.Material.valueOf("SKULL_ITEM"), 1, (short) 3);
            item = legacy;
        }
        if (playerName == null || playerName.isEmpty()) return item;
        if (item.getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            applyOwner(meta, playerName);
            item.setItemMeta(meta);
        }
        return item;
    }
}
