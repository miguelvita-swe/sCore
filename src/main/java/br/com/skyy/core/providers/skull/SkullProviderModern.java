package br.com.skyy.core.providers.skull;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SkullProvider for 1.18.2+ using the PlayerProfile API.
 */
public class SkullProviderModern implements SkullProvider {

    private static final Logger log = Logger.getLogger("sCore-Skull");

    @Override
    public void applyTexture(SkullMeta meta, String url) {
        if (meta == null || url == null) return;
        try {
            // UUID determinístico baseado no base64 da textura — igual ao Legacy.
            // Isso garante que o cliente faça cache e a textura apareça sempre.
            String base64 = Base64.getEncoder().encodeToString(
                    ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes());
            UUID deterministicUUID = UUID.nameUUIDFromBytes(base64.getBytes());

            PlayerProfile profile = Bukkit.createPlayerProfile(deterministicUUID, "sCore");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException e) {
            log.warning("[sCore] SkullProviderModern bad URL: " + url);
        } catch (Exception e) {
            log.warning("[sCore] SkullProviderModern applyTexture error: " + e.getMessage());
        }
    }

    @Override
    public void applyOwner(SkullMeta meta, String playerName) {
        if (meta == null || playerName == null) return;
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        } catch (Exception e) {
            log.warning("[sCore] SkullProviderModern applyOwner error: " + e.getMessage());
        }
    }
}
