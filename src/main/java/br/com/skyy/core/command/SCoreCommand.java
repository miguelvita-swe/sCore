package br.com.skyy.core.command;

import br.com.skyy.core.SCore;
import br.com.skyy.core.SCorePlugin;
import br.com.skyy.core.database.SCoreDatabase;
import br.com.skyy.core.database.SCoreDatabaseMySQL;
import br.com.skyy.core.database.SCoreDatabaseSQLite;
import br.com.skyy.core.providers.economy.EconomyManager;
import br.com.skyy.core.providers.economy.EconomyProviderCMI;
import br.com.skyy.core.providers.economy.EconomyProviderCoinsEngine;
import br.com.skyy.core.providers.economy.EconomyProviderEcoCredits;
import br.com.skyy.core.providers.economy.EconomyProviderEssentials;
import br.com.skyy.core.providers.economy.EconomyProviderGemsEconomy;
import br.com.skyy.core.providers.economy.EconomyProviderIConomy;
import br.com.skyy.core.providers.economy.EconomyProviderPlayerPoints;
import br.com.skyy.core.providers.economy.EconomyProviderTNE;
import br.com.skyy.core.providers.economy.EconomyProviderVault;
import br.com.skyy.core.providers.hologram.HologramProvider;
import br.com.skyy.core.providers.hologram.HologramProviderArmorStand;
import br.com.skyy.core.providers.hologram.HologramProviderDecent;
import br.com.skyy.core.providers.hologram.HologramProviderHD;
import br.com.skyy.core.utils.HologramTestUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Comando /score — administração do sCore em runtime.
 *
 * Subcomandos:
 *   /score reload           — recarrega config + reconnecta DB + re-detecta hologramas
 *   /score status           — exibe estado atual de todos os providers
 *   /score db               — informações detalhadas do pool de database
 *   /score economy          — lista todos os providers de economia e qual está ativo
 */
public class SCoreCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "score.admin";
    private static final String PREFIX = "§8[§6sCore§8] §r";

    private final SCorePlugin plugin;

    public SCoreCommand(SCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(PREFIX + "§cVocê não tem permissão.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "reload":   handleReload(sender);              break;
            case "status":   handleStatus(sender);              break;
            case "db":       handleDb(sender);                  break;
            case "economy":  handleEconomy(sender);             break;
            case "eventbus": handleEventBus(sender);            break;
            case "hologram": handleHologram(sender, args);      break;
            default:         handleHelp(sender);                break;
        }
        return true;
    }

    // ── Subcomandos ───────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eRecarregando sCore...");
        long start = System.currentTimeMillis();

        // 1. Recarrega o config.yml do disco
        plugin.reloadConfig();

        // 2. Fecha o DB atual e reabre (pode ter mudado SQLITE ↔ MYSQL)
        SCoreDatabase oldDb = plugin.getDatabase();
        if (oldDb != null) {
            try { oldDb.close(); } catch (Exception ignored) {}
        }
        String tipo = plugin.getConfig().getString("Database.Tipo", "SQLITE").toUpperCase();
        SCoreDatabase newDb = tipo.equals("MYSQL") ? new SCoreDatabaseMySQL() : new SCoreDatabaseSQLite();
        try {
            newDb.initialize(plugin);
            plugin.setDatabase(newDb);
            sender.sendMessage(PREFIX + "§aDatabase: §f" + newDb.getType() + " §areconectado.");
        } catch (Exception e) {
            sender.sendMessage(PREFIX + "§cErro ao reconectar database: " + e.getMessage());
            plugin.getLogger().severe("[sCore] Reload DB error: " + e.getMessage());
            // Mantém o banco anterior se falhar
            plugin.setDatabase(oldDb);
        }

        // 3. Re-detecta hologramas (pode ter instalado DH sem restart)
        HologramProvider oldHolo = plugin.getHologramProvider();
        if (oldHolo != null) {
            try { oldHolo.removeAll(); } catch (Exception ignored) {}
        }
        HologramProviderDecent decent = new HologramProviderDecent();
        if (decent.isAvailable()) {
            plugin.setHologramProvider(decent);
        } else {
            HologramProviderHD hd = new HologramProviderHD(plugin);
            if (hd.isAvailable()) {
                plugin.setHologramProvider(hd);
            } else {
                plugin.setHologramProvider(new HologramProviderArmorStand(plugin));
            }
        }
        sender.sendMessage(PREFIX + "§aHolograma: §f" + plugin.getHologramProvider().getProviderName());

        // 4. Re-registra providers de economia (providers lazy: isAvailable() será
        //    reavaliado na próxima chamada — não precisamos fazer mais nada)
        EconomyManager newEconomy = new EconomyManager();
        newEconomy.register(new EconomyProviderVault());
        newEconomy.register(new EconomyProviderCoinsEngine());
        newEconomy.register(new EconomyProviderGemsEconomy());
        newEconomy.register(new EconomyProviderTNE());
        newEconomy.register(new EconomyProviderCMI());
        newEconomy.register(new EconomyProviderEcoCredits());
        newEconomy.register(new EconomyProviderEssentials());
        newEconomy.register(new EconomyProviderPlayerPoints());
        newEconomy.register(new EconomyProviderIConomy());
        plugin.setEconomyManager(newEconomy);
        sender.sendMessage(PREFIX + "§aEconomia recarregada.");

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(PREFIX + "§a✓ Reload concluído em §f" + elapsed + "ms§a.");
    }

    private void handleStatus(CommandSender sender) {
        SCore.HealthReport report = SCore.healthCheck();

        sender.sendMessage(PREFIX + "§6=== sCore Status ===");
        sender.sendMessage("§7Plugin: §fsCore v" + plugin.getPluginMeta().getVersion()
                + " §7| API v§f" + SCore.API_VERSION
                + " §7| " + (report.isHealthy() ? "§aHEALTHY" : "§cUNHEALTHY"));

        // Exibe cada componente com ícone colorido
        for (SCore.HealthReport.Entry entry : report.getEntries()) {
            String color;
            String icon;
            switch (entry.getStatus()) {
                case OK:   color = "§a"; icon = "✓"; break;
                case WARN: color = "§e"; icon = "⚠"; break;
                default:   color = "§c"; icon = "✗"; break;
            }
            sender.sendMessage("  " + color + icon + " §7" + entry.getComponent()
                    + "§8: §f" + entry.getDetail());
        }

        // Falhas extras detalhadas
        if (!report.isHealthy()) {
            sender.sendMessage("§c  Falhas detectadas:");
            for (SCore.HealthReport.Entry f : report.getFailures()) {
                sender.sendMessage("§c  → " + f.getComponent() + ": " + f.getDetail());
            }
        }
    }

    private void handleDb(CommandSender sender) {
        SCoreDatabase db = plugin.getDatabase();
        sender.sendMessage(PREFIX + "§6=== Database Info ===");
        sender.sendMessage("§7Tipo:       §f" + db.getType());
        sender.sendMessage("§7Conectado:  " + (db.isConnected() ? "§atrue" : "§cfalse"));
        sender.sendMessage("§7Nota: pool de 4 conexões thread-safe.");
        sender.sendMessage("§7Uso correto: getConnection() + finally releaseConnection()");
    }

    private void handleEconomy(CommandSender sender) {
        sender.sendMessage(PREFIX + "§6=== Economy Providers ===");
        EconomyManager em = plugin.getEconomyManager();
        for (java.util.Map.Entry<String, br.com.skyy.core.providers.economy.EconomyProvider> e
                : em.getProviders().entrySet()) {
            boolean avail = e.getValue().isAvailable();
            sender.sendMessage("§7" + e.getKey() + ": " + (avail ? "§a✓ disponível" : "§8✗ não instalado"));
        }
        br.com.skyy.core.providers.economy.EconomyProvider def = em.get(null);
        sender.sendMessage("§7Default: §f" + (def != null ? def.getName() : "§cnone"));
    }

    private void handleHologram(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "list";
        switch (action) {
            case "test":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(PREFIX + "§cApenas jogadores podem testar hologramas.");
                    return;
                }
                Player player = (Player) sender;
                Location loc = player.getLocation().add(0, 1, 0);
                HologramTestUtil.testCurrent(plugin, sender, loc);
                break;
            case "list":
                HologramTestUtil.listAll(plugin, sender);
                break;
            case "switch":
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + "§cUso: /score hologram switch <decent|hd|armorstand|none>");
                    return;
                }
                HologramTestUtil.switchProvider(plugin, sender, args[2]);
                break;
            default:
                sender.sendMessage(PREFIX + "§7Subcomandos: list, test, switch <provider>");
        }
    }

    private void handleHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "§6Comandos disponíveis:");
        sender.sendMessage("§7/score reload          §f— recarrega config, DB e hologramas");
        sender.sendMessage("§7/score status          §f— estado de todos os providers");
        sender.sendMessage("§7/score db              §f— informações do pool de database");
        sender.sendMessage("§7/score economy         §f— providers de economia");
        sender.sendMessage("§7/score eventbus        §f— handlers do EventBus");
        sender.sendMessage("§7/score hologram list   §f— lista todos os providers de holograma");
        sender.sendMessage("§7/score hologram test   §f— cria holograma de teste (requer estar online)");
        sender.sendMessage("§7/score hologram switch §f— troca o provider ativo em runtime");
    }

    private void handleEventBus(CommandSender sender) {
        br.com.skyy.core.events.SCoreEventBus bus = plugin.getEventBus();
        sender.sendMessage(PREFIX + "§6=== EventBus Info ===");
        sender.sendMessage("§7" + bus.describe());
        sender.sendMessage("§7Handlers totais: §f" + bus.getHandlerCount());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("reload", "status", "db", "economy", "eventbus", "hologram");
        }
        if (args.length == 2 && "hologram".equalsIgnoreCase(args[0])) {
            return Arrays.asList("list", "test", "switch");
        }
        if (args.length == 3 && "hologram".equalsIgnoreCase(args[0])
                             && "switch".equalsIgnoreCase(args[1])) {
            return Arrays.asList("decent", "hd", "armorstand", "none");
        }
        return Collections.emptyList();
    }
}
