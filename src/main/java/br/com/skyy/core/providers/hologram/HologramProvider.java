package br.com.skyy.core.providers.hologram;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

public interface HologramProvider {
    boolean isAvailable();
    String getProviderName();
    void createHologram(String id, Location location, List<String> lines);
    void updateHologram(String id, List<String> lines);
    void removeHologram(String id);
    void removeAll();
    /** Suporte a linha de item — exibe item físico no holograma */
    void createHologramWithItem(String id, Location location, List<String> lines, Material itemMaterial);
}
