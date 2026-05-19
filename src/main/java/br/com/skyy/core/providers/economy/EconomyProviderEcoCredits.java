package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * EconomyProvider para EcoCredits (com.willfp.ecocredits).
 *
 * EcoCredits é um plugin moderno de economia para Paper 1.17–1.21,
 * parte do ecossistema EcoPlugins (WillFP).
 *
 * API: EcoCredits.getAPI() → getCredits(Player), setCredits(Player, double)
 */
public class EconomyProviderEcoCredits implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object api;
    private Method getCredits;
    private Method setCredits;
    private boolean available = false;

    public EconomyProviderEcoCredits() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("EcoCredits");
            if (plugin == null) return;

            Class<?> pluginClass = plugin.getClass();
            Method getAPI = findMethod(pluginClass, "getAPI");
            if (getAPI == null) {
                // EcoCredits mais novo pode expor static getInstance()
                try {
                    Class<?> cls = Class.forName("com.willfp.ecocredits.api.EcoCreditsAPI");
                    Method getInstance = findMethod(cls, "getInstance");
                    if (getInstance != null) api = getInstance.invoke(null);
                } catch (Exception ignored) {}
            } else {
                api = getAPI.invoke(plugin);
            }

            if (api == null) {
                // tenta acessar direto pelo plugin
                api = plugin;
            }

            Class<?> apiClass = api.getClass();
            getCredits = findMethod(apiClass, "getCredits", Player.class);
            setCredits = findMethod(apiClass, "setCredits", Player.class, double.class);

            // Nomes alternativos
            if (getCredits == null) getCredits = findMethod(apiClass, "getBalance", Player.class);
            if (setCredits == null) setCredits = findMethod(apiClass, "setBalance", Player.class, double.class);

            available = (getCredits != null && setCredits != null);
        } catch (Exception e) {
            log.fine("[sCore] EcoCredits não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "ecocredits"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object result = getCredits.invoke(api, player);
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
            double current = getBalance(player);
            if (current < amount) return false;
            setCredits.invoke(api, player, current - amount);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            double current = getBalance(player);
            setCredits.invoke(api, player, current + amount);
            return true;
        } catch (Exception e) { return false; }
    }
}
