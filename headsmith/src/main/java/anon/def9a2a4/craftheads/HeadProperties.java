package anon.def9a2a4.headsmith;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static anon.def9a2a4.headsmith.HeadUtils.textureIdFromSkullBlock;

enum HeadProperty {
    LIGHTABLE,      // Can be lit/unlit by player (candles)
    GLOWING,        // Always emits light when placed (pumpkins)
    WORKBENCH,      // Opens crafting table GUI
    ANVIL,          // Opens anvil GUI
    ENCHANTING,     // Opens enchanting table GUI
    SMITHING,       // Opens smithing table GUI
    LOOM,           // Opens loom GUI
    STONECUTTER,    // Opens stonecutter GUI
    GRINDSTONE,     // Opens grindstone GUI
    CARTOGRAPHY,    // Opens cartography table GUI
    ENDERCHEST;     // Opens ender chest inventory

    private static final int LIGHT_LEVEL = 14;

    static Optional<HeadProperty> fromString(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static Set<HeadProperty> parseProperties(java.util.List<String> list) {
        Set<HeadProperty> props = EnumSet.noneOf(HeadProperty.class);
        if (list == null) return props;
        for (String s : list) {
            fromString(s).ifPresent(props::add);
        }
        return props;
    }
}

class HeadPropertiesListener implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey pdcLitKey;
    private final Function<String, HeadDef> headDefLookup;
    private final Function<String, String> textureIdToHeadId;
    private final Set<Location> litCandleLocations = new HashSet<>();
    private BukkitTask particleTask;

    private static final int LIGHT_LEVEL = 14;

    HeadPropertiesListener(JavaPlugin plugin, NamespacedKey pdcLitKey,
                           Function<String, HeadDef> headDefLookup,
                           Function<String, String> textureIdToHeadId) {
        this.plugin = plugin;
        this.pdcLitKey = pdcLitKey;
        this.headDefLookup = headDefLookup;
        this.textureIdToHeadId = textureIdToHeadId;
    }

    void startParticleTask() {
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : litCandleLocations) {
                if (!loc.isWorldLoaded()) continue;
                Block block = loc.getBlock();
                double px = loc.getX() + 0.5;
                double py;
                double pz = loc.getZ() + 0.5;

                if (block.getType() == Material.PLAYER_WALL_HEAD) {
                    // Wall head - offset based on facing direction
                    py = loc.getY() + 0.8;
                    if (block.getBlockData() instanceof Directional directional) {
                        BlockFace facing = directional.getFacing();
                        // Head faces this direction, so particle spawns in front (opposite side of wall)
                        switch (facing) {
                            case NORTH -> pz = loc.getZ() + 0.75;
                            case SOUTH -> pz = loc.getZ() + 0.25;
                            case EAST -> px = loc.getX() + 0.25;
                            case WEST -> px = loc.getX() + 0.75;
                            default -> {}
                        }
                    }
                } else {
                    // Floor head
                    py = loc.getY() + 0.55;
                }

                loc.getWorld().spawnParticle(
                    Particle.SMALL_FLAME,
                    px, py, pz,
                    1, 0.05, 0.05, 0.05, 0.0
                );
            }
        }, 5L, 5L);
    }

    void stopParticleTask() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        litCandleLocations.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return;

        Optional<String> textureIdOpt = textureIdFromSkullBlock(skull);
        if (textureIdOpt.isEmpty()) return;

        String headId = textureIdToHeadId.apply(textureIdOpt.get());
        if (headId == null) return;

        HeadDef def = headDefLookup.apply(headId);
        if (def == null) return;

        Player player = event.getPlayer();
        Set<HeadProperty> props = def.properties();

        // Handle functional block properties
        if (props.contains(HeadProperty.WORKBENCH)) {
            event.setCancelled(true);
            player.openWorkbench(null, true);
        } else if (props.contains(HeadProperty.ANVIL)) {
            event.setCancelled(true);
            player.openAnvil(null, true);
        } else if (props.contains(HeadProperty.ENCHANTING)) {
            event.setCancelled(true);
            player.openEnchanting(null, true);
        } else if (props.contains(HeadProperty.SMITHING)) {
            event.setCancelled(true);
            player.openSmithingTable(null, true);
        } else if (props.contains(HeadProperty.LOOM)) {
            event.setCancelled(true);
            player.openLoom(null, true);
        } else if (props.contains(HeadProperty.STONECUTTER)) {
            event.setCancelled(true);
            player.openStonecutter(null, true);
        } else if (props.contains(HeadProperty.GRINDSTONE)) {
            event.setCancelled(true);
            player.openGrindstone(null, true);
        } else if (props.contains(HeadProperty.CARTOGRAPHY)) {
            event.setCancelled(true);
            player.openCartographyTable(null, true);
        } else if (props.contains(HeadProperty.ENDERCHEST)) {
            event.setCancelled(true);
            player.openInventory(player.getEnderChest());
        } else if (props.contains(HeadProperty.LIGHTABLE)) {
            // Handle lightable property (candles)
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            boolean isLit = isHeadLit(skull);

            if (!isLit && itemInHand.getType() == Material.FLINT_AND_STEEL) {
                // Light the candle
                event.setCancelled(true);
                lightHead(block, skull);
                damageItem(player, itemInHand);
                player.playSound(block.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
            } else if (isLit) {
                // Extinguish the candle
                event.setCancelled(true);
                extinguishHead(block, skull);
                player.playSound(block.getLocation(), Sound.BLOCK_CANDLE_EXTINGUISH, 1.0f, 1.0f);
                Location smokeLoc = getParticleLocation(block);
                block.getWorld().spawnParticle(Particle.SMOKE, smokeLoc, 5, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // Get head_id from the item's PDC using the same key pattern as HeadSmithPlugin
        NamespacedKey headIdKey = new NamespacedKey(plugin, "head_id");
        String headId = pdc.get(headIdKey, PersistentDataType.STRING);
        if (headId == null) return;

        HeadDef def = headDefLookup.apply(headId);
        if (def == null) return;

        if (def.properties().contains(HeadProperty.GLOWING)) {
            // Place light block above for glowing heads
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                placeLightAbove(block);
            });
        }
    }

    void onHeadBreak(Block block, HeadDef def) {
        // Remove light block above if present
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.LIGHT) {
            above.setType(Material.AIR);
        }

        // Remove from lit candle tracking
        litCandleLocations.remove(block.getLocation());
    }

    private void lightHead(Block block, Skull skull) {
        // Set lit state in PDC
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        pdc.set(pdcLitKey, PersistentDataType.BYTE, (byte) 1);
        skull.update();

        // Place light block above
        placeLightAbove(block);

        // Track for particles
        litCandleLocations.add(block.getLocation());
    }

    private void extinguishHead(Block block, Skull skull) {
        // Clear lit state in PDC
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        pdc.remove(pdcLitKey);
        skull.update();

        // Remove light block above
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.LIGHT) {
            above.setType(Material.AIR);
        }

        // Stop tracking for particles
        litCandleLocations.remove(block.getLocation());
    }

    private boolean isHeadLit(Skull skull) {
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        Byte lit = pdc.get(pdcLitKey, PersistentDataType.BYTE);
        return lit != null && lit == 1;
    }

    private void placeLightAbove(Block block) {
        Block above = block.getRelative(0, 1, 0);
        if (above.getType() == Material.AIR) {
            above.setType(Material.LIGHT);
            if (above.getBlockData() instanceof Levelled levelled) {
                levelled.setLevel(LIGHT_LEVEL);
                above.setBlockData(levelled);
            }
        }
    }

    private Location getParticleLocation(Block block) {
        Location loc = block.getLocation();
        double px = loc.getX() + 0.5;
        double py;
        double pz = loc.getZ() + 0.5;

        if (block.getType() == Material.PLAYER_WALL_HEAD) {
            py = loc.getY() + 0.8;
            if (block.getBlockData() instanceof Directional directional) {
                BlockFace facing = directional.getFacing();
                // Head faces this direction, so particle spawns in front (opposite side of wall)
                switch (facing) {
                    case NORTH -> pz = loc.getZ() + 0.75;
                    case SOUTH -> pz = loc.getZ() + 0.25;
                    case EAST -> px = loc.getX() + 0.25;
                    case WEST -> px = loc.getX() + 0.75;
                    default -> {}
                }
            }
        } else {
            py = loc.getY() + 0.55;
        }

        return new Location(loc.getWorld(), px, py, pz);
    }

    private void damageItem(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + 1);
            item.setItemMeta(meta);
            if (damageable.getDamage() >= item.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
}
