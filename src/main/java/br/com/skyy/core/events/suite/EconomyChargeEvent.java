package br.com.skyy.core.events.suite;

import br.com.skyy.core.events.SCoreEvent;
import org.bukkit.entity.Player;

/**
 * Disparado quando um plugin da suite cobra uma quantia de um jogador.
 *
 * Implementa {@link Cancellable} — cancelar impede a cobrança.
 *
 * Disparado por: sMaquinas (combustível), sShop (compra), etc.
 * Ouvido por:    sEconomia (log de transações), sRanking (ranking de gastos)
 *
 * Exemplo (sMaquinas):
 *   EconomyChargeEvent event = SCore.getEventBus().fire(
 *       new EconomyChargeEvent(player, 100.0, "vault", "combustível: carvão")
 *   );
 *   if (event.isCancelled()) return; // cobrança vetada
 *   SCore.getEconomy().withdraw(player, event.getAmount(), event.getProvider());
 */
public class EconomyChargeEvent extends SCoreEvent implements SCoreEvent.Cancellable {

    private final Player player;
    private       double amount;
    private final String provider;
    private final String reason;
    private final String sourcePlugin;

    /**
     * @param player       Jogador sendo cobrado
     * @param amount       Quantia a cobrar (positivo)
     * @param provider     Nome do provider de economia (ex: "vault", "playerpoints")
     * @param reason       Motivo legível (ex: "combustível: carvão x3")
     * @param sourcePlugin Nome do plugin que disparou (ex: "sMaquinas")
     */
    public EconomyChargeEvent(Player player, double amount, String provider,
                              String reason, String sourcePlugin) {
        this.player       = player;
        this.amount       = amount;
        this.provider     = provider;
        this.reason       = reason;
        this.sourcePlugin = sourcePlugin;
    }

    public Player getPlayer()       { return player; }
    public double getAmount()       { return amount; }
    public String getProvider()     { return provider; }
    public String getReason()       { return reason; }
    public String getSourcePlugin() { return sourcePlugin; }

    /**
     * Permite que um handler modifique a quantia cobrada.
     * Ex: sEconomia pode aplicar desconto para VIPs.
     */
    public void setAmount(double amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount não pode ser negativo");
        this.amount = amount;
    }
}
