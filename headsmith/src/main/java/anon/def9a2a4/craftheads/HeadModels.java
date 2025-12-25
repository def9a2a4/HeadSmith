package anon.def9a2a4.headsmith;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static anon.def9a2a4.headsmith.HeadUtils.asInt;

record HeadDef(
    String id,
    String textureBase64,
    String textureUrl,
    String textureId,
    String name,
    List<String> lore,
    Set<String> tags,
    Set<HeadProperty> properties,
    List<CraftShapedRecipeDef> shaped,
    List<CraftShapelessRecipeDef> shapeless,
    List<StonecutterRecipeDef> stonecutter,
    List<DropRule> dropRules
) {}

record HeadStonecutterRecipe(String inputHeadId, String outputHeadId, int amount) {}

enum ToolCategory {
    PICKAXE, AXE, SHOVEL, HOE, SHEARS;

    static Optional<ToolCategory> fromMaterial(Material mat) {
        if (mat == null) return Optional.empty();
        String name = mat.name();
        if (name.endsWith("_PICKAXE")) return Optional.of(PICKAXE);
        if (name.endsWith("_AXE")) return Optional.of(AXE);
        if (name.endsWith("_SHOVEL")) return Optional.of(SHOVEL);
        if (name.endsWith("_HOE")) return Optional.of(HOE);
        if (mat == Material.SHEARS) return Optional.of(SHEARS);
        return Optional.empty();
    }

    static Optional<ToolCategory> fromString(String s) {
        if (s == null) return Optional.empty();
        try { return Optional.of(valueOf(s.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}

record DropRule(Optional<Boolean> matchesSilkTouch, Optional<ToolCategory> matchesTool, List<ItemSpec> drops) {
    boolean matches(boolean silkTouch, Optional<ToolCategory> tool) {
        if (matchesSilkTouch.isPresent() && matchesSilkTouch.get() != silkTouch) return false;
        if (matchesTool.isPresent() && !matchesTool.equals(tool)) return false;
        return true;
    }

    List<ItemStack> toDrops(BiFunction<String, Integer, ItemStack> headItemMaker) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSpec spec : drops) {
            out.add(spec.toItemStack(headItemMaker));
        }
        return out;
    }
}

record ItemSpec(Optional<String> headId, Optional<Material> material, int amount) {
    static ItemSpec fromMap(Map<?, ?> m) {
        String head = m.containsKey("head") ? String.valueOf(m.get("head")) : null;
        String matS = m.containsKey("material") ? String.valueOf(m.get("material")) : null;
        Material mat = matS == null ? null : Material.matchMaterial(matS.toUpperCase(Locale.ROOT));
        int amt = asInt(m.get("amount"), 1);
        return new ItemSpec(Optional.ofNullable(head), Optional.ofNullable(mat), Math.max(1, amt));
    }

    ItemStack toItemStack(BiFunction<String, Integer, ItemStack> headItemMaker) {
        if (headId.isPresent()) {
            return headItemMaker.apply(headId.get(), amount);
        }
        if (material.isPresent()) {
            return new ItemStack(material.get(), amount);
        }
        return new ItemStack(Material.AIR);
    }
}

final class IngredientSpec {
    final Material material;
    final String headId;

    IngredientSpec(Material material, String headId) {
        this.material = material;
        this.headId = headId;
    }

    static IngredientSpec fromMap(Map<?, ?> m) {
        String matS = m.containsKey("material") ? String.valueOf(m.get("material")) : null;
        Material mat = matS == null ? null : Material.matchMaterial(matS.toUpperCase(Locale.ROOT));
        String head = m.containsKey("head") ? String.valueOf(m.get("head")) : null;
        return new IngredientSpec(mat, head);
    }

    boolean matchesItem(ItemStack item, HeadIdResolver resolver) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (material != null) return item.getType() == material;
        if (headId != null) {
            return resolver.resolve(item).map(headId::equals).orElse(false);
        }
        return false;
    }
}

record StonecutterRecipeDef(String id, String outputHeadId, int amount, IngredientSpec input) {}

final class CraftShapedRecipeDef {
    final String id;
    final String outputHeadId;
    final int amount;
    final List<String> pattern;
    final Map<Character, IngredientSpec> key;
    final int patternMinRow, patternMinCol, patternMaxRow, patternMaxCol;
    final int effectiveWidth, effectiveHeight;

    CraftShapedRecipeDef(String id, String outputHeadId, int amount, List<String> pattern, Map<Character, IngredientSpec> key) {
        this.id = id;
        this.outputHeadId = outputHeadId;
        this.amount = amount;
        this.pattern = pattern;
        this.key = key;

        int minR = pattern.size(), maxR = -1, minC = Integer.MAX_VALUE, maxC = -1;
        for (int r = 0; r < pattern.size(); r++) {
            String line = pattern.get(r);
            for (int c = 0; c < line.length(); c++) {
                if (line.charAt(c) != ' ') {
                    minR = Math.min(minR, r);
                    maxR = Math.max(maxR, r);
                    minC = Math.min(minC, c);
                    maxC = Math.max(maxC, c);
                }
            }
        }
        this.patternMinRow = minR;
        this.patternMinCol = minC == Integer.MAX_VALUE ? 0 : minC;
        this.patternMaxRow = maxR;
        this.patternMaxCol = maxC;
        this.effectiveWidth = maxC >= 0 ? maxC - patternMinCol + 1 : 0;
        this.effectiveHeight = maxR >= 0 ? maxR - minR + 1 : 0;
    }

    boolean matches(ItemStack[] matrix, HeadIdResolver resolver) {
        if (matrix == null || (matrix.length != 4 && matrix.length != 9)) return false;
        if (pattern.isEmpty() || effectiveWidth == 0) return false;

        int gridSize = matrix.length == 4 ? 2 : 3;
        int minRow = gridSize, maxRow = -1, minCol = gridSize, maxCol = -1;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                ItemStack item = matrix[row * gridSize + col];
                if (item != null && item.getType() != Material.AIR) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }

        if (maxRow < 0) return effectiveWidth == 0;

        int gridWidth = maxCol - minCol + 1;
        int gridHeight = maxRow - minRow + 1;
        if (gridWidth != effectiveWidth || gridHeight != effectiveHeight) return false;

        for (int pRow = 0; pRow < effectiveHeight; pRow++) {
            int patternRow = patternMinRow + pRow;
            String line = patternRow < pattern.size() ? pattern.get(patternRow) : "";
            for (int pCol = 0; pCol < effectiveWidth; pCol++) {
                int patternCol = patternMinCol + pCol;
                char c = patternCol < line.length() ? line.charAt(patternCol) : ' ';
                int gridRow = minRow + pRow;
                int gridCol = minCol + pCol;
                ItemStack item = matrix[gridRow * gridSize + gridCol];

                if (c == ' ') {
                    if (item != null && item.getType() != Material.AIR) return false;
                    continue;
                }

                IngredientSpec req = key.get(c);
                if (req == null) return false;
                if (!req.matchesItem(item, resolver)) return false;
            }
        }
        return true;
    }

    int[] findPatternOffset(ItemStack[] matrix) {
        if (matrix == null || (matrix.length != 4 && matrix.length != 9)) return null;

        int gridSize = matrix.length == 4 ? 2 : 3;
        int minRow = gridSize, maxRow = -1, minCol = gridSize, maxCol = -1;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                ItemStack item = matrix[row * gridSize + col];
                if (item != null && item.getType() != Material.AIR) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }

        if (maxRow < 0) return null;

        int gridWidth = maxCol - minCol + 1;
        int gridHeight = maxRow - minRow + 1;
        if (gridWidth != effectiveWidth || gridHeight != effectiveHeight) return null;

        return new int[]{minRow, minCol};
    }
}

final class CraftShapelessRecipeDef {
    final String id;
    final String outputHeadId;
    final int amount;
    final List<IngredientSpec> ingredients;

    CraftShapelessRecipeDef(String id, String outputHeadId, int amount, List<IngredientSpec> ingredients) {
        this.id = id;
        this.outputHeadId = outputHeadId;
        this.amount = amount;
        this.ingredients = ingredients;
    }

    boolean matches(ItemStack[] matrix, HeadIdResolver resolver) {
        if (matrix == null || (matrix.length != 4 && matrix.length != 9)) return false;

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : matrix) {
            if (it != null && it.getType() != Material.AIR) {
                items.add(it);
            }
        }

        if (items.size() != ingredients.size()) return false;

        boolean[] used = new boolean[items.size()];
        for (IngredientSpec req : ingredients) {
            boolean found = false;
            for (int i = 0; i < items.size(); i++) {
                if (used[i]) continue;
                ItemStack it = items.get(i);
                if (req.matchesItem(it, resolver)) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}

final class Match {
    private final HeadDef outputHead;
    private final String recipeId;
    private final int amountPerCraft;
    private final CraftShapedRecipeDef shaped;
    private final CraftShapelessRecipeDef shapeless;
    private final HeadIdResolver resolver;
    private final BiFunction<String, Integer, ItemStack> headItemMaker;
    private final NamespacedKey pdcCraftKey;

    private Match(HeadDef outputHead, String recipeId, int amountPerCraft,
                  CraftShapedRecipeDef shaped, CraftShapelessRecipeDef shapeless,
                  HeadIdResolver resolver, BiFunction<String, Integer, ItemStack> headItemMaker,
                  NamespacedKey pdcCraftKey) {
        this.outputHead = outputHead;
        this.recipeId = recipeId;
        this.amountPerCraft = amountPerCraft;
        this.shaped = shaped;
        this.shapeless = shapeless;
        this.resolver = resolver;
        this.headItemMaker = headItemMaker;
        this.pdcCraftKey = pdcCraftKey;
    }

    static Match shaped(HeadDef head, CraftShapedRecipeDef r, HeadIdResolver resolver,
                        BiFunction<String, Integer, ItemStack> headItemMaker, NamespacedKey pdcCraftKey) {
        return new Match(head, r.id, r.amount, r, null, resolver, headItemMaker, pdcCraftKey);
    }

    static Match shapeless(HeadDef head, CraftShapelessRecipeDef r, HeadIdResolver resolver,
                           BiFunction<String, Integer, ItemStack> headItemMaker, NamespacedKey pdcCraftKey) {
        return new Match(head, r.id, r.amount, null, r, resolver, headItemMaker, pdcCraftKey);
    }

    ItemStack makeResult() {
        ItemStack out = headItemMaker.apply(outputHead.id(), amountPerCraft);
        ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pdcCraftKey, PersistentDataType.STRING, recipeId);
            out.setItemMeta(meta);
        }
        return out;
    }
}

final class HeadConfigParser {

    private HeadConfigParser() {}

    static List<DropRule> parseDropRules(ConfigurationSection dropsSec) {
        if (dropsSec == null) return List.of();
        List<Map<?, ?>> raw = dropsSec.getMapList("on_break");
        if (raw.isEmpty()) return List.of();

        List<DropRule> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Optional<Boolean> silkTouchCond = Optional.empty();
            Optional<ToolCategory> toolCond = Optional.empty();

            Object whenObj = m.get("when");
            if (whenObj instanceof Map<?, ?> whenMap) {
                Object st = whenMap.get("silk_touch");
                silkTouchCond = st instanceof Boolean b ? Optional.of(b) : Optional.empty();

                Object toolObj = whenMap.get("tool");
                if (toolObj instanceof String ts) {
                    toolCond = ToolCategory.fromString(ts);
                }
            }

            Object dropsObj = m.get("drops");
            List<ItemSpec> drops = new ArrayList<>();
            if (dropsObj instanceof List<?> lst) {
                for (Object o : lst) {
                    if (o instanceof Map<?, ?> dm) {
                        drops.add(ItemSpec.fromMap(dm));
                    }
                }
            }

            out.add(new DropRule(silkTouchCond, toolCond, drops));
        }
        return out;
    }

    static void parseCraftingRecipes(ConfigurationSection recipesSec, String headId,
                                     List<CraftShapedRecipeDef> shapedOut,
                                     List<CraftShapelessRecipeDef> shapelessOut) {
        if (recipesSec == null) return;
        ConfigurationSection craftSec = recipesSec.getConfigurationSection("craft");
        if (craftSec == null) return;

        for (Map<?, ?> m : craftSec.getMapList("shaped")) {
            Object idObj = m.get("id");
            String id = idObj != null ? String.valueOf(idObj) : headId + "_shaped";
            int amount = asInt(m.get("amount"), 1);

            List<String> pattern = new ArrayList<>();
            Object patObj = m.get("pattern");
            if (patObj instanceof List<?> pl) {
                for (Object row : pl) {
                    pattern.add(String.valueOf(row));
                }
            }

            Map<Character, IngredientSpec> key = new HashMap<>();
            Object keyObj = m.get("key");
            if (keyObj instanceof Map<?, ?> km) {
                for (Map.Entry<?, ?> e : km.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (k.length() != 1) continue;
                    if (e.getValue() instanceof Map<?, ?> iv) {
                        key.put(k.charAt(0), IngredientSpec.fromMap(iv));
                    }
                }
            }

            shapedOut.add(new CraftShapedRecipeDef(id, headId, amount, pattern, key));
        }

        for (Map<?, ?> m : craftSec.getMapList("shapeless")) {
            Object idObj = m.get("id");
            String id = idObj != null ? String.valueOf(idObj) : headId + "_shapeless";
            int amount = asInt(m.get("amount"), 1);

            List<IngredientSpec> ingredients = new ArrayList<>();
            Object ingObj = m.get("ingredients");
            if (ingObj instanceof List<?> il) {
                for (Object o : il) {
                    if (o instanceof Map<?, ?> im) {
                        ingredients.add(IngredientSpec.fromMap(im));
                    }
                }
            }

            shapelessOut.add(new CraftShapelessRecipeDef(id, headId, amount, ingredients));
        }
    }

    static List<StonecutterRecipeDef> parseStonecutterRecipes(ConfigurationSection recipesSec, String headId) {
        if (recipesSec == null) return List.of();
        List<Map<?, ?>> raw = recipesSec.getMapList("stonecutter");
        if (raw.isEmpty()) return List.of();

        List<StonecutterRecipeDef> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Object idObj = m.get("id");
            String id = idObj != null ? String.valueOf(idObj) : headId + "_stonecut";
            int amount = asInt(m.get("amount"), 1);
            Object inputObj = m.get("input");
            if (!(inputObj instanceof Map<?, ?> im)) continue;
            IngredientSpec input = IngredientSpec.fromMap(im);
            out.add(new StonecutterRecipeDef(id, headId, amount, input));
        }
        return out;
    }
}
