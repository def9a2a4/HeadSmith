package anon.def9a2a4.craftheads;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static anon.def9a2a4.craftheads.HeadUtils.color;
import static anon.def9a2a4.craftheads.HeadUtils.giveToInventoryOrDrop;
import static anon.def9a2a4.craftheads.HeadUtils.getPdcString;

enum MenuType {
    CATALOG, SEARCH_RESULTS, HEAD_DETAIL, STONECUTTER_SELECT, TAG_LIST
}

abstract class CraftHeadsMenuHolder implements InventoryHolder {
    protected Inventory inventory;

    @Override
    public Inventory getInventory() { return inventory; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }
    abstract MenuType getMenuType();
}

final class CatalogMenuHolder extends CraftHeadsMenuHolder {
    private final int page;
    private final String searchQuery;
    private final String tagFilter;
    private final List<HeadDef> displayedHeads;
    private final int totalHeads;

    CatalogMenuHolder(int page, String searchQuery, String tagFilter, List<HeadDef> displayedHeads, int totalHeads) {
        this.page = page;
        this.searchQuery = searchQuery;
        this.tagFilter = tagFilter;
        this.displayedHeads = displayedHeads;
        this.totalHeads = totalHeads;
    }

    int getPage() { return page; }
    String getSearchQuery() { return searchQuery; }
    String getTagFilter() { return tagFilter; }
    List<HeadDef> getDisplayedHeads() { return displayedHeads; }
    int getTotalHeads() { return totalHeads; }

    @Override
    MenuType getMenuType() {
        return searchQuery != null ? MenuType.SEARCH_RESULTS : MenuType.CATALOG;
    }
}

final class HeadDetailMenuHolder extends CraftHeadsMenuHolder {
    private final HeadDef headDef;
    private final List<String> navigationStack; // head IDs visited before this one
    private final int catalogReturnPage;
    private final String catalogReturnSearchQuery;
    private final Map<Integer, String> clickableHeadSlots; // slot -> headId

    HeadDetailMenuHolder(HeadDef headDef, List<String> navigationStack,
                         int catalogReturnPage, String catalogReturnSearchQuery) {
        this.headDef = headDef;
        this.navigationStack = navigationStack;
        this.catalogReturnPage = catalogReturnPage;
        this.catalogReturnSearchQuery = catalogReturnSearchQuery;
        this.clickableHeadSlots = new HashMap<>();
    }

    HeadDef getHeadDef() { return headDef; }
    List<String> getNavigationStack() { return navigationStack; }
    int getCatalogReturnPage() { return catalogReturnPage; }
    String getCatalogReturnSearchQuery() { return catalogReturnSearchQuery; }
    Map<Integer, String> getClickableHeadSlots() { return clickableHeadSlots; }

    @Override
    MenuType getMenuType() { return MenuType.HEAD_DETAIL; }
}

final class StonecutterSelectMenuHolder extends CraftHeadsMenuHolder {
    private final String inputHeadId;
    private final List<HeadStonecutterRecipe> availableRecipes;
    private final int page;

    StonecutterSelectMenuHolder(String inputHeadId, List<HeadStonecutterRecipe> availableRecipes, int page) {
        this.inputHeadId = inputHeadId;
        this.availableRecipes = availableRecipes;
        this.page = page;
    }

    String getInputHeadId() { return inputHeadId; }
    List<HeadStonecutterRecipe> getAvailableRecipes() { return availableRecipes; }
    int getPage() { return page; }

    @Override
    MenuType getMenuType() { return MenuType.STONECUTTER_SELECT; }
}

final class TagListMenuHolder extends CraftHeadsMenuHolder {
    private final int page;
    private final String parentTag;  // null for root, "alphabet" when viewing subtags
    private final List<String> displayedTags;
    private final int totalTags;

    TagListMenuHolder(int page, String parentTag, List<String> displayedTags, int totalTags) {
        this.page = page;
        this.parentTag = parentTag;
        this.displayedTags = displayedTags;
        this.totalTags = totalTags;
    }

    int getPage() { return page; }
    String getParentTag() { return parentTag; }
    List<String> getDisplayedTags() { return displayedTags; }
    int getTotalTags() { return totalTags; }

    @Override
    MenuType getMenuType() { return MenuType.TAG_LIST; }
}

record Pagination(int page, int totalPages, int startIndex, int endIndex) {
    static Pagination of(int requestedPage, int totalItems, int itemsPerPage) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        return new Pagination(page, totalPages, startIndex, endIndex);
    }

    boolean hasPrev() { return page > 0; }
    boolean hasNext() { return page < totalPages - 1; }
}

final class HeadMenus {
    static final int HEADS_PER_PAGE = 36;
    static final int STONECUTTER_ITEMS_PER_PAGE = 45;

    private final Map<String, HeadDef> headsById;
    private final List<HeadStonecutterRecipe> headStonecutterRecipes;
    private final Map<String, String> firstHeadByTag;
    private final Map<String, Set<String>> tagChildren;  // parent tag -> child tags
    private final NamespacedKey pdcHeadIdKey;
    private final BiFunction<String, Integer, ItemStack> headItemMaker;

    HeadMenus(Map<String, HeadDef> headsById, List<HeadStonecutterRecipe> headStonecutterRecipes,
              Map<String, String> firstHeadByTag, Map<String, Set<String>> tagChildren,
              NamespacedKey pdcHeadIdKey, BiFunction<String, Integer, ItemStack> headItemMaker) {
        this.headsById = headsById;
        this.headStonecutterRecipes = headStonecutterRecipes;
        this.firstHeadByTag = firstHeadByTag;
        this.tagChildren = tagChildren;
        this.pdcHeadIdKey = pdcHeadIdKey;
        this.headItemMaker = headItemMaker;
    }

    void openCatalogMenu(Player player, int page, String searchQuery) {
        openCatalogMenu(player, page, searchQuery, null);
    }

    void openCatalogMenu(Player player, int page, String searchQuery, String tagFilter) {
        List<HeadDef> allHeads;

        if (tagFilter != null && !tagFilter.isBlank()) {
            allHeads = headsById.values().stream()
                .filter(h -> h.tags().contains(tagFilter))
                .toList();
        } else if (searchQuery != null && !searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase();
            allHeads = headsById.values().stream()
                .filter(h -> ChatColor.stripColor(color(h.name())).toLowerCase().contains(query)
                          || h.id().toLowerCase().contains(query))
                .toList();
        } else {
            allHeads = new ArrayList<>(headsById.values());
        }

        Pagination pag = Pagination.of(page, allHeads.size(), HEADS_PER_PAGE);
        List<HeadDef> pageHeads = allHeads.subList(pag.startIndex(), pag.endIndex());

        CatalogMenuHolder holder = new CatalogMenuHolder(pag.page(), searchQuery, tagFilter, pageHeads, allHeads.size());

        String title;
        if (tagFilter != null) {
            title = ChatColor.DARK_PURPLE + "Tag: " + ChatColor.WHITE + tagFilter;
        } else if (searchQuery != null) {
            title = ChatColor.DARK_PURPLE + "Search: " + ChatColor.WHITE + searchQuery;
        } else {
            title = ChatColor.DARK_PURPLE + "Head Catalog";
        }

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFillerPane();
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(0, createInfoItem(allHeads.size(), pag.page(), pag.totalPages(), searchQuery, tagFilter));
        inv.setItem(3, createTagsButton());
        inv.setItem(4, createPageItemPaper(pag));
        inv.setItem(8, createSearchButton());

        for (int i = 0; i < pageHeads.size(); i++) {
            inv.setItem(9 + i, makeHeadDisplayItem(pageHeads.get(i)));
        }

        inv.setItem(45, createNavigationArrow("Previous Page", pag.hasPrev()));
        inv.setItem(49, createCloseButton());
        inv.setItem(53, createNavigationArrow("Next Page", pag.hasNext()));

        player.openInventory(inv);
    }

    void openHeadDetailMenu(Player player, HeadDef head, int returnPage, String returnSearchQuery) {
        openHeadDetailMenu(player, head, new ArrayList<>(), returnPage, returnSearchQuery);
    }

    void openHeadDetailMenu(Player player, HeadDef head, List<String> navigationStack,
                            int catalogReturnPage, String catalogReturnSearchQuery) {
        HeadDetailMenuHolder holder = new HeadDetailMenuHolder(head, navigationStack,
            catalogReturnPage, catalogReturnSearchQuery);

        String title = truncateTitle(ChatColor.DARK_PURPLE + ChatColor.stripColor(color(head.name())));

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFillerPane();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(0, createBackButton());
        inv.setItem(4, headItemMaker.apply(head.id(), 1));
        inv.setItem(8, createGiveButton());

        displayLoreSection(inv, head);

        int currentRow = 2;
        if (!head.shaped().isEmpty()) {
            displayShapedRecipe(inv, holder, head, head.shaped().get(0), currentRow);
            currentRow++;
        }
        if (!head.shapeless().isEmpty()) {
            displayShapelessRecipe(inv, holder, head, head.shapeless().get(0), currentRow);
            currentRow++;
        }
        if (!head.stonecutter().isEmpty()) {
            displayStonecutterRecipe(inv, holder, head, head.stonecutter().get(0), currentRow);
        }

        player.openInventory(inv);
    }

    void openStonecutterSelectMenu(Player player, String inputHeadId, int page) {
        List<HeadStonecutterRecipe> recipes = headStonecutterRecipes.stream()
            .filter(r -> r.inputHeadId().equals(inputHeadId))
            .toList();

        if (recipes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No stonecutter recipes available for this head.");
            return;
        }

        Pagination pag = Pagination.of(page, recipes.size(), STONECUTTER_ITEMS_PER_PAGE);
        List<HeadStonecutterRecipe> pageRecipes = recipes.subList(pag.startIndex(), pag.endIndex());

        StonecutterSelectMenuHolder holder = new StonecutterSelectMenuHolder(inputHeadId, pageRecipes, pag.page());

        String title = truncateTitle(ChatColor.DARK_PURPLE + "Stonecutter: " + getHeadName(inputHeadId));

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFillerPane();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        ItemStack inputItem = headItemMaker.apply(inputHeadId, 1);
        ItemMeta inputMeta = inputItem.getItemMeta();
        if (inputMeta != null) {
            appendLore(inputMeta, "", ChatColor.GRAY + "Select an output below");
            inputItem.setItemMeta(inputMeta);
        }
        inv.setItem(49, inputItem);
        inv.setItem(4, createPageItem(pag, ChatColor.GRAY + "" + recipes.size() + " recipes available"));

        inv.setItem(45, createNavigationArrow("Previous Page", pag.hasPrev()));
        inv.setItem(53, createNavigationArrow("Next Page", pag.hasNext()));
        inv.setItem(48, createCloseButton());

        for (int i = 0; i < pageRecipes.size(); i++) {
            HeadStonecutterRecipe recipe = pageRecipes.get(i);
            ItemStack outputItem = headItemMaker.apply(recipe.outputHeadId(), recipe.amount());
            ItemMeta meta = outputItem.getItemMeta();
            if (meta != null) {
                if (recipe.amount() > 1) {
                    appendLore(meta, "", ChatColor.YELLOW + "Click to craft",
                        ChatColor.GRAY + "Produces: " + ChatColor.WHITE + recipe.amount());
                } else {
                    appendLore(meta, "", ChatColor.YELLOW + "Click to craft");
                }
                outputItem.setItemMeta(meta);
            }
            inv.setItem(i, outputItem);
        }

        player.openInventory(inv);
    }

    void openTagListMenu(Player player, int page) {
        openTagListMenu(player, page, null);
    }

    void openTagListMenu(Player player, int page, String parentTag) {
        List<String> tagsToShow;
        String title;

        if (parentTag == null) {
            // Root level: show top-level tags (first segment of hierarchical, or full flat tags)
            tagsToShow = getTopLevelTags();
            title = ChatColor.DARK_PURPLE + "Browse by Tag";
        } else {
            // Subtag level: show children of parent tag
            Set<String> children = tagChildren.get(parentTag);
            tagsToShow = children != null ? new ArrayList<>(children) : List.of();
            title = ChatColor.DARK_PURPLE + "Tag: " + ChatColor.WHITE + parentTag;
        }

        Pagination pag = Pagination.of(page, tagsToShow.size(), HEADS_PER_PAGE);
        List<String> pageTags = tagsToShow.subList(pag.startIndex(), pag.endIndex());

        TagListMenuHolder holder = new TagListMenuHolder(pag.page(), parentTag, pageTags, tagsToShow.size());

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFillerPane();
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Back button: goes to parent level or catalog
        if (parentTag != null) {
            inv.setItem(0, createBackToTagsButton());
        } else {
            inv.setItem(0, createBackToCatalogButton());
        }
        inv.setItem(4, createPageItemPaper(pag));

        for (int i = 0; i < pageTags.size(); i++) {
            String tag = pageTags.get(i);
            // For display, use the full tag path to find a representative head
            String fullTag = parentTag != null ? parentTag + "/" + tag : tag;
            String headId = firstHeadByTag.get(fullTag);
            if (headId == null && tagChildren.containsKey(tag)) {
                // It's a parent tag, find first child's head
                Set<String> children = tagChildren.get(tag);
                if (children != null && !children.isEmpty()) {
                    String firstChild = children.iterator().next();
                    headId = firstHeadByTag.get(tag + "/" + firstChild);
                }
            }

            ItemStack item;
            if (headId != null) {
                item = headItemMaker.apply(headId, 1);
            } else {
                item = new ItemStack(Material.NAME_TAG);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + tag);

                // Count heads with this tag (or subtag prefix)
                long count;
                boolean hasChildren = tagChildren.containsKey(parentTag != null ? fullTag : tag);
                if (hasChildren) {
                    // Count all heads that have tags starting with this prefix
                    String prefix = (parentTag != null ? fullTag : tag) + "/";
                    count = headsById.values().stream()
                        .filter(h -> h.tags().stream().anyMatch(t -> t.startsWith(prefix)))
                        .count();
                } else {
                    count = headsById.values().stream()
                        .filter(h -> h.tags().contains(fullTag))
                        .count();
                }

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "" + count + " heads");
                lore.add("");
                if (hasChildren) {
                    lore.add(ChatColor.YELLOW + "Click to view subtags");
                } else {
                    lore.add(ChatColor.YELLOW + "Click to browse");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(9 + i, item);
        }

        inv.setItem(45, createNavigationArrow("Previous Page", pag.hasPrev()));
        inv.setItem(49, createCloseButton());
        inv.setItem(53, createNavigationArrow("Next Page", pag.hasNext()));

        player.openInventory(inv);
    }

    /**
     * Get top-level tags for the root tag menu.
     * For hierarchical tags like "alphabet/oak", returns "alphabet".
     * For flat tags like "storage", returns "storage".
     */
    private List<String> getTopLevelTags() {
        List<String> result = new ArrayList<>();
        for (String tag : firstHeadByTag.keySet()) {
            int slashIndex = tag.indexOf('/');
            String topLevel = slashIndex > 0 ? tag.substring(0, slashIndex) : tag;
            if (!result.contains(topLevel)) {
                result.add(topLevel);
            }
        }
        return result;
    }

    void handleCatalogClick(Player player, CatalogMenuHolder holder, int slot) {
        Pagination pag = Pagination.of(holder.getPage(), holder.getTotalHeads(), HEADS_PER_PAGE);

        switch (slot) {
            case 3 -> openTagListMenu(player, 0);
            case 45 -> { if (pag.hasPrev()) openCatalogMenu(player, pag.page() - 1, holder.getSearchQuery(), holder.getTagFilter()); }
            case 49 -> player.closeInventory();
            case 53 -> { if (pag.hasNext()) openCatalogMenu(player, pag.page() + 1, holder.getSearchQuery(), holder.getTagFilter()); }
            default -> {
                if (slot >= 9 && slot <= 44) {
                    int index = slot - 9;
                    List<HeadDef> displayed = holder.getDisplayedHeads();
                    if (index < displayed.size()) {
                        openHeadDetailMenu(player, displayed.get(index), holder.getPage(), holder.getSearchQuery());
                    }
                }
            }
        }
    }

    void handleDetailClick(Player player, HeadDetailMenuHolder holder, int slot) {
        switch (slot) {
            case 0 -> {
                // Back button - navigate up the stack or return to catalog
                List<String> stack = holder.getNavigationStack();
                if (stack.isEmpty()) {
                    openCatalogMenu(player, holder.getCatalogReturnPage(), holder.getCatalogReturnSearchQuery());
                } else {
                    String prevHeadId = stack.get(stack.size() - 1);
                    List<String> newStack = new ArrayList<>(stack.subList(0, stack.size() - 1));
                    HeadDef prevHead = headsById.get(prevHeadId);
                    if (prevHead != null) {
                        openHeadDetailMenu(player, prevHead, newStack,
                            holder.getCatalogReturnPage(), holder.getCatalogReturnSearchQuery());
                    } else {
                        openCatalogMenu(player, holder.getCatalogReturnPage(), holder.getCatalogReturnSearchQuery());
                    }
                }
            }
            case 8 -> {
                if (player.hasPermission("craftheads.admin")) {
                    ItemStack head = headItemMaker.apply(holder.getHeadDef().id(), 1);
                    giveToInventoryOrDrop(player, head);
                    player.sendMessage(ChatColor.GREEN + "Gave you 1x " +
                        ChatColor.stripColor(color(holder.getHeadDef().name())));
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                }
            }
            default -> {
                // Check if this slot contains a clickable head ingredient
                String clickedHeadId = holder.getClickableHeadSlots().get(slot);
                if (clickedHeadId != null) {
                    HeadDef clickedHead = headsById.get(clickedHeadId);
                    if (clickedHead != null) {
                        // Build new navigation stack with current head added
                        List<String> newStack = new ArrayList<>(holder.getNavigationStack());
                        newStack.add(holder.getHeadDef().id());
                        openHeadDetailMenu(player, clickedHead, newStack,
                            holder.getCatalogReturnPage(), holder.getCatalogReturnSearchQuery());
                    }
                }
            }
        }
    }

    void handleStonecutterSelectClick(Player player, StonecutterSelectMenuHolder holder, int slot) {
        List<HeadStonecutterRecipe> recipes = holder.getAvailableRecipes();
        int totalRecipes = (int) headStonecutterRecipes.stream()
            .filter(r -> r.inputHeadId().equals(holder.getInputHeadId()))
            .count();
        Pagination pag = Pagination.of(holder.getPage(), totalRecipes, STONECUTTER_ITEMS_PER_PAGE);

        switch (slot) {
            case 45 -> { if (pag.hasPrev()) openStonecutterSelectMenu(player, holder.getInputHeadId(), pag.page() - 1); }
            case 48 -> player.closeInventory();
            case 53 -> { if (pag.hasNext()) openStonecutterSelectMenu(player, holder.getInputHeadId(), pag.page() + 1); }
            default -> {
                if (slot >= 0 && slot < 45 && slot < recipes.size()) {
                    HeadStonecutterRecipe recipe = recipes.get(slot);

                    int inputSlot = -1;
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        ItemStack invItem = player.getInventory().getItem(i);
                        if (invItem != null && invItem.getType() == Material.PLAYER_HEAD) {
                            String itemHeadId = getPdcString(invItem, pdcHeadIdKey).orElse(null);
                            if (holder.getInputHeadId().equals(itemHeadId)) {
                                inputSlot = i;
                                break;
                            }
                        }
                    }

                    if (inputSlot == -1) {
                        player.sendMessage(ChatColor.RED + "You need the input head in your inventory to craft this.");
                        return;
                    }

                    ItemStack inputItem = player.getInventory().getItem(inputSlot);
                    if (inputItem.getAmount() > 1) {
                        inputItem.setAmount(inputItem.getAmount() - 1);
                    } else {
                        player.getInventory().setItem(inputSlot, null);
                    }

                    ItemStack result = headItemMaker.apply(recipe.outputHeadId(), recipe.amount());
                    giveToInventoryOrDrop(player, result);

                    player.sendMessage(ChatColor.GREEN + "Crafted " + recipe.amount() + "x " + getHeadName(recipe.outputHeadId()));
                    player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.0f);
                }
            }
        }
    }

    void handleTagListClick(Player player, TagListMenuHolder holder, int slot) {
        Pagination pag = Pagination.of(holder.getPage(), holder.getTotalTags(), HEADS_PER_PAGE);
        String parentTag = holder.getParentTag();

        switch (slot) {
            case 0 -> {
                // Back button: go to parent level or catalog
                if (parentTag != null) {
                    openTagListMenu(player, 0, null);  // Back to root tag list
                } else {
                    openCatalogMenu(player, 0, null);  // Back to catalog
                }
            }
            case 45 -> { if (pag.hasPrev()) openTagListMenu(player, pag.page() - 1, parentTag); }
            case 49 -> player.closeInventory();
            case 53 -> { if (pag.hasNext()) openTagListMenu(player, pag.page() + 1, parentTag); }
            default -> {
                if (slot >= 9 && slot <= 44) {
                    int index = slot - 9;
                    List<String> displayed = holder.getDisplayedTags();
                    if (index < displayed.size()) {
                        String clickedTag = displayed.get(index);
                        String fullTag = parentTag != null ? parentTag + "/" + clickedTag : clickedTag;

                        // Check if this tag has children (is a parent tag)
                        if (tagChildren.containsKey(fullTag)) {
                            // Drill down into subtags
                            openTagListMenu(player, 0, fullTag);
                        } else {
                            // Leaf tag: open catalog filtered by this tag
                            openCatalogMenu(player, 0, null, fullTag);
                        }
                    }
                }
            }
        }
    }

    // Display helpers

    private ItemStack makeHeadDisplayItem(HeadDef def) {
        ItemStack item = headItemMaker.apply(def.id(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            appendLore(meta, "", ChatColor.YELLOW + "Click to view details");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createIngredientDisplay(IngredientSpec spec, HeadDetailMenuHolder holder, int slot) {
        if (spec.material != null) return new ItemStack(spec.material, 1);
        if (spec.headId != null) {
            ItemStack item = headItemMaker.apply(spec.headId, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                appendLore(meta, "", ChatColor.YELLOW + "Click to view details");
                item.setItemMeta(meta);
            }
            holder.getClickableHeadSlots().put(slot, spec.headId);
            return item;
        }
        return new ItemStack(Material.BARRIER);
    }

    private ItemStack getPatternCell(String patternRow, int col, Map<Character, IngredientSpec> key,
                                     ItemStack emptySlot, HeadDetailMenuHolder holder, int slot) {
        if (col >= patternRow.length()) return emptySlot;
        char ch = patternRow.charAt(col);
        if (ch == ' ') return emptySlot;
        IngredientSpec ing = key.get(ch);
        return ing != null ? createIngredientDisplay(ing, holder, slot) : emptySlot;
    }

    private void displayLoreSection(Inventory inv, HeadDef head) {
        ItemStack infoPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = infoPane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Description");
            List<String> lore = new ArrayList<>();
            for (String line : head.lore()) lore.add(color(line));
            if (lore.isEmpty()) lore.add(ChatColor.GRAY + "(No description)");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "ID: " + head.id());
            meta.setLore(lore);
            infoPane.setItemMeta(meta);
        }
        for (int i = 10; i <= 16; i++) inv.setItem(i, infoPane.clone());
    }

    private void displayShapedRecipe(Inventory inv, HeadDetailMenuHolder holder, HeadDef head,
                                      CraftShapedRecipeDef recipe, int row) {
        int baseSlot = row * 9;
        inv.setItem(baseSlot, createRecipeLabel("Shaped", "3x3 crafting grid"));

        ItemStack emptySlot = createEmptySlot();

        for (int patRow = 0; patRow < Math.min(recipe.pattern.size(), 3); patRow++) {
            int rowSlot = (row + patRow) * 9;
            String patternRowStr = recipe.pattern.get(patRow);
            for (int c = 0; c < 3; c++) {
                int slot = rowSlot + 2 + c;
                if (slot < 54) {
                    inv.setItem(slot, getPatternCell(patternRowStr, c, recipe.key, emptySlot, holder, slot));
                }
            }
        }

        inv.setItem(baseSlot + 6, createRecipeArrow());
        inv.setItem(baseSlot + 7, headItemMaker.apply(head.id(), recipe.amount));
    }

    private void displayShapelessRecipe(Inventory inv, HeadDetailMenuHolder holder, HeadDef head,
                                        CraftShapelessRecipeDef recipe, int row) {
        int baseSlot = row * 9;
        inv.setItem(baseSlot, createRecipeLabel("Shapeless", "Any arrangement"));

        int col = 2;
        for (int i = 0; i < Math.min(recipe.ingredients.size(), 5); i++) {
            int slot = baseSlot + col + i;
            inv.setItem(slot, createIngredientDisplay(recipe.ingredients.get(i), holder, slot));
        }

        inv.setItem(baseSlot + 7, createRecipeArrow());
        inv.setItem(baseSlot + 8, headItemMaker.apply(head.id(), recipe.amount));
    }

    private void displayStonecutterRecipe(Inventory inv, HeadDetailMenuHolder holder, HeadDef head,
                                          StonecutterRecipeDef recipe, int row) {
        int baseSlot = row * 9;
        inv.setItem(baseSlot, createRecipeLabel("Stonecutter", "Use a stonecutter"));
        int inputSlot = baseSlot + 2;
        inv.setItem(inputSlot, createIngredientDisplay(recipe.input(), holder, inputSlot));
        inv.setItem(baseSlot + 4, createRecipeArrow());
        inv.setItem(baseSlot + 6, headItemMaker.apply(head.id(), recipe.amount()));
    }

    // Helpers

    private String getHeadName(String headId) {
        HeadDef head = headsById.get(headId);
        return head != null ? ChatColor.stripColor(color(head.name())) : headId;
    }

    private static String truncateTitle(String title) {
        return truncateTitle(title, 32);
    }

    private static String truncateTitle(String title, int maxLen) {
        return title.length() > maxLen ? title.substring(0, maxLen) : title;
    }

    private static void appendLore(ItemMeta meta, String... lines) {
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        for (String line : lines) lore.add(line);
        meta.setLore(lore);
    }

    private static ItemStack makeItem(Material material, String name, ChatColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((color != null ? color : ChatColor.WHITE) + name);
            if (lore.length > 0) meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createFillerPane() {
        return makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private static ItemStack createEmptySlot() {
        return makeItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private static ItemStack createCloseButton() {
        return makeItem(Material.BARRIER, "Close", ChatColor.RED);
    }

    private static ItemStack createRecipeArrow() {
        return makeItem(Material.ARROW, "=>", ChatColor.WHITE);
    }

    private static ItemStack createBackButton() {
        return makeItem(Material.ARROW, "Back to Catalog", ChatColor.YELLOW);
    }

    private static ItemStack createGiveButton() {
        return makeItem(Material.EMERALD, "Give Head", ChatColor.GREEN,
            ChatColor.GRAY + "Admin only", ChatColor.GRAY + "Click to receive this head");
    }

    private static ItemStack createSearchButton() {
        return makeItem(Material.COMPASS, "Search Heads", ChatColor.AQUA,
            ChatColor.GRAY + "Use " + ChatColor.WHITE + "/heads search <query>",
            ChatColor.GRAY + "to filter heads by name");
    }

    private static ItemStack createPageItem(Pagination pag) {
        return createPageItem(pag, null);
    }

    private static ItemStack createPageItem(Pagination pag, String extraInfo) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Page " + (pag.page() + 1) + " / " + pag.totalPages());
            if (extraInfo != null) meta.setLore(List.of(extraInfo));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNavigationArrow(String name, boolean enabled) {
        if (!enabled) {
            return makeItem(Material.GRAY_STAINED_GLASS_PANE, name, ChatColor.DARK_GRAY,
                ChatColor.GRAY + "No more pages");
        }
        return makeItem(Material.ARROW, name, ChatColor.GREEN);
    }

    private static ItemStack createInfoItem(int totalHeads, int currentPage, int totalPages, String searchQuery, String tagFilter) {
        List<String> lore = new ArrayList<>();
        if (tagFilter != null) lore.add(ChatColor.GOLD + "Tag: " + ChatColor.WHITE + tagFilter);
        if (searchQuery != null) lore.add(ChatColor.AQUA + "Search: " + ChatColor.WHITE + searchQuery);
        lore.add(ChatColor.GRAY + "Total heads: " + ChatColor.WHITE + totalHeads);
        lore.add(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (currentPage + 1) + "/" + totalPages);
        return makeItem(Material.BOOK, "Head Catalog", ChatColor.GOLD, lore.toArray(String[]::new));
    }

    private static ItemStack createTagsButton() {
        return makeItem(Material.NAME_TAG, "Browse by Tag", ChatColor.GOLD,
            ChatColor.GRAY + "Click to browse heads by category");
    }

    private static ItemStack createBackToCatalogButton() {
        return makeItem(Material.ARROW, "Back to Catalog", ChatColor.YELLOW);
    }

    private static ItemStack createBackToTagsButton() {
        return makeItem(Material.ARROW, "Back to Tags", ChatColor.YELLOW);
    }

    private static ItemStack createPageItemPaper(Pagination pag) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Page " + (pag.page() + 1) + "/" + pag.totalPages());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createRecipeLabel(String type, String description) {
        Material mat = switch (type.toLowerCase()) {
            case "shaped", "shapeless" -> Material.CRAFTING_TABLE;
            case "stonecutter" -> Material.STONECUTTER;
            default -> Material.BOOK;
        };
        if (description != null) {
            return makeItem(mat, type + " Recipe", ChatColor.GOLD, ChatColor.GRAY + description);
        }
        return makeItem(mat, type + " Recipe", ChatColor.GOLD);
    }
}
