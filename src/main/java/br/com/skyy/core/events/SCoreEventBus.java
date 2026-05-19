package br.com.skyy.core.events;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Bus de eventos interno da suite s.
 *
 * Permite que plugins da suite se comuniquem sem acoplamento direto:
 *   sMaquinas dispara → sEconomia / sRanking / sLog ouvem
 *
 * Características:
 *   - Thread-safe: ConcurrentHashMap + CopyOnWriteArrayList
 *   - Prioridades: LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR
 *   - Cancelamento: handlers NORMAL a HIGHEST são pulados se evento cancelado
 *   - MONITOR: sempre recebe, mesmo cancelado — para logs/auditoria
 *   - Unsubscribe por token retornado no subscribe()
 *   - Plugin owner: handlers são removidos automaticamente no disable do plugin
 *   - Fire assíncrono: fireAsync() executa handlers em thread separada
 *
 * ─── Exemplo completo ────────────────────────────────────────────────────────
 *
 * // Plugin A (sMaquinas) — disparar evento
 * SCore.getEventBus().fire(new MachineProducedEvent(machineId, drops));
 *
 * // Plugin B (sEconomia) — ouvir evento, prioridade HIGH
 * String token = SCore.getEventBus().subscribe(
 *     MachineProducedEvent.class,
 *     SCoreEventBus.Priority.HIGH,
 *     this,   // plugin owner
 *     event -> {
 *         SCore.getEconomy().deposit(owner, calculateValue(event.getDrops()), "vault");
 *     }
 * );
 *
 * // Plugin B onDisable — remover subscription manualmente (ou deixar o bus limpar)
 * SCore.getEventBus().unsubscribeAll(this);
 */
public final class SCoreEventBus {

    private static final Logger log = Logger.getLogger("sCore-EventBus");

    /**
     * Prioridades de execução dos handlers.
     * Handlers são executados em ordem crescente de ordinal (LOWEST primeiro).
     * MONITOR é especial: sempre executa, mesmo com evento cancelado.
     */
    public enum Priority {
        /** Primeiro a executar — raramente usado */
        LOWEST,
        /** Abaixo do normal */
        LOW,
        /** Padrão para a maioria dos handlers */
        NORMAL,
        /** Acima do normal — economia, regras de negócio */
        HIGH,
        /** Último a modificar o evento antes do MONITOR */
        HIGHEST,
        /**
         * Sempre executa, mesmo se o evento foi cancelado.
         * Use apenas para logs, auditoria e observabilidade.
         * Nunca modifique o estado do evento aqui.
         */
        MONITOR
    }

    /** Functional interface do handler — Java 8 compatible */
    public interface EventHandler<T extends SCoreEvent> {
        void handle(T event);
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /** Registro de um handler com seus metadados */
    private static final class HandlerEntry<T extends SCoreEvent> {
        final String        token;
        final Priority      priority;
        final Plugin        owner;    // null = sem owner (não será auto-removido)
        final EventHandler<T> handler;

        HandlerEntry(String token, Priority priority, Plugin owner, EventHandler<T> handler) {
            this.token    = token;
            this.priority = priority;
            this.owner    = owner;
            this.handler  = handler;
        }
    }

    /**
     * Map: Class do evento → lista de HandlerEntry (qualquer tipo).
     * CopyOnWriteArrayList garante que iterações durante fire() são seguras
     * mesmo que outro thread adicione/remova handlers simultaneamente.
     */
    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends SCoreEvent>, CopyOnWriteArrayList<HandlerEntry>>
        registry = new ConcurrentHashMap<>();

    // ── Subscribe ─────────────────────────────────────────────────────────

    /**
     * Registra um handler com prioridade NORMAL, sem plugin owner.
     * Retorna um token para unsubscribe manual.
     */
    public <T extends SCoreEvent> String subscribe(Class<T> eventClass,
                                                   EventHandler<T> handler) {
        return subscribe(eventClass, Priority.NORMAL, null, handler);
    }

    /**
     * Registra um handler com prioridade NORMAL e plugin owner.
     * O handler é removido automaticamente quando o plugin é desabilitado
     * via {@link #unsubscribeAll(Plugin)}.
     */
    public <T extends SCoreEvent> String subscribe(Class<T> eventClass,
                                                   Plugin owner,
                                                   EventHandler<T> handler) {
        return subscribe(eventClass, Priority.NORMAL, owner, handler);
    }

    /**
     * Registra um handler com prioridade e plugin owner explícitos.
     * Retorna token UUID para unsubscribe manual com {@link #unsubscribe(String)}.
     */
    public <T extends SCoreEvent> String subscribe(Class<T> eventClass,
                                                   Priority priority,
                                                   Plugin owner,
                                                   EventHandler<T> handler) {
        if (eventClass == null || handler == null) {
            throw new IllegalArgumentException("[sCore-EventBus] eventClass e handler não podem ser null");
        }

        String token = UUID.randomUUID().toString();
        HandlerEntry<T> entry = new HandlerEntry<>(
            token,
            priority != null ? priority : Priority.NORMAL,
            owner,
            handler
        );

        registry
            .computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
            .add(entry);

        return token;
    }

    // ── Unsubscribe ───────────────────────────────────────────────────────

    /**
     * Remove o handler identificado pelo token retornado no subscribe().
     * Thread-safe — pode ser chamado de qualquer thread.
     */
    public void unsubscribe(String token) {
        if (token == null) return;
        for (CopyOnWriteArrayList<HandlerEntry> list : registry.values()) {
            list.removeIf(entry -> token.equals(entry.token));
        }
    }

    /**
     * Remove TODOS os handlers registrados pelo plugin informado.
     * Deve ser chamado no onDisable() do plugin para evitar vazamento de memória.
     *
     * O SCorePlugin chama isso automaticamente via PluginDisableEvent,
     * mas chamar manualmente no onDisable() é uma boa prática.
     */
    public void unsubscribeAll(Plugin plugin) {
        if (plugin == null) return;
        int removed = 0;
        for (CopyOnWriteArrayList<HandlerEntry> list : registry.values()) {
            int before = list.size();
            list.removeIf(entry -> plugin.equals(entry.owner));
            removed += before - list.size();
        }
        if (removed > 0) {
            log.fine("[sCore-EventBus] " + removed + " handler(s) removidos para " + plugin.getName());
        }
    }

    /**
     * Remove todos os handlers de todos os plugins.
     * Chamado no onDisable() do sCore.
     */
    public void unsubscribeAll() {
        registry.clear();
        log.fine("[sCore-EventBus] Todos os handlers removidos.");
    }

    // ── Fire ──────────────────────────────────────────────────────────────

    /**
     * Dispara um evento sincronamente na thread atual.
     *
     * Ordem de execução: LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR
     * Se o evento for cancelado, handlers de prioridade < MONITOR são pulados
     * a partir do ponto do cancelamento. MONITOR sempre executa.
     *
     * Retorna o evento após processamento (pode estar cancelado).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends SCoreEvent> T fire(T event) {
        if (event == null) return null;

        CopyOnWriteArrayList<HandlerEntry> entries = registry.get(event.getClass());
        if (entries == null || entries.isEmpty()) return event;

        // Copia e ordena por prioridade — CopyOnWriteArrayList é imutável na iteração
        List<HandlerEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(e -> e.priority.ordinal()));

        for (HandlerEntry entry : sorted) {
            // MONITOR sempre executa; demais são pulados se cancelado
            if (event.isCancelled() && entry.priority != Priority.MONITOR) continue;

            try {
                entry.handler.handle(event);
            } catch (Exception ex) {
                String owner = entry.owner != null ? entry.owner.getName() : "unknown";
                log.warning("[sCore-EventBus] Erro no handler de " + owner
                    + " para " + event.getClass().getSimpleName()
                    + ": " + ex.getMessage());
            }
        }

        return event;
    }

    /**
     * Dispara o evento em uma thread separada (assíncrono).
     * Use quando o handler faz operações pesadas (I/O, banco de dados)
     * e você não quer bloquear a thread do servidor.
     *
     * ATENÇÃO: handlers assíncronos NÃO devem acessar a API do Bukkit diretamente.
     * Use Bukkit.getScheduler().runTask() dentro do handler para voltar à thread principal.
     */
    public <T extends SCoreEvent> void fireAsync(final T event) {
        if (event == null) return;
        Thread t = new Thread(() -> fire(event), "sCore-EventBus-async");
        t.setDaemon(true);
        t.start();
    }

    // ── Diagnóstico ───────────────────────────────────────────────────────

    /**
     * Retorna o número total de handlers registrados em todos os eventos.
     */
    public int getHandlerCount() {
        int total = 0;
        for (CopyOnWriteArrayList<HandlerEntry> list : registry.values()) {
            total += list.size();
        }
        return total;
    }

    /**
     * Retorna o número de handlers registrados para um tipo de evento específico.
     */
    public int getHandlerCount(Class<? extends SCoreEvent> eventClass) {
        CopyOnWriteArrayList<HandlerEntry> list = registry.get(eventClass);
        return list != null ? list.size() : 0;
    }

    /**
     * Retorna string de diagnóstico para /score status.
     * Exemplo: "EventBus: 3 tipos de evento, 7 handlers registrados"
     */
    public String describe() {
        return "EventBus: " + registry.size() + " tipo(s) de evento, "
            + getHandlerCount() + " handler(s) registrado(s)";
    }
}
