package br.com.skyy.core.events.suite;

import br.com.skyy.core.events.SCoreEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Disparado quando uma máquina do sMaquinas produz drops.
 *
 * Não é cancelável — a produção já aconteceu.
 * Use para logging, ranking, estatísticas.
 *
 * Disparado por: sMaquinas
 * Ouvido por:    sRanking (produção total), sLog (auditoria), sEconomia (bônus)
 *
 * Exemplo (sMaquinas):
 *   SCore.getEventBus().fire(
 *       new MachineProducedEvent(machineId, ownerUUID, location, drops)
 *   );
 */
public class MachineProducedEvent extends SCoreEvent {

    private final String     machineId;
    private final java.util.UUID ownerUUID;
    private final Location   location;
    private final List<ItemStack> drops;

    public MachineProducedEvent(String machineId, java.util.UUID ownerUUID,
                                Location location, List<ItemStack> drops) {
        this.machineId = machineId;
        this.ownerUUID = ownerUUID;
        this.location  = location;
        this.drops     = drops;
    }

    public String         getMachineId() { return machineId; }
    public java.util.UUID getOwnerUUID() { return ownerUUID; }
    public Location       getLocation()  { return location; }
    public List<ItemStack> getDrops()    { return drops; }
}
