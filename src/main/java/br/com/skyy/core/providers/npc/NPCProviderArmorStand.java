package br.com.skyy.core.providers.npc;

import br.com.skyy.core.providers.skull.SkullProvider;
import br.com.skyy.core.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * ArmorStand-based NPC fallback for 1.8–1.21.
 *
 * Each NPC is an invisible ArmorStand wearing a skull with the player's texture.
 * Interactions are captured via PlayerInteractAtEntityEvent.
 */
public class NPCProviderArmorStand implements NPCProvider, Listener {

    private static final Logger log = Logger.getLogger("sCore-NPC");

    private final Plugin plugin;
    private final SkullProvider skullProvider;

    /** id → ArmorStand UUID */
    private final Map<String, UUID> npcMap     = new ConcurrentHashMap<>();
    /** id → click consumer */
    private final Map<String, Consumer<Player>> clickMap = new ConcurrentHashMap<>();
    /** stand UUID → npc id (reverse lookup for click) */
    private final Map<UUID, String> reverseMap = new ConcurrentHashMap<>();

    private boolean listenerRegistered = false;

    public NPCProviderArmorStand(Plugin plugin, SkullProvider skullProvider) {
        this.plugin       = plugin;
        this.skullProvider = skullProvider;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "ArmorStand";
    }

    // ── Spawn / Remove ────────────────────────────────────────────────────────

    @Override
    public void spawnNPC(String id, Location location, String name, String textureUrl) {
        removeNPC(id); // remove previous if any

        if (location == null || location.getWorld() == null) return;

        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomNameVisible(name != null && !name.isEmpty());
        stand.setCustomName(name != null ? ColorUtil.colorize(name) : "");
        stand.setArms(false);
        stand.setBasePlate(false);

        // setInvulnerable — wrap for safety
        try { stand.setInvulnerable(true); } catch (Throwable ignored) {}

        // setMarker (no hitbox for text NPCs) — 1.9+ only via reflection
        // NOTE: For NPC interaction we KEEP hitbox (marker=false) so players can click it.
        // We intentionally do NOT call setMarker here.

        // Apply skull with player texture
        if (textureUrl != null && !textureUrl.isEmpty()) {
            ItemStack skull = skullProvider.getSkull(textureUrl);
            stand.getEquipment().setHelmet(skull);
        }

        npcMap.put(id, stand.getUniqueId());
        reverseMap.put(stand.getUniqueId(), id);

        ensureListenerRegistered();
    }

    @Override
    public void removeNPC(String id) {
        UUID uuid = npcMap.remove(id);
        if (uuid == null) return;
        reverseMap.remove(uuid);

        // Must remove entity on main thread
        org.bukkit.entity.Entity entity = findEntity(uuid);
        if (entity != null) entity.remove();
    }

    @Override
    public void removeAll() {
        for (UUID uuid : npcMap.values()) {
            reverseMap.remove(uuid);
            org.bukkit.entity.Entity entity = findEntity(uuid);
            if (entity != null) entity.remove();
        }
        npcMap.clear();
        reverseMap.clear();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Override
    public void setClickAction(String id, Consumer<Player> action) {
        if (action == null) clickMap.remove(id);
        else                clickMap.put(id, action);
    }

    @Override
    public void updateName(String id, String name) {
        ArmorStand stand = getStand(id);
        if (stand == null) return;
        stand.setCustomName(name != null ? ColorUtil.colorize(name) : "");
        stand.setCustomNameVisible(name != null && !name.isEmpty());
    }

    @Override
    public void updateSkin(String id, String playerName) {
        ArmorStand stand = getStand(id);
        if (stand == null) return;

        // Fetch skin async then apply on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
                String textureUrl = resolveTextureUrl(op);
                if (textureUrl != null) {
                    ItemStack skull = skullProvider.getSkull(textureUrl);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stand.isValid()) stand.getEquipment().setHelmet(skull);
                    });
                }
            } catch (Exception e) {
                log.warning("[sCore NPC] Failed to update skin for " + playerName + ": " + e.getMessage());
            }
        });
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        // Avoid double-fire on 1.9+ (main + offhand)
        try {
            if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        } catch (Throwable ignored) {}

        org.bukkit.entity.Entity entity = event.getRightClicked();
        String id = reverseMap.get(entity.getUniqueId());
        if (id == null) return;

        event.setCancelled(true);
        Consumer<Player> action = clickMap.get(id);
        if (action != null) action.accept(event.getPlayer());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ArmorStand getStand(String id) {
        UUID uuid = npcMap.get(id);
        if (uuid == null) return null;
        org.bukkit.entity.Entity e = findEntity(uuid);
        return (e instanceof ArmorStand) ? (ArmorStand) e : null;
    }

    private org.bukkit.entity.Entity findEntity(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(uuid)) return e;
            }
        }
        return null;
    }

    private void ensureListenerRegistered() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
    }

    /**
     * Attempts to resolve a player's Mojang texture URL from their OfflinePlayer.
     * Returns null if unavailable.
     */
    private String resolveTextureUrl(org.bukkit.OfflinePlayer op) {
        try {
            // Paper 1.12+: getPlayerProfile().getTextures().getSkin()
            Object profile = op.getClass().getMethod("getPlayerProfile").invoke(op);
            if (profile == null) return null;
            Object textures = profile.getClass().getMethod("getTextures").invoke(profile);
            if (textures == null) return null;
            java.net.URL skin = (java.net.URL) textures.getClass().getMethod("getSkin").invoke(textures);
            return skin != null ? skin.toString() : null;
        } catch (Throwable ignored) {}
        return null;
    }
}
