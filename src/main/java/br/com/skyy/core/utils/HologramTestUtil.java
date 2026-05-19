package br.com.skyy.core.utils;

import br.com.skyy.core.SCore;
import br.com.skyy.core.providers.hologram.HologramProvider;
import br.com.skyy.core.providers.hologram.HologramProviderArmorStand;
import br.com.skyy.core.providers.hologram.HologramProviderDecent;
import br.com.skyy.core.providers.hologram.HologramProviderHD;
import br.com.skyy.core.providers.hologram.HologramProviderNone;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

/**
 * Utilitário para testar os providers de hologramas em runtime.
 *
 * Usado principalmente pelo comando /score hologram test para validar
 * qual provider está ativo e se está funcionando corretamente,
 * especialmente útil para verificar comportamento com e sem DecentHolograms.
 *
 * Uso via comando:
 *   /score hologram test   — testa o provider atual
 *   /score hologram list   — lista todos os providers e disponibilidade
 *   /score hologram switch <decent|hd|armorstand|none> — força um provider específico
 */
public final class HologramTestUtil {

    private static final String TEST_ID     = "score_test_hologram";
    private static final List<String> LINES = Arrays.asList(
        "&6&l[sCore] Teste de Holograma",
        "&7Provider: &f{provider}",
        "&7Versão MC: &f{version}",
        "&aFuncionando corretamente!"
    );

    private HologramTestUtil() {}

    /**
     * Testa o provider atual criando um holograma temporário na posição do sender.
     * O holograma é removido após 5 segundos automaticamente.
     *
     * @param plugin Plugin para agendar a remoção
     * @param sender Quem executa o teste (posição usada se for Player)
     * @param loc    Localização para criar o holograma
     */
    public static void testCurrent(Plugin plugin, CommandSender sender, Location loc) {
        HologramProvider provider = SCore.getHologram();
        String providerName = provider.getProviderName();

        sender.sendMessage("§8[sCore] §7Testando provider: §f" + providerName);
        sender.sendMessage("§8[sCore] §7Disponível: " + (provider.isAvailable() ? "§atrue" : "§cfalse"));

        if (!provider.isAvailable()) {
            sender.sendMessage("§c  → Provider não disponível. Verifique se o plugin está instalado.");
            if (provider instanceof HologramProviderNone) {
                sender.sendMessage("§c  → Nenhum plugin de holograma detectado.");
                sender.sendMessage("§7  Instale: DecentHolograms, HolographicDisplays, ou use ArmorStand.");
            }
            return;
        }

        // Monta as linhas com os dados reais
        List<String> lines = Arrays.asList(
            "&6&l[sCore] Teste de Holograma",
            "&7Provider: &f" + providerName,
            "&7Versão MC: &f" + SCore.getVersion().name(),
            "&aFuncionando corretamente!"
        );

        try {
            provider.createHologram(TEST_ID, loc.clone().add(0, 0.5, 0), lines);
            sender.sendMessage("§a  ✓ Holograma criado com sucesso!");
            sender.sendMessage("§7  Será removido em 5 segundos.");

            // Remove após 5s (100 ticks)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                provider.removeHologram(TEST_ID);
                sender.sendMessage("§8[sCore] §7Holograma de teste removido.");
            }, 100L);
        } catch (Exception e) {
            sender.sendMessage("§c  ✗ Erro ao criar holograma: " + e.getMessage());
        }
    }

    /**
     * Lista todos os providers conhecidos e informa disponibilidade.
     */
    public static void listAll(Plugin plugin, CommandSender sender) {
        sender.sendMessage("§8[sCore] §6=== Hologram Providers ===");

        // DecentHolograms
        HologramProviderDecent decent = new HologramProviderDecent();
        sender.sendMessage(status("DecentHolograms", decent.isAvailable())
            + (decent.isAvailable() ? "" : " §8(instale: spigotmc.org/resources/96927)"));

        // HolographicDisplays
        HologramProviderHD hd = new HologramProviderHD(plugin);
        sender.sendMessage(status("HolographicDisplays", hd.isAvailable())
            + (hd.isAvailable() ? "" : " §8(legado, max 1.19)"));

        // ArmorStand
        sender.sendMessage(status("ArmorStand (fallback)", true) + " §8(sempre disponível, vanilla)");

        // None
        sender.sendMessage(status("None", false) + " §8(quando nenhum plugin encontrado)");

        // Provider ativo
        sender.sendMessage("§7Provider ativo: §f" + SCore.getHologram().getProviderName());
    }

    /**
     * Força troca de provider em runtime.
     * Útil para testar comportamento sem precisar reinstalar plugins.
     *
     * ATENÇÃO: hologramas existentes do provider anterior são removidos.
     */
    public static boolean switchProvider(Plugin plugin, CommandSender sender, String name) {
        HologramProvider current = SCore.getHologram();
        HologramProvider next;

        switch (name.toLowerCase()) {
            case "decent":
            case "decentholograms":
                next = new HologramProviderDecent();
                break;
            case "hd":
            case "holographicdisplays":
                next = new HologramProviderHD(plugin);
                break;
            case "armorstand":
            case "armor":
                next = new HologramProviderArmorStand(plugin);
                break;
            case "none":
                next = new HologramProviderNone();
                break;
            default:
                sender.sendMessage("§cProvider desconhecido: " + name);
                sender.sendMessage("§7Opções: decent, hd, armorstand, none");
                return false;
        }

        if (!next.isAvailable() && !(next instanceof HologramProviderNone)
                && !(next instanceof HologramProviderArmorStand)) {
            sender.sendMessage("§c  ✗ " + next.getProviderName() + " não está disponível no servidor.");
            return false;
        }

        // Remove todos os hologramas do provider atual antes de trocar
        try { current.removeAll(); } catch (Exception ignored) {}

        // Troca o provider no plugin
        if (SCore.isReady()) {
            br.com.skyy.core.SCorePlugin.getInstance().setHologramProvider(next);
            sender.sendMessage("§a  ✓ Provider trocado para: §f" + next.getProviderName());
            sender.sendMessage("§7  (Reinicie o servidor para que os hologramas sejam recriados)");
            return true;
        }
        sender.sendMessage("§c  ✗ sCore não está pronto.");
        return false;
    }

    private static String status(String name, boolean available) {
        return "  " + (available ? "§a✓" : "§8✗") + " §7" + name;
    }
}
