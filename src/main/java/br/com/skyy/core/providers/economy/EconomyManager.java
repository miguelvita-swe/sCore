package br.com.skyy.core.providers.economy;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerenciador central de economia do sCore.
 *
 * Suporta os seguintes providers (em ordem de prioridade para resolução "money"):
 *   1. vault        — Vault (wrapper; cobre EssentialsX, CMI via Vault, etc.)
 *   2. coinsengine  — CoinsEngine (moeda virtual independente de Vault)
 *   3. gemseconomy  — GemsEconomy (multi-moeda)
 *   4. tne          — TheNewEconomy / Reserve
 *   5. cmi          — CMI Economy (direto)
 *   6. ecocredits   — EcoCredits (WillFP)
 *   7. essentials   — EssentialsX (direto, sem Vault)
 *   8. playerpoints — PlayerPoints (pontos, não dinheiro)
 *   9. iconomy      — iConomy (legacy 1.8)
 *  10. scoins       — sEconomia (registrado pelo plugin sEconomia quando instalado)
 *  11. Primeiro disponível — fallback final
 *
 * Plugins da suite podem registrar providers extras:
 *   SCore.getEconomy().register(new MinhaEconomy());
 *
 * O provider "scoins" (placeholder) é registrado pelo plugin sEconomia
 * quando instalado. Está na prioridade 10 pois é a economia própria da suite.
 */
public class EconomyManager {

    /**
     * Ordem de prioridade para resolução do provider padrão ("money" / null).
     * "scoins" é mantido para quando o plugin sEconomia registrar o provider.
     */
    private static final String[] DEFAULT_PRIORITY = {
        "vault", "coinsengine", "gemseconomy", "tne", "cmi",
        "ecocredits", "essentials", "playerpoints", "iconomy", "scoins"
    };

    private final Map<String, EconomyProvider> providers = new LinkedHashMap<>();

    /**
     * Registra um provider.
     * NÃO verifica isAvailable() aqui — providers lazy (ex: Vault) só ficam
     * disponíveis depois que o plugin de economia se registra no ServicesManager.
     * A checagem ocorre em resolveDefault() no momento do uso.
     */
    public void register(EconomyProvider provider) {
        if (provider != null) {
            providers.put(provider.getName().toLowerCase(), provider);
        }
    }

    /**
     * Remove um provider pelo nome.
     * Útil para substituir providers em runtime.
     */
    public void unregister(String name) {
        if (name != null) providers.remove(name.toLowerCase());
    }

    /**
     * Retorna o provider pelo nome.
     * Se nome for null ou "money", resolve o melhor provider disponível
     * seguindo DEFAULT_PRIORITY.
     */
    public EconomyProvider get(String name) {
        if (name == null || "money".equalsIgnoreCase(name)) {
            return resolveDefault();
        }
        return providers.get(name.toLowerCase());
    }

    /**
     * Resolve o provider padrão de forma lazy e com fallback completo.
     * Segue DEFAULT_PRIORITY, depois qualquer provider disponível.
     */
    private EconomyProvider resolveDefault() {
        for (String name : DEFAULT_PRIORITY) {
            EconomyProvider ep = providers.get(name);
            if (ep != null && ep.isAvailable()) return ep;
        }
        // Fallback absoluto: primeiro provider disponível fora da lista
        for (EconomyProvider ep : providers.values()) {
            if (ep.isAvailable()) return ep;
        }
        return null;
    }

    // ── Métodos de operação ────────────────────────────────────────────────────

    public boolean has(Player player, double amount, String provider) {
        EconomyProvider ep = get(provider);
        return ep != null && ep.has(player, amount);
    }

    public boolean withdraw(Player player, double amount, String provider) {
        EconomyProvider ep = get(provider);
        return ep != null && ep.withdraw(player, amount);
    }

    public boolean deposit(Player player, double amount, String provider) {
        EconomyProvider ep = get(provider);
        return ep != null && ep.deposit(player, amount);
    }

    public double getBalance(Player player, String provider) {
        EconomyProvider ep = get(provider);
        return ep != null ? ep.getBalance(player) : 0;
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    /** Retorna todos os providers registrados (disponíveis ou não). */
    public Map<String, EconomyProvider> getProviders() {
        return providers;
    }

    /** Retorna lista de providers que estão atualmente disponíveis. */
    public List<String> listAvailable() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, EconomyProvider> entry : providers.entrySet()) {
            if (entry.getValue().isAvailable()) list.add(entry.getKey());
        }
        return list;
    }

    /**
     * Retorna string de diagnóstico para uso no log de inicialização.
     * Exemplo: "Economy providers: [vault✓] [playerpoints✓] [coinsengine✗]"
     */
    public String describe() {
        StringBuilder sb = new StringBuilder("Economy providers: ");
        for (Map.Entry<String, EconomyProvider> e : providers.entrySet()) {
            sb.append("[").append(e.getKey())
              .append(e.getValue().isAvailable() ? "✓" : "✗")
              .append("] ");
        }
        EconomyProvider def = resolveDefault();
        sb.append("| default=").append(def != null ? def.getName() : "none");
        return sb.toString().trim();
    }
}