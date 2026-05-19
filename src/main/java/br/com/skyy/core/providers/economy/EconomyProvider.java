package br.com.skyy.core.providers.economy;

import org.bukkit.entity.Player;

public interface EconomyProvider {
    String getName();
    boolean isAvailable();
    double getBalance(Player player);
    boolean has(Player player, double amount);
    boolean withdraw(Player player, double amount);
    boolean deposit(Player player, double amount);
}
