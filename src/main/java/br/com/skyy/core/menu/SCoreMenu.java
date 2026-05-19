package br.com.skyy.core.menu;

import br.com.skyy.core.item.SCoreItemBuilder;
import br.com.skyy.core.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Abstract base menu for all sCore-based GUIs.
 * Extend this class and implement build() and onClick().
 *
 * Migration guide para sMaquinas:
 *   ANTES: class MaquinaMenu extends BaseMenu { ... }
 *   DEPOIS: class MaquinaMenu extends SCoreMenu {
 *       public MaquinaMenu(Plugin plugin, Maquina maquina) {
 *           super(plugin);
 *           this.maquina = maquina;
 *       }
 *       @Override public void build() {
 *           createInventory(54, "&8Máquina de &f" + maquina.getTipo());
 *           fillBorder(glass("GRAY_STAINED_GLASS_PANE"));
 *           setItem(13, new SCoreItemBuilder(maquina.getTipo())
 *               .name("&f" + maquina.getTipo())
 *               .lore("&7Stack: &f{stack}")
 *               .placeholder("stack", String.valueOf(maquina.getStack()))
 *               .build());
 *       }
 *       @Override public void onClick(Player player, int slot, ItemStack item, ClickType click) {
 *           if (slot == 31) { player.closeInventory(); return; }
 *       }
 *   }
 */
public abstract class SCoreMenu implements InventoryHolder {

    protected final JavaPlugin plugin;
    protected Inventory inventory;

    public SCoreMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Popula o inventário. Chamado antes de abrir. */
    public abstract void build();

    /**
     * Chamado quando um jogador clica em um slot não-nulo e não-AIR.
     * O evento já está cancelado antes desta chamada.
     */
    public abstract void onClick(Player player, int slot, ItemStack item, ClickType click);

    /** Opcional — chamado ao fechar o inventário. */
    public void onClose(Player player) {}

    /** Abre o menu para o jogador — chama build() primeiro. */
    public void open(Player player) {
        build();
        player.openInventory(inventory);
    }

    // ── Criação de inventário ─────────────────────────────────────────────────

    /** Cria o inventário base. Chamar dentro do build(). */
    protected Inventory createInventory(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.colorize(title));
        return this.inventory;
    }

    // ── Utilitários de slot ───────────────────────────────────────────────────

    protected void setItem(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /** Preenche todos os slots vazios com o item dado. */
    protected void fill(ItemStack item) {
        if (inventory == null) return;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, item);
        }
    }

    /** Preenche uma linha (row 0–5) inteira com o item dado. */
    protected void fillRow(int row, ItemStack item) {
        if (inventory == null) return;
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, item);
        }
    }

    /**
     * Preenche a borda do inventário com o item dado.
     * Funciona para inventários de 1–6 linhas.
     *
     * Muito usado no sMaquinas para vidros decorativos.
     */
    protected void fillBorder(ItemStack item) {
        if (inventory == null) return;
        int size = inventory.getSize();
        int rows  = size / 9;
        for (int col = 0; col < 9; col++) {
            setItem(col, item);                    // primeira linha
            setItem((rows - 1) * 9 + col, item);  // última linha
        }
        for (int row = 1; row < rows - 1; row++) {
            setItem(row * 9,       item);          // coluna esquerda
            setItem(row * 9 + 8,  item);           // coluna direita
        }
    }

    /**
     * Preenche uma coluna inteira com o item dado.
     * col: 0 (esquerda) a 8 (direita).
     */
    protected void fillColumn(int col, ItemStack item) {
        if (inventory == null || col < 0 || col > 8) return;
        int rows = inventory.getSize() / 9;
        for (int row = 0; row < rows; row++) {
            setItem(row * 9 + col, item);
        }
    }

    /**
     * Cria um item de vidro colorido pronto para borda/decoração.
     * Usa MaterialProvider para compatibilidade 1.8–1.21.
     *
     * Atalho para uso frequente no sMaquinas:
     *   fillBorder(glass("GRAY_STAINED_GLASS_PANE"));
     */
    protected ItemStack glass(String materialName) {
        return new SCoreItemBuilder(materialName)
            .name("&r")
            .hideAll()
            .build();
    }

    /**
     * Cria botão de fechar (BARRIER) para uso em menus.
     * Posição recomendada: slot central da última linha.
     *
     * Exemplo: setItem(49, closeButton("&cFechar"));
     */
    protected ItemStack closeButton(String label) {
        return new SCoreItemBuilder("BARRIER")
            .name(label != null ? label : "&cFechar")
            .build();
    }

    /**
     * Cria botão de navegação "próxima página".
     * Material: ARROW. Slot recomendado: 53 (última linha, direita).
     */
    protected ItemStack nextPageButton(String label) {
        return new SCoreItemBuilder("ARROW")
            .name(label != null ? label : "&aProxima página →")
            .build();
    }

    /**
     * Cria botão de navegação "página anterior".
     * Material: ARROW. Slot recomendado: 45 (última linha, esquerda).
     */
    protected ItemStack prevPageButton(String label) {
        return new SCoreItemBuilder("ARROW")
            .name(label != null ? label : "&7← Página anterior")
            .build();
    }

    /**
     * Converte slot (row, col) → índice linear do inventário.
     * row: 0–5, col: 0–8.
     */
    protected int slot(int row, int col) {
        return row * 9 + col;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
