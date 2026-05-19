package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EconomyProvider para GemsEconomy (me.xanium.gemseconomy).
 *
 * GemsEconomy é um plugin de economia multi-moeda popular em 1.8–1.21.
 * Este provider usa a moeda padrão (default currency).
 *
 * API: GemsEconomyAPI ou via GemsEconomy.getInstance().getCurrencyManager()
 */
public class EconomyProviderGemsEconomy implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object currencyManager;
    private Object defaultCurrency;
    private Object accountManager;
    private Method getAccount;
    private Method getBalance;
    private Method deposit;
    private Method withdraw;
    private boolean available = false;

    public EconomyProviderGemsEconomy() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("GemsEconomy");
            if (plugin == null) return;

            Class<?> pluginClass = plugin.getClass();

            // GemsEconomy.getInstance().getCurrencyManager()
            Method getCurrMgr = findMethod(pluginClass, "getCurrencyManager");
            if (getCurrMgr == null) return;
            currencyManager = getCurrMgr.invoke(plugin);
            if (currencyManager == null) return;

            // getDefaultCurrency()
            Method getDefault = findMethod(currencyManager.getClass(), "getDefaultCurrency");
            if (getDefault == null) return;
            defaultCurrency = getDefault.invoke(currencyManager);
            if (defaultCurrency == null) return;

            // getAccountManager()
            Method getAccMgr = findMethod(pluginClass, "getAccountManager");
            if (getAccMgr == null) return;
            accountManager = getAccMgr.invoke(plugin);
            if (accountManager == null) return;

            getAccount = findMethod(accountManager.getClass(), "getAccount", UUID.class);

            // Account methods
            if (getAccount != null) {
                // Test call to get account class
                Class<?> accountClass = null;
                for (Method m : accountManager.getClass().getMethods()) {
                    if (m.getName().equals("getAccount") &&
                        m.getParameterTypes().length == 1 &&
                        m.getParameterTypes()[0] == UUID.class) {
                        accountClass = m.getReturnType();
                        break;
                    }
                }
                if (accountClass != null && !accountClass.equals(Object.class)) {
                    getBalance = findMethod(accountClass, "getBalance", defaultCurrency.getClass());
                    deposit    = findMethod(accountClass, "deposit",    defaultCurrency.getClass(), double.class);
                    withdraw   = findMethod(accountClass, "withdraw",   defaultCurrency.getClass(), double.class);
                }
            }

            available = (getAccount != null && getBalance != null);
        } catch (Exception e) {
            log.fine("[sCore] GemsEconomy não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "gemseconomy"; }
    @Override public boolean isAvailable() { return available; }

    private Object getAccount(Player player) {
        try { return getAccount.invoke(accountManager, player.getUniqueId()); }
        catch (Exception e) { return null; }
    }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object account = getAccount(player);
            if (account == null) return 0;
            Object result = getBalance.invoke(account, defaultCurrency);
            return result instanceof Number ? ((Number) result).doubleValue() : 0;
        } catch (Exception e) { return 0; }
    }

    @Override public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!available || player == null || this.withdraw == null) return false;
        try {
            Object account = getAccount(player);
            if (account == null) return false;
            this.withdraw.invoke(account, defaultCurrency, amount);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null || this.deposit == null) return false;
        try {
            Object account = getAccount(player);
            if (account == null) return false;
            this.deposit.invoke(account, defaultCurrency, amount);
            return true;
        } catch (Exception e) { return false; }
    }
}
