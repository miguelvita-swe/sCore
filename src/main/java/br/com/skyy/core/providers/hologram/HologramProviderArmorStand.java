package br.com.skyy.core.providers.hologram;

import br.com.skyy.core.utils.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback HologramProvider using stacked invisible ArmorStands.
 * Compatible with Minecraft 1.8–1.21 without any external dependencies.
 *
 * Each hologram line = 1 invisible ArmorStand with a custom name visible.
 * Lines are spaced 0.28 blocks apart vertically (top to bottom).
 */
public class HologramProviderArmorStand implements HologramProvider {

    /** Gap between each line (in blocks). */
    private static final double LINE_GAP = 0.28;

    private final Plugin plugin;
    /** id → list of ArmorStands (one per line, top→bottom) */
    private final Map<String, List<ArmorStand>> active = new LinkedHashMap<>();

    public HologramProviderArmorStand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return true; // always available — pure vanilla
    }

    @Override
    public String getProviderName() {
        return "ArmorStand";
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        removeHologram(id); // remove old if exists
        if (location == null || location.getWorld() == null || lines == null || lines.isEmpty()) return;

        List<ArmorStand> stands = new ArrayList<>();
        // Top line at 'location', subsequent lines below
        double y = location.getY();

        for (String line : lines) {
            Location lineLoc = new Location(location.getWorld(), location.getX(), y, location.getZ());
            ArmorStand stand = spawnStand(lineLoc, ColorUtil.colorize(line));
            stands.add(stand);
            y -= LINE_GAP;
        }

        active.put(id, stands);
    }

    @Override
    public void createHologramWithItem(String id, Location location, List<String> lines, Material itemMaterial) {
        // ArmorStand fallback: item line not supported — create text-only hologram
        createHologram(id, location, lines);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Override
    public void updateHologram(String id, List<String> lines) {
        List<ArmorStand> stands = active.get(id);
        if (stands == null || lines == null) return;

        // If same line count — just update names
        if (stands.size() == lines.size()) {
            for (int i = 0; i < stands.size(); i++) {
                ArmorStand s = stands.get(i);
                if (s != null && s.isValid()) {
                    s.setCustomName(ColorUtil.colorize(lines.get(i)));
                }
            }
            return;
        }

        // Different count — recreate at first stand's location
        if (!stands.isEmpty() && stands.get(0).isValid()) {
            Location loc = stands.get(0).getLocation();
            createHologram(id, loc, lines);
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Override
    public void removeHologram(String id) {
        List<ArmorStand> stands = active.remove(id);
        if (stands == null) return;
        for (ArmorStand s : stands) {
            if (s != null && s.isValid()) s.remove();
        }
    }

    @Override
    public void removeAll() {
        for (List<ArmorStand> stands : active.values()) {
            for (ArmorStand s : stands) {
                if (s != null && s.isValid()) s.remove();
            }
        }
        active.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ArmorStand spawnStand(Location loc, String name) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        stand.setCustomName(name);
        stand.setCustomNameVisible(!name.isEmpty());
        stand.setVisible(false);      // invisible body
        stand.setGravity(false);
        stand.setArms(false);
        stand.setBasePlate(false);

        // Invulnerable — wrap for 1.8 compat (added in 1.9 on ArmorStand, exists via Entity in 1.8+)
        try { stand.setInvulnerable(true); } catch (Throwable ignored) {}

        // setSmall — exists in 1.8+ but wrap just in case
        try { stand.setSmall(true); } catch (Throwable ignored) {}

        // setMarker (no hitbox) — 1.9+ only; reflection so it doesn't crash on 1.8
        try {
            stand.getClass().getMethod("setMarker", boolean.class).invoke(stand, true);
        } catch (Throwable ignored) {}

        return stand;
    }
}
