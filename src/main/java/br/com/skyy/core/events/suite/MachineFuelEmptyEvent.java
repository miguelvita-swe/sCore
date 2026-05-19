package br.com.skyy.core.events.suite;

import br.com.skyy.core.events.SCoreEvent;
import org.bukkit.entity.Player;

/**
 * Disparado quando uma máquina do sMaquinas fica sem combustível.
 *
 * Cancelável — handlers podem adicionar combustível programaticamente.
 *
 * Disparado por: sMaquinas
 * Ouvido por:    sEconomia (compra automática de combustível?), sistemas de alerta
 *
 * Exemplo de uso — reabastecimento automático:
 *   SCore.getEventBus().subscribe(MachineFuelEmptyEvent.class, Priority.HIGH, this, event -> {
 *       if (hasAutoRefuel(event.getMachineId())) {
 *           event.cancel(); // impede parada da máquina
 *           addFuel(event.getMachineId(), 64);
 *       }
 *   });
 */
public class MachineFuelEmptyEvent extends SCoreEvent implements SCoreEvent.Cancellable {

    private final String  machineId;
    private final Player  owner;
    private final String  machineType;

    public MachineFuelEmptyEvent(String machineId, Player owner, String machineType) {
        this.machineId   = machineId;
        this.owner       = owner;
        this.machineType = machineType;
    }

    public String getMachineId()   { return machineId; }
    public Player getOwner()       { return owner; }
    public String getMachineType() { return machineType; }
}
