package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * EconomyProvider para EssentialsX (direto, sem Vault).
 *
 * Útil como fallback quando Vault está presente mas nenhum economy provider
 * foi registrado nele ainda. EssentialsX é o plugin de economia mais comum
 * em servidores 1.8–1.21.
 *
 * Classe alvo: com.earth2me.essentials.Essentials
 * User API:    com.earth2me.essentials.User
 */
public class EconomyProviderEssentials implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object essentials;
    private Method getUser;
    private Method getMoney;
    private Method setMoney;
    private Method giveMoney;
    private Method takeMoney;
    private boolean available = false;

    public EconomyProviderEssentials() {
        try {
            Class<?> essClass = Class.forName("com.earth2me.essentials.Essentials");
            essentials = Bukkit.getPluginManager().getPlugin("Essentials");
            if (essentials == null) {
                // tenta EssentialsX com nome alternativo
                essentials = Bukkit.getPluginManager().getPlugin("EssentialsX");
            }
            if (essentials == null || !essClass.isInstance(essentials)) return;

            getUser = essClass.getMethod("getUser", Player.class);

            Class<?> userClass = Class.forName("com.earth2me.essentials.User");
            getMoney  = findMethod(userClass, "getMoney");
            setMoney  = findMethod(userClass, "setMoney", BigDecimal.class);
            giveMoney = findMethod(userClass, "giveMoney", BigDecimal.class);
            takeMoney = findMethod(userClass, "takeMoney", BigDecimal.class);

            available = (getUser != null && getMoney != null);
        } catch (Exception e) {
            log.fine("[sCore] EssentialsX não encontrado (direto). Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "essentials"; }
    @Override public boolean isAvailable() { return available; }

    private Object getUser(Player player) {
        try { return getUser.invoke(essentials, player); }
        catch (Exception e) { return null; }
    }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object user = getUser(player);
            if (user == null) return 0;
            Object result = getMoney.invoke(user);
            if (result instanceof BigDecimal) return ((BigDecimal) result).doubleValue();
            if (result instanceof Number)    return ((Number) result).doubleValue();
            return 0;
        } catch (Exception e) { return 0; }
    }

    @Override
    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object user = getUser(player);
            if (user == null) return false;
            BigDecimal bd = BigDecimal.valueOf(amount);
            if (takeMoney != null) {
                takeMoney.invoke(user, bd);
            } else if (setMoney != null) {
                double current = getBalance(player);
                setMoney.invoke(user, BigDecimal.valueOf(current - amount));
            } else return false;
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object user = getUser(player);
            if (user == null) return false;
            BigDecimal bd = BigDecimal.valueOf(amount);
            if (giveMoney != null) {
                giveMoney.invoke(user, bd);
            } else if (setMoney != null) {
                double current = getBalance(player);
                setMoney.invoke(user, BigDecimal.valueOf(current + amount));
            } else return false;
            return true;
        } catch (Exception e) { return false; }
    }
}
