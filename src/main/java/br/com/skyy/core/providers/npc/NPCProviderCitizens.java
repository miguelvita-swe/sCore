package br.com.skyy.core.providers.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * NPCProvider backed by Citizens 2.x via full reflection.
 * This keeps the Citizens dependency soft — if Citizens is absent,
 * the class is never instantiated.
 */
public class NPCProviderCitizens implements NPCProvider {

    private static final Logger log = Logger.getLogger("sCore-NPC");

    private final Plugin plugin;
    private final Map<String, Integer> npcIdMap = new HashMap<>(); // npcKey → citizens npc id

    // Reflected Citizens API
    private Class<?>  citizensAPIClass;
    private Class<?>  npcRegistryClass;
    private Class<?>  npcClass;
    private Method    mGetRegistry;
    private Method    mCreateNPC;
    private Method    mSpawn;
    private Method    mDespawn;
    private Method    mDestroy;
    private Method    mIsSpawned;
    private Method    mGetById;
    private Method    mSetProtected;
    private Method    mGetEntity;
    private Method    mGetOrAddTrait;
    private Class<?>  skinTraitClass;
    private Method    mSetSkinName;
    private Class<?>  lookCloseClass;
    private Method    mToggleLookClose;
    private Method    mSetRange;
    private Method    mSetRealistic;

    private boolean available = false;

    public NPCProviderCitizens(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) return;

            citizensAPIClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            npcRegistryClass = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            npcClass         = Class.forName("net.citizensnpcs.api.npc.NPC");
            skinTraitClass   = Class.forName("net.citizensnpcs.trait.SkinTrait");
            lookCloseClass   = Class.forName("net.citizensnpcs.trait.LookClose");

            mGetRegistry   = citizensAPIClass.getMethod("getNPCRegistry");
            mCreateNPC     = npcRegistryClass.getMethod("createNPC", EntityType.class, String.class);
            mSpawn         = npcClass.getMethod("spawn", Location.class);
            mDespawn       = npcClass.getMethod("despawn");
            mDestroy       = npcClass.getMethod("destroy");
            mIsSpawned     = npcClass.getMethod("isSpawned");
            mGetById       = npcRegistryClass.getMethod("getById", int.class);
            mSetProtected  = npcClass.getMethod("setProtected", boolean.class);
            mGetEntity     = npcClass.getMethod("getEntity");
            mGetOrAddTrait = npcClass.getMethod("getOrAddTrait", Class.class);

            Method mGetId = npcClass.getMethod("getId");
            mSetSkinName   = skinTraitClass.getMethod("setSkinName", String.class, boolean.class);

            // LookClose helpers (optional)
            try {
                mToggleLookClose = lookCloseClass.getMethod("toggle");
                mSetRange        = lookCloseClass.getMethod("setRange", double.class);
                mSetRealistic    = lookCloseClass.getMethod("setRealisticLooking", boolean.class);
            } catch (Throwable ignored) {}

            available = true;
        } catch (Exception e) {
            log.warning("[sCore NPC] Citizens reflection init failed: " + e.getMessage());
        }
    }

    @Override public boolean isAvailable()    { return available; }
    @Override public String getProviderName() { return "Citizens"; }

    @Override
    public void spawnNPC(String id, Location location, String name, String textureUrl) {
        removeNPC(id);
        if (!available || location == null) return;
        try {
            Object registry = mGetRegistry.invoke(null);
            Object npc      = mCreateNPC.invoke(registry, EntityType.PLAYER, "");

            // Hide nameplate
            try {
                Class<?> metaCls = Class.forName("net.citizensnpcs.api.npc.NPC$Metadata");
                Object nameplateField = metaCls.getField("NAMEPLATE_VISIBLE").get(null);
                Object data = npcClass.getMethod("data").invoke(npc);
                data.getClass().getMethod("setPersistent", String.class, Object.class)
                        .invoke(data, nameplateField.toString(), false);
            } catch (Throwable ignored) {}

            mSetProtected.invoke(npc, true);

            // LookClose — NPC follows player with eyes
            if (mGetOrAddTrait != null && mToggleLookClose != null) {
                try {
                    Object lc = mGetOrAddTrait.invoke(npc, lookCloseClass);
                    if (mSetRange     != null) mSetRange.invoke(lc, 8.0);
                    if (mSetRealistic != null) mSetRealistic.invoke(lc, true);
                    mToggleLookClose.invoke(lc);
                } catch (Throwable ignored) {}
            }

            // Skin — use player name to fetch from Mojang (updateSkin=true)
            // 'name' is the player name passed from the plugin
            if (name != null && !name.isEmpty()) {
                try {
                    Object skin = mGetOrAddTrait.invoke(npc, skinTraitClass);
                    mSetSkinName.invoke(skin, name, true); // true = fetch skin from Mojang
                } catch (Throwable ignored) {}
            }

            mSpawn.invoke(npc, location);

            int citizensId = (int) npcClass.getMethod("getId").invoke(npc);
            npcIdMap.put(id, citizensId);
        } catch (Exception e) {
            log.warning("[sCore NPC] Citizens spawnNPC error: " + e.getMessage());
        }
    }

    @Override
    public void removeNPC(String id) {
        Integer citizensId = npcIdMap.remove(id);
        if (citizensId == null || !available) return;
        try {
            Object registry = mGetRegistry.invoke(null);
            Object npc      = mGetById.invoke(registry, citizensId);
            if (npc == null) return;
            boolean spawned = (boolean) mIsSpawned.invoke(npc);
            if (spawned) mDespawn.invoke(npc);
            mDestroy.invoke(npc);
        } catch (Exception e) {
            log.warning("[sCore NPC] Citizens removeNPC error: " + e.getMessage());
        }
    }

    @Override
    public void removeAll() {
        for (String id : npcIdMap.keySet()) {
            removeNPC(id);
        }
        npcIdMap.clear();
    }

    @Override
    public void setClickAction(String id, Consumer<Player> action) {
        // Citizens click is handled via NPCRightClickEvent — can be registered externally
        // sCore does not hard-depend on Citizens; plugins should listen to this event directly
        // if they need Citizens-specific click detection.
    }

    @Override
    public void updateName(String id, String name) {
        // name = skin player name for Citizens (NPC label kept empty)
        updateSkin(id, name);
    }

    @Override
    public void updateSkin(String id, String playerName) {
        Integer citizensId = npcIdMap.get(id);
        if (citizensId == null || !available) return;
        try {
            Object registry = mGetRegistry.invoke(null);
            Object npc      = mGetById.invoke(registry, citizensId);
            if (npc == null) return;
            Object skin = mGetOrAddTrait.invoke(npc, skinTraitClass);
            mSetSkinName.invoke(skin, playerName, true); // true = fetch skin from Mojang
        } catch (Exception e) {
            log.warning("[sCore NPC] Citizens updateSkin error: " + e.getMessage());
        }
    }

    public Integer getCitizensId(String id) {
        return npcIdMap.get(id);
    }
}
