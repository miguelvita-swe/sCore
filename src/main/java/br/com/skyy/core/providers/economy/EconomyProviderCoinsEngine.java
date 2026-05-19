package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EconomyProvider para CoinsEngine (su.nightexpress.coinsengine).
 *
 * CoinsEngine é um dos plugins de moeda virtual mais populares em 1.16–1.21.
 * Suporta múltiplas moedas — este provider usa a moeda padrão (default currency).
 *
 * API: CoinsEngineAPI (via CoinsEnginePlugin.getAPI())
 * Métodos: getBalance(UUID, Currency), addBalance(UUID, Currency, double), etc.
 */
public class EconomyProviderCoinsEngine implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object api;
    private Object defaultCurrency;
    private Method getBalance;
    private Method addBalance;
    private Method removeBalance;
    private boolean available = false;

    public EconomyProviderCoinsEngine() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
            if (plugin == null) return;

            // CoinsEnginePlugin → getAPI() → CoinsEngineAPI
            Method getAPI = plugin.getClass().getMethod("getAPI");
            api = getAPI.invoke(plugin);
            if (api == null) return;

            Class<?> apiClass = api.getClass();

            // Tentar obter a moeda padrão via getCurrencies() ou getDefaultCurrency()
            try {
                Method getDefault = findMethod(apiClass, "getDefaultCurrency");
                if (getDefault != null) defaultCurrency = getDefault.invoke(api);
            } catch (Exception ignored) {}

            if (defaultCurrency == null) {
                // Tentar getCurrencies() → pegar o primeiro
                try {
                    Method getCurrencies = findMethod(apiClass, "getCurrencies");
                    if (getCurrencies != null) {
                        java.util.Collection<?> currencies =
                            (java.util.Collection<?>) getCurrencies.invoke(api);
                        if (currencies != null && !currencies.isEmpty()) {
                            defaultCurrency = currencies.iterator().next();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (defaultCurrency == null) return;

            getBalance    = findMethod(apiClass, "getBalance",    UUID.class, defaultCurrency.getClass());
            addBalance    = findMethod(apiClass, "addBalance",    UUID.class, defaultCurrency.getClass(), double.class);
            removeBalance = findMethod(apiClass, "removeBalance", UUID.class, defaultCurrency.getClass(), double.class);

            available = (getBalance != null && addBalance != null && removeBalance != null);
        } catch (Exception e) {
            log.fine("[sCore] CoinsEngine não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "coinsengine"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object result = getBalance.invoke(api, player.getUniqueId(), defaultCurrency);
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
            removeBalance.invoke(api, player.getUniqueId(), defaultCurrency, amount);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            addBalance.invoke(api, player.getUniqueId(), defaultCurrency, amount);
            return true;
        } catch (Exception e) { return false; }
    }
}
