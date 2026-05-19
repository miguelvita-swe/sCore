package br.com.skyy.core.providers.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * HologramProvider backed by DecentHolograms via full reflection.
 * Supports DecentHolograms 2.x API.
 */
public class HologramProviderDecent implements HologramProvider {

    private static final Logger log = Logger.getLogger("sCore-Hologram");

    private final Map<String, Object> holograms = new HashMap<>();
    private boolean available   = false;
    /** true depois que a resolução completa de métodos foi feita com sucesso */
    private boolean fullyInit   = false;

    // Resolved methods
    private Class<?> dhapiClass;
    private Method mCreate;           // createHologram(String, Location, boolean)
    private Method mRemove;           // removeHologram(String)
    private Method mGetHologram;      // getHologram(String)
    private Method mGetPage;          // getHologramPage(Hologram, int)
    private Method mSetLinesPage;     // setHologramLines(HologramPage, List)
    private Method mSetLinesHolo;     // setHologramLines(Hologram, List)   — newer API
    private Method mAddLine;          // addHologramLine(Hologram, String)
    private Method mClearLines;       // clearLines() on HologramPage

    public HologramProviderDecent() {
        // Fase 1 (construtor): apenas verifica se o plugin existe e carrega a classe DHAPI
        // sem executar static initializers (initialize=false).
        // Fase 2 (lazy): resolve todos os métodos na primeira operação real.
        // Isso evita ExceptionInInitializerError quando DH ainda não terminou seu onEnable.
        try {
            if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) return;
            dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI", false,
                    getClass().getClassLoader());
            // Disponível = plugin existe e classe foi encontrada
            // Resolução completa dos métodos é adiada para o primeiro uso
            available = (dhapiClass != null);
        } catch (ExceptionInInitializerError e) {
            log.info("[sCore] DecentHolograms static init pendente — será re-tentado no primeiro uso.");
            available = false;
        } catch (Exception e) {
            log.info("[sCore] DecentHolograms não disponível: " + e.getMessage());
            available = false;
        }
    }

    /**
     * Fase 2 de inicialização — resolve todos os métodos usando a classe DHAPI
     * com initialize=true. Chamada na primeira operação real, quando DH já
     * terminou seu onEnable() e seus static initializers foram executados com segurança.
     */
    private boolean lazyInit() {
        if (fullyInit) return true;
        try {
            // Agora pode inicializar — DH já carregou seus statics
            dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI",
                    true, getClass().getClassLoader());

            mCreate      = tryGetMethod(dhapiClass, "createHologram", String.class, Location.class, boolean.class);
            mRemove      = tryGetMethod(dhapiClass, "removeHologram", String.class);
            mGetHologram = tryGetMethod(dhapiClass, "getHologram", String.class);

            Class<?> hologramClass     = tryClassNoInit("eu.decentsoftware.holograms.api.holograms.Hologram");
            Class<?> hologramPageClass = tryClassNoInit("eu.decentsoftware.holograms.api.holograms.HologramPage");

            if (hologramClass != null && hologramPageClass != null) {
                mGetPage      = tryGetMethod(dhapiClass, "getHologramPage", hologramClass, int.class);
                mSetLinesPage = tryGetMethod(dhapiClass, "setHologramLines", hologramPageClass, List.class);
                mAddLine      = tryGetMethod(dhapiClass, "addHologramLine", hologramClass, String.class);
                mClearLines   = tryGetMethod(hologramPageClass, "clearLines");
            }
            if (hologramClass != null) {
                mSetLinesHolo = tryGetMethod(dhapiClass, "setHologramLines", hologramClass, List.class);
            }

            fullyInit = (mCreate != null && mRemove != null);
            if (!fullyInit) {
                log.warning("[sCore] DecentHolograms lazy init: métodos essenciais não encontrados.");
            }
            return fullyInit;
        } catch (ExceptionInInitializerError | Exception e) {
            log.warning("[sCore] DecentHolograms lazy init falhou: " + e.getMessage());
            available = false;
            return false;
        }
    }

    @Override public String getProviderName() { return "DecentHolograms"; }
    @Override public boolean isAvailable()    { return available; }

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        if (!available || id == null || location == null) return;
        if (!lazyInit()) return;
        // Remove if already exists
        removeHologram(id);
        try {
            Object hologram = mCreate.invoke(null, id, location, false);
            if (hologram == null) return;
            holograms.put(id, hologram);
            setLines(hologram, processLines(lines));
        } catch (Exception e) {
            log.warning("[sCore] DH createHologram error: " + e.getMessage());
        }
    }

    @Override
    public void updateHologram(String id, List<String> lines) {
        if (!available || id == null) return;
        if (!lazyInit()) return;
        Object hologram = holograms.get(id);
        if (hologram == null) return;
        try {
            setLines(hologram, processLines(lines));
        } catch (Exception e) {
            log.warning("[sCore] DH updateHologram error: " + e.getMessage());
        }
    }

    @Override
    public void createHologramWithItem(String id, Location location, List<String> lines, Material itemMaterial) {
        if (!available || id == null || location == null) return;
        if (!lazyInit()) return;
        List<String> processed = new ArrayList<>(processLines(lines));
        if (itemMaterial != null) {
            processed.add("#ICON: " + itemMaterial.name()); // END = bottom of hologram
        }
        removeHologram(id);
        try {
            Object hologram = mCreate.invoke(null, id, location, false);
            if (hologram == null) return;
            holograms.put(id, hologram);
            setLines(hologram, processed);
        } catch (Exception e) {
            log.warning("[sCore] DH createHologramWithItem error: " + e.getMessage());
        }
    }

    @Override
    public void removeHologram(String id) {
        if (!available || id == null) return;
        try {
            mRemove.invoke(null, id);
        } catch (Exception ignored) {}
        holograms.remove(id);
    }

    @Override
    public void removeAll() {
        for (String id : new HashSet<>(holograms.keySet())) removeHologram(id);
    }

    /** Attempts multiple strategies to set lines on a hologram */
    private void setLines(Object hologram, List<String> lines) {
        // Strategy 1: DHAPI.setHologramLines(Hologram, List) — newer API
        if (mSetLinesHolo != null) {
            try { mSetLinesHolo.invoke(null, hologram, lines); return; } catch (Exception ignored) {}
        }
        // Strategy 2: DHAPI.getHologramPage(hologram, 0) → DHAPI.setHologramLines(page, lines)
        if (mGetPage != null && mSetLinesPage != null) {
            try {
                Object page = mGetPage.invoke(null, hologram, 0);
                if (page != null) {
                    mSetLinesPage.invoke(null, page, lines);
                    return;
                }
            } catch (Exception ignored) {}
        }
        // Strategy 3: clear page lines + addHologramLine per line
        if (mGetPage != null && mClearLines != null && mAddLine != null) {
            try {
                Object page = mGetPage.invoke(null, hologram, 0);
                if (page != null) {
                    mClearLines.invoke(page);
                    for (String line : lines) mAddLine.invoke(null, hologram, line);
                    return;
                }
            } catch (Exception ignored) {}
        }
        // Strategy 4: addHologramLine only
        if (mAddLine != null) {
            try {
                for (String line : lines) mAddLine.invoke(null, hologram, line);
            } catch (Exception e) {
                log.warning("[sCore] DH setLines all strategies failed: " + e.getMessage());
            }
        }
    }

    /** Converts [item]MATERIAL lines to #ICON: MATERIAL format */
    private List<String> processLines(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line != null && line.startsWith("[item]")) {
                String mat = line.substring(6).split(":")[0].trim();
                result.add("#ICON: " + mat);
            } else {
                result.add(line != null ? line : "");
            }
        }
        return result;
    }

    private Class<?> tryClassNoInit(String name) {
        try { return Class.forName(name, false, getClass().getClassLoader()); }
        catch (Exception | ExceptionInInitializerError e) { return null; }
    }

    private Class<?> tryClass(String name) {
        try { return Class.forName(name); } catch (Exception e) { return null; }
    }

    private Method tryGetMethod(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) return null;
        try { return clazz.getMethod(name, params); }
        catch (Exception e) {
            log.fine("[sCore] DH method not found: " + name + " — " + e.getMessage());
            return null;
        }
    }
}
