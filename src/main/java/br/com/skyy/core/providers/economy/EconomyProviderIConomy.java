package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * EconomyProvider para iConomy (legacy 1.8–1.12).
 *
 * iConomy foi um dos primeiros plugins de economia do Minecraft.
 * Muito usado em servidores 1.8 legados.
 *
 * Tenta: com.iConomy.iConomy (iConomy 5/6) e com.iConomy.system.Holdings
 * Também tenta: com.nijikokun.register.payment (Register abstraction)
 */
public class EconomyProviderIConomy implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Class<?> iconomy;
    private Method getAccount;
    private Method getHoldings;
    private Method addHoldings;
    private Method subtractHoldings;
    private boolean available = false;

    public EconomyProviderIConomy() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("iConomy");
            if (plugin == null) return;

            // iConomy 6
            try {
                iconomy = Class.forName("com.iConomy.iConomy");
                getAccount = findMethod(iconomy, "getAccount", String.class);

                if (getAccount != null) {
                    // Obter classe Account e Holdings
                    Class<?> accountClass = Class.forName("com.iConomy.system.Account");
                    getHoldings = findMethod(accountClass, "getHoldings");

                    Class<?> holdingsClass = Class.forName("com.iConomy.system.Holdings");
                    addHoldings      = findMethod(holdingsClass, "add",      double.class);
                    subtractHoldings = findMethod(holdingsClass, "subtract", double.class);

                    available = (getHoldings != null && addHoldings != null && subtractHoldings != null);
                }
            } catch (Exception ignored) {}

            if (!available) {
                // Tenta iConomy 5 legacy
                try {
                    iconomy = Class.forName("com.iConomy.iConomy");
                    available = iconomy != null;
                } catch (Exception ignored2) {}
            }

        } catch (Exception e) {
            log.fine("[sCore] iConomy não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "iconomy"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object account = getAccount.invoke(null, player.getName()); // static method
            if (account == null) return 0;
            Object holdings = getHoldings.invoke(account);
            if (holdings == null) return 0;
            Method balance = findMethod(holdings.getClass(), "balance");
            if (balance == null) return 0;
            Object result = balance.invoke(holdings);
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
            Object account  = getAccount.invoke(null, player.getName());
            Object holdings = getHoldings.invoke(account);
            subtractHoldings.invoke(holdings, amount);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object account  = getAccount.invoke(null, player.getName());
            Object holdings = getHoldings.invoke(account);
            addHoldings.invoke(holdings, amount);
            return true;
        } catch (Exception e) { return false; }
    }
}
