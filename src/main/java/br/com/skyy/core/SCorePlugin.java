package br.com.skyy.core;

import br.com.skyy.core.command.SCoreCommand;
import br.com.skyy.core.database.SCoreDatabase;
import br.com.skyy.core.database.SCoreDatabaseMySQL;
import br.com.skyy.core.database.SCoreDatabaseSQLite;
import br.com.skyy.core.events.SCoreEventBus;
import br.com.skyy.core.events.SCoreEventBusCleanupListener;
import br.com.skyy.core.menu.SCoreMenuListener;
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
import br.com.skyy.core.providers.hologram.HologramProviderNone;
import br.com.skyy.core.providers.npc.NPCManager;
import br.com.skyy.core.providers.material.MaterialProvider;
import br.com.skyy.core.providers.material.MaterialProviderLegacy;
import br.com.skyy.core.providers.material.MaterialProviderModern;
import br.com.skyy.core.providers.nbt.NBTProvider;
import br.com.skyy.core.providers.nbt.NBTProviderLegacy;
import br.com.skyy.core.providers.nbt.NBTProviderPDC;
import br.com.skyy.core.providers.skull.SkullProvider;
import br.com.skyy.core.providers.skull.SkullProviderLegacy;
import br.com.skyy.core.providers.skull.SkullProviderModern;
import br.com.skyy.core.version.ServerVersion;
import org.bukkit.plugin.java.JavaPlugin;

public class SCorePlugin extends JavaPlugin {

    private static SCorePlugin instance;

    private ServerVersion serverVersion;
    private NBTProvider nbtProvider;
    private SkullProvider skullProvider;
    private MaterialProvider materialProvider;
    private HologramProvider hologramProvider;
    private EconomyManager economyManager;
    private SCoreDatabase database;
    private NPCManager npcManager;
    private SCoreEventBus eventBus;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        instance = this;

        serverVersion = ServerVersion.detect();

        nbtProvider = serverVersion.hasPDC()
                ? new NBTProviderPDC(this)
                : new NBTProviderLegacy(serverVersion);

        materialProvider = serverVersion.isLegacy()
                ? new MaterialProviderLegacy()
                : new MaterialProviderModern();

        skullProvider = serverVersion.hasModernSkull()
                ? new SkullProviderModern()
                : new SkullProviderLegacy();

        SCore.init(this);

        // EventBus inicializado no onLoad para que plugins dependentes
        // possam fazer subscribe logo no início do seu onEnable()
        eventBus = new SCoreEventBus();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        setupHolograms();
        economyManager = new EconomyManager();
        // Registra todos os providers imediatamente — sem delay.
        // Vault e PlayerPoints usam lazy init: só resolvem o provider real
        // na primeira chamada a isAvailable(), então não há race condition.
        setupEconomy();
        npcManager = new NPCManager(this, skullProvider);
        getServer().getPluginManager().registerEvents(new SCoreMenuListener(), this);
        getServer().getPluginManager().registerEvents(new SCoreEventBusCleanupListener(eventBus), this);

        // Registrar comando /score
        SCoreCommand scoreCmd = new SCoreCommand(this);
        getCommand("score").setExecutor(scoreCmd);
        getCommand("score").setTabCompleter(scoreCmd);

        getLogger().info("sCore v" + getDescription().getVersion()
                + "  MC: " + serverVersion.name()
                + "  NBT: " + (serverVersion.hasPDC() ? "PDC" : "Legacy NMS")
                + "  DB: " + database.getType()
                + "  Holograma: " + hologramProvider.getProviderName()
                + "  API: v" + SCore.API_VERSION);

        // Health check — loga warnings/falhas detectadas na inicialização
        SCore.HealthReport report = SCore.healthCheck();
        if (!report.isHealthy()) {
            getLogger().severe("=== sCore HEALTH CHECK: UNHEALTHY ===");
            for (SCore.HealthReport.Entry entry : report.getFailures()) {
                getLogger().severe("  [✗] " + entry.getComponent() + ": " + entry.getDetail());
            }
        } else if (report.hasWarnings()) {
            for (SCore.HealthReport.Entry entry : report.getEntries()) {
                if (entry.getStatus() == SCore.HealthReport.Status.WARN) {
                    getLogger().warning("  [⚠] " + entry.getComponent() + ": " + entry.getDetail());
                }
            }
        } else {
            getLogger().info("Health check: HEALTHY ✓");
        }
    }

    @Override
    public void onDisable() {
        if (npcManager != null) npcManager.removeAll();
        if (hologramProvider != null) hologramProvider.removeAll();
        if (database != null) database.close();
        if (eventBus != null) eventBus.unsubscribeAll();
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void setupDatabase() {
        String tipo = getConfig().getString("Database.Tipo", "SQLITE").toUpperCase();
        database = tipo.equals("MYSQL") ? new SCoreDatabaseMySQL() : new SCoreDatabaseSQLite();
        database.initialize(this);
        getLogger().info("Database: " + database.getType() + " conectado.");
    }

    private void setupEconomy() {
        // Registra todos os providers conhecidos.
        // Cada um usa lazy init / reflection — não falha se o plugin não estiver instalado.
        economyManager.register(new EconomyProviderVault());
        economyManager.register(new EconomyProviderCoinsEngine());
        economyManager.register(new EconomyProviderGemsEconomy());
        economyManager.register(new EconomyProviderTNE());
        economyManager.register(new EconomyProviderCMI());
        economyManager.register(new EconomyProviderEcoCredits());
        economyManager.register(new EconomyProviderEssentials());
        economyManager.register(new EconomyProviderPlayerPoints());
        economyManager.register(new EconomyProviderIConomy());
        // "scoins" será registrado pelo plugin sEconomia quando instalado:
        //   SCore.getEconomy().register(new EconomyProviderSCoins(...));
        getLogger().info(economyManager.describe());
    }

    private void setupHolograms() {
        HologramProviderDecent decent = new HologramProviderDecent();
        if (decent.isAvailable()) { hologramProvider = decent; return; }

        HologramProviderHD hd = new HologramProviderHD(this);
        if (hd.isAvailable()) { hologramProvider = hd; return; }

        hologramProvider = new HologramProviderArmorStand(this);
    }

    // ── Setters para reload em runtime ────────────────────────────────────────

    public void setDatabase(SCoreDatabase database)           { this.database = database; }
    public void setHologramProvider(HologramProvider holo)   { this.hologramProvider = holo; }
    public void setEconomyManager(EconomyManager economy)     { this.economyManager = economy; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static SCorePlugin getInstance()          { return instance; }
    public ServerVersion getServerVersion()          { return serverVersion; }
    public NBTProvider getNBTProvider()              { return nbtProvider; }
    public SkullProvider getSkullProvider()          { return skullProvider; }
    public MaterialProvider getMaterialProvider()    { return materialProvider; }
    public HologramProvider getHologramProvider()    { return hologramProvider; }
    public EconomyManager getEconomyManager()        { return economyManager; }
    public SCoreDatabase getDatabase()               { return database; }
    public NPCManager getNPCManager()                { return npcManager; }
    public SCoreEventBus getEventBus()               { return eventBus; }
}
