package br.com.skyy.core.events;

/**
 * Classe base para todos os eventos internos do sCore.
 *
 * Eventos internos NÃO são Bukkit Events — eles trafegam apenas
 * entre plugins da suite s via {@link SCoreEventBus}, sem custo
 * do sistema de eventos do Bukkit e sem necessidade de Listener registrado.
 *
 * Para criar um evento customizado:
 *
 *   public class MachineProducedEvent extends SCoreEvent {
 *       private final String machineId;
 *       private final List<ItemStack> drops;
 *
 *       public MachineProducedEvent(String machineId, List<ItemStack> drops) {
 *           this.machineId = machineId;
 *           this.drops     = drops;
 *       }
 *
 *       public String getMachineId()      { return machineId; }
 *       public List<ItemStack> getDrops() { return drops; }
 *   }
 *
 * Para disparar:
 *   SCore.getEventBus().fire(new MachineProducedEvent(id, drops));
 *
 * Para ouvir:
 *   SCore.getEventBus().subscribe(MachineProducedEvent.class, event -> {
 *       logger.info("Máquina " + event.getMachineId() + " produziu " + event.getDrops().size() + " drops");
 *   });
 */
public abstract class SCoreEvent {

    private boolean cancelled = false;

    /**
     * Cancela o evento. Apenas {@link SCoreEvent}s que implementam
     * {@link Cancellable} devem usar este método — por convenção.
     *
     * O bus respeita o cancelamento: handlers com prioridade
     * {@link SCoreEventBus.Priority#MONITOR} ainda recebem o evento,
     * mas handlers normais são ignorados após o cancelamento.
     */
    public final void cancel() {
        this.cancelled = true;
    }

    /** Retorna true se o evento foi cancelado. */
    public final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Interface de marcação para eventos canceláveis.
     * Implemente junto com a classe de evento para indicar que o evento
     * pode ser cancelado por handlers.
     *
     * Convenção:
     *   public class EconomyChargeEvent extends SCoreEvent implements SCoreEvent.Cancellable { ... }
     */
    public interface Cancellable {}

    /** Retorna o nome simples da classe do evento para logs. */
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
