# sCore

**sCore** é o núcleo compartilhado da suite de plugins **"s"** para servidores Minecraft (Spigot/Paper **1.8 até 1.21**).

Ele não é um plugin jogável — é uma **biblioteca de compatibilidade** que centraliza toda a lógica de versão para que os outros plugins da suite funcionem em qualquer versão do servidor sem duplicar código.

---

## 🧩 Plugins da Suite

| Plugin | Descrição |
|---|---|
| **sCore** | Núcleo — providers, API, database, menus |

---

## ✨ Funcionalidades

- **Detecção de versão automática** — ServerVersion.java detecta de 1.8 a 1.21
- **NBT universal** — PDC (1.14+) e NMS reflection (1.8–1.13) com namespace fixo `score`
- **Hologramas** — DecentHolograms → HolographicDisplays → ArmorStand fallback automático
- **ItemBuilder cross-version** — glow, skull texture, NBT, lore colorida
- **Menus de inventário** — BaseMenu abstrata com listener global
- **Multi-economia** — Vault, PlayerPoints, JH_Economy, Essentials e mais
- **Database** — SQLite e MySQL com reconexão automática
- **Skull API** — PlayerProfile (1.18.2+) e GameProfile reflection (1.8–1.18.1)
- **Material API** — mapeamento de nomes modernos → legado (data values)
- **ColorUtil** — suporte a `&` codes e hex `{#RRGGBB}` em 1.16+
- **NumberFormatter** — formata números (1.5M, 2B) e tempo (1d 3h 10m)

---

## 📦 Instalação

1. Baixe o `sCore.jar` em [Releases](../../releases)
2. Coloque em `plugins/` do servidor
3. Reinicie o servidor
4. *(Opcional)* Configure `plugins/sCore/config.yml`

### Dependências Opcionais

| Plugin | Função |
|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Economia via Vault |
| [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/) | Hologramas (prioridade) |
| [HolographicDisplays](https://dev.bukkit.org/projects/holographic-displays) | Hologramas (fallback) |
| [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) | Economia alternativa |

---

## ⚙️ Configuração

```yaml
# plugins/sCore/config.yml

Database:
  Tipo: SQLITE       # SQLITE ou MYSQL
  Host: localhost
  Port: 3306
  Database: score
  User: root
  Password: ''

Debug: false

economy:
  default: money     # money (Vault) | playerpoints | jh_economy | essentials
```

---

## 🔌 Como usar em outro plugin

**plugin.yml:**
```yaml
depend:
  - sCore
```

**Java:**
```java
// NBT
SCore.getNBT().setString(item, "tipo", "ferro");
SCore.getNBT().getString(item, "tipo");

// Material (cross-version)
Material mat = SCore.getMaterial().get("PLAYER_HEAD");
ItemStack item = SCore.getMaterial().createItem("GRAY_STAINED_GLASS_PANE", 1);

// Holograma
SCore.getHologram().createHologram("myplugin_" + id, location, lines);
SCore.getHologram().updateHologram("myplugin_" + id, lines);
SCore.getHologram().removeHologram("myplugin_" + id);

// Economia
SCore.getEconomy().has(player, 1000.0, "money");
SCore.getEconomy().withdraw(player, 500.0, "playerpoints");

// Skull com textura customizada
SCore.getSkull().applyTexture(skullMeta, "http://textures.minecraft.net/...");

// ItemBuilder
new SCoreItemBuilder(Material.IRON_BLOCK)
    .name("&fMáquina de Ferro")
    .lore("&7Nível: &a1", "&7Stack: &f64")
    .glow(true)
    .nbt("tipo", "ferro")
    .build();

// Menu
public class MinhaJanela extends SCoreMenu {
    public MinhaJanela(JavaPlugin plugin) { super(plugin); }

    @Override
    public void build() {
        inventory = createInventory(27, "&8Meu Menu");
        inventory.setItem(13, new SCoreItemBuilder(Material.IRON_INGOT).name("&fItem").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack item, ClickType click) {
        if (slot == 13) player.sendMessage("Clicou!");
    }
}
new MinhaJanela(plugin).open(player);

// Database
Connection conn = SCore.getDatabase().getConnection();

// Versão do servidor
ServerVersion v = SCore.getVersion();
if (v.isAtLeast(ServerVersion.V1_16)) { ... }
if (v.hasPDC()) { ... }
```

---

## 🏗️ Estrutura do Projeto

```
sCore/
├── src/main/java/br/com/skyy/score/
│   ├── SCorePlugin.java            ← JavaPlugin principal
│   ├── SCore.java                  ← API estática pública
│   ├── version/ServerVersion.java  ← Detecção de versão (1.8–1.21)
│   ├── providers/
│   │   ├── nbt/                    ← NBTProvider (PDC + NMS Legacy)
│   │   ├── hologram/               ← HologramProvider (DH + HD + None)
│   │   ├── skull/                  ← SkullProvider (Modern + Legacy)
│   │   ├── material/               ← MaterialProvider (Modern + Legacy)
│   │   └── economy/                ← EconomyProvider + EconomyManager
│   ├── item/SCoreItemBuilder.java  ← ItemBuilder universal
│   ├── menu/                       ← SCoreMenu + SCoreMenuListener
│   ├── database/                   ← SCoreDatabase (SQLite + MySQL)
│   └── utils/                      ← ColorUtil, NumberFormatter
└── src/main/resources/
    ├── plugin.yml
    └── config.yml
```

---

## 🔧 Build

```bash
mvn clean package
```

Requer Java 8+. O JAR gerado em `target/sCore-1.0.0.jar` é copiado para `plugins/`.

---

## 📋 Versões Suportadas

| Versão MC | NBT | Skull | Hologramas | Hex Colors |
|---|---|---|---|---|
| 1.8 – 1.12 | NMS Reflection | GameProfile | DH / HD | ❌ |
| 1.13 | NMS Reflection | GameProfile | DH / HD | ❌ |
| 1.14 – 1.15 | PDC | GameProfile | DH / HD | ❌ |
| 1.16 – 1.17 | PDC | GameProfile | DH / HD | ✅ |
| 1.18 – 1.21 | PDC | PlayerProfile | DH / HD | ✅ |

---

## 👤 Autor

**Skyy** — Suite de plugins "s" para Minecraft

---

## 📄 Licença

Este projeto é de uso privado. Todos os direitos reservados.
