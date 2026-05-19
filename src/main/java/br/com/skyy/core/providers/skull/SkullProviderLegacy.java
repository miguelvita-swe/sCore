package br.com.skyy.core.providers.skull;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SkullProvider for 1.8–1.18.1 using GameProfile via reflection.
 */
public class SkullProviderLegacy implements SkullProvider {

    private static final Logger log = Logger.getLogger("sCore-Skull");

    @Override
    public void applyTexture(SkullMeta meta, String url) {
        if (meta == null || url == null) return;
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass   = Class.forName("com.mojang.authlib.properties.Property");

            // Build base64 texture value FIRST — used for deterministic UUID seed
            String encodedUrl = Base64.getEncoder().encodeToString(
                    ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());

            // Deterministic UUID from base64 value — guarantees client-side skin cache
            UUID deterministicUUID = UUID.nameUUIDFromBytes(encodedUrl.getBytes());

            Object profile = gameProfileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(deterministicUUID, "sCore");

            // Try to create Property with (name, value) or (name, value, null)
            Object property = null;
            try {
                Constructor<?> c = propertyClass.getConstructor(String.class, String.class);
                property = c.newInstance("textures", encodedUrl);
            } catch (NoSuchMethodException e) {
                Constructor<?> c = propertyClass.getConstructor(String.class, String.class, String.class);
                property = c.newInstance("textures", encodedUrl, null);
            }

            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(profile);
            Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(propertyMap, "textures", property);

            // Set profile field in SkullMeta, searching class hierarchy
            setProfileField(meta, profile);
        } catch (Exception e) {
            log.warning("[sCore] SkullProviderLegacy applyTexture error: " + e.getMessage());
        }
    }

    @Override
    public void applyOwner(SkullMeta meta, String playerName) {
        if (meta == null || playerName == null) return;
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        } catch (Exception e) {
            log.warning("[sCore] SkullProviderLegacy applyOwner error: " + e.getMessage());
        }
    }

    private void setProfileField(SkullMeta meta, Object profile) throws Exception {
        String[] fieldNames = {"profile", "serializedProfile", "gameProfile"};
        Class<?> clazz = meta.getClass();
        while (clazz != null) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(meta, profile);
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        log.warning("[sCore] SkullProviderLegacy: Could not find profile field in SkullMeta");
    }
}
