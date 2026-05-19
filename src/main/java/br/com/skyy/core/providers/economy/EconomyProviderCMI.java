package br.com.skyy.core.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EconomyProvider para CMI Economy (com.zrips.cmi).
 *
 * CMI é um plugin de utilidades all-in-one muito popular em servidores 1.8–1.21 BR/internacional.
 * Possui sistema de economy próprio além de integração com Vault.
 * Este provider acessa a economy diretamente via CMIUser para evitar dependência do Vault.
 *
 * Classe alvo: com.zrips.cmi.CMI
 * User API: CMIUser → getBalance(), withdraw(), deposit()
 */
public class EconomyProviderCMI implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");

    private Object cmi;
    private Method getPlayerManager;
    private Method getUser;
    private Method getBalance;
    private Method withdraw;
    private Method deposit;
    private boolean available = false;

    public EconomyProviderCMI() {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("CMI");
            if (plugin == null) return;

            Class<?> cmiClass = plugin.getClass();
            cmi = plugin;

            // CMI.getInstance().getPlayerManager()
            getPlayerManager = findMethod(cmiClass, "getPlayerManager");
            if (getPlayerManager == null) return;

            Object playerManager = getPlayerManager.invoke(cmi);
            if (playerManager == null) return;

            // getUser(UUID) ou getUser(Player)
            getUser = findMethod(playerManager.getClass(), "getUser", UUID.class);
            if (getUser == null) getUser = findMethod(playerManager.getClass(), "getUser", Player.class);
            if (getUser == null) return;

            // Descobrir tipo de retorno de getUser para pegar métodos de economia
            Class<?> userClass = null;
            for (Method m : playerManager.getClass().getMethods()) {
                if (m.getName().equals("getUser") && m.getParameterTypes().length == 1) {
                    userClass = m.getReturnType();
                    break;
                }
            }
            if (userClass == null || userClass.equals(Object.class)) return;

            getBalance = findMethod(userClass, "getBalance");
            withdraw   = findMethod(userClass, "withdraw",  double.class);
            deposit    = findMethod(userClass, "deposit",   double.class);

            // CMI pode usar "take" ao invés de "withdraw"
            if (withdraw == null) withdraw = findMethod(userClass, "take", double.class);
            if (deposit  == null) deposit  = findMethod(userClass, "give", double.class);

            available = (getBalance != null && withdraw != null && deposit != null);
        } catch (Exception e) {
            log.fine("[sCore] CMI Economy não encontrado. Ignorado.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    @Override public String getName()      { return "cmi"; }
    @Override public boolean isAvailable() { return available; }

    private Object getUser(Player player) {
        try {
            Object pm = getPlayerManager.invoke(cmi);
            if (pm == null) return null;
            // tenta com UUID primeiro, depois com Player
            try { return getUser.invoke(pm, player.getUniqueId()); }
            catch (Exception e) { return getUser.invoke(pm, player); }
        } catch (Exception e) { return null; }
    }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object user = getUser(player);
            if (user == null) return 0;
            Object result = getBalance.invoke(user);
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
            Object user = getUser(player);
            if (user == null) return false;
            withdraw.invoke(user, amount);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object user = getUser(player);
            if (user == null) return false;
            deposit.invoke(user, amount);
            return true;
        } catch (Exception e) { return false; }
    }
}
