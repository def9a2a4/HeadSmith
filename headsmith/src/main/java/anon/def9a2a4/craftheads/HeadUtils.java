package anon.def9a2a4.headsmith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@FunctionalInterface
interface HeadIdResolver {
    Optional<String> resolve(ItemStack item);
}

record TextureInfo(String textureUrl, String textureId) {}

final class HeadUtils {

    private HeadUtils() {}

    static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    static Optional<String> getPdcString(ItemStack item, NamespacedKey key) {
        if (item == null) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        String v = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return Optional.ofNullable(v);
    }

    static Optional<TextureInfo> parseTextureBase64(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = obj.getAsJsonObject("textures");
            JsonObject skin = textures.getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString();
            String textureId = textureIdFromSkinUrl(url).orElseThrow();
            return Optional.of(new TextureInfo(url, textureId));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> textureIdFromSkullBlock(Skull skull) {
        PlayerProfile prof = skull.getOwnerProfile();
        if (prof == null || prof.getTextures() == null || prof.getTextures().getSkin() == null) {
            return Optional.empty();
        }
        return textureIdFromSkinUrl(prof.getTextures().getSkin().toString());
    }

    static Optional<String> textureIdFromSkinUrl(String url) {
        if (url == null) return Optional.empty();
        int idx = url.lastIndexOf('/');
        if (idx < 0 || idx + 1 >= url.length()) {
            return Optional.empty();
        }
        return Optional.of(url.substring(idx + 1));
    }

    static Optional<String> requireString(ConfigurationSection sec, String path) {
        String v = sec.getString(path);
        if (v == null) return Optional.empty();
        String t = v.trim();
        if (t.isEmpty()) return Optional.empty();
        return Optional.of(t);
    }

    static int asInt(Object o, int dflt) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                return dflt;
            }
        }
        return dflt;
    }

    static void giveToInventoryOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack lf : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), lf);
            }
        }
    }

    static ItemStack makeHeadItem(HeadDef def, int amount, NamespacedKey pdcHeadIdKey, Logger logger) {
        if (def == null) {
            return new ItemStack(Material.AIR);
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD, Math.max(1, amount));
        ItemMeta meta0 = item.getItemMeta();
        if (!(meta0 instanceof SkullMeta meta)) {
            return item;
        }

        UUID profileUuid = UUID.nameUUIDFromBytes(def.textureId().getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createPlayerProfile(profileUuid);
        try {
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(def.textureUrl()));
            profile.setTextures(textures);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Invalid texture URL for " + def.id() + ": " + e.getMessage());
            }
        }
        meta.setOwnerProfile(profile);

        meta.setDisplayName(color(def.name()));
        if (!def.lore().isEmpty()) {
            meta.setLore(def.lore().stream().map(HeadUtils::color).toList());
        }

        meta.getPersistentDataContainer().set(pdcHeadIdKey, PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    static Optional<String> getHeadIdFromItem(ItemStack item, NamespacedKey pdcHeadIdKey,
                                               Map<String, String> headIdByTextureId) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        String tagged = meta.getPersistentDataContainer().get(pdcHeadIdKey, PersistentDataType.STRING);
        if (tagged != null && !tagged.isBlank()) {
            return Optional.of(tagged);
        }

        if (!(meta instanceof SkullMeta sm)) {
            return Optional.empty();
        }
        PlayerProfile prof = sm.getOwnerProfile();
        if (prof == null || prof.getTextures() == null || prof.getTextures().getSkin() == null) {
            return Optional.empty();
        }

        String textureId = textureIdFromSkinUrl(prof.getTextures().getSkin().toString()).orElse(null);
        if (textureId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(headIdByTextureId.get(textureId));
    }
}
