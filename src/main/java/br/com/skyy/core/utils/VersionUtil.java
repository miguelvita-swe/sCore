package br.com.skyy.core.utils;

import br.com.skyy.core.version.ServerVersion;
import org.bukkit.Bukkit;

/**
 * Version-related helper utilities.
 */
public final class VersionUtil {

    private VersionUtil() {}

    public static ServerVersion current() {
        return ServerVersion.getCurrent();
    }

    public static boolean isAtLeast(ServerVersion version) {
        return ServerVersion.getCurrent().isAtLeast(version);
    }

    public static boolean isLegacy() {
        return ServerVersion.getCurrent().isLegacy();
    }

    public static boolean hasPDC() {
        return ServerVersion.getCurrent().hasPDC();
    }

    public static boolean hasHexColors() {
        return ServerVersion.getCurrent().hasHexColors();
    }

    public static boolean hasModernSkull() {
        return ServerVersion.getCurrent().hasModernSkull();
    }

    /** Returns the raw NMS version string, e.g. "v1_20_R3" */
    public static String getNMSVersion() {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        return pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    /** Returns true if a plugin with the given name is loaded on the server */
    public static boolean isPluginPresent(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }
}
