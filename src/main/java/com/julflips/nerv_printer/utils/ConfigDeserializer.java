package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

public final class ConfigDeserializer {

    private static BlockPos jsonToBlockPos(JsonObject obj) {
        return new BlockPos(
            obj.get("x").getAsInt(),
            obj.get("y").getAsInt(),
            obj.get("z").getAsInt()
        );
    }

    private static Vec3 jsonToVec3d(JsonObject obj) {
        return new Vec3(
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble()
        );
    }

    private static Tuple<BlockPos, Vec3> jsonToBlockPosVecPair(JsonObject obj) {
        BlockPos pos = jsonToBlockPos(obj.getAsJsonObject("blockPos"));
        Vec3 openPos = jsonToVec3d(obj.getAsJsonObject("openPos"));
        return new Tuple<>(pos, openPos);
    }

    /**
     * Data container for config values
     */
    public static class ConfigData {
        public String type;
        public Tuple<BlockPos, Vec3> reset;
        public Tuple<BlockPos, Vec3> cartographyTable;
        public Tuple<BlockPos, Vec3> finishedMapChest;
        public Tuple<BlockPos, Vec3> usedToolChest;
        public Tuple<BlockPos, Vec3> bed;
        public ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests;
        public Tuple<Vec3, Tuple<Float, Float>> dumpStation;
        public ArrayList<Tuple<Vec3, Tuple<Float, Float>>> dumpStations;
        public BlockPos mapCorner;
        public BlockPos upperMapCorner;
        public HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict;
        public Set<ItemStack> toolSet;
    }

    private static JsonObject getObj(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject()
            ? root.getAsJsonObject(key)
            : null;
    }

    public static ConfigData readFromJson(Path file) throws IOException {
        Gson gson = new Gson();

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            ConfigData data = new ConfigData();

            data.type = root.get("type").getAsString();

            JsonObject obj;
            obj = getObj(root, "reset");
            data.reset = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "cartographyTable");
            data.cartographyTable = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "finishedMapChest");
            data.finishedMapChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "usedToolChest");
            data.usedToolChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "bed");
            data.bed = obj != null ? jsonToBlockPosVecPair(obj) : null;

            data.mapMaterialChests = new ArrayList<>();
            if (root.has("mapMaterialChests")) {
                for (JsonElement e : root.getAsJsonArray("mapMaterialChests")) {
                    data.mapMaterialChests.add(
                        jsonToBlockPosVecPair(e.getAsJsonObject())
                    );
                }
            }

            data.dumpStations = new ArrayList<>();
            if (root.has("dumpStations")) {
                for (JsonElement e : root.getAsJsonArray("dumpStations")) {
                    JsonObject dump = e.getAsJsonObject();

                    Vec3 pos = jsonToVec3d(dump.getAsJsonObject("pos"));
                    float yaw = dump.get("yaw").getAsFloat();
                    float pitch = dump.get("pitch").getAsFloat();
                    data.dumpStations.add(new Tuple<>(pos, new Tuple<>(yaw, pitch)));
                }
            } else {
                data.dumpStation = null;
                data.dumpStations = null;
            }
            if (!data.dumpStations.isEmpty()) {
                data.dumpStation = data.dumpStations.get(0);
            }

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));
            data.upperMapCorner = jsonToBlockPos(root.getAsJsonObject("upperMapCorner"));

            data.materialDict = new HashMap<>();
            if (root.has("materialDict")) {
                JsonObject materialDictObj = root.getAsJsonObject("materialDict");
                for (String key : materialDictObj.keySet()) {
                    Identifier id = Identifier.parse(key);
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    ArrayList<Tuple<BlockPos, Vec3>> list = new ArrayList<>();
                    for (JsonElement e : materialDictObj.getAsJsonArray(key)) {
                        list.add(jsonToBlockPosVecPair(e.getAsJsonObject()));
                    }
                    data.materialDict.put(item, list);
                }
            }

            if (net.minecraft.client.Minecraft.getInstance() == null
                || net.minecraft.client.Minecraft.getInstance().level == null ) return data;

            data.toolSet = new HashSet<>();
            HolderLookup.Provider registries =
                net.minecraft.client.Minecraft.getInstance().level.registryAccess();
            if (root.has("toolSet") && root.get("toolSet").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("toolSet")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("item")) continue;
                    ItemStack stack = jsonToItemStack(o, registries);
                    if (!stack.isEmpty()) {
                        data.toolSet.add(stack);
                    }
                }
            }

            return data;
        }
    }

    private static ItemStack jsonToItemStack(JsonObject obj, HolderLookup.Provider registries) {
        Identifier itemId = Identifier.parse(obj.get("item").getAsString());
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        ItemStack stack = new ItemStack(item);
        // Backward compatibility: older configs may not contain enchantments
        if (!obj.has("enchantments") || !obj.get("enchantments").isJsonArray()) {
            return stack;
        }
        ItemEnchantments.Mutable builder =
            new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        // Get the dynamic enchantment registry using our passed-in wrapper lookup
        HolderLookup.RegistryLookup<Enchantment> enchantmentRegistry =
            registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (JsonElement enchantmentElement : obj.getAsJsonArray("enchantments")) {
            if (!enchantmentElement.isJsonObject()) continue;
            JsonObject enchantmentObj = enchantmentElement.getAsJsonObject();
            if (!enchantmentObj.has("id") || !enchantmentObj.has("level")) continue;
            Identifier enchantmentId = Identifier.parse(enchantmentObj.get("id").getAsString());
            int level = enchantmentObj.get("level").getAsInt();
            if (level <= 0) continue;
            ResourceKey<Enchantment> enchantKey = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
            Optional<Holder.Reference<Enchantment>> enchantmentEntry =
                enchantmentRegistry.get(enchantKey);
            enchantmentEntry.ifPresent(entry -> builder.upgrade(entry, level));
        }
        stack.set(DataComponents.ENCHANTMENTS, builder.toImmutable());
        return stack;
    }
}
