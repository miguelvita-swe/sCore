package br.com.skyy.core.providers.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Provedor de economia via Vault.
 *
 * Usa lazy initialization: o Economy do Vault é resolvido na primeira
 * chamada a isAvailable(), não no construtor.
 *
 * Isso corrige o problema de ordem de carregamento onde o sCore (STARTUP)
 * inicializava antes que qualquer plugin de economia (ex: sCoins) tivesse
 * se registrado no ServicesManager do Vault.
 */
public class EconomyProviderVault implements EconomyProvider {

    private static final Logger log = Logger.getLogger("sCore-Economy");
    private Economy economy;

    // Construtor vazio — não faz lookup aqui para evitar race condition de startup
    public EconomyProviderVault() {}

    @Override
    public String getName() { return "vault"; }

    /**
     * Lazy: tenta resolver o Economy do Vault na primeira chamada.
     * Se ainda não houver provider registrado, tenta novamente na próxima chamada.
     * Thread-safe via synchronized para evitar double-check duplicado.
     */
    @Override
    public synchronized boolean isAvailable() {
        if (economy != null) return true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (Exception e) {
            log.warning("[sCore] EconomyProviderVault lookup error: " + e.getMessage());
        }
        return economy != null;
    }

    @Override
    public double getBalance(Player player) {
        if (!isAvailable() || player == null) return 0;
        try { return economy.getBalance(player); } catch (Exception e) { return 0; }
    }

    @Override
    public boolean has(Player player, double amount) {
        if (!isAvailable() || player == null) return false;
        try { return economy.has(player, amount); } catch (Exception e) { return false; }
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!isAvailable() || player == null) return false;
        try { return economy.withdrawPlayer(player, amount).transactionSuccess(); }
        catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!isAvailable() || player == null) return false;
        try { return economy.depositPlayer(player, amount).transactionSuccess(); }
        catch (Exception e) { return false; }
    }
}
