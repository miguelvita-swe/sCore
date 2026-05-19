package br.com.skyy.core.providers.nbt;

import br.com.skyy.core.version.ServerVersion;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * NBT provider for 1.8–1.13 via NMS reflection.
 * Keys are stored as "score_<key>" inside the item's NBTTagCompound.
 */
public class NBTProviderLegacy implements NBTProvider {

    private static final Logger log = Logger.getLogger("sCore-NBT");
    private static final String NBT_PREFIX = "score_";

    // NMS class references resolved once
    private final Class<?> craftItemStackClass;
    private final Class<?> nbtTagCompoundClass;
    private final Method asNMSCopy;
    private final Method asBukkitCopy;
    private final Method getTag;
    private final Method setTag;
    private final Method hasKey;
    private final Method setString;
    private final Method getString;
    private final Method setInt;
    private final Method getInt;
    private final Method remove;
    private final Method newTagCompound; // constructor/static factory

    private boolean available = false;

    public NBTProviderLegacy(ServerVersion version) {
        Class<?> cis = null, ntc = null;
        Method aNMS = null, aBukkit = null, gTag = null, sTag = null,
                hKey = null, sStr = null, gStr = null, sInt = null, gInt = null, rem = null, newTag = null;

        try {
            // Always resolve CraftBukkit path dynamically from the server's package name.
            // This handles every version (1.8–1.16 NMS versioned, 1.17–1.21 relocated).
            String pkg = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String nmsVersion = pkg.substring(pkg.lastIndexOf('.') + 1); // e.g. "v1_20_R1"

            // CraftItemStack path is always versioned in CraftBukkit
            cis = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".inventory.CraftItemStack");

            // NBT compound class: net.minecraft.server.{V}.NBTTagCompound (≤1.16)
            // or net.minecraft.nbt.CompoundTag (1.17+)
            try {
                ntc = Class.forName("net.minecraft.nbt.CompoundTag");
            } catch (ClassNotFoundException e1) {
                ntc = Class.forName("net.minecraft.server." + nmsVersion + ".NBTTagCompound");
            }

            aNMS    = cis.getMethod("asNMSCopy", ItemStack.class);
            aBukkit = cis.getMethod("asBukkitCopy", Object.class);

            // getTag: method that returns the NBT compound from an NMS ItemStack
            gTag = tryMethod(ntc, "getTag");          // ≤1.20
            if (gTag == null) gTag = tryMethod(ntc, "getTags"); // some builds

            sTag = tryMethod(ntc, "setTag", ntc);

            // hasKey / contains: check if a key exists
            hKey = tryMethod(ntc, "hasKey", String.class);      // ≤1.16
            if (hKey == null) hKey = tryMethod(ntc, "contains", String.class); // 1.17+

            // String get/set
            sStr = tryMethod(ntc, "setString", String.class, String.class); // ≤1.16
            if (sStr == null) sStr = tryMethod(ntc, "putString", String.class, String.class); // 1.17+
            gStr = tryMethod(ntc, "getString", String.class);

            // Int get/set
            sInt = tryMethod(ntc, "setInt", String.class, int.class);       // ≤1.16
            if (sInt == null) sInt = tryMethod(ntc, "putInt", String.class, int.class); // 1.17+
            gInt = tryMethod(ntc, "getInt", String.class);

            rem  = tryMethod(ntc, "remove", String.class);

            available = (aNMS != null && gTag != null && hKey != null && sStr != null && gStr != null);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy: Failed to initialize NMS reflection: " + e.getMessage());
        }

        craftItemStackClass = cis;
        nbtTagCompoundClass = ntc;
        asNMSCopy = aNMS;
        asBukkitCopy = aBukkit;
        getTag = gTag;
        setTag = sTag;
        hasKey = hKey;
        setString = sStr;
        getString = gStr;
        setInt = sInt;
        getInt = gInt;
        remove = rem;
        newTagCompound = newTag;
    }

    private Object getNMSItem(ItemStack item) throws Exception {
        return asNMSCopy.invoke(null, item);
    }

    private Object getOrCreateTag(Object nmsItem) throws Exception {
        Object tag = getTag.invoke(nmsItem);
        if (tag == null) {
            tag = nbtTagCompoundClass.getDeclaredConstructor().newInstance();
            // Must attach it to the item immediately so setTag works
            if (setTag != null) setTag.invoke(nmsItem, tag);
        }
        return tag;
    }

    private static Method tryMethod(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) return null;
        try { return clazz.getMethod(name, params); } catch (Exception e) { return null; }
    }

    @Override
    public void setString(ItemStack item, String key, String value) {
        if (!available || item == null || key == null) return;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getOrCreateTag(nmsItem);
            setString.invoke(tag, NBT_PREFIX + key, value == null ? "" : value);
            setTag.invoke(nmsItem, tag);
            // Apply back: if item is CraftItemStack, modify via handle field; otherwise set meta
            applyBack(item, nmsItem);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy setString error: " + e.getMessage());
        }
    }

    @Override
    public void setInt(ItemStack item, String key, int value) {
        if (!available || item == null || key == null) return;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getOrCreateTag(nmsItem);
            setInt.invoke(tag, NBT_PREFIX + key, value);
            setTag.invoke(nmsItem, tag);
            applyBack(item, nmsItem);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy setInt error: " + e.getMessage());
        }
    }

    @Override
    public String getString(ItemStack item, String key) {
        if (!available || item == null || key == null) return null;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getTag.invoke(nmsItem);
            if (tag == null) return null;
            boolean has = (boolean) hasKey.invoke(tag, NBT_PREFIX + key);
            if (!has) return null;
            return (String) getString.invoke(tag, NBT_PREFIX + key);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy getString error: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Integer getInt(ItemStack item, String key) {
        if (!available || item == null || key == null) return null;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getTag.invoke(nmsItem);
            if (tag == null) return null;
            boolean has = (boolean) hasKey.invoke(tag, NBT_PREFIX + key);
            if (!has) return null;
            return (Integer) getInt.invoke(tag, NBT_PREFIX + key);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy getInt error: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean has(ItemStack item, String key) {
        if (!available || item == null || key == null) return false;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getTag.invoke(nmsItem);
            if (tag == null) return false;
            return (boolean) hasKey.invoke(tag, NBT_PREFIX + key);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy has error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void remove(ItemStack item, String key) {
        if (!available || item == null || key == null) return;
        try {
            Object nmsItem = getNMSItem(item);
            Object tag = getTag.invoke(nmsItem);
            if (tag == null) return;
            remove.invoke(tag, NBT_PREFIX + key);
            setTag.invoke(nmsItem, tag);
            applyBack(item, nmsItem);
        } catch (Exception e) {
            log.warning("[sCore] NBTProviderLegacy remove error: " + e.getMessage());
        }
    }

    /**
     * Tries to apply the modified NMS item back to the Bukkit ItemStack by reference
     * (if it's a CraftItemStack with a "handle" field), otherwise does nothing
     * (caller should use the returned ItemStack from asBukkitCopy if needed).
     */
    private void applyBack(ItemStack item, Object nmsItem) {
        if (craftItemStackClass.isInstance(item)) {
            try {
                java.lang.reflect.Field handleField = findField(craftItemStackClass, "handle");
                if (handleField != null) {
                    handleField.setAccessible(true);
                    handleField.set(item, nmsItem);
                }
            } catch (Exception e) {
                log.warning("[sCore] NBTProviderLegacy applyBack error: " + e.getMessage());
            }
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    public boolean isAvailable() {
        return available;
    }
}
