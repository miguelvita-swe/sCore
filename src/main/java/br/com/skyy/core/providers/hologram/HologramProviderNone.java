package br.com.skyy.core.providers.hologram;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

/** No-op hologram provider — used when no hologram plugin is installed. */
public class HologramProviderNone implements HologramProvider {
    @Override public boolean isAvailable()  { return false; }
    @Override public String getProviderName() { return "None"; }
    @Override public void createHologram(String id, Location location, List<String> lines) {}
    @Override public void updateHologram(String id, List<String> lines) {}
    @Override public void removeHologram(String id) {}
    @Override public void removeAll() {}
    @Override public void createHologramWithItem(String id, Location location, List<String> lines, Material itemMaterial) {}
}
