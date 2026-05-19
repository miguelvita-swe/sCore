package br.com.skyy.core.providers.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Abstraction layer for NPC operations across different providers
 * (Citizens, ArmorStand fallback, etc.)
 *
 * <p>Usage example:
 * <pre>
 *   NPCProvider npc = SCore.getNPC();
 *   npc.spawnNPC("top1", location, "Jogador", "http://textures.../skin.png");
 *   npc.setClickAction("top1", player -> player.sendMessage("Clicou!"));
 * </pre>
 */
public interface NPCProvider {

    /** @return true if this provider is available and loaded. */
    boolean isAvailable();

    /** Human-readable name of this provider (e.g. "Citizens", "ArmorStand"). */
    String getProviderName();

    /**
     * Spawns (or replaces) an NPC at the given location.
     *
     * @param id         Unique identifier — used to update/remove later.
     * @param location   Where to spawn.
     * @param name       Display name shown above the NPC head (empty = no name).
     * @param textureUrl Mojang texture URL (http://textures.minecraft.net/...) — may be null.
     */
    void spawnNPC(String id, Location location, String name, String textureUrl);

    /**
     * Removes the NPC with the given id.
     * No-op if the NPC does not exist.
     */
    void removeNPC(String id);

    /** Removes all NPCs managed by this provider. */
    void removeAll();

    /**
     * Registers a callback triggered when a player right-clicks the NPC.
     *
     * @param id     The NPC id.
     * @param action Consumer called with the Player who clicked.
     */
    void setClickAction(String id, Consumer<Player> action);

    /**
     * Updates the name displayed above the NPC without re-spawning it.
     *
     * @param id   The NPC id.
     * @param name New display name.
     */
    void updateName(String id, String name);

    /**
     * Updates the skin of an already-spawned NPC.
     *
     * @param id         The NPC id.
     * @param playerName In-game name of the player whose skin to use.
     */
    void updateSkin(String id, String playerName);
}
