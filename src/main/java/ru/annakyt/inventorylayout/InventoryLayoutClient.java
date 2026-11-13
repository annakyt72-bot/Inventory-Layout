package ru.annakyt.inventorylayout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static final List<String> savedLayout = new ArrayList<>(36);

    // Sorting is performed over several client ticks. This gives the server time
    // to acknowledge each click before the next click is sent.
    private static boolean sorting;
    private static AbstractContainerMenu sortingMenu;
    private static Inventory sortingInventory;
    private static int nextInventoryIndex;
    private static int sourceSlot = -1;
    private static int targetSlot = -1;
    private static int clickPhase;

    @Override
    public void onInitializeClient() {
        loadLayout();
        ClientTickEvents.END_CLIENT_TICK.register(InventoryLayoutClient::tickSorting);
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
        if (sorting || minecraft.player == null || minecraft.gameMode == null || !hasLayout()) return;
        if (menu != minecraft.player.containerMenu) return;

        sortingMenu = menu;
        sortingInventory = inventory;
        nextInventoryIndex = 0;
        sourceSlot = -1;
        targetSlot = -1;
        clickPhase = 0;
        sorting = true;
    }

    private static void tickSorting(Minecraft minecraft) {
        if (!sorting) return;

        if (minecraft.player == null || minecraft.gameMode == null
                || minecraft.player.containerMenu != sortingMenu
                || minecraft.screen == null) {
            stopSorting();
            return;
        }

        // Phase 0: find the next item and its destination.
        if (clickPhase == 0) {
            while (nextInventoryIndex < 36) {
                String wantedId = savedLayout.get(nextInventoryIndex);
                int currentTarget = findPlayerSlot(sortingMenu, sortingInventory, nextInventoryIndex);

                if (wantedId.isEmpty() || currentTarget < 0) {
                    nextInventoryIndex++;
                    continue;
                }

                ItemStack current = sortingMenu.slots.get(currentTarget).getItem();
                if (!current.isEmpty() && wantedId.equals(itemId(current))) {
                    nextInventoryIndex++;
                    continue;
                }

                int foundSource = findContainerStack(sortingMenu, sortingInventory, wantedId);
                if (foundSource < 0) {
                    nextInventoryIndex++;
                    continue;
                }

                Slot destination = sortingMenu.slots.get(currentTarget);
                Slot source = sortingMenu.slots.get(foundSource);
                if (!destination.mayPlace(source.getItem()) || !destination.mayPickup(minecraft.player)) {
                    nextInventoryIndex++;
                    continue;
                }

                sourceSlot = foundSource;
                targetSlot = currentTarget;
                clickPhase = 1;
                break;
            }

            if (nextInventoryIndex >= 36) {
                stopSorting();
                return;
            }
        }

        // One click per tick: don't send a whole click sequence against stale slots.
        if (clickPhase == 1) {
            minecraft.gameMode.handleInventoryMouseClick(
                    sortingMenu.containerId, sourceSlot, 0, ClickType.PICKUP, minecraft.player);
            clickPhase = 2;
        } else if (clickPhase == 2) {
            minecraft.gameMode.handleInventoryMouseClick(
                    sortingMenu.containerId, targetSlot, 0, ClickType.PICKUP, minecraft.player);
            clickPhase = 3;
        } else if (clickPhase == 3) {
            minecraft.gameMode.handleInventoryMouseClick(
                    sortingMenu.containerId, sourceSlot, 0, ClickType.PICKUP, minecraft.player);
            clickPhase = 0;
            nextInventoryIndex++;
        }
    }

    private static void stopSorting() {
        sorting = false;
        sortingMenu = null;
        sortingInventory = null;
        sourceSlot = -1;
        targetSlot = -1;
        clickPhase = 0;
    }

    private static int findContainerStack(AbstractContainerMenu menu, Inventory inventory, String wantedId) {
        // Only search container slots (for example, the chest), never the player's
        // own inventory slots. Otherwise the mod can pick an item it already owns.
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == inventory || slot.getItem().isEmpty()) continue;
            if (wantedId.equals(itemId(slot.getItem()))) return i;
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

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
