package br.com.skyy.core.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Listener Bukkit que remove automaticamente os handlers do EventBus
 * quando um plugin da suite é desabilitado.
 *
 * Isso evita vazamento de memória e chamadas a plugins descarregados.
 * Registrado pelo SCorePlugin no onEnable().
 */
public class SCoreEventBusCleanupListener implements Listener {

    private final SCoreEventBus eventBus;

    public SCoreEventBusCleanupListener(SCoreEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        eventBus.unsubscribeAll(event.getPlugin());
    }
}
