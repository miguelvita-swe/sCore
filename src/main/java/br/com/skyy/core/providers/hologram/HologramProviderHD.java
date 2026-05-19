package br.com.skyy.core.providers.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * HologramProvider backed by HolographicDisplays via full reflection (fallback, 1.8â€“1.19).
 */
public class HologramProviderHD implements HologramProvider {

    private static final Logger log = Logger.getLogger("sCore-Hologram");

    private final Map<String, Object> holograms = new HashMap<>();
    private boolean available = false;
    private Plugin plugin;

    private Method mCreate;       // HologramsAPI.createHologram(Plugin, Location) â†’ Hologram
    private Method mDelete;       // hologram.delete()
    private Method mClear;        // hologram.clearLines()
    private Method mAppendText;   // hologram.appendTextLine(String)
    private Method mAppendItem;   // hologram.appendItemLine(ItemStack) â€” may not exist

    public HologramProviderHD(Plugin plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("HolographicDisplays") == null) return;

            // Try both the old and Citizens-fork package
            Class<?> apiClass = null;
            for (String pkg : new String[]{
                    "com.gmail.filoghost.holographicdisplays.api.HologramsAPI",
                    "me.filoghost.holographicdisplays.api.HolographicDisplaysAPI"}) {
                try { apiClass = Class.forName(pkg); break; } catch (ClassNotFoundException ignored) {}
            }
            if (apiClass == null) return;

            mCreate = tryGetMethod(apiClass, "createHologram", Plugin.class, Location.class);

            // Create a dummy hologram to get instance methods
            if (mCreate != null) {
                // Resolve instance methods from a temporary hologram class
                // We use the return type to discover instance methods
                Class<?> hologramClass = mCreate.getReturnType(); // Hologram interface
                mDelete     = tryGetMethod(hologramClass, "delete");
                mClear      = tryGetMethod(hologramClass, "clearLines");
                mAppendText = tryGetMethod(hologramClass, "appendTextLine", String.class);
                mAppendItem = tryGetMethod(hologramClass, "appendItemLine", ItemStack.class);
            }

            available = (mCreate != null && mAppendText != null);
        } catch (Exception e) {
            log.info("[sCore] HolographicDisplays not available: " + e.getMessage());
        }
    }

    @Override public String getProviderName() { return "HolographicDisplays"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        if (!available || id == null || location == null) return;
        removeHologram(id);
        try {
            Object hologram = mCreate.invoke(null, plugin, location);
            if (hologram == null) return;
            holograms.put(id, hologram);
            appendLines(hologram, lines, null);
        } catch (Exception e) {
            log.warning("[sCore] HD createHologram error: " + e.getMessage());
        }
    }

    @Override
    public void updateHologram(String id, List<String> lines) {
        if (!available || id == null) return;
        Object hologram = holograms.get(id);
        if (hologram == null) return;
        try {
            if (mClear != null) mClear.invoke(hologram);
            appendLines(hologram, lines, null);
        } catch (Exception e) {
            log.warning("[sCore] HD updateHologram error: " + e.getMessage());
        }
    }

    @Override
    public void createHologramWithItem(String id, Location location, List<String> lines, Material itemMaterial) {
        if (!available || id == null || location == null) return;
        removeHologram(id);
        try {
            Object hologram = mCreate.invoke(null, plugin, location);
            if (hologram == null) return;
            holograms.put(id, hologram);
            // Text lines first, THEN item at bottom
            appendLines(hologram, lines, null);
            if (itemMaterial != null && mAppendItem != null) {
                try { mAppendItem.invoke(hologram, new ItemStack(itemMaterial)); }
                catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warning("[sCore] HD createHologramWithItem error: " + e.getMessage());
        }
    }

    @Override
    public void removeHologram(String id) {
        if (!available || id == null) return;
        Object hologram = holograms.remove(id);
        if (hologram == null) return;
        try {
            if (mDelete != null) mDelete.invoke(hologram);
        } catch (Exception e) {
            log.warning("[sCore] HD removeHologram error: " + e.getMessage());
        }
    }

    @Override
    public void removeAll() {
        for (String id : new HashSet<>(holograms.keySet())) removeHologram(id);
    }

    private void appendLines(Object hologram, List<String> lines, Material itemMaterial) {
        if (lines == null) return;
        for (String line : lines) {
            if (line == null) continue;
            if (line.startsWith("[item]") && mAppendItem != null) {
                String matName = line.substring(6).split(":")[0].trim();
                try {
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) mAppendItem.invoke(hologram, new ItemStack(mat));
                    else mAppendText.invoke(hologram, line);
                } catch (Exception e) {
                    try { mAppendText.invoke(hologram, line); } catch (Exception ignored) {}
                }
            } else {
                try { mAppendText.invoke(hologram, line); } catch (Exception e) {
                    log.warning("[sCore] HD appendTextLine error: " + e.getMessage());
                }
            }
        }
    }

    private Method tryGetMethod(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) return null;
        try { return clazz.getMethod(name, params); }
        catch (Exception e) { return null; }
    }
}

