package br.com.skyy.core.providers.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Manages NPC spawning/removing with automatic provider selection:
 * <ol>
 *   <li>Citizens — if installed (realistic player NPCs)</li>
 *   <li>ArmorStand — fallback, no dependencies required (1.8–1.21)</li>
 * </ol>
 *
 * Accessible via {@code SCore.getNPC()}.
 */
public class NPCManager {

    private static final Logger log = Logger.getLogger("sCore-NPC");

    private final NPCProvider provider;

    public NPCManager(Plugin plugin, br.com.skyy.core.providers.skull.SkullProvider skullProvider) {
        NPCProviderCitizens citizens = new NPCProviderCitizens(plugin);
        if (citizens.isAvailable()) {
            this.provider = citizens;
            log.info("[sCore] NPC provider: Citizens");
        } else {
            this.provider = new NPCProviderArmorStand(plugin, skullProvider);
            log.info("[sCore] NPC provider: ArmorStand (fallback)");
        }
    }

    // ── Delegation ────────────────────────────────────────────────────────────

    /**
     * Spawns an NPC. If an NPC with the same {@code id} already exists, it is removed first.
     *
     * @param id         Unique identifier for this NPC.
     * @param location   Spawn location.
     * @param name       Display name (empty = no name tag).
     * @param textureUrl Mojang texture URL — may be null.
     */
    public void spawnNPC(String id, Location location, String name, String textureUrl) {
        provider.spawnNPC(id, location, name, textureUrl);
    }

    /** Removes the NPC with this id. No-op if not found. */
    public void removeNPC(String id) {
        provider.removeNPC(id);
    }

    /** Removes all NPCs managed by this instance. */
    public void removeAll() {
        provider.removeAll();
    }

    /**
     * Registers a click action. The consumer is called on the main thread
     * when a player right-clicks the NPC.
     */
    public void setClickAction(String id, Consumer<Player> action) {
        provider.setClickAction(id, action);
    }

    /** Updates the NPC's display name. */
    public void updateName(String id, String name) {
        provider.updateName(id, name);
    }

    /** Updates the NPC's skin to the given player name. */
    public void updateSkin(String id, String playerName) {
        provider.updateSkin(id, playerName);
    }

    /** Returns the underlying provider being used. */
    public NPCProvider getProvider() {
        return provider;
    }

    /** Returns true if Citizens is the active provider. */
    public boolean isCitizens() {
        return provider instanceof NPCProviderCitizens;
    }
}
