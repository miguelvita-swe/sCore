package br.com.skyy.core.providers.economy;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * EconomyProvider for PlayerPoints via reflection.
 */
public class EconomyProviderPlayerPoints implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");
    private Object api;
    private Method look, take, give;
    private boolean available = false;

    public EconomyProviderPlayerPoints() {
        try {
            Class<?> ppClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object instance = ppClass.getMethod("getInstance").invoke(null);
            api = ppClass.getMethod("getAPI").invoke(instance);

            Class<?> apiClass = api.getClass();
            look = findMethod(apiClass, "look",  java.util.UUID.class);
            take = findMethod(apiClass, "take",  java.util.UUID.class, int.class);
            give = findMethod(apiClass, "give",  java.util.UUID.class, int.class);

            available = (look != null && take != null && give != null);
        } catch (Exception e) {
            log.info("[sCore] PlayerPoints not found, EconomyProviderPlayerPoints disabled.");
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override public String getName() { return "PlayerPoints"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public double getBalance(Player player) {
        if (!available || player == null) return 0;
        try {
            Object result = look.invoke(api, player.getUniqueId());
            return result instanceof Number ? ((Number) result).doubleValue() : 0;
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
            Object result = take.invoke(api, player.getUniqueId(), (int) amount);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available || player == null) return false;
        try {
            Object result = give.invoke(api, player.getUniqueId(), (int) amount);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) { return false; }
    }
}
