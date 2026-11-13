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
        List<String> newLayout = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            newLayout.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }

        JsonArray json = new JsonArray();
        newLayout.forEach(json::add);
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, json.toString());
            savedLayout.clear();
            savedLayout.addAll(newLayout);
            System.out.println("[Inventory Layout] Layout saved.");
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
            if (savedLayout.size() != 36) savedLayout.clear();
        } catch (Exception exception) {
            savedLayout.clear();
            System.err.println("[Inventory Layout] Load failed: " + exception.getMessage());
        }
    }

    public static void applyLayout(AbstractContainerMenu menu, Inventory inventory) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null || !hasLayout()) return;

        // Process each saved inventory position independently. This preserves repeated
        // item types in different slots instead of always targeting the first match.
        for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
            String wantedId = savedLayout.get(inventoryIndex);
            if (wantedId.isEmpty()) continue;

            int targetMenuSlot = findPlayerSlot(menu, inventory, inventoryIndex);
            if (targetMenuSlot < 0) continue;

            int sourceMenuSlot = findContainerStack(menu, inventory, wantedId);
            if (sourceMenuSlot < 0) continue;

            Slot target = menu.slots.get(targetMenuSlot);
            if (!target.mayPlace(menu.slots.get(sourceMenuSlot).getItem())) continue;

            // Use normal container clicks so the server remains authoritative.
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, minecraft.player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, targetMenuSlot, 0, ClickType.PICKUP, minecraft.player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, minecraft.player);
        }
    }

    private static int findContainerStack(AbstractContainerMenu menu, Inventory inventory, String itemId) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == inventory || slot.getItem().isEmpty()) continue;
            String currentId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();
            if (itemId.equals(currentId)) return i;
        }
        return -1;
    }

    private static int findPlayerSlot(AbstractContainerMenu menu, Inventory inventory, int inventoryIndex) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == inventory && slot.index == inventoryIndex) return i;
        }
        return -1;
    }
}
