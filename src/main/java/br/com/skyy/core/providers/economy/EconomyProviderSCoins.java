package br.com.skyy.core.providers.economy;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Provider de economia da suite s — implementado pelo plugin sEconomia.
 *
 * Esta classe abstrata vive no sCore para que qualquer plugin da suite
 * (sMaquinas, sShop, etc.) possa usar a economia sCoins sem depender
 * diretamente do plugin sEconomia.
 *
 * ─── Como registrar (no onEnable() do sEconomia) ─────────────────────────
 *
 *   public class SCoinsProvider extends EconomyProviderSCoins {
 *       private final SCoinsManager manager;
 *
 *       public SCoinsProvider(SCoinsManager manager) {
 *           this.manager = manager;
 *       }
 *
 *       @Override public long getCoins(UUID uuid)              { return manager.getCoins(uuid); }
 *       @Override public void addCoins(UUID uuid, long amount) { manager.addCoins(uuid, amount); }
 *       @Override public void removeCoins(UUID uuid, long amt) { manager.removeCoins(uuid, amt); }
 *       @Override public boolean hasCoins(UUID uuid, long amt) { return manager.getCoins(uuid) >= amt; }
 *   }
 *
 *   // No onEnable() do sEconomia:
 *   SCore.getEconomy().register(new SCoinsProvider(sCoinsManager));
 *
 * ─── Como usar (em sMaquinas, sShop, etc.) ───────────────────────────────
 *
 *   // Verificar saldo
 *   SCore.getEconomy().has(player, 100.0, "scoins");
 *
 *   // Cobrar
 *   SCore.getEconomy().withdraw(player, 100.0, "scoins");
 *
 *   // Ou via EconomyChargeEvent para auditoria:
 *   EconomyChargeEvent e = SCore.getEventBus().fire(
 *       new EconomyChargeEvent(player, 100.0, "scoins", "compra de item", "sMaquinas")
 *   );
 *   if (!e.isCancelled()) SCore.getEconomy().withdraw(player, e.getAmount(), "scoins");
 */
public abstract class EconomyProviderSCoins implements EconomyProvider {

    @Override
    public final String getName() {
        return "scoins";
    }

    @Override
    public boolean isAvailable() {
        return true; // se está registrado, está disponível
    }

    // ── API da sCoins — implementar no sEconomia ──────────────────────────────

    /**
     * Retorna o saldo em sCoins do jogador.
     * @param uuid UUID do jogador (pode não estar online)
     */
    public abstract long getCoins(UUID uuid);

    /**
     * Adiciona sCoins ao jogador.
     * @param uuid   UUID do jogador
     * @param amount Quantidade a adicionar (positivo)
     */
    public abstract void addCoins(UUID uuid, long amount);

    /**
     * Remove sCoins do jogador.
     * @param uuid   UUID do jogador
     * @param amount Quantidade a remover (positivo)
     */
    public abstract void removeCoins(UUID uuid, long amount);

    /**
     * Verifica se o jogador tem saldo suficiente.
     * @param uuid   UUID do jogador
     * @param amount Quantia exigida
     */
    public abstract boolean hasCoins(UUID uuid, long amount);

    // ── EconomyProvider bridge — converte double→long ─────────────────────────
    //
    // sCoins opera com long (moeda inteira, sem decimais).
    // EconomyProvider opera com double (compatível com Vault/PlayerPoints).
    // A conversão trunca a parte decimal: 10.9 → 10 coins.

    @Override
    public double getBalance(Player player) {
        if (player == null) return 0;
        return getCoins(player.getUniqueId());
    }

    @Override
    public boolean has(Player player, double amount) {
        if (player == null) return false;
        return hasCoins(player.getUniqueId(), (long) amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (player == null || amount <= 0) return false;
        long coins = (long) amount;
        if (!hasCoins(player.getUniqueId(), coins)) return false;
        removeCoins(player.getUniqueId(), coins);
        return true;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (player == null || amount <= 0) return false;
        addCoins(player.getUniqueId(), (long) amount);
        return true;
    }
}
