package anon.def9a2a4.headsmith;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static anon.def9a2a4.headsmith.HeadUtils.*;

public final class HeadSmithPlugin extends JavaPlugin implements Listener, TabCompleter {

    private record LoadResult(int loaded, int excluded) {}

    private final Map<String, HeadDef> headsById = new LinkedHashMap<>();
    private final Map<String, String> headIdByTextureId = new HashMap<>();
    private final Map<String, String> firstHeadByTag = new LinkedHashMap<>();
    private final Map<String, Set<String>> tagChildren = new LinkedHashMap<>(); // parent tag -> child tags
    private final List<HeadStonecutterRecipe> headStonecutterRecipes = new ArrayList<>();
    private final List<NamespacedKey> registeredRecipeKeys = new ArrayList<>();
    private final List<Recipe> pendingRecipes = new ArrayList<>();

    private NamespacedKey pdcHeadIdKey;
    private NamespacedKey pdcLitKey;
    private HeadMenus menus;
    private HeadPropertiesListener propertiesListener;

    private Set<String> excludedTags = new HashSet<>();
    private Set<String> excludedHeads = new HashSet<>();
    private List<String> tagOrderFirst = List.of();
    private List<String> tagOrderLast = List.of();

    @Override
    public void onEnable() {
        int pluginId = 28528;
        Metrics metrics = new Metrics(this, pluginId);

        pdcHeadIdKey = new NamespacedKey(this, "head_id");
        pdcLitKey = new NamespacedKey(this, "lit");

        saveDefaultConfig();
        reloadHeadsSync();

        menus = new HeadMenus(headsById, headStonecutterRecipes, firstHeadByTag, tagChildren, pdcHeadIdKey, this::makeHeadItem, tagOrderFirst, tagOrderLast);
        propertiesListener = new HeadPropertiesListener(this, pdcLitKey, headsById::get, headIdByTextureId::get);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(propertiesListener, this);
        propertiesListener.startParticleTask();
        getLogger().info("HeadSmith enabled: loaded " + headsById.size() + " heads");
    }

    @Override
    public void onDisable() {
        if (propertiesListener != null) {
            propertiesListener.stopParticleTask();
        }
        headsById.clear();
        headIdByTextureId.clear();
        firstHeadByTag.clear();
        tagChildren.clear();
    }

    private void sendHelp(CommandSender sender) {
        String version = getDescription().getVersion();
        String bar = ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        sender.sendMessage(bar);
        sender.sendMessage(ChatColor.GOLD + "  HeadSmith " + ChatColor.GRAY + "v" + version);
        sender.sendMessage(ChatColor.GRAY + "  Aliases: " + ChatColor.WHITE + "/headsmith, /hs");
        sender.sendMessage(bar);

        if (sender.hasPermission("headsmith.catalog")) {
            sender.sendMessage(ChatColor.YELLOW + "  /headsmith show");
            sender.sendMessage(ChatColor.GRAY + "    Open the head catalog menu");
            sender.sendMessage(ChatColor.YELLOW + "  /headsmith search <query>");
            sender.sendMessage(ChatColor.GRAY + "    Search for heads by name or tag");
        }

        if (sender.hasPermission("headsmith.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "  /headsmith reload");
            sender.sendMessage(ChatColor.GRAY + "    Reload configuration and heads");
            sender.sendMessage(ChatColor.YELLOW + "  /headsmith give <id> [player] [amount]");
            sender.sendMessage(ChatColor.GRAY + "    Give a head to a player");
        }

        sender.sendMessage(ChatColor.YELLOW + "  /headsmith help");
        sender.sendMessage(ChatColor.GRAY + "    Show this help message");
        sender.sendMessage(bar);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("headsmith")) return false;

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("show")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }
            menus.openTagListMenu(player, 0);
            return true;
        }

        if (subCmd.equals("reload")) {
            if (!sender.hasPermission("headsmith.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] " + ChatColor.WHITE + "Reloading will re-register all recipes.");
                sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] " + ChatColor.WHITE + "This may take 30-60 seconds with ~3000 heads.");
                sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] " + ChatColor.RED + "The server will be laggy during the reload.");
                sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] " + ChatColor.WHITE + "Run " + ChatColor.AQUA + "/headsmith reload confirm" + ChatColor.WHITE + " to proceed.");
                return true;
            }
            reloadHeadsAsync(sender);
            menus = new HeadMenus(headsById, headStonecutterRecipes, firstHeadByTag, tagChildren, pdcHeadIdKey, this::makeHeadItem, tagOrderFirst, tagOrderLast);
            return true;
        }

        if (subCmd.equals("search")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /headsmith search <query>");
                return true;
            }
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            menus.openCatalogMenu(player, 0, query);
            return true;
        }

        if (subCmd.equals("give")) {
            if (!sender.hasPermission("headsmith.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /headsmith give <head_id> [player] [amount]");
                return true;
            }

            String headId = args[1];
            HeadDef def = headsById.get(headId);
            if (def == null) {
                sender.sendMessage(ChatColor.RED + "Unknown head: " + headId);
                return true;
            }

            Player target = null;
            int amount = 1;

            if (args.length >= 3) {
                Player found = Bukkit.getPlayer(args[2]);
                if (found != null) {
                    target = found;
                } else {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                        return true;
                    }
                }
            }

            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                    return true;
                }
            }

            if (target == null) {
                if (sender instanceof Player player) {
                    target = player;
                } else {
                    sender.sendMessage(ChatColor.RED + "Must specify a player when running from console.");
                    return true;
                }
            }

            amount = Math.max(1, Math.min(64, amount));
            ItemStack item = makeHeadItem(headId, amount);
            giveToInventoryOrDrop(target, item);

            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " +
                ChatColor.stripColor(color(def.name())) + " to " + target.getName());
            if (sender instanceof Player player && target != player) {
                target.sendMessage(ChatColor.GREEN + "You received " + amount + "x " +
                    ChatColor.stripColor(color(def.name())));
            }
            return true;
        }

        if (subCmd.equals("help")) {
            sendHelp(sender);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("headsmith")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            options.add("show");
            options.add("search");
            if (sender.hasPermission("headsmith.admin")) {
                options.add("reload");
                options.add("give");
            }
            options.add("help");
            return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String partial = args[1].toLowerCase();
            return headsById.keySet().stream()
                .filter(id -> id.toLowerCase().startsWith(partial))
                .limit(20)
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String partial = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return List.of();
    }

    // Config loading

    private void reloadHeadsSync() {
        loadHeadData();
        // Register all recipes synchronously (for startup)
        for (Recipe recipe : pendingRecipes) {
            Bukkit.addRecipe(recipe);
        }
        pendingRecipes.clear();
    }

    private void reloadHeadsAsync(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] Starting reload...");

        // Copy keys to remove and clear the list
        List<NamespacedKey> keysToRemove = new ArrayList<>(registeredRecipeKeys);
        registeredRecipeKeys.clear();

        // Load new head data (this populates pendingRecipes and registeredRecipeKeys)
        loadHeadData();

        int totalToRemove = keysToRemove.size();
        int totalToAdd = pendingRecipes.size();
        sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] Loaded " + headsById.size() + " heads. Removing " + totalToRemove + " old recipes, adding " + totalToAdd + " new recipes...");

        // First batch-remove old recipes, then batch-add new ones
        removeRecipesBatched(sender, keysToRemove, () -> {
            registerRecipesBatched(sender, () -> {
                sender.sendMessage(ChatColor.GREEN + "[HeadSmith] Reload complete! " + totalToAdd + " recipes registered.");
            });
        });
    }

    private void loadHeadData() {
        headsById.clear();
        headIdByTextureId.clear();
        firstHeadByTag.clear();
        tagChildren.clear();
        pendingRecipes.clear();

        reloadConfig();

        // Load exclusion sets from config
        excludedTags = new HashSet<>(getConfig().getStringList("excluded-tags"));
        excludedHeads = new HashSet<>(getConfig().getStringList("excluded-heads"));

        // Load tag ordering preferences
        tagOrderFirst = getConfig().getStringList("tag-order.first");
        tagOrderLast = getConfig().getStringList("tag-order.last");

        // Load bundled heads from JAR
        List<String> bundledFiles = readHeadsManifest();
        int jarHeadCount = 0;
        int jarFileCount = 0;
        int totalExcluded = 0;

        for (String resourcePath : bundledFiles) {
            String fileTag = filePathToTag(resourcePath);

            if (isTagExcluded(fileTag)) {
                getLogger().fine("Skipping excluded tag: " + fileTag);
                continue;
            }

            LoadResult result = loadHeadsFromJarResource(resourcePath, fileTag);
            jarHeadCount += result.loaded();
            totalExcluded += result.excluded();
            if (result.loaded() > 0) {
                jarFileCount++;
            }
        }

        getLogger().info("Loaded " + jarHeadCount + " heads from " + jarFileCount + " bundled files");

        // Load custom head files from data folder
        List<String> customFiles = getConfig().getStringList("custom-head-files");
        int customHeadCount = 0;

        for (String filePath : customFiles) {
            File headsFile = new File(getDataFolder(), filePath);
            if (!headsFile.exists()) {
                getLogger().warning("Custom head file not found: " + filePath);
                continue;
            }
            String fileTag = "custom/" + filePath.replaceFirst("\\.yml$", "");
            LoadResult result = loadHeadsFromFile(headsFile, filePath, fileTag);
            customHeadCount += result.loaded();
            totalExcluded += result.excluded();
            getLogger().info("Loaded " + result.loaded() + " custom heads from " + filePath);
        }

        int totalFound = jarHeadCount + customHeadCount + totalExcluded;
        getLogger().info("Total: " + totalFound + " heads found, " + totalExcluded + " excluded, " + headsById.size() + " enabled");

        // Build tag-to-first-head index and tag hierarchy for menu display
        for (HeadDef head : headsById.values()) {
            for (String tag : head.tags()) {
                firstHeadByTag.putIfAbsent(tag, head.id());

                // Build tag hierarchy (e.g., "alphabet/oak" -> parent "alphabet" has child "oak")
                int slashIndex = tag.indexOf('/');
                if (slashIndex > 0) {
                    String parent = tag.substring(0, slashIndex);
                    String child = tag.substring(slashIndex + 1);
                    tagChildren.computeIfAbsent(parent, k -> new LinkedHashSet<>()).add(child);
                }
            }
        }

        collectStonecutterRecipes();
        collectCraftingRecipes();
    }

    private List<String> readHeadsManifest() {
        List<String> files = new ArrayList<>();
        try (InputStream is = getResource("head-count.json")) {
            if (is == null) {
                getLogger().warning("head-count.json not found in JAR - using fallback list");
                return getFallbackHeadFiles();
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String key : obj.keySet()) {
                if (key.endsWith(".yml")) {
                    files.add(key);
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to read head-count.json: " + e.getMessage());
            return getFallbackHeadFiles();
        }
        return files;
    }

    private List<String> getFallbackHeadFiles() {
        return List.of(
            "heads/books.yml",
            "heads/barrels.yml",
            "heads/bottles.yml",
            "heads/buckets.yml",
            "heads/bundles.yml",
            "heads/candles.yml",
            "heads/chalices.yml",
            "heads/pumpkins.yml",
            "heads/misc.yml",
            "heads/mini_blocks.yml",
            "heads/alphabet/oak.yml",
            "heads/alphabet/birch.yml",
            "heads/alphabet/spruce.yml",
            "heads/alphabet/jungle.yml",
            "heads/alphabet/cherry_planks.yml",
            "heads/alphabet/mangrove_planks.yml",
            "heads/alphabet/cobblestone.yml",
            "heads/alphabet/diamond.yml",
            "heads/alphabet/emerald.yml",
            "heads/alphabet/gold.yml",
            "heads/alphabet/iron.yml",
            "heads/alphabet/quartz.yml",
            "heads/alphabet/ice.yml",
            "heads/alphabet/dirt.yml"
        );
    }

    private String filePathToTag(String filePath) {
        return filePath
            .replaceFirst("^heads/", "")
            .replaceFirst("\\.yml$", "");
    }

    private boolean isTagExcluded(String fileTag) {
        if (excludedTags.contains(fileTag)) {
            return true;
        }
        // Check if any parent tag is excluded (e.g., "alphabet" excludes "alphabet/oak")
        int slashIndex = fileTag.indexOf('/');
        if (slashIndex > 0) {
            String parentTag = fileTag.substring(0, slashIndex);
            if (excludedTags.contains(parentTag)) {
                return true;
            }
        }
        return false;
    }

    private LoadResult loadHeadsFromJarResource(String resourcePath, String fileTag) {
        try (InputStream is = getResource(resourcePath)) {
            if (is == null) {
                getLogger().warning("Resource not found in JAR: " + resourcePath);
                return new LoadResult(0, 0);
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
                new InputStreamReader(is, StandardCharsets.UTF_8));
            return processHeadsConfig(cfg, resourcePath, fileTag);
        } catch (IOException e) {
            getLogger().warning("Failed to load " + resourcePath + ": " + e.getMessage());
            return new LoadResult(0, 0);
        }
    }

    private LoadResult loadHeadsFromFile(File headsFile, String filePath, String fileTag) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(headsFile);
        return processHeadsConfig(cfg, filePath, fileTag);
    }

    private LoadResult processHeadsConfig(YamlConfiguration cfg, String source, String fileTag) {
        ConfigurationSection headsSec = cfg.getConfigurationSection("heads");
        if (headsSec == null) {
            getLogger().warning(source + " missing 'heads:' section");
            return new LoadResult(0, 0);
        }

        int loaded = 0;
        int excluded = 0;
        for (String headId : headsSec.getKeys(false)) {
            // Check if this specific head ID is excluded
            if (excludedHeads.contains(headId)) {
                getLogger().fine("Skipping excluded head: " + headId);
                excluded++;
                continue;
            }

            ConfigurationSection h = headsSec.getConfigurationSection(headId);
            if (h == null) continue;

            String base64 = requireString(h, "texture").orElse(null);
            if (base64 == null || base64.isBlank()) {
                getLogger().warning("Head '" + headId + "' missing texture in " + source);
                continue;
            }

            Optional<TextureInfo> texInfoOpt = parseTextureBase64(base64);
            if (texInfoOpt.isEmpty()) {
                getLogger().warning("Head '" + headId + "' has invalid base64 texture in " + source);
                continue;
            }
            TextureInfo texInfo = texInfoOpt.get();

            String name = requireString(h, "name").orElse(headId);
            List<String> lore = h.getStringList("lore");

            // Parse tags: file-source tag first, then explicit tags from YAML
            Set<String> tags = new LinkedHashSet<>();
            tags.add(fileTag);
            tags.addAll(h.getStringList("tags"));

            Set<HeadProperty> properties = HeadProperty.parseProperties(h.getStringList("properties"));
            List<DropRule> dropRules = HeadConfigParser.parseDropRules(h.getConfigurationSection("drops"));

            List<CraftShapedRecipeDef> shaped = new ArrayList<>();
            List<CraftShapelessRecipeDef> shapeless = new ArrayList<>();
            HeadConfigParser.parseCraftingRecipes(h.getConfigurationSection("recipes"), headId, shaped, shapeless);

            List<StonecutterRecipeDef> stonecut = HeadConfigParser.parseStonecutterRecipes(
                h.getConfigurationSection("recipes"), headId);

            HeadDef def = new HeadDef(headId, base64, texInfo.textureUrl(), texInfo.textureId(),
                name, lore, tags, properties, shaped, shapeless, stonecut, dropRules);

            if (headsById.containsKey(headId)) {
                throw new IllegalStateException("Duplicate head ID '" + headId + "' in " + source);
            }
            headsById.put(headId, def);
            headIdByTextureId.put(def.textureId(), headId);
            loaded++;
        }
        return new LoadResult(loaded, excluded);
    }

    private void collectStonecutterRecipes() {
        headStonecutterRecipes.clear();
        for (HeadDef head : headsById.values()) {
            for (StonecutterRecipeDef r : head.stonecutter()) {
                if (r.input().material != null) {
                    NamespacedKey key = new NamespacedKey(this, "stonecut_" + head.id() + "_" + r.id());
                    ItemStack result = makeHeadItem(head.id(), r.amount());
                    StonecuttingRecipe recipe = new StonecuttingRecipe(key, result,
                        new RecipeChoice.MaterialChoice(r.input().material));
                    pendingRecipes.add(recipe);
                    registeredRecipeKeys.add(key);
                } else if (r.input().headId != null) {
                    headStonecutterRecipes.add(new HeadStonecutterRecipe(r.input().headId, head.id(), r.amount()));
                }
            }
        }
    }

    private void collectCraftingRecipes() {
        for (HeadDef head : headsById.values()) {
            int totalRecipes = head.shaped().size() + head.shapeless().size();
            int index = 0;
            for (CraftShapedRecipeDef r : head.shaped()) {
                collectShapedRecipe(head, r, index++, totalRecipes);
            }
            for (CraftShapelessRecipeDef r : head.shapeless()) {
                collectShapelessRecipe(head, r, index++, totalRecipes);
            }
        }
    }

    private void collectShapedRecipe(HeadDef head, CraftShapedRecipeDef r, int index, int total) {
        String keyName = total == 1 ? "craft_" + head.id() : "craft_" + head.id() + "_" + index;
        NamespacedKey key = new NamespacedKey(this, keyName);
        ItemStack result = makeHeadItem(r.id, r.amount);

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(r.pattern.toArray(new String[0]));

        for (Map.Entry<Character, IngredientSpec> e : r.key.entrySet()) {
            IngredientSpec spec = e.getValue();
            if (spec.headId != null) {
                ItemStack headItem = makeHeadItem(spec.headId, 1);
                recipe.setIngredient(e.getKey(), new RecipeChoice.ExactChoice(headItem));
            } else if (spec.material != null) {
                recipe.setIngredient(e.getKey(), spec.material);
            }
        }

        pendingRecipes.add(recipe);
        registeredRecipeKeys.add(key);
    }

    private void collectShapelessRecipe(HeadDef head, CraftShapelessRecipeDef r, int index, int total) {
        String keyName = total == 1 ? "craft_" + head.id() : "craft_" + head.id() + "_" + index;
        NamespacedKey key = new NamespacedKey(this, keyName);
        ItemStack result = makeHeadItem(r.id, r.amount);

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);

        for (IngredientSpec spec : r.ingredients) {
            if (spec.headId != null) {
                ItemStack headItem = makeHeadItem(spec.headId, 1);
                recipe.addIngredient(new RecipeChoice.ExactChoice(headItem));
            } else if (spec.material != null) {
                recipe.addIngredient(spec.material);
            }
        }

        pendingRecipes.add(recipe);
        registeredRecipeKeys.add(key);
    }

    private void removeRecipesBatched(CommandSender sender, List<NamespacedKey> keys, Runnable onComplete) {
        if (keys.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        final int BATCH_SIZE = 50;
        final int totalKeys = keys.size();
        final int PROGRESS_INTERVAL_TICKS = 100; // 5 seconds
        new BukkitRunnable() {
            int index = 0;
            int tickCount = 0;
            @Override
            public void run() {
                int end = Math.min(index + BATCH_SIZE, totalKeys);
                for (int i = index; i < end; i++) {
                    Bukkit.removeRecipe(keys.get(i));
                }
                index = end;
                tickCount++;

                // Print progress every 5 seconds
                if (tickCount % PROGRESS_INTERVAL_TICKS == 0 && index < totalKeys) {
                    int percent = (index * 100) / totalKeys;
                    sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] Removing: " + index + "/" + totalKeys + " (" + percent + "%)");
                }

                if (index >= totalKeys) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    private void registerRecipesBatched(CommandSender sender, Runnable onComplete) {
        if (pendingRecipes.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        final int BATCH_SIZE = 50;
        final int totalRecipes = pendingRecipes.size();
        final int PROGRESS_INTERVAL_TICKS = 100; // 5 seconds
        new BukkitRunnable() {
            int index = 0;
            int tickCount = 0;
            @Override
            public void run() {
                int end = Math.min(index + BATCH_SIZE, totalRecipes);
                for (int i = index; i < end; i++) {
                    Bukkit.addRecipe(pendingRecipes.get(i));
                }
                index = end;
                tickCount++;

                // Print progress every 5 seconds
                if (tickCount % PROGRESS_INTERVAL_TICKS == 0 && index < totalRecipes) {
                    int percent = (index * 100) / totalRecipes;
                    sender.sendMessage(ChatColor.YELLOW + "[HeadSmith] Adding: " + index + "/" + totalRecipes + " (" + percent + "%)");
                }

                if (index >= totalRecipes) {
                    cancel();
                    pendingRecipes.clear();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    // Event handlers

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return;

        Optional<String> textureIdOpt = textureIdFromSkullBlock(skull);
        if (textureIdOpt.isEmpty()) return;

        String headId = headIdByTextureId.get(textureIdOpt.get());
        if (headId == null) return;

        HeadDef def = headsById.get(headId);
        if (def == null) return;

        event.setDropItems(false);

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silk = tool.getType() != Material.AIR && tool.containsEnchantment(Enchantment.SILK_TOUCH);

        List<ItemStack> drops = computeDrops(def, silk, tool.getType());
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) continue;
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }

        // Clean up light blocks and particle tracking
        if (propertiesListener != null) {
            propertiesListener.onHeadBreak(block, def);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.7f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Skull skull)) return;

        Optional<String> textureIdOpt = textureIdFromSkullBlock(skull);
        if (textureIdOpt.isEmpty()) return;

        String headId = headIdByTextureId.get(textureIdOpt.get());
        if (headId == null) return;

        // Cancel vanilla behavior and give custom head
        event.setCancelled(true);

        Player player = event.getPlayer();
        int targetSlot = event.getTargetSlot();

        ItemStack customHead = makeHeadItem(headId, 1);
        player.getInventory().setItem(targetSlot, customHead);
        player.getInventory().setHeldItemSlot(targetSlot);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HeadSmithMenuHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        switch (holder.getMenuType()) {
            case CATALOG, SEARCH_RESULTS -> menus.handleCatalogClick(player, (CatalogMenuHolder) holder, slot, event.getClick());
            case HEAD_DETAIL -> menus.handleDetailClick(player, (HeadDetailMenuHolder) holder, slot);
            case STONECUTTER_SELECT -> menus.handleStonecutterSelectClick(player, (StonecutterSelectMenuHolder) holder, slot);
            case TAG_LIST -> menus.handleTagListClick(player, (TagListMenuHolder) holder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStonecutterClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.STONECUTTER) return;
        if (!(event.getInventory() instanceof StonecutterInventory inv)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == 1) {
            ItemStack input = inv.getInputItem();
            if (input == null || input.getType() != Material.PLAYER_HEAD) return;

            String inputHeadId = getPdcString(input, pdcHeadIdKey).orElse(null);
            if (inputHeadId == null) return;

            boolean hasRecipes = headStonecutterRecipes.stream()
                .anyMatch(r -> r.inputHeadId().equals(inputHeadId));

            if (hasRecipes) {
                event.setCancelled(true);
                menus.openStonecutterSelectMenu(player, inputHeadId, 0);
                return;
            }
        }

        Bukkit.getScheduler().runTask(this, () -> checkAndOpenStonecutterMenu(player, inv));
    }

    @EventHandler
    public void onStonecutterDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() != InventoryType.STONECUTTER) return;
        if (!(event.getInventory() instanceof StonecutterInventory inv)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getRawSlots().contains(0)) {
            Bukkit.getScheduler().runTask(this, () -> checkAndOpenStonecutterMenu(player, inv));
        }
    }

    private void checkAndOpenStonecutterMenu(Player player, StonecutterInventory inv) {
        ItemStack input = inv.getInputItem();
        if (input == null || input.getType() != Material.PLAYER_HEAD) {
            updateStonecutterResult(inv);
            return;
        }

        String inputHeadId = getPdcString(input, pdcHeadIdKey).orElse(null);
        if (inputHeadId == null) {
            updateStonecutterResult(inv);
            return;
        }

        boolean hasRecipes = headStonecutterRecipes.stream()
            .anyMatch(r -> r.inputHeadId().equals(inputHeadId));

        if (hasRecipes) {
            menus.openStonecutterSelectMenu(player, inputHeadId, 0);
        } else {
            updateStonecutterResult(inv);
        }
    }

    private void updateStonecutterResult(StonecutterInventory inv) {
        ItemStack input = inv.getInputItem();
        if (input == null || input.getType() != Material.PLAYER_HEAD) {
            clearCustomStonecutterResult(inv);
            return;
        }

        String inputHeadId = getPdcString(input, pdcHeadIdKey).orElse(null);
        if (inputHeadId == null) {
            clearCustomStonecutterResult(inv);
            return;
        }

        List<HeadStonecutterRecipe> matchingRecipes = headStonecutterRecipes.stream()
            .filter(r -> r.inputHeadId().equals(inputHeadId))
            .toList();

        if (!matchingRecipes.isEmpty()) {
            ItemStack hintItem = new ItemStack(Material.STONECUTTER);
            ItemMeta meta = hintItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + "Click to select output");
                meta.setLore(List.of(
                    ChatColor.GRAY + "" + matchingRecipes.size() + " recipes available",
                    "",
                    ChatColor.YELLOW + "Click to open selection menu"
                ));
                meta.getPersistentDataContainer().set(pdcHeadIdKey, PersistentDataType.STRING, "_stonecutter_hint");
                hintItem.setItemMeta(meta);
            }
            inv.setResult(hintItem);
            return;
        }

        clearCustomStonecutterResult(inv);
    }

    private void clearCustomStonecutterResult(StonecutterInventory inv) {
        ItemStack currentResult = inv.getResult();
        if (currentResult == null) return;

        if (currentResult.getType() == Material.STONECUTTER || currentResult.getType() == Material.PLAYER_HEAD) {
            if (getPdcString(currentResult, pdcHeadIdKey).isPresent()) {
                inv.setResult(null);
            }
        }
    }

    // Helper methods

    private List<ItemStack> computeDrops(HeadDef def, boolean silkTouch, Material toolMaterial) {
        Optional<ToolCategory> toolCat = ToolCategory.fromMaterial(toolMaterial);

        // Check explicit rules first (including catch-all)
        for (DropRule rule : def.dropRules()) {
            if (rule.matches(silkTouch, toolCat)) {
                return rule.toDrops(this::makeHeadItem);
            }
        }

        // Implicit fallback: always drop itself
        return List.of(makeHeadItem(def.id(), 1));
    }

    public ItemStack makeHeadItem(String headId, int amount) {
        return HeadUtils.makeHeadItem(headsById.get(headId), amount, pdcHeadIdKey, getLogger());
    }
}
