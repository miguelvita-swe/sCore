package br.com.skyy.core.version;

import org.bukkit.Bukkit;

public enum ServerVersion {
    V1_8, V1_9, V1_10, V1_11, V1_12,  // Legacy (sem PDC, materiais com data)
    V1_13,                               // Transição (novos nomes, sem PDC)
    V1_14, V1_15, V1_16, V1_17,        // PDC disponível
    V1_18, V1_19, V1_20, V1_21,        // Moderno
    UNKNOWN;

    private static ServerVersion current;

    public static ServerVersion detect() {
        if (current != null) return current;
        String version = Bukkit.getBukkitVersion(); // e.g. "1.21.1-R0.1-SNAPSHOT"
        try {
            String[] parts = version.split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[1]);
            current = fromMajor(major);
        } catch (Exception e) {
            current = UNKNOWN;
        }
        return current;
    }

    private static ServerVersion fromMajor(int major) {
        switch (major) {
            case 8:  return V1_8;
            case 9:  return V1_9;
            case 10: return V1_10;
            case 11: return V1_11;
            case 12: return V1_12;
            case 13: return V1_13;
            case 14: return V1_14;
            case 15: return V1_15;
            case 16: return V1_16;
            case 17: return V1_17;
            case 18: return V1_18;
            case 19: return V1_19;
            case 20: return V1_20;
            case 21: return V1_21;
            default: return major > 21 ? V1_21 : UNKNOWN;
        }
    }

    public static ServerVersion getCurrent() {
        if (current == null) detect();
        return current;
    }

    public boolean isAtLeast(ServerVersion version) {
        return this.ordinal() >= version.ordinal();
    }

    public boolean isLegacy() {
        return this.ordinal() < V1_13.ordinal();
    }

    public boolean hasPDC() {
        return this.ordinal() >= V1_14.ordinal();
    }

    public boolean hasModernSkull() {
        return this.ordinal() >= V1_18.ordinal();
    }

    public boolean hasHexColors() {
        return this.ordinal() >= V1_16.ordinal();
    }
}
