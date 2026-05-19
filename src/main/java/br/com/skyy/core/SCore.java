package br.com.skyy.core;

import br.com.skyy.core.database.SCoreDatabase;
import br.com.skyy.core.events.SCoreEventBus;
import br.com.skyy.core.providers.economy.EconomyManager;
import br.com.skyy.core.providers.hologram.HologramProvider;
import br.com.skyy.core.providers.material.MaterialProvider;
import br.com.skyy.core.providers.nbt.NBTProvider;
import br.com.skyy.core.providers.skull.SkullProvider;
import br.com.skyy.core.providers.npc.NPCManager;
import br.com.skyy.core.version.ServerVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API estática pública do sCore.
 *
 * Uso básico: SCore.getNBT(), SCore.getMaterial(), etc.
 *
 * Verificação de saúde para plugins dependentes:
 *   // No onEnable() do seu plugin:
 *   SCore.requireApiVersion(1, this);
 *   SCore.HealthReport report = SCore.healthCheck();
 *   if (!report.isHealthy()) {
 *       getLogger().severe(report.getSummary());
 *       getServer().getPluginManager().disablePlugin(this);
 *       return;
 *   }
 */
public final class SCore {

    // ── Versionamento Semântico da API ───────────────────────────────────────
    //
    //  MAJOR — breaking change: remoção/renomeação de método, mudança de assinatura
    //          Ex: NBTProvider.setString() muda para setString(Plugin, item, key, value)
    //          → plugins precisam ser atualizados obrigatoriamente
    //
    //  MINOR — adição backward-compatible: novo método/provider/utilitário
    //          Ex: SCore.getScheduler() adicionado
    //          → plugins antigos continuam funcionando sem modificação
    //
    //  PATCH — correção interna: bug fix, melhora de performance, sem mudança de API
    //          Ex: pool SQLite corrigido
    //          → transparente para plugins dependentes
    //
    //  Regra de incremento:
    //    PATCH bump → PATCH++
    //    MINOR bump → MINOR++, PATCH = 0
    //    MAJOR bump → MAJOR++, MINOR = 0, PATCH = 0

    /** Breaking changes — plugins DEVEM ser atualizados */
    public static final int API_MAJOR = 1;

    /** Adições backward-compatible — plugins antigos continuam funcionando */
    public static final int API_MINOR = 0;

    /** Correções internas — transparente para plugins dependentes */
    public static final int API_PATCH = 0;

    /**
     * Versão completa como string: "MAJOR.MINOR.PATCH"
     * Exemplo: "1.2.0"
     */
    public static final String API_VERSION_STRING = API_MAJOR + "." + API_MINOR + "." + API_PATCH;

    /**
     * @deprecated Use {@link #API_MAJOR} para verificar breaking changes.
     * Mantido para compatibilidade com código que usa SCore.API_VERSION diretamente.
     */
    @Deprecated
    public static final int API_VERSION = API_MAJOR;

    private static SCorePlugin instance;

    private SCore() {}

    static void init(SCorePlugin plugin) {
        instance = plugin;
    }

    // ── Verificação de saúde ──────────────────────────────────────────────────

    /**
     * Retorna a instância do plugin com verificação de null.
     * Lança IllegalStateException com mensagem CLARA e ACIONÁVEL
     * ao invés de NullPointerException genérico.
     */
    private static SCorePlugin get() {
        if (instance == null) {
            throw new IllegalStateException(
                "[sCore] API não inicializada! " +
                "Certifique-se de que:\n" +
                "  1. O sCore.jar está instalado na pasta plugins/\n" +
                "  2. O plugin.yml do seu plugin declara 'depend: [sCore]'\n" +
                "  3. O sCore carregou sem erros (verifique o console)");
        }
        return instance;
    }

    /**
     * Verifica se a API está inicializada sem lançar exception.
     * Útil para checks opcionais ou soft-depend.
     *
     * Exemplo:
     *   if (!SCore.isReady()) return; // sCore não está disponível
     */
    public static boolean isReady() {
        return instance != null;
    }

    /**
     * Executa uma verificação completa de saúde de todos os providers.
     * Retorna um {@link HealthReport} com o status de cada componente.
     *
     * Uso recomendado no onEnable() de plugins dependentes:
     *   SCore.HealthReport report = SCore.healthCheck();
     *   if (!report.isHealthy()) {
     *       getLogger().severe(report.getSummary());
     *       getServer().getPluginManager().disablePlugin(this);
     *       return;
     *   }
     */
    public static HealthReport healthCheck() {
        HealthReport report = new HealthReport();

        // 1. Verificar inicialização básica
        if (instance == null) {
            report.fail("core", "sCore não inicializado — verifique depend: [sCore] no plugin.yml");
            return report; // sem instância, não há nada mais a checar
        }

        // 2. ServerVersion
        try {
            ServerVersion v = ServerVersion.getCurrent();
            if (v == null || v == ServerVersion.UNKNOWN) {
                report.warn("version", "Versão do servidor não reconhecida: " + v);
            } else {
                report.ok("version", v.name());
            }
        } catch (Exception e) {
            report.fail("version", e.getMessage());
        }

        // 3. NBTProvider
        try {
            NBTProvider nbt = instance.getNBTProvider();
            if (nbt == null) {
                report.fail("nbt", "NBTProvider é null — falha no onLoad()");
            } else {
                report.ok("nbt", nbt.getClass().getSimpleName());
            }
        } catch (Exception e) {
            report.fail("nbt", e.getMessage());
        }

        // 4. MaterialProvider
        try {
            MaterialProvider mat = instance.getMaterialProvider();
            if (mat == null) {
                report.fail("material", "MaterialProvider é null");
            } else {
                report.ok("material", mat.getClass().getSimpleName());
            }
        } catch (Exception e) {
            report.fail("material", e.getMessage());
        }

        // 5. SkullProvider
        try {
            SkullProvider skull = instance.getSkullProvider();
            if (skull == null) {
                report.fail("skull", "SkullProvider é null");
            } else {
                report.ok("skull", skull.getClass().getSimpleName());
            }
        } catch (Exception e) {
            report.fail("skull", e.getMessage());
        }

        // 6. HologramProvider (não crítico — pode ser HologramProviderNone)
        try {
            HologramProvider holo = instance.getHologramProvider();
            if (holo == null) {
                report.fail("hologram", "HologramProvider é null");
            } else {
                report.ok("hologram", holo.getProviderName());
            }
        } catch (Exception e) {
            report.warn("hologram", e.getMessage());
        }

        // 7. Database
        try {
            SCoreDatabase db = instance.getDatabase();
            if (db == null) {
                report.fail("database", "SCoreDatabase é null — falha no onEnable()");
            } else if (!db.isConnected()) {
                report.fail("database", db.getType() + " não está conectado");
            } else {
                report.ok("database", db.getType() + " conectado");
            }
        } catch (Exception e) {
            report.fail("database", e.getMessage());
        }

        // 8. EconomyManager (não crítico — pode não ter nenhum economy plugin)
        try {
            EconomyManager em = instance.getEconomyManager();
            if (em == null) {
                report.warn("economy", "EconomyManager é null");
            } else {
                List<String> available = em.listAvailable();
                if (available.isEmpty()) {
                    report.warn("economy", "Nenhum provider de economia disponível");
                } else {
                    report.ok("economy", available.toString());
                }
            }
        } catch (Exception e) {
            report.warn("economy", e.getMessage());
        }

        // 9. EventBus
        try {
            SCoreEventBus bus = instance.getEventBus();
            if (bus == null) {
                report.fail("eventbus", "SCoreEventBus é null — falha no onLoad()");
            } else {
                report.ok("eventbus", bus.describe());
            }
        } catch (Exception e) {
            report.fail("eventbus", e.getMessage());
        }

        return report;
    }

    /**
     * Verifica compatibilidade de API no onEnable() do plugin dependente.
     *
     * Verifica apenas MAJOR — garante que não houve breaking changes.
     * Uso mais comum para a maioria dos plugins da suite.
     *
     * Exemplo:
     *   SCore.requireApiVersion(1, this); // requer MAJOR >= 1
     */
    public static void requireApiVersion(int minMajor, org.bukkit.plugin.Plugin caller) {
        requireApiVersion(minMajor, 0, 0, caller);
    }

    /**
     * Verifica compatibilidade de API exigindo MAJOR e MINOR mínimos.
     * Útil quando o plugin usa um recurso adicionado em uma MINOR específica.
     *
     * Exemplo:
     *   SCore.requireApiVersion(1, 2, this); // requer >= 1.2.x
     */
    public static void requireApiVersion(int minMajor, int minMinor, org.bukkit.plugin.Plugin caller) {
        requireApiVersion(minMajor, minMinor, 0, caller);
    }

    /**
     * Verifica compatibilidade de API com precisão total: MAJOR.MINOR.PATCH.
     *
     * Exemplo:
     *   SCore.requireApiVersion(1, 2, 3, this); // requer >= 1.2.3
     */
    public static void requireApiVersion(int minMajor, int minMinor, int minPatch,
                                         org.bukkit.plugin.Plugin caller) {
        if (!isReady()) {
            throw new IllegalStateException(
                "[sCore] O plugin '" + caller.getName() + "' tentou verificar a API " +
                "mas o sCore não está inicializado. Declare 'depend: [sCore]' no plugin.yml.");
        }

        boolean compatible = compareVersion(API_MAJOR, API_MINOR, API_PATCH,
                                            minMajor,  minMinor,  minPatch) >= 0;
        if (!compatible) {
            String required  = minMajor + "." + minMinor + "." + minPatch;
            throw new IllegalStateException(
                "[sCore] O plugin '" + caller.getName() + "' requer sCore API v" + required +
                " mas a versão instalada é v" + API_VERSION_STRING + "." +
                " Atualize o sCore para uma versão mais recente.");
        }
    }

    /**
     * Retorna true se a API instalada é compatível com a versão mínima exigida.
     * Versão não-lançante — útil para soft-depends ou features opcionais.
     *
     * Exemplo:
     *   if (SCore.isApiCompatible(1, 2, 0)) {
     *       // usa recurso adicionado na 1.2.0
     *   }
     */
    public static boolean isApiCompatible(int minMajor, int minMinor, int minPatch) {
        return isReady() &&
               compareVersion(API_MAJOR, API_MINOR, API_PATCH, minMajor, minMinor, minPatch) >= 0;
    }

    /**
     * Compara duas versões semânticas.
     * Retorna positivo se (aMaj.aMin.aPatch) > (bMaj.bMin.bPatch),
     *         zero     se iguais,
     *         negativo se menor.
     */
    private static int compareVersion(int aMaj, int aMin, int aPatch,
                                      int bMaj, int bMin, int bPatch) {
        if (aMaj != bMaj) return Integer.compare(aMaj, bMaj);
        if (aMin != bMin) return Integer.compare(aMin, bMin);
        return Integer.compare(aPatch, bPatch);
    }

    // ── Accessors de providers ────────────────────────────────────────────────

    public static NBTProvider getNBT()           { return get().getNBTProvider(); }
    public static HologramProvider getHologram() { return get().getHologramProvider(); }
    public static SkullProvider getSkull()       { return get().getSkullProvider(); }
    public static MaterialProvider getMaterial() { return get().getMaterialProvider(); }
    public static EconomyManager getEconomy()    { return get().getEconomyManager(); }
    public static SCoreDatabase getDatabase()    { return get().getDatabase(); }
    public static NPCManager getNPC()            { return get().getNPCManager(); }
    public static SCoreEventBus getEventBus()    { return get().getEventBus(); }
    public static ServerVersion getVersion()     { return ServerVersion.getCurrent(); }

    public static boolean isLegacy() { return getVersion().isLegacy(); }
    public static boolean hasPDC()   { return getVersion().hasPDC(); }

    // ── HealthReport ─────────────────────────────────────────────────────────

    /**
     * Resultado de uma verificação de saúde do sCore.
     *
     * Um componente com status FAIL bloqueia {@link #isHealthy()}.
     * Um componente com status WARN é informativo — não bloqueia.
     */
    public static final class HealthReport {

        public enum Status { OK, WARN, FAIL }

        public static final class Entry {
            private final String   component;
            private final Status   status;
            private final String   detail;

            private Entry(String component, Status status, String detail) {
                this.component = component;
                this.status    = status;
                this.detail    = detail;
            }

            public String   getComponent() { return component; }
            public Status   getStatus()    { return status; }
            public String   getDetail()    { return detail; }

            @Override
            public String toString() {
                String icon = status == Status.OK ? "✓" : (status == Status.WARN ? "⚠" : "✗");
                return "[" + icon + "] " + component + ": " + detail;
            }
        }

        private final List<Entry> entries = new ArrayList<>();

        private void ok(String component, String detail) {
            entries.add(new Entry(component, Status.OK, detail));
        }

        private void warn(String component, String detail) {
            entries.add(new Entry(component, Status.WARN, detail));
        }

        private void fail(String component, String detail) {
            entries.add(new Entry(component, Status.FAIL, detail));
        }

        /** true se não há nenhum componente com status FAIL */
        public boolean isHealthy() {
            for (Entry e : entries) {
                if (e.status == Status.FAIL) return false;
            }
            return true;
        }

        /** true se há ao menos um WARN (e nenhum FAIL) */
        public boolean hasWarnings() {
            for (Entry e : entries) {
                if (e.status == Status.WARN) return true;
            }
            return false;
        }

        /** Lista imutável de todas as entradas */
        public List<Entry> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        /** Lista apenas as entradas com status FAIL */
        public List<Entry> getFailures() {
            List<Entry> list = new ArrayList<>();
            for (Entry e : entries) {
                if (e.status == Status.FAIL) list.add(e);
            }
            return list;
        }

        /**
         * Resumo em uma única string para log.
         * Exemplo:
         *   "sCore Health: HEALTHY | [✓] version: V1_21 | [✓] nbt: NBTProviderPDC | ..."
         *   "sCore Health: UNHEALTHY | [✗] database: SQLITE não está conectado"
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder("sCore Health: ")
                .append(isHealthy() ? "§aHEALTHY" : "§cUNHEALTHY")
                .append(" §r| ");
            for (int i = 0; i < entries.size(); i++) {
                sb.append(entries.get(i).toString());
                if (i < entries.size() - 1) sb.append(" | ");
            }
            return sb.toString();
        }
    }
}
