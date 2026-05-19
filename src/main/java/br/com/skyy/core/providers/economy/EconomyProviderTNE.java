package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EconomyProvider para TheNewEconomy (TNE) / Reserve (com.github.tnerevival).
 *
 * TNE é um sistema de economia avançado multi-moeda para 1.8–1.21.
 * Usa Reserve como abstração. Este provider acessa via API de Reserve
 * ou diretamente via TNE se Reserve não estiver disponível.
 *
 * Plugin IDs tentados: "TheNewEconomy", "Reserve"
 */
public class EconomyProviderTNE implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object api;
    private Method getHoldings;
    private Method addHoldings;
    private Method removeHoldings;
    private boolean available = false;
    private boolean useUUID = false;

    public EconomyProviderTNE() {
        // Tenta via Reserve primeiro (abstração do TNE)
        if (!tryReserve()) {
            tryTNE();
        }
    }

    private boolean tryReserve() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("Reserve");
            if (plugin == null) return false;
            Class<?> pluginClass = plugin.getClass();

            Method getEcon = findMethod(pluginClass, "economy");
            if (getEcon == null) getEcon = findMethod(pluginClass, "getEconomy");
            if (getEcon == null) return false;

            api = getEcon.invoke(plugin);
            if (api == null) return false;

            Class<?> econClass = api.getClass();

            // Reserve usa UUID ou String (nome de jogador) dependendo da versão
            getHoldings    = findMethod(econClass, "getHoldings",    UUID.class);
            addHoldings    = findMethod(econClass, "addHoldings",    UUID.class, double.class);
            removeHoldings = findMethod(econClass, "removeHoldings", UUID.class, double.class);

            if (getHoldings != null) {
                useUUID = true;
                available = (addHoldings != null && removeHoldings != null);
                return available;
            }

            getHoldings    = findMethod(econClass, "getHoldings",    String.class);
            addHoldings    = findMethod(econClass, "addHoldings",    String.class, double.class);
            removeHoldings = findMethod(econClass, "removeHoldings", String.class, double.class);
            available = (getHoldings != null && addHoldings != null && removeHoldings != null);
            return available;
        } catch (Exception e) {
            return false;
        }
    }

    private void tryTNE() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("TheNewEconomy");
            if (plugin == null) return;
            Class<?> pluginClass = plugin.getClass();

            Method getEcon = findMethod(pluginClass, "getAPI");
            if (getEcon == null) return;
            api = getEcon.invoke(plugin);
            if (api == null) return;

            Class<?> econClass = api.getClass();

            getHoldings    = findMethod(econClass, "getHoldings",    UUID.class);
            addHoldings    = findMethod(econClass, "addHoldings",    UUID.class, double.class);
            removeHoldings = findMethod(econClass, "removeHoldings", UUID.class, double.class);

            useUUID = true;
            available = (getHoldings != null && addHoldings != null && removeHoldings != null);
        } catch (Exception e) {
            log.fine("[sCore] TNE/Reserve não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "tne"; }
    @Override public boolean isAvailable() { return available; }

    private Object playerKey(Player player) {
        return useUUID ? player.getUniqueId() : player.getName();
    }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object result = getHoldings.invoke(api, playerKey(player));
            return result instanceof Number ? ((Number) result).doubleValue() : 0;
        } catch (Exception e) { return 0; }
    }

    @Override public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object result = removeHoldings.invoke(api, playerKey(player), amount);
            return result == null || Boolean.TRUE.equals(result);
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object result = addHoldings.invoke(api, playerKey(player), amount);
            return result == null || Boolean.TRUE.equals(result);
        } catch (Exception e) { return false; }
    }
}
