package ru.annakyt.inventorylayout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InventoryLayoutClient implements ClientModInitializer {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("inventory-layout.json");
    private static final List<String> savedLayout = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        loadLayout();
    }

    public static boolean hasLayout() {
        return savedLayout.size() == 36;
    }

    public static void saveLayout(Inventory inventory) {
        savedLayout.clear();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            savedLayout.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }

        JsonArray json = new JsonArray();
        savedLayout.forEach(json::add);
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, json.toString());
        } catch (IOException exception) {
            System.err.println("[Inventory Layout] Save failed: " + exception.getMessage());
        }
    }

    private static void loadLayout() {
        savedLayout.clear();
        if (!Files.exists(CONFIG)) return;
        try {
            JsonArray json = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonArray();
            for (JsonElement element : json) savedLayout.add(element.getAsString());
        } catch (Exception exception) {
            savedLayout.clear();
            System.err.println("[Inventory Layout] Load failed: " + exception.getMessage());
        }
    }

    public static void applyLayout(AbstractContainerMenu menu, Inventory inventory) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null || !hasLayout()) return;

        for (int sourceMenuSlot = 0; sourceMenuSlot < menu.slots.size(); sourceMenuSlot++) {
            Slot source = menu.slots.get(sourceMenuSlot);
            if (source.container == inventory || source.getItem().isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(source.getItem().getItem()).toString();
            int targetInventoryIndex = savedLayout.indexOf(itemId);
            if (targetInventoryIndex < 0) continue;

            int targetMenuSlot = findPlayerSlot(menu, inventory, targetInventoryIndex);
            if (targetMenuSlot < 0 || targetMenuSlot == sourceMenuSlot) continue;

            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, minecraft.player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, targetMenuSlot, 0, ClickType.PICKUP, minecraft.player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, minecraft.player);
        }
    }

    private static int findPlayerSlot(AbstractContainerMenu menu, Inventory inventory, int inventoryIndex) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == inventory && slot.getSlotIndex() == inventoryIndex) return i;
        }
        return -1;
    }
}
